package de.mhus.nimbus.world.shared.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for JobExecutor beans.
 * Discovers and manages all JobExecutor implementations.
 * Similar to CommandService pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutorRegistry {

    private final ApplicationContext applicationContext;
    private volatile Map<String, JobExecutor> executors;

    /**
     * Get executors lazily (on first access).
     * Thread-safe double-check locking pattern.
     */
    private Map<String, JobExecutor> getExecutors() {
        if (executors == null) {
            synchronized (this) {
                if (executors == null) {
                    Map<String, JobExecutor> executorBeans =
                            applicationContext.getBeansOfType(JobExecutor.class);
                    executors = new ConcurrentHashMap<>();

                    for (JobExecutor executor : executorBeans.values()) {
                        String name = executor.getExecutorName();
                        if (executors.containsKey(name)) {
                            log.warn("Duplicate executor name detected: {}", name);
                        }
                        executors.put(name, executor);
                    }

                    log.info("Registered {} job executors: {}",
                            executors.size(), executors.keySet());
                }
            }
        }
        return executors;
    }

    /**
     * Get executor by name.
     */
    public Optional<JobExecutor> getExecutor(String name) {
        return Optional.ofNullable(getExecutors().get(name));
    }

    /**
     * Check if executor exists.
     */
    public boolean hasExecutor(String name) {
        return getExecutors().containsKey(name);
    }

    /**
     * Get all registered executor names.
     */
    public Set<String> getExecutorNames() {
        return getExecutors().keySet();
    }
}
