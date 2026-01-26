package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing hex grid composition builders.
 * Provides factory functionality to retrieve builders by scenario type.
 */
@Service
@Slf4j
public class HexGridBuilderService {

    public Map<String,Class<? extends HexGridBuilder>> builderRegistry = new HashMap<>();
    public Map<String,Class<? extends HexGridBuilder>> manipulatorRegistry = new HashMap<>();

    public HexGridBuilderService() {
        // Main builders (use singular names matching BiomeType.getBuilderName())
        builderRegistry.put("ocean", OceanBuilder.class);
        builderRegistry.put("island", IslandBuilder.class);
        builderRegistry.put("coast", CoastBuilder.class);
        builderRegistry.put("mountain", MountainBuilder.class);
//        builderRegistry.put("plains", PlainsBuilder.class);
//        builderRegistry.put("desert", DesertBuilder.class);
//        builderRegistry.put("forest", ForestBuilder.class);
//        builderRegistry.put("swamp", SwampBuilder.class);
//        builderRegistry.put("village", VillageBuilder.class);
//        builderRegistry.put("town", TownBuilder.class);

        // Manipulator builders
        manipulatorRegistry.put("edge-blender", EdgeBlenderBuilder.class);
        manipulatorRegistry.put("river", RiverBuilder.class);
        manipulatorRegistry.put("road", RoadBuilder.class);
        manipulatorRegistry.put("wall", WallBuilder.class);
        manipulatorRegistry.put("sidewall", SideWallBuilder.class);
        manipulatorRegistry.put("plot", PlotBuilder.class);
        manipulatorRegistry.put("village", VillageBuilder.class);
    }

    /**
     * Get a composition builder by scenario type.
     *
     * @param builderType Scenario type (e.g., "ocean", "island", "plains")
     * @return CompositionBuilder for the given type, or null if not found
     */
    public Optional<HexGridBuilder> createBuilder(String builderType, Map<String, String> parameters) {
        try {
            HexGridBuilder builder = builderRegistry.get(builderType).getConstructor().newInstance();
            builder.init(parameters);
            return Optional.of(builder);
        } catch (Exception e) {
            log.error("Error creating builder for type: {}", builderType, e);
            return Optional.empty();
        }
    }

    public Optional<HexGridBuilder> createBuilder(WHexGrid grid) {
        String type = grid.getParameters().get("g_builder");
        if (type == null) return Optional.empty();
        return createBuilder(type, grid.getParameters().
                entrySet().stream().filter(p -> p.getKey().startsWith("g_"))
                        .collect(HashMap::new, (m, e) -> m.put(e.getKey().substring(2), e.getValue()), Map::putAll)
                );
    }

    /**
     * Get a composition builder by scenario type.
     *
     * @param manipulatorType Scenario type (e.g., "ocean", "island", "plains")
     * @return CompositionBuilder for the given type, or null if not found
     */
    public Optional<HexGridBuilder> createManipulator(String manipulatorType, Map<String, String> parameters) {
        try {
            HexGridBuilder builder = manipulatorRegistry.get(manipulatorType).getConstructor().newInstance();
            builder.init(parameters);
            return Optional.of(builder);
        } catch (Exception e) {
            log.error("Error creating builder for type: {}", manipulatorType, e);
            return Optional.empty();
        }
    }

