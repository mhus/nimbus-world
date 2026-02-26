package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.generator.mcp.dto.CreateBillboardBlockTypeRequest;
import de.mhus.nimbus.world.generator.mcp.dto.CreateCubeBlockTypeRequest;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockTypeTools {

    private final WBlockTypeService blockTypeService;

    @Tool(name = "get_block_types", description = "Get block types for a world")
    public Map<String, Object> getBlockTypes(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Maximum number of results", required = false) Integer limit) {
        log.debug("MCP: Get block types: worldId={}, limit={}", worldId, limit);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        int effectiveLimit = limit != null ? limit : 100;

        List<WBlockType> blockTypes = blockTypeService.findByWorldId(wid);
        List<Map<String, Object>> blockTypeDtos = blockTypes.stream()
                .limit(effectiveLimit)
                .map(this::toBlockTypeDto)
                .collect(Collectors.toList());

        return Map.of(
                "blockTypes", blockTypeDtos,
                "count", blockTypeDtos.size(),
                "total", blockTypes.size()
        );
    }

    @Tool(name = "get_block_type", description = "Get a specific block type by ID (supports collection prefix like 'm:sand', 'n:air')")
    public Map<String, Object> getBlockType(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Block type ID, optionally with collection prefix (e.g., 'sand', 'm:sand', 'n:air')") String blockId) {
        log.debug("MCP: Get block type: worldId={}, blockId={}", worldId, blockId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        Optional<WBlockType> blockTypeOpt = blockTypeService.findByBlockId(wid, blockId);

        if (blockTypeOpt.isEmpty()) {
            throw new McpToolException("block type not found");
        }

        return toBlockTypeDto(blockTypeOpt.get());
    }

    @Tool(name = "create_cube_block_type", description = "Create a new cube block type with textures. Textures keys: 0=ALL, 1=TOP, 2=BOTTOM, 3=NORTH, 4=SOUTH, 5=EAST, 6=WEST.")
    public Map<String, Object> createCubeBlockType(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Unique block type ID (e.g., 'stone', 'm:sand')") String blockTypeId,
            @ToolParam(description = "Display name of the block type") String title,
            @ToolParam(description = "Description of the block type", required = false) String description,
            @ToolParam(description = "Texture definitions (keys: 0=ALL, 1=TOP, 2=BOTTOM, etc.)") Map<Integer, Object> textures,
            @ToolParam(description = "Block type: GROUND, WATER, STRUCTURE, DECORATION, etc.", required = false) String type,
            @ToolParam(description = "Whether the block is solid (cannot walk through)", required = false) Boolean solid,
            @ToolParam(description = "Auto-jump height (0 = disabled)", required = false) Double autoJump) {
        log.debug("MCP: Create cube block type: worldId={}, blockTypeId={}", worldId, blockTypeId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (Strings.isBlank(blockTypeId)) {
            throw new McpToolException("blockTypeId is required");
        }
        if (Strings.isBlank(title)) {
            throw new McpToolException("title is required");
        }
        if (textures == null || textures.isEmpty()) {
            throw new McpToolException("textures are required");
        }

        Optional<WBlockType> existing = blockTypeService.findByBlockId(wid, blockTypeId);
        if (existing.isPresent()) {
            throw new McpToolException("block type with ID '" + blockTypeId + "' already exists");
        }

        de.mhus.nimbus.generated.types.BlockType publicData = de.mhus.nimbus.generated.types.BlockType.builder()
                .id(blockTypeId)
                .title(title)
                .description(description)
                .type(parseBlockTypeType(type))
                .initialStatus(BlockUtil.DEFAULT_STATUS)
                .modifiers(new HashMap<>())
                .build();

        de.mhus.nimbus.generated.types.BlockModifier modifier = de.mhus.nimbus.generated.types.BlockModifier.builder()
                .visibility(de.mhus.nimbus.generated.types.VisibilityModifier.builder()
                        .shape(de.mhus.nimbus.generated.types.Shape.CUBE.getTsIndex())
                        .textures(textures)
                        .build())
                .physics(de.mhus.nimbus.generated.types.PhysicsModifier.builder()
                        .solid(solid != null ? solid : true)
                        .autoJump(autoJump != null ? autoJump : 0.0)
                        .build())
                .build();

        publicData.getModifiers().put(BlockUtil.DEFAULT_STATUS, modifier);

        WBlockType saved = blockTypeService.save(wid, blockTypeId, publicData);

        log.info("MCP: Created cube block type: id={}, blockTypeId={}", saved.getId(), saved.getBlockId());
        return Map.of(
                "id", saved.getId(),
                "blockTypeId", saved.getBlockId(),
                "worldId", saved.getWorldId()
        );
    }

    @Tool(name = "create_billboard_block_type", description = "Create a new billboard block type (flat sprite facing camera, ideal for plants). Texture is always transparent with back-face culling enabled.")
    public Map<String, Object> createBillboardBlockType(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Unique block type ID (e.g., 'grass', 'm:flower')") String blockTypeId,
            @ToolParam(description = "Display name of the block type") String title,
            @ToolParam(description = "Description of the block type", required = false) String description,
            @ToolParam(description = "Single texture definition with path (e.g., {\"path\": \"m:textures/plants/grass.png\"})") Map<String, Object> texture,
            @ToolParam(description = "Block type: DECORATION, PLANT, etc.", required = false) String type,
            @ToolParam(description = "Whether the block is solid (usually false for billboards)", required = false) Boolean solid,
            @ToolParam(description = "Auto-jump height (usually 0 for billboards)", required = false) Double autoJump) {
        log.debug("MCP: Create billboard block type: worldId={}, blockTypeId={}", worldId, blockTypeId);

        var wid = WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (Strings.isBlank(blockTypeId)) {
            throw new McpToolException("blockTypeId is required");
        }
        if (Strings.isBlank(title)) {
            throw new McpToolException("title is required");
        }
        if (texture == null) {
            throw new McpToolException("texture is required");
        }

        Optional<WBlockType> existing = blockTypeService.findByBlockId(wid, blockTypeId);
        if (existing.isPresent()) {
            throw new McpToolException("block type with ID '" + blockTypeId + "' already exists");
        }

        de.mhus.nimbus.generated.types.BlockType publicData = de.mhus.nimbus.generated.types.BlockType.builder()
                .id(blockTypeId)
                .title(title)
                .description(description)
                .type(parseBlockTypeType(type != null ? type : "DECORATION"))
                .initialStatus(BlockUtil.DEFAULT_STATUS)
                .modifiers(new HashMap<>())
                .build();

        Map<String, Object> textureConfig = new HashMap<>(texture);
        textureConfig.put("transparent", true);
        textureConfig.put("backFaceCulling", true);

        Map<Integer, Object> textures = new HashMap<>();
        textures.put(0, textureConfig);

        de.mhus.nimbus.generated.types.BlockModifier modifier = de.mhus.nimbus.generated.types.BlockModifier.builder()
                .visibility(de.mhus.nimbus.generated.types.VisibilityModifier.builder()
                        .shape(de.mhus.nimbus.generated.types.Shape.BILLBOARD.getTsIndex())
                        .textures(textures)
                        .build())
                .physics(de.mhus.nimbus.generated.types.PhysicsModifier.builder()
                        .solid(solid != null ? solid : false)
                        .autoJump(autoJump != null ? autoJump : 0.0)
                        .build())
                .build();

        publicData.getModifiers().put(BlockUtil.DEFAULT_STATUS, modifier);

        WBlockType saved = blockTypeService.save(wid, blockTypeId, publicData);

        log.info("MCP: Created billboard block type: id={}, blockTypeId={}", saved.getId(), saved.getBlockId());
        return Map.of(
                "id", saved.getId(),
                "blockTypeId", saved.getBlockId(),
                "worldId", saved.getWorldId()
        );
    }

    private de.mhus.nimbus.generated.types.BlockTypeType parseBlockTypeType(String type) {
        if (type == null || type.isBlank()) {
            return de.mhus.nimbus.generated.types.BlockTypeType.BLOCK;
        }
        try {
            return de.mhus.nimbus.generated.types.BlockTypeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid block type: {}, using BLOCK as default", type);
            return de.mhus.nimbus.generated.types.BlockTypeType.BLOCK;
        }
    }

    private Map<String, Object> toBlockTypeDto(WBlockType blockType) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("blockId", blockType.getBlockId());
        dto.put("enabled", blockType.isEnabled());
        if (blockType.getPublicData() != null) {
            dto.put("description", blockType.getPublicData().getDescription());
        }
        return dto;
    }
}
