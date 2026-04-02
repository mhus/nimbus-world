package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlatTools implements McpToolBean {

    private final WFlatService flatService;

    @Tool(name = "list_flats", description = "List all flats for a world. Returns flatId, mountX, mountZ, sizeX, sizeZ, hexGrid info.")
    public Map<String, Object> listFlats(
            @ToolParam(description = "World ID") String worldId) {
        log.debug("MCP: List flats: worldId={}", worldId);

        List<WFlat> flats = flatService.findByWorldId(worldId);

        List<Map<String, Object>> flatDtos = flats.stream()
                .map(this::toFlatSummaryDto)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("flats", flatDtos);
        result.put("count", flatDtos.size());
        return result;
    }

    @Tool(name = "get_flat", description = "Get flat metadata including mountX, mountZ, sizeX, sizeZ, materials and hex grid info")
    public Map<String, Object> getFlat(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Flat ID (e.g. genesis_0_0_0 = genesis_{epoch}_{q}_{r})") String flatId) {
        log.debug("MCP: Get flat: worldId={}, flatId={}", worldId, flatId);

        WFlat flat = flatService.findByWorldAndFlatId(worldId, flatId);
        if (flat == null) {
            throw new McpToolException("Flat not found: " + flatId);
        }

        return toFlatDetailDto(flat);
    }

    @Tool(name = "get_flat_data", description = "Get flat column data at a world position (x,z). Returns level, column material, extraBlocks, and neighbor info. Converts world coordinates to local flat coordinates.")
    public Map<String, Object> getFlatData(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Flat ID (e.g. genesis_0_0_0 = genesis_{epoch}_{q}_{r})") String flatId,
            @ToolParam(description = "World X coordinate") int x,
            @ToolParam(description = "World Z coordinate") int z) {
        log.debug("MCP: Get flat data: worldId={}, flatId={}, x={}, z={}", worldId, flatId, x, z);

        WFlat flat = flatService.findByWorldAndFlatId(worldId, flatId);
        if (flat == null) {
            throw new McpToolException("Flat not found: " + flatId);
        }

        int localX = x - flat.getMountX();
        int localZ = z - flat.getMountZ();

        Map<String, Object> result = new HashMap<>();
        result.put("worldX", x);
        result.put("worldZ", z);
        result.put("localX", localX);
        result.put("localZ", localZ);
        result.put("mountX", flat.getMountX());
        result.put("mountZ", flat.getMountZ());
        result.put("sizeX", flat.getSizeX());
        result.put("sizeZ", flat.getSizeZ());

        if (localX < 0 || localZ < 0 || localX >= flat.getSizeX() || localZ >= flat.getSizeZ()) {
            result.put("inBounds", false);
            result.put("message", "Position is outside flat bounds");
            return result;
        }

        result.put("inBounds", true);

        int level = flat.getLevel(localX, localZ);
        result.put("level", level);
        result.put("levelIsNotSet", level == WFlat.LEVEL_NOT_SET);

        int columnMaterial = flat.getColumn(localX, localZ);
        result.put("columnMaterial", columnMaterial);
        result.put("columnIsNotSet", columnMaterial == WFlat.MATERIAL_NOT_SET);
        result.put("columnIsNotSetMutable", columnMaterial == WFlat.MATERIAL_NOT_SET_MUTABLE);
        result.put("isColumnSet", flat.isColumnSet(localX, localZ));

        WFlat.MaterialDefinition materialDef = flat.getColumnMaterial(localX, localZ);
        if (materialDef != null) {
            Map<String, Object> matDto = new HashMap<>();
            matDto.put("blockDef", materialDef.getBlockDef());
            matDto.put("nextBlockDef", materialDef.getNextBlockDef());
            matDto.put("hasOcean", materialDef.isHasOcean());
            matDto.put("isBlockMapDelta", materialDef.isBlockMapDelta());
            if (materialDef.getBlockAtLevels() != null && !materialDef.getBlockAtLevels().isEmpty()) {
                matDto.put("blockAtLevels", materialDef.getBlockAtLevels());
            }
            result.put("materialDefinition", matDto);
        }

        String[] extraBlocks = flat.getExtraBlocksForColumn(localX, localZ);
        Map<Integer, String> extraBlockMap = new HashMap<>();
        for (int y = 0; y < 256; y++) {
            if (extraBlocks[y] != null) {
                extraBlockMap.put(y, extraBlocks[y]);
            }
        }
        if (!extraBlockMap.isEmpty()) {
            result.put("extraBlocks", extraBlockMap);
        }

        List<Map<String, Object>> neighbors = new ArrayList<>();
        int[][] offsets = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};
        String[] names = {"West","East","North","South","NW","NE","SW","SE"};
        for (int i = 0; i < offsets.length; i++) {
            int nx = localX + offsets[i][0];
            int nz = localZ + offsets[i][1];
            Map<String, Object> neighbor = new HashMap<>();
            neighbor.put("direction", names[i]);
            neighbor.put("localX", nx);
            neighbor.put("localZ", nz);
            if (nx >= 0 && nz >= 0 && nx < flat.getSizeX() && nz < flat.getSizeZ()) {
                neighbor.put("level", flat.getLevel(nx, nz));
                neighbor.put("columnMaterial", flat.getColumn(nx, nz));
                neighbor.put("isColumnSet", flat.isColumnSet(nx, nz));
            } else {
                neighbor.put("outOfBounds", true);
            }
            neighbors.add(neighbor);
        }
        result.put("neighbors", neighbors);

        String group = flat.getGroup(localX, localZ);
        if (group != null) {
            result.put("group", group);
        }

        return result;
    }

    private Map<String, Object> toFlatSummaryDto(WFlat flat) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", flat.getId());
        dto.put("flatId", flat.getFlatId());
        dto.put("worldId", flat.getWorldId());
        dto.put("layerDataId", flat.getLayerDataId());
        dto.put("title", flat.getTitle());
        dto.put("mountX", flat.getMountX());
        dto.put("mountZ", flat.getMountZ());
        dto.put("sizeX", flat.getSizeX());
        dto.put("sizeZ", flat.getSizeZ());
        dto.put("seaLevel", flat.getSeaLevel());
        if (flat.getHexGrid() != null) {
            dto.put("hexGrid", Map.of("q", flat.getHexGrid().getQ(), "r", flat.getHexGrid().getR()));
        }
        dto.put("createdAt", flat.getCreatedAt());
        dto.put("updatedAt", flat.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toFlatDetailDto(WFlat flat) {
        Map<String, Object> dto = toFlatSummaryDto(flat);
        dto.put("description", flat.getDescription());
        dto.put("unknownProtected", flat.isUnknownProtected());
        dto.put("borderProtected", flat.isBorderProtected());
        dto.put("seaBlockId", flat.getSeaBlockId());

        Map<String, Object> materials = new HashMap<>();
        for (var entry : flat.getMaterials().entrySet()) {
            int key = Byte.toUnsignedInt(entry.getKey());
            WFlat.MaterialDefinition mat = entry.getValue();
            Map<String, Object> matDto = new HashMap<>();
            matDto.put("blockDef", mat.getBlockDef());
            matDto.put("nextBlockDef", mat.getNextBlockDef());
            matDto.put("hasOcean", mat.isHasOcean());
            matDto.put("isBlockMapDelta", mat.isBlockMapDelta());
            if (mat.getBlockAtLevels() != null && !mat.getBlockAtLevels().isEmpty()) {
                matDto.put("blockAtLevels", mat.getBlockAtLevels());
            }
            materials.put(String.valueOf(key), matDto);
        }
        dto.put("materials", materials);

        if (flat.getGroups() != null && !flat.getGroups().isEmpty()) {
            Map<String, Integer> groupSummary = new HashMap<>();
            for (var entry : flat.getGroups().entrySet()) {
                groupSummary.put(entry.getKey(), entry.getValue().size());
            }
            dto.put("groupsSummary", groupSummary);
        }

        return dto;
    }
}
