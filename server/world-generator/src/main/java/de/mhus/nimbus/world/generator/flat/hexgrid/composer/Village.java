package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Village extends Structure implements BuildFeature {
    private List<VillageBuildingDefinition> buildings;
    private List<VillageStreetDefinition> streets;
    private Map<String, String> parameters;

    /**
     * Template name for village design (e.g., "hamlet-medieval", "village-2x1-medieval")
     */
    private String templateName;

    /**
     * Base level for village terrain (typically 95)
     */
    @Builder.Default
    private int baseLevel = 95;

    public static VillageBuilder builder() {
        return new VillageBuilder();
    }

    /**
     * Applies village-specific default configuration from StructureType
     */
    @Override
    protected void applyStructureDefaults(Map<String, String> defaults) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // Apply defaults from StructureType enum
        if (defaults != null) {
            defaults.forEach(parameters::putIfAbsent);
        }

        // Set default builder
        if (getType() != null) {
            parameters.putIfAbsent("g_builder", getType().getDefaultBuilder());
        }
    }

    /**
     * Builds this village internally using VillageDesigner.
     * Creates ALL HexGrids with complete parameters, with RELATIVE coordinates to village origin (0,0).
     * The parent Composer will later transform these relative coordinates to absolute world coordinates.
     *
     * Implements BuildFeature interface for cascaded composites.
     *
     * @param context Build context (not used for village, uses templateName and baseLevel instead)
     * @return CompositionResult with village design information
     */
    @Override
    public CompositionResult build(BuildContext context) {
        if (templateName == null || templateName.isBlank()) {
            return CompositionResult.failed("Village templateName is required for build()");
        }

        try {
            // Load template
            VillageTemplate template = VillageTemplateLoader.load(templateName);
            if (template == null) {
                return CompositionResult.failed("Village template not found: " + templateName);
            }

            // Design village using VillageDesigner
            VillageDesigner designer = new VillageDesigner();
            VillageDesignResult designResult = designer.design(template, baseLevel);

            // Use the existing toVillage() conversion which already handles buildings/streets correctly
            Village designedVillage = designResult.toVillage(getName());

            // Copy buildings and streets from designed village
            this.buildings = designedVillage.getBuildings();
            this.streets = designedVillage.getStreets();

            // Clear existing HexGrids
            if (getHexGrids() != null) {
                getHexGrids().clear();
            }

            // Create FeatureHexGrid for EACH grid in the design result with RELATIVE coordinates
            // AND with complete parameters from the HexGridConfig
            for (Map.Entry<HexVector2, HexGridConfig> entry : designResult.getGridConfigs().entrySet()) {
                HexVector2 relativeCoord = entry.getKey();  // Relative to village origin (0,0)
                HexGridConfig config = entry.getValue();

                // Create FeatureHexGrid with relative coordinate
                FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                    .coordinate(relativeCoord)
                    .name(getName() + " [" + relativeCoord.getQ() + "," + relativeCoord.getR() + "]")
                    .description("Part of " + (getType() != null ? getType().name() : "VILLAGE") + " village")
                    .build();

                // Generate and add village parameter from this grid's config
                String villageParam = config.toVillageParameter();
                if (villageParam != null) {
                    featureHexGrid.addParameter("village", villageParam);
                }

                // Generate and add road parameter from this grid's config
                String roadParam = config.toRoadParameter();
                if (roadParam != null) {
                    featureHexGrid.addParameter("road", roadParam);
                }

                // Add structure type and name
                if (getType() != null) {
                    featureHexGrid.addParameter("structure", getType().name().toLowerCase());
                    featureHexGrid.addParameter("structureName", getName());
                } else {
                    featureHexGrid.addParameter("structure", "village");
                    featureHexGrid.addParameter("structureName", getName());
                }

                // Add g_builder parameter
                featureHexGrid.addParameter("g_builder", "island");

                // Add to this village's HexGrids (with RELATIVE coordinates)
                addHexGrid(featureHexGrid);
            }

            // Store global parameters for compatibility with existing code
            if (this.parameters == null) {
                this.parameters = new HashMap<>();
            }

            // Copy center grid parameters to global parameters for backwards compatibility
            if (!getHexGrids().isEmpty()) {
                FeatureHexGrid centerGrid = getHexGrids().get(0);
                if (centerGrid != null && centerGrid.getParameters() != null) {
                    this.parameters.putAll(centerGrid.getParameters());
                }
            }

            // Return successful result
            return CompositionResult.builder()
                .success(true)
                .totalStructures(1)
                .totalGrids(getHexGrids().size())
                .build();

        } catch (Exception e) {
            return CompositionResult.failed("Village build failed: " + e.getMessage());
        }
    }

    /**
     * Configures HexGrids for this village with buildings and streets.
     * Creates FeatureHexGrid configurations for each village grid.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return;
        }

        // Clear existing configurations
        if (getHexGrids() != null) {
            getHexGrids().clear();
        }

        // Create FeatureHexGrid for each coordinate
        for (HexVector2 coord : coordinates) {
            FeatureHexGrid featureHexGrid = FeatureHexGrid.builder()
                .coordinate(coord)
                .name(getName() + " [" + coord.getQ() + "," + coord.getR() + "]")
                .description("Part of " + (getType() != null ? getType().name() : "VILLAGE") + " village")
                .build();

            // Copy village parameters to grid
            if (parameters != null) {
                featureHexGrid.getParameters().putAll(parameters);
            }

            // Add structure type parameter
            if (getType() != null) {
                featureHexGrid.addParameter("structure", getType().name().toLowerCase());
                featureHexGrid.addParameter("structureName", getName());
            } else {
                featureHexGrid.addParameter("structure", "village");
                featureHexGrid.addParameter("structureName", getName());
            }

            // Add to this feature
            addHexGrid(featureHexGrid);
        }
    }
}
