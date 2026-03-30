package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing WLogicRule entities.
 * Auto-computes the {@code affected} field from spelCondition and effects on save.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WLogicRuleService {

    private final WLogicRuleRepository repository;

    /**
     * Regex to find flag references in SpEL expressions.
     * Matches "flags.xxx" where xxx is a valid identifier (letters, digits, underscores).
     */
    private static final Pattern FLAGS_PATTERN = Pattern.compile("flags\\.([a-zA-Z_][a-zA-Z0-9_]*)");

    public Optional<WLogicRule> findById(String id) {
        return repository.findById(id);
    }

    public Optional<WLogicRule> findByWorldIdAndName(String worldId, String name) {
        return repository.findByWorldIdAndName(worldId, name);
    }

    public List<WLogicRule> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    /**
     * Save a rule with auto-computed affected flags.
     * The affected field is derived from:
     * 1. Flag names referenced in spelCondition (e.g. "flags.hasKey" → "hasKey")
     * 2. Flag names produced by effects (e.g. LogicFlagUpdate parameters)
     */
    public WLogicRule save(WLogicRule rule) {
        rule.setAffected(computeAffected(rule));
        rule.setUpdatedAt(Instant.now());
        if (rule.getCreatedAt() == null) {
            rule.setCreatedAt(Instant.now());
        }
        WLogicRule saved = repository.save(rule);
        log.debug("Saved logic rule: id={}, name={}, affected={}", saved.getId(), saved.getName(), saved.getAffected());
        return saved;
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void deleteByWorldId(String worldId) {
        repository.deleteByWorldId(worldId);
    }

    /**
     * Compute the affected flag list from condition and effects.
     */
    List<String> computeAffected(WLogicRule rule) {
        Set<String> affected = new LinkedHashSet<>();

        // 1. Extract flags from spelCondition
        affected.addAll(extractFlagsFromExpression(rule.getSpelCondition()));

        // 2. Extract output flags from effects
        if (rule.getEffects() != null) {
            for (LogicEffect effect : rule.getEffects()) {
                affected.addAll(extractOutputFlags(effect));
            }
        }

        return new ArrayList<>(affected);
    }

    /**
     * Extract flag names from a SpEL expression by finding "flags.xxx" patterns.
     */
    static Set<String> extractFlagsFromExpression(String expression) {
        Set<String> flags = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) return flags;

        Matcher matcher = FLAGS_PATTERN.matcher(expression);
        while (matcher.find()) {
            flags.add(matcher.group(1));
        }
        return flags;
    }

    /**
     * Extract output flag names from an effect definition.
     * Uses static knowledge of known effect types:
     * - LogicFlagUpdate: parameter keys are the output flag names
     * - block_status: no logic flag output
     */
    static Set<String> extractOutputFlags(LogicEffect effect) {
        Set<String> flags = new LinkedHashSet<>();
        if (effect == null || effect.getType() == null) return flags;

        if ("LogicFlagUpdate".equals(effect.getType()) && effect.getParameters() != null) {
            flags.addAll(effect.getParameters().keySet());
        }
        // block_status and other effects don't produce logic flags

        return flags;
    }
}
