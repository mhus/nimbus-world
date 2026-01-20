package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing hex grid composition builders.
 * Provides factory functionality to retrieve builders by scenario type.
 */
@Service
@Slf4j
public class HexGridBuilderService {

    public Map<String,Class<? extends HexGridBuilder>> registry = new HashMap<>();

    public HexGridBuilderService() {
        registry.put("ocean", OceanBuilder.class);
        registry.put("island", IslandBuilder.class);
        registry.put("plains", PlainsBuilder.class);
        registry.put("hills", HillsBuilder.class);
        registry.put("forest", UforestBuilder.class);
        registry.put("desert", UdessertBuilder.class);
        registry.put("heath", UheathBuilder.class);
        registry.put("mountains", UmountainsBuilder.class);
        registry.put("coast", UcoastBuilder.class);
        registry.put("swamp", UswampBuilder.class);
        registry.put("city", UcityBuilder.class);
        registry.put("village", UvillageBuilder.class);
    }

    /**
     * Get a composition builder by scenario type.
     *
     * @param type Scenario type (e.g., "ocean", "island", "plains")
     * @return CompositionBuilder for the given type, or null if not found
     */
    public Optional<HexGridBuilder> createBuilder(String type, Map<String, String> parameters) {
        try {
            HexGridBuilder builder = registry.get(type).getConstructor().newInstance();
            builder.init(parameters);
            return Optional.of(builder);
        } catch (Exception e) {
            log.error("Error creating builder for type: {}", type, e);
            return Optional.empty();
        }
    }

}
