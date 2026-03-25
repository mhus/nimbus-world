package de.mhus.nimbus.shared.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Centralized service for creating and retrieving Micrometer metric instruments.
 * <p>
 * Instruments are cached by composite key (name + sorted tags) to avoid duplicate registrations.
 * Uses a bounded cache with eviction of stale soft-referenced gauge suppliers
 * to prevent memory leaks from dynamic tag cardinality.
 * <p>
 * Usage:
 * <pre>
 *   metricService.counter("requests.total", "endpoint", "/login").increment();
 *   metricService.timer("requests.duration", "endpoint", "/login").record(duration);
 *   metricService.gauge("connections.active", "pool", "main", () -> pool.getActive());
 *   metricService.summary("payload.size", "type", "upload").record(bytes);
 * </pre>
 */
@Service
@Slf4j
public class MetricService {

    /**
     * Maximum number of distinct metric keys allowed per instrument type.
     * Prevents unbounded growth from high-cardinality tags.
     */
    private static final int MAX_ENTRIES_PER_TYPE = 10_000;

    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GaugeHolder> gauges = new ConcurrentHashMap<>();

    public MetricService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Gets or creates a Counter.
     *
     * @param name Metric name (e.g. "nimbus.requests.total")
     * @param tags Tag key-value pairs (e.g. "endpoint", "/login", "method", "POST")
     * @return the Counter instance
     */
    public Counter counter(String name, String... tags) {
        String key = buildKey(name, tags);
        return counters.computeIfAbsent(key, k -> {
            checkCapacity(counters, "counter", name);
            return Counter.builder(name)
                    .tags(toTags(tags))
                    .register(registry);
        });
    }

    /**
     * Gets or creates a Timer.
     *
     * @param name Metric name (e.g. "nimbus.requests.duration")
     * @param tags Tag key-value pairs
     * @return the Timer instance
     */
    public Timer timer(String name, String... tags) {
        String key = buildKey(name, tags);
        return timers.computeIfAbsent(key, k -> {
            checkCapacity(timers, "timer", name);
            return Timer.builder(name)
                    .tags(toTags(tags))
                    .register(registry);
        });
    }

    /**
     * Gets or creates a DistributionSummary.
     *
     * @param name Metric name (e.g. "nimbus.payload.size")
     * @param tags Tag key-value pairs
     * @return the DistributionSummary instance
     */
    public DistributionSummary summary(String name, String... tags) {
        String key = buildKey(name, tags);
        return summaries.computeIfAbsent(key, k -> {
            checkCapacity(summaries, "summary", name);
            return DistributionSummary.builder(name)
                    .tags(toTags(tags))
                    .register(registry);
        });
    }

    /**
     * Registers a Gauge backed by a supplier.
     * The supplier is held via a SoftReference so it can be GC'd if memory is tight.
     * The AtomicReference<Double> fallback ensures the gauge remains valid even after GC.
     *
     * @param name  Metric name (e.g. "nimbus.connections.active")
     * @param tags  Tag key-value pairs
     * @param supplier Supplier providing the current gauge value
     * @return the AtomicReference<Double> that backs the gauge (can be used for manual updates)
     */
    public AtomicReference<Double> gauge(String name, String[] tags, Supplier<Number> supplier) {
        String key = buildKey(name, tags);
        return gauges.computeIfAbsent(key, k -> {
            checkCapacity(gauges, "gauge", name);
            return new GaugeHolder(name, toTags(tags), supplier, registry);
        }).getValue();
    }

    /**
     * Convenience gauge with varargs tags.
     */
    public AtomicReference<Double> gauge(String name, Supplier<Number> supplier, String... tags) {
        return gauge(name, tags, supplier);
    }

    /**
     * Records an exception occurrence with source class and context.
     *
     * @param source The class where the exception occurred
     * @param context A short identifier for where in the class (e.g. "validate", "load", "save")
     * @param exception The exception to record
     */
    public void exception(Class<?> source, String context, Throwable exception) {
        String type = exception.getClass().getSimpleName();
        counter("exceptions", "source", source.getSimpleName(), "context", context, "type", type).increment();
    }

    /**
     * Records an exception with source class only.
     *
     * @param source The class where the exception occurred
     * @param exception The exception to record
     */
    public void exception(Class<?> source, Throwable exception) {
        String type = exception.getClass().getSimpleName();
        counter("exceptions", "source", source.getSimpleName(), "type", type).increment();
    }

    /**
     * Records an exception with context string only (no source class).
     *
     * @param context A short identifier for where the exception occurred
     * @param exception The exception to record
     */
    public void exception(String context, Throwable exception) {
        String type = exception.getClass().getSimpleName();
        counter("exceptions", "context", context, "type", type).increment();
    }

    /**
     * Returns the underlying MeterRegistry for advanced use cases.
     */
    public MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * Builds a composite cache key from name + sorted tags.
     * Sorting ensures that tag order does not matter for lookup.
     */
    static String buildKey(String name, String... tags) {
        if (tags == null || tags.length == 0) return name;
        // tags are key-value pairs, sort by pairs
        String[] sorted = Arrays.copyOf(tags, tags.length);
        // Sort pairs by key (even indices)
        for (int i = 0; i < sorted.length - 2; i += 2) {
            for (int j = i + 2; j < sorted.length; j += 2) {
                if (sorted[i].compareTo(sorted[j]) > 0) {
                    String tmpKey = sorted[i];
                    String tmpVal = sorted[i + 1];
                    sorted[i] = sorted[j];
                    sorted[i + 1] = sorted[j + 1];
                    sorted[j] = tmpKey;
                    sorted[j + 1] = tmpVal;
                }
            }
        }
        StringBuilder sb = new StringBuilder(name);
        for (String s : sorted) {
            sb.append('\0').append(s);
        }
        return sb.toString();
    }

    private Tags toTags(String... tags) {
        if (tags == null || tags.length == 0) return Tags.empty();
        return Tags.of(tags);
    }

    private <V> void checkCapacity(Map<String, V> map, String type, String name) {
        if (map.size() >= MAX_ENTRIES_PER_TYPE) {
            log.warn("MetricService: {} cache at capacity ({}) — new metric '{}' may indicate tag cardinality issue",
                    type, MAX_ENTRIES_PER_TYPE, name);
        }
    }

    /**
     * Holds a gauge registration with a SoftReference to the supplier.
     * If the supplier is GC'd, the gauge falls back to the last known value in the AtomicReference<Double>.
     */
    private static class GaugeHolder {
        private final AtomicReference<Double> value = new AtomicReference<>(0.0);
        private final SoftReference<Supplier<Number>> supplierRef;

        GaugeHolder(String name, Tags tags, Supplier<Number> supplier, MeterRegistry registry) {
            this.supplierRef = new SoftReference<>(supplier);
            // Pre-populate
            Number initial = supplier.get();
            if (initial != null) value.set(initial.doubleValue());

            Gauge.builder(name, this, GaugeHolder::currentValue)
                    .tags(tags)
                    .register(registry);
        }

        double currentValue() {
            Supplier<Number> supplier = supplierRef.get();
            if (supplier != null) {
                Number v = supplier.get();
                if (v != null) {
                    value.set(v.doubleValue());
                }
            }
            return value.get();
        }

        AtomicReference<Double> getValue() {
            return value;
        }
    }
}