    /**
     * Create a pipeline of builders for a hex grid.
     * The pipeline consists of:
     * 1. Main builder (from g_builder parameter)
     * 2. EdgeBlenderBuilder (always)
     * 3. RiverBuilder (if river parameter exists)
     * 4. RoadBuilder (if road parameter exists)
     * 5. WallBuilder (if wall parameter exists)
     * 6. SideWallBuilder (if sidewall parameter exists)
     * 7. PlotBuilder (if plot parameter exists)
     * 8. VillageBuilder (if village parameter exists)
     *
     * @param grid The hex grid to build pipeline for
     * @return List of builders to execute in order
     */
    public List<HexGridBuilder> createBuilderPipeline(WHexGrid grid) {
        List<HexGridBuilder> pipeline = new ArrayList<>();

        Map<String, String> gridParams = grid.getParameters();
        if (gridParams == null) {
            log.warn("No parameters found on hex grid: {}", grid.getPosition());
            return pipeline;
        }

        // Extract parameters with 'g_' prefix
        Map<String, String> builderParams = new HashMap<>();
        gridParams.entrySet().stream()
                .filter(p -> p.getKey().startsWith("g_"))
                .forEach(e -> builderParams.put(e.getKey().substring(2), e.getValue()));

        // Also add landLevel and landOffset parameters (used by MountainBuilder and other terrain builders)
        if (gridParams.containsKey("g_asl")) {
            builderParams.put("g_asl", gridParams.get("g_asl"));
        }
        if (gridParams.containsKey("g_offset")) {
            builderParams.put("g_offset", gridParams.get("g_offset"));
        }

        // 1. Main builder (g_builder)
        String mainBuilderType = gridParams.get("g_builder");
        if (mainBuilderType != null && !mainBuilderType.isBlank()) {
            Optional<HexGridBuilder> mainBuilder = createBuilder(mainBuilderType, builderParams);
            if (mainBuilder.isPresent()) {
                pipeline.add(mainBuilder.get());
                log.debug("Added main builder to pipeline: {}", mainBuilderType);
            } else {
                log.warn("Failed to create main builder: {}", mainBuilderType);
            }
        } else {
            log.warn("No g_builder parameter found on hex grid: {}", grid.getPosition());
        }

        // 2. EdgeBlenderBuilder (always, expect edge-blender: false to skip)
        if (!gridParams.containsKey("edge-blender") || !"false".equals(gridParams.get("edge-blender"))) {
            Optional<HexGridBuilder> edgeBlender = createManipulator("edge-blender", builderParams);
            if (edgeBlender.isPresent()) {
                pipeline.add(edgeBlender.get());
                log.debug("Added EdgeBlenderBuilder to pipeline");
            }
        }

        // 3. RiverBuilder (if river parameter exists)
        if (gridParams.containsKey("river") && !gridParams.get("river").isBlank()) {
            Optional<HexGridBuilder> riverBuilder = createManipulator("river", builderParams);
            if (riverBuilder.isPresent()) {
                pipeline.add(riverBuilder.get());
                log.debug("Added RiverBuilder to pipeline");
            }
        }

        // 4. RoadBuilder (if road parameter exists)
        if (gridParams.containsKey("road") && !gridParams.get("road").isBlank()) {
            Optional<HexGridBuilder> roadBuilder = createManipulator("road", builderParams);
            if (roadBuilder.isPresent()) {
                pipeline.add(roadBuilder.get());
                log.debug("Added RoadBuilder to pipeline");
            }
        }

        // 5. WallBuilder (if wall parameter exists)
        if (gridParams.containsKey("wall") && !gridParams.get("wall").isBlank()) {
            Optional<HexGridBuilder> wallBuilder = createManipulator("wall", builderParams);
            if (wallBuilder.isPresent()) {
                pipeline.add(wallBuilder.get());
                log.debug("Added WallBuilder to pipeline");
            }
        }

        // 6. SideWallBuilder (if sidewall parameter exists)
        if (gridParams.containsKey("sidewall") && !gridParams.get("sidewall").isBlank()) {
            Optional<HexGridBuilder> sideWallBuilder = createManipulator("sidewall", builderParams);
            if (sideWallBuilder.isPresent()) {
                pipeline.add(sideWallBuilder.get());
                log.debug("Added SideWallBuilder to pipeline");
            }
        }

        // 7. PlotBuilder (if plot parameter exists)
        if (gridParams.containsKey("plot") && !gridParams.get("plot").isBlank()) {
            Optional<HexGridBuilder> plotBuilder = createManipulator("plot", builderParams);
            if (plotBuilder.isPresent()) {
                pipeline.add(plotBuilder.get());
                log.debug("Added PlotBuilder to pipeline");
            }
        }

        // 8. VillageBuilder (if village parameter exists)
        if (gridParams.containsKey("village") && !gridParams.get("village").isBlank()) {
            Optional<HexGridBuilder> villageBuilder = createManipulator("village", builderParams);
            if (villageBuilder.isPresent()) {
                pipeline.add(villageBuilder.get());
                log.debug("Added VillageBuilder to pipeline");
            }
        }

        log.info("Created builder pipeline with {} builders for hex grid: {}", pipeline.size(), grid.getPosition());
        return pipeline;
    }

}
