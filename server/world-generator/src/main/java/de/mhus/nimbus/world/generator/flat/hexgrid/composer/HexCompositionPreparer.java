package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Prepares the HexComposition model by calling prepareForComposition() on all features.
 * Features now store their calculated values directly (calculatedSizeFrom, preparedPositions, etc.)
 * instead of creating separate Prepared* objects.
 */
@Slf4j
public class HexCompositionPreparer {

    /**
     * Prepares a HexComposition by calling prepareForComposition() on all features in-place.
     * @return true if preparation was successful, false otherwise
     */
    public boolean prepare(HexComposition composition) {
        if (composition == null) {
            log.error("HexComposition cannot be null");
            return false;
        }

        // Ensure composition is initialized (triggers migration if needed)
        composition.initialize();

        log.debug("Preparing HexComposition: {}", composition.getName());

        if (composition.getFeatures() == null || composition.getFeatures().isEmpty()) {
            log.warn("HexComposition has no features. " +
                "Legacy biomes/villages should have been migrated during initialize().");
            return false;
        }

        // Prepare all features in-place
        prepareAllFeatures(composition.getFeatures());

        // Update feature status to PREPARED
        for (Feature feature : composition.getFeatures()) {
            if (feature.getStatus() == FeatureStatus.NEW) {
                feature.setStatus(FeatureStatus.PREPARED);
            }
        }

        log.debug("Prepared {} features", composition.getFeatures().size());

        return true;
    }

    /**
     * Prepares all features in a list by calling prepareForComposition() on each.
     */
    private void prepareAllFeatures(List<Feature> features) {
        if (features == null || features.isEmpty()) {
            return;
        }

        for (Feature feature : features) {
            prepareFeature(feature);
        }
    }

    /**
     * Prepares a single feature by calling its prepareForComposition() method.
     * Also recursively prepares nested features in Composites.
     */
    private void prepareFeature(Feature feature) {
        if (feature == null) {
            return;
        }

        // Call prepareForComposition() on Area or Flow features
        if (feature instanceof Area area) {
            area.prepareForComposition();
            log.debug("Prepared area feature '{}' (type: {})",
                area.getName(), area.getClass().getSimpleName());
        } else if (feature instanceof Flow flow) {
            // Set FlowType automatically based on class if not already set
            if (flow.getType() == null) {
                if (flow instanceof Road) {
                    flow.setType(FlowType.ROAD);
                } else if (flow instanceof River) {
                    flow.setType(FlowType.RIVER);
                } else if (flow instanceof Wall) {
                    flow.setType(FlowType.WALL);
                }
                log.debug("Auto-set flow type: {} for feature '{}'",
                    flow.getType(), flow.getName());
            }

            flow.prepareForComposition();
            log.debug("Prepared flow feature '{}' (type: {})",
                flow.getName(), flow.getClass().getSimpleName());
        }

        // Recursively prepare nested features in Composites
        if (feature instanceof Composite composite && composite.getFeatures() != null) {
            prepareAllFeatures(composite.getFeatures());
        }
    }

}
