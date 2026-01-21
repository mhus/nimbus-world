package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
        builderRegistry.put("ocean", OceanBuilder.class);
        builderRegistry.put("island", IslandBuilder.class);
        builderRegistry.put("coast", CoastBuilder.class);
        builderRegistry.put("mountains", MountainBuilder.class);
//        registry.put("plains", PlainsBuilder.class);
//        registry.put("hills", HillsBuilder.class);
//        registry.put("forest", ForestBuilder.class);
//        registry.put("desert", DessertBuilder.class);
//        registry.put("heath", HeathBuilder.class);
//        registry.put("swamp", SwampBuilder.class);
//        registry.put("city", CityBuilder.class);
//        registry.put("village", VillageBuilder.class);

        // Manipulator builders
        manipulatorRegistry.put("edge-blender", EdgeBlenderBuilder.class);
        manipulatorRegistry.put("road", RoadBuilder.class);
        manipulatorRegistry.put("river", RiverBuilder.class);
        manipulatorRegistry.put("wall", WallBuilder.class);
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

}
