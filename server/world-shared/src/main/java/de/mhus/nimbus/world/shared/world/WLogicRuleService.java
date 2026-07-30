package de.mhus.nimbus.world.shared.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
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
    private final WLogicStateService logicStateService;
    private final MongoTemplate mongoTemplate;

    /**
     * Matches fully qualified state references: "state.pkg.key"
     */
    private static final Pattern QUALIFIED_STATE = Pattern.compile(
            "state\\.([a-zA-Z_]\\w*)\\.([a-zA-Z_]\\w*)");

    /**
     * Matches unqualified state references: "state.xxx" NOT followed by ".yyy"
     */
    private static final Pattern UNQUALIFIED_STATE = Pattern.compile(
            "state\\.([a-zA-Z_]\\w*)(?![\\w.])");

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

    public List<WLogicRule> findByWorldIdAndRulePackage(String worldId, String rulePackage) {
        return repository.findByWorldIdAndRulePackage(worldId, rulePackage);
    }

    /**
     * Find all enabled rules that are affected by any of the changed flags and
     * match the given epoch. Used by the Logic Machine cascade.
     *
     * @param worldId      world identifier
     * @param changedFlags qualified flag names that changed
     * @param epoch        current epoch of the world instance
     * @return affected, enabled rules for the epoch
     */
    public List<WLogicRule> findAffectedRules(String worldId, List<String> changedFlags, int epoch) {
        return repository.findByWorldIdAndAffectedInAndEnabledTrueAndEpochesContaining(
                worldId, changedFlags, epoch);
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
     * Bulk-delete all logic rules AND state definitions of a world.
     * Owner bulk operation used for world teardown; keeps the two related
     * collections ({@code w_logic_rules}, {@code w_logic_states}) consistent.
     *
     * @param worldId the world whose logic data should be removed
     * @return the total number of deleted documents (rules + state definitions)
     */
    public int deleteAllByWorldId(String worldId) {
        List<WLogicRule> rules = repository.findByWorldId(worldId);
        repository.deleteAll(rules);

        // Delegate state definition teardown to its owner service.
        int flagCount = logicStateService.deleteAllByWorldId(worldId);

        log.info("Deleted {} logic rules and {} state definitions for world {}",
                rules.size(), flagCount, worldId);
        return rules.size() + flagCount;
    }

    /**
     * Distinct worldIds that own any logic data (rules or state definitions).
     * Replaces direct {@code MongoTemplate.findDistinct} access by callers.
     *
     * @return sorted, de-duplicated list of worldIds
     */
    public List<String> findDistinctWorldIds() {
        Set<String> worldIds = new LinkedHashSet<>();
        worldIds.addAll(mongoTemplate.findDistinct(
                new Query(), "worldId", WLogicRule.class, String.class));
        worldIds.addAll(mongoTemplate.findDistinct(
                new Query(), "worldId", WLogicStateDef.class, String.class));
        return worldIds.stream().sorted().toList();
    }

    /**
     * Duplicate all logic rules AND state definitions from a source world into a
     * target world. Copies preserve the original {@code affected} flags and are
     * persisted via the repository directly (no re-computation), matching the
     * historical duplication semantics.
     *
     * @param sourceWorldId world to copy from
     * @param targetWorldId world to copy to (must already exist)
     * @return the total number of duplicated documents (rules + state definitions)
     */
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        List<WLogicRule> sourceRules = repository.findByWorldId(sourceWorldId);
        int ruleCount = 0;
        for (WLogicRule source : sourceRules) {
            WLogicRule target = WLogicRule.builder()
                    .worldId(targetWorldId)
                    .name(source.getName())
                    .description(source.getDescription())
                    .rulePackage(source.getRulePackage())
                    .affected(source.getAffected() != null ? new ArrayList<>(source.getAffected()) : null)
                    .spelCondition(source.getSpelCondition())
                    .effects(source.getEffects() != null ? new ArrayList<>(source.getEffects()) : null)
                    .epoches(source.getEpoches() != null ? new ArrayList<>(source.getEpoches()) : null)
                    .enabled(source.isEnabled())
                    .priority(source.getPriority())
                    .testFlags(source.getTestFlags())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            repository.save(target);
            ruleCount++;
        }

        // Delegate state definition duplication to its owner service.
        int flagCount = logicStateService.duplicateToWorld(sourceWorldId, targetWorldId);

        log.info("Duplicated {} logic rules and {} state definitions from {} to {}",
                ruleCount, flagCount, sourceWorldId, targetWorldId);
        return ruleCount + flagCount;
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

    // ==================== EPOCH MANAGEMENT (data ownership) ====================

    /**
     * Validate epoch consistency for this world's logic-rule documents.
     */
    public EpochProcessResult validateEpochs(String worldId, List<WEpochMeta> epochMetas) {
        return EpochArrayHelper.validate(mongoTemplate, WLogicRule.class, "logic-rule", worldId, epochMetas);
    }

    /**
     * Propagate a new epoch by copying it into documents that hold the source epoch.
     */
    public EpochProcessResult createEpoch(String worldId, int sourceEpoch, int newEpoch) {
        return EpochArrayHelper.create(mongoTemplate, WLogicRule.class, "logic-rule", worldId, sourceEpoch, newEpoch);
    }

    /**
     * Remove an epoch from all of this world's logic-rule documents.
     */
    public EpochProcessResult deleteEpoch(String worldId, int epoch) {
        return EpochArrayHelper.delete(mongoTemplate, WLogicRule.class, "logic-rule", worldId, epoch);
    }

    /**
     * Repair duplicate WLogicRule entries (unique: worldId + name).
     * Owner-level operation so callers do not access the WLogicRule collection
     * directly (data ownership). Matches the raw worldId exactly.
     *
     * @param worldId World identifier (raw stored worldId)
     * @return neutral repair result with duplicate counts
     */
    public DuplicateRepairResult repairDuplicates(String worldId) {
        return DuplicateRepairHelper.repairDuplicates(
                mongoTemplate, WLogicRule.class, "logic-rule", worldId,
                doc -> {
                    String name = doc.getString("name");
                    return name != null ? doc.getString("worldId") + "|" + name : null;
                }
        );
    }
}
