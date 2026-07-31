package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.LogicEffect;
import de.mhus.nimbus.world.shared.world.WLogicRule;
import de.mhus.nimbus.world.shared.world.WLogicRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stage D6 — materialize the plan's building/logic rules as {@code WLogicRule}s (region-scoped by the
 * raw region worldId). The plan's free-text {@code when} becomes the SpEL condition and each effect
 * string is stored as a {@link LogicEffect} (type {@code reality}, parameter {@code effect}).
 * Idempotent by name: an existing rule with the same name is updated in place.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityRuleMaterializer {

    private final WLogicRuleService ruleService;

    public MaterializeResult materialize(WorldId region, RealityPlan plan) {
        MaterializeResult result = MaterializeResult.builder().build();
        if (plan.getRules() == null) {
            return result;
        }
        String worldId = region.getId();

        for (RealityPlan.RuleSpec spec : plan.getRules()) {
            if (spec == null || Strings.isBlank(spec.getName())) {
                continue;
            }
            String name = RealityItemGenerator.slug(spec.getName());
            try {
                WLogicRule rule = WLogicRule.builder()
                        .worldId(worldId)
                        .name(name)
                        .description(spec.getDescription())
                        .rulePackage(Strings.isBlank(spec.getKind()) ? "reality" : spec.getKind())
                        .spelCondition(spec.getWhen())
                        .effects(mapEffects(spec.getEffects()))
                        .epoches(List.of(0))
                        .priority(100)
                        .enabled(true)
                        .build();
                // idempotent by (worldId, name): reuse the existing document id if present
                Optional<WLogicRule> existing = ruleService.findByWorldIdAndName(worldId, name);
                existing.ifPresent(w -> rule.setId(w.getId()));
                ruleService.save(rule);
                result.inc();
            } catch (Exception ex) {
                log.warn("Failed to materialize rule '{}'", name, ex);
                result.addError("rule '" + name + "': " + ex.getMessage());
            }
        }
        log.info("RealityRuleMaterializer: {} rules, {} errors", result.getCreated(), result.getErrors().size());
        return result;
    }

    private List<LogicEffect> mapEffects(List<String> effects) {
        List<LogicEffect> out = new ArrayList<>();
        if (effects != null) {
            for (String e : effects) {
                if (!Strings.isBlank(e)) {
                    out.add(LogicEffect.builder().type("reality").parameters(Map.of("effect", e)).build());
                }
            }
        }
        return out;
    }
}
