package de.mhus.nimbus.world.ai.tool;

import de.mhus.nimbus.generated.types.BlockType;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI Tool Service for block type management.
 * Provides AI agents with tools to search and lookup block types.
 *
 * Block types are searched in multiple scopes with preference order:
 * 1. @shared:n collections (preferred)
 * 2. @region:regionId collections
 * 3. worldId collections
 *
 * The worldId parameter must always be provided by the agent for each tool call.
 */
@Slf4j
@Service
public class BlockTypeToolService {

    private final WBlockTypeService blockTypeService;

    public BlockTypeToolService(WBlockTypeService blockTypeService) {
        this.blockTypeService = blockTypeService;
        log.info("BlockTypeToolService created");
    }

    // ========== Lookup Methods (Preferred - searches across shared, region, world) ==========

    /**
     * Lookup block types across shared, region, and world collections.
     * Searches in @shared:n (preferred), @region:regionId, and worldId collections.
     * Returns a list of all available block types from all scopes.
     * Use this to get an overview of all available block types.
     *
     * @param worldId The world identifier (required)
     * @return Formatted list of all block types (shared first, then region, then world)
     */
    @Tool("Lookup all block types - searches across shared, region, and world collections. Returns all available block types with shared types first.")
    public String lookupAllBlockTypes(String worldId) {
        log.info("AI Tool: lookupAllBlockTypes - worldId={}", worldId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WBlockType> blockTypes = blockTypeService.lookupBlockTypes(wid);

            if (blockTypes.isEmpty()) {
                return "No block types found in any collection (shared, region, world)";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d block type(s) (shared, region, world):\n\n",
                    blockTypes.size()));

            for (WBlockType blockType : blockTypes) {
                BlockType publicData = blockType.getPublicData();
                result.append(String.format("Block ID: %s\n", blockType.getBlockId()));
                result.append(String.format("World: %s\n", blockType.getWorldId()));
                if (publicData != null) {
                    result.append(String.format("Title: %s\n", publicData.getTitle() != null ? publicData.getTitle() : "(no title)"));
                    result.append(String.format("Type: %s\n", publicData.getType() != null ? publicData.getType() : "(no type)"));
                    if (publicData.getDescription() != null) {
                        result.append(String.format("Description: %s\n", publicData.getDescription()));
                    }
                }
                result.append(String.format("Enabled: %s\n", blockType.isEnabled()));
                result.append("---\n\n");
            }

            log.info("AI Tool: lookupAllBlockTypes - found {} block types", blockTypes.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: lookupAllBlockTypes failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Lookup block types by search query across shared, region, and world collections.
     * Searches in @shared:n (preferred), @region:regionId, and worldId collections.
     * Filters results by query matching block ID, title, or description.
     * Use this to find specific block types based on requirements.
     *
     * @param worldId The world identifier (required)
     * @param query The search query to filter block types (required)
     * @return Formatted list of matching block types (shared first, then region, then world)
     */
    @Tool("Lookup block types by query - searches across shared, region, and world collections. Filters by query in block ID, title, and description. Returns matching block types with shared types first.")
    public String lookupBlockTypesByQuery(String worldId, String query) {
        log.info("AI Tool: lookupBlockTypesByQuery - worldId={}, query={}", worldId, query);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(query)) {
            return "ERROR: query parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WBlockType> blockTypes = blockTypeService.lookupBlockTypesByQuery(wid, query);

            if (blockTypes.isEmpty()) {
                return String.format("No block types found matching query '%s' in any collection (shared, region, world)", query);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d block type(s) matching '%s' (shared, region, world):\n\n",
                    blockTypes.size(), query));

            for (WBlockType blockType : blockTypes) {
                BlockType publicData = blockType.getPublicData();
                result.append(String.format("Block ID: %s\n", blockType.getBlockId()));
                result.append(String.format("World: %s\n", blockType.getWorldId()));
                if (publicData != null) {
                    result.append(String.format("Title: %s\n", publicData.getTitle() != null ? publicData.getTitle() : "(no title)"));
                    result.append(String.format("Type: %s\n", publicData.getType() != null ? publicData.getType() : "(no type)"));
                    if (publicData.getDescription() != null) {
                        result.append(String.format("Description: %s\n", publicData.getDescription()));
                    }
                }
                result.append(String.format("Enabled: %s\n", blockType.isEnabled()));
                result.append("---\n\n");
            }

            log.info("AI Tool: lookupBlockTypesByQuery - found {} block types", blockTypes.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: lookupBlockTypesByQuery failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get detailed information about a specific block type by its block ID.
     * Searches in the specific worldId collection only (no lookup across scopes).
     * Use this when you need complete details about a known block type.
     *
     * @param worldId The world identifier (required)
     * @param blockId The block identifier (required)
     * @return Complete block type information or error message
     */
    @Tool("Get block type by ID - retrieves complete information about a specific block type. Searches only in the specified worldId.")
    public String getBlockTypeById(String worldId, String blockId) {
        log.info("AI Tool: getBlockTypeById - worldId={}, blockId={}", worldId, blockId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(blockId)) {
            return "ERROR: blockId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            Optional<WBlockType> blockTypeOpt = blockTypeService.findByBlockId(wid, blockId);

            if (blockTypeOpt.isEmpty()) {
                return String.format("ERROR: Block type not found - blockId='%s'", blockId);
            }

            WBlockType blockType = blockTypeOpt.get();
            BlockType publicData = blockType.getPublicData();

            StringBuilder result = new StringBuilder();
            result.append(String.format("Block ID: %s\n", blockType.getBlockId()));
            result.append(String.format("World: %s\n", blockType.getWorldId()));
            result.append(String.format("Enabled: %s\n", blockType.isEnabled()));

            if (publicData != null) {
                result.append(String.format("\nTitle: %s\n", publicData.getTitle() != null ? publicData.getTitle() : "(no title)"));
                result.append(String.format("Type: %s\n", publicData.getType() != null ? publicData.getType() : "(no type)"));
                result.append(String.format("Initial Status: %d\n", publicData.getInitialStatus()));
                if (publicData.getDescription() != null) {
                    result.append(String.format("Description: %s\n", publicData.getDescription()));
                }
                if (publicData.getModifiers() != null && !publicData.getModifiers().isEmpty()) {
                    result.append(String.format("Modifiers: %d defined\n", publicData.getModifiers().size()));
                }
            }

            log.info("AI Tool: getBlockTypeById - block type retrieved successfully");
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: getBlockTypeById failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== Search Methods (Only in specific worldId) ==========

    /**
     * Search block types in the specific worldId collection only.
     * Does not search in region or shared collections.
     * Returns all block types available in the specified world.
     * Use this when you need world-specific block types only.
     *
     * @param worldId The world identifier (required)
     * @return Formatted list of block types in the specific world
     */
    @Tool("Search block types in world - searches only in the specific worldId collection. Returns all block types available in that world.")
    public String searchBlockTypesInWorld(String worldId) {
        log.info("AI Tool: searchBlockTypesInWorld - worldId={}", worldId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WBlockType> blockTypes = blockTypeService.findByWorldId(wid);

            if (blockTypes.isEmpty()) {
                return String.format("No block types found in worldId '%s'", worldId);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d block type(s) in worldId '%s':\n\n",
                    blockTypes.size(), worldId));

            for (WBlockType blockType : blockTypes) {
                BlockType publicData = blockType.getPublicData();
                result.append(String.format("Block ID: %s\n", blockType.getBlockId()));
                if (publicData != null) {
                    result.append(String.format("Title: %s\n", publicData.getTitle() != null ? publicData.getTitle() : "(no title)"));
                    result.append(String.format("Type: %s\n", publicData.getType() != null ? publicData.getType() : "(no type)"));
                    if (publicData.getDescription() != null) {
                        result.append(String.format("Description: %s\n", publicData.getDescription()));
                    }
                }
                result.append(String.format("Enabled: %s\n", blockType.isEnabled()));
                result.append("---\n\n");
            }

            log.info("AI Tool: searchBlockTypesInWorld - found {} block types", blockTypes.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: searchBlockTypesInWorld failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Search block types by query in the specific worldId collection only.
     * Does not search in region or shared collections.
     * Filters results by query matching block ID, title, or description.
     * Use this when you need to find world-specific block types only.
     *
     * @param worldId The world identifier (required)
     * @param query The search query to filter block types (required)
     * @return Formatted list of matching block types in the specific world
     */
    @Tool("Search block types by query in world - searches only in the specific worldId collection. Filters by query in block ID, title, and description.")
    public String searchBlockTypesByQueryInWorld(String worldId, String query) {
        log.info("AI Tool: searchBlockTypesByQueryInWorld - worldId={}, query={}", worldId, query);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(query)) {
            return "ERROR: query parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WBlockType> blockTypes = blockTypeService.findByWorldIdAndQuery(wid, query);

            if (blockTypes.isEmpty()) {
                return String.format("No block types found matching query '%s' in worldId '%s'", query, worldId);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d block type(s) matching '%s' in worldId '%s':\n\n",
                    blockTypes.size(), query, worldId));

            for (WBlockType blockType : blockTypes) {
                BlockType publicData = blockType.getPublicData();
                result.append(String.format("Block ID: %s\n", blockType.getBlockId()));
                if (publicData != null) {
                    result.append(String.format("Title: %s\n", publicData.getTitle() != null ? publicData.getTitle() : "(no title)"));
                    result.append(String.format("Type: %s\n", publicData.getType() != null ? publicData.getType() : "(no type)"));
                    if (publicData.getDescription() != null) {
                        result.append(String.format("Description: %s\n", publicData.getDescription()));
                    }
                }
                result.append(String.format("Enabled: %s\n", blockType.isEnabled()));
                result.append("---\n\n");
            }

            log.info("AI Tool: searchBlockTypesByQueryInWorld - found {} block types", blockTypes.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: searchBlockTypesByQueryInWorld failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * List all enabled block types across shared, region, and world collections.
     * Only returns block types that are currently enabled.
     * Use this to get a list of block types that can be used for world generation.
     *
     * @param worldId The world identifier (required)
     * @return Formatted list of enabled block types
     */
    @Tool("List enabled block types - returns all enabled block types across shared, region, and world collections. Only includes types that can be used.")
    public String listEnabledBlockTypes(String worldId) {
        log.info("AI Tool: listEnabledBlockTypes - worldId={}", worldId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            // Get all block types from lookup (shared, region, world)
            List<WBlockType> allBlockTypes = blockTypeService.lookupBlockTypes(wid);

            // Filter only enabled ones
            List<WBlockType> enabledBlockTypes = allBlockTypes.stream()
                    .filter(WBlockType::isEnabled)
                    .collect(Collectors.toList());

            if (enabledBlockTypes.isEmpty()) {
                return "No enabled block types found in any collection (shared, region, world)";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d enabled block type(s) (shared, region, world):\n\n",
                    enabledBlockTypes.size()));

            for (WBlockType blockType : enabledBlockTypes) {
                BlockType publicData = blockType.getPublicData();
                result.append(String.format("Block ID: %s\n", blockType.getBlockId()));
                result.append(String.format("World: %s\n", blockType.getWorldId()));
                if (publicData != null) {
                    result.append(String.format("Title: %s\n", publicData.getTitle() != null ? publicData.getTitle() : "(no title)"));
                    if (publicData.getDescription() != null) {
                        result.append(String.format("Description: %s\n", publicData.getDescription()));
                    }
                }
                result.append("---\n\n");
            }

            log.info("AI Tool: listEnabledBlockTypes - found {} enabled block types", enabledBlockTypes.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: listEnabledBlockTypes failed", e);
            return "ERROR: " + e.getMessage();
        }
    }
}
