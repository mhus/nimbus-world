package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

/**
 * Stage D5 — materialize creature presets as {@code WEntityModel}s (region-scoped). Each creature
 * references an existing shared {@code .glb} via {@code modelPath} (we do NOT generate 3D models —
 * see preset-catalog.md §5); the plan's {@code modifiers} become the model's
 * {@code modelModifierMapping}. Upserted by name, so re-runs update in place.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityCreatureMaterializer {

    private final WEntityModelService entityModelService;

    public MaterializeResult materialize(WorldId region, RealityPlan plan) {
        MaterializeResult result = MaterializeResult.builder().build();
        if (plan.getCreatures() == null) {
            return result;
        }

        for (RealityPlan.CreatureSpec spec : plan.getCreatures()) {
            if (spec == null || Strings.isBlank(spec.getName())) {
                continue;
            }
            String modelId = RealityItemGenerator.slug(spec.getName());
            try {
                EntityModel dto = EntityModel.builder()
                        .name(modelId)
                        .type(spec.getType())
                        .modelPath(spec.getModelPath())
                        .modelModifierMapping(spec.getModifiers())
                        .build();
                entityModelService.save(region, modelId, dto);
                result.inc();
            } catch (Exception ex) {
                log.warn("Failed to materialize creature '{}'", modelId, ex);
                result.addError("creature '" + modelId + "': " + ex.getMessage());
            }
        }
        log.info("RealityCreatureMaterializer: {} entity models, {} errors",
                result.getCreated(), result.getErrors().size());
        return result;
    }
}
