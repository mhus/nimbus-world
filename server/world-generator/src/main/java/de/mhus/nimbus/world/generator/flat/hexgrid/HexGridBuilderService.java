package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing hex grid composition builders.
 * Provides factory functionality to retrieve builders by scenario type.
 */
@Service
@Slf4j
public class HexGridBuilderService {

    private final List<CompositionBuilder> compositionBuilders;
    private Map<String, CompositionBuilder> builderCache;

    @Autowired
    public HexGridBuilderService(List<CompositionBuilder> compositionBuilders) {
        this.compositionBuilders = compositionBuilders;
        initializeBuilderCache();
    }

    /**
     * Initialize builder cache by registering all available builders.
     */
    private void initializeBuilderCache() {
        builderCache = new HashMap<>();
        for (CompositionBuilder builder : compositionBuilders) {
            builderCache.put(builder.getType(), builder);
            log.debug("Registered composition builder: {}", builder.getType());
        }
        log.info("Initialized HexGridBuilderService with {} builders", builderCache.size());
    }

    /**
     * Get a composition builder by scenario type.
     *
     * @param type Scenario type (e.g., "ocean", "island", "plains")
     * @return CompositionBuilder for the given type, or null if not found
     */
    public CompositionBuilder getBuilder(String type) {
        CompositionBuilder builder = builderCache.get(type);
        if (builder == null) {
            log.warn("No builder found for type: {}, available types: {}", type, builderCache.keySet());
        }
        return builder;
    }

    /**
     * Check if a builder exists for the given scenario type.
     *
     * @param type Scenario type to check
     * @return true if a builder exists for this type
     */
    public boolean hasBuilder(String type) {
        return builderCache.containsKey(type);
    }

    /**
     * Get all registered scenario types.
     *
     * @return Set of all available scenario type names
     */
    public Set<String> getAvailableTypes() {
        return builderCache.keySet();
    }

    /**
     * Get the number of registered builders.
     *
     * @return Count of available builders
     */
    public int getBuilderCount() {
        return builderCache.size();
    }
}
