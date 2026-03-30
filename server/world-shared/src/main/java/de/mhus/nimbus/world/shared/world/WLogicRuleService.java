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
     * Matches fully qualified state references: "state.pkg.key"
     */
    private static final Pattern QUALIFIED_STATE = Pattern.compile(
            "state\\.([a-zA-Z_]\\w*)\\.([a-zA-Z_]\\w*)");

    /**
     * Matches unqualified state references: "state.xxx" NOT followed by ".yyy"
     */
    private static final Pattern UNQUALIFIED_STATE = Pattern.compile(
            "state\\.([a-zA-Z_]\\w*)(?!\\.)");

    private static final String DEFAULT_PACKAGE = "default";

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
     * 2. Flag names produced by effects (e.g. state_update parameters)
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
     * All flag names are fully qualified: "package.name".
     * Unqualified references are resolved using the rule's rulePackage.
     */
    List<String> computeAffected(WLogicRule rule) {
        String pkg = rule.getRulePackage() != null && !rule.getRulePackage().isBlank()
                ? rule.getRulePackage() : DEFAULT_PACKAGE;

        Set<String> affected = new LinkedHashSet<>();

        // 1. Extract state keys from spelCondition
        affected.addAll(extractKeysFromExpression(rule.getSpelCondition(), pkg));

        // 2. Extract output flags from effects
        if (rule.getEffects() != null) {
            for (LogicEffect effect : rule.getEffects()) {
                affected.addAll(extractOutputFlags(effect, pkg));
            }
        }

        return new ArrayList<>(affected);
    }

    /**
     * Extract qualified state keys from a SpEL expression.
     * - "state.pkg.key" -> "pkg.key" (already qualified)
     * - "state.key"     -> "{rulePackage}.key" (shorthand resolved)
     */
    static Set<String> extractKeysFromExpression(String expression, String rulePackage) {
        Set<String> keys = new LinkedHashSet<>();
        if (expression == null || expression.isBlank()) return keys;

        Matcher qualified = QUALIFIED_STATE.matcher(expression);
        while (qualified.find()) {
            keys.add(qualified.group(1) + "." + qualified.group(2));
        }

        Matcher unqualified = UNQUALIFIED_STATE.matcher(expression);
        while (unqualified.find()) {
            keys.add(rulePackage + "." + unqualified.group(1));
        }

        return keys;
    }

    /**
     * Extract output flag names from an effect definition.
     * Keys without "." are resolved with the rule's package.
     * - state_update: parameter keys are the output flag names
     * - block_status: no logic flag output
     */
    static Set<String> extractOutputFlags(LogicEffect effect, String rulePackage) {
        Set<String> flags = new LinkedHashSet<>();
        if (effect == null || effect.getType() == null) return flags;

        if ("state_update".equals(effect.getType()) && effect.getParameters() != null) {
            for (String key : effect.getParameters().keySet()) {
                flags.add(key.contains(".") ? key : rulePackage + "." + key);
            }
        }

        return flags;
    }
}
