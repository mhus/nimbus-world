package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.world.generator.mcp.McpToolBean;
import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
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
public class HexGridTools implements McpToolBean {

    private final WHexGridService hexGridService;

    @Tool(name = "list_hexgrids", description = "List all hex grids for a world. Returns id, position (q,r), name, title, description, enabled status, epoches, and parameters. Use epoch parameter to filter by specific epoch.")
    public Map<String, Object> listHexGrids(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Optional epoch number to filter hex grids belonging to this epoch", required = false) Integer epoch) {
        log.debug("MCP: List hex grids: worldId={}, epoch={}", worldId, epoch);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        List<WHexGrid> hexGrids;
        if (epoch != null) {
            hexGrids = hexGridService.findByWorldId(worldId, epoch);
        } else {
            hexGrids = hexGridService.findByWorldId(worldId);
        }

        var dtos = hexGrids.stream().map(this::toDto).toList();

        return Map.of(
                "worldId", worldId,
                "count", dtos.size(),
                "hexGrids", dtos
        );
    }

    @Tool(name = "get_hexgrid", description = "Get a single hex grid by its position (q, r). Returns full data including publicData, parameters, areas, and epoches.")
    public Map<String, Object> getHexGrid(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Hex position Q coordinate") int q,
            @ToolParam(description = "Hex position R coordinate") int r,
            @ToolParam(description = "Optional epoch number to get the hex grid variant for this epoch", required = false) Integer epoch) {
        log.debug("MCP: Get hex grid: worldId={}, q={}, r={}, epoch={}", worldId, q, r, epoch);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        HexVector2 hexPos = HexVector2.builder().q(q).r(r).build();

        WHexGrid hexGrid;
        if (epoch != null) {
            hexGrid = hexGridService.findByWorldIdAndPosition(worldId, hexPos, epoch)
                    .orElseThrow(() -> new McpToolException("Hex grid not found at position " + q + ";" + r + " for epoch " + epoch));
        } else {
            hexGrid = hexGridService.findByWorldIdAndPosition(worldId, hexPos)
                    .orElseThrow(() -> new McpToolException("Hex grid not found at position " + q + ";" + r));
        }

        Map<String, Object> result = toDto(hexGrid);
        if (hexGrid.getAreas() != null && !hexGrid.getAreas().isEmpty()) {
            result.put("areas", hexGrid.getAreas());
        }
        return result;
    }

    @Tool(name = "create_hexgrid", description = "Create a new hex grid at the given position. Multiple hex grids can exist at the same position with different epoches.")
    public Map<String, Object> createHexGrid(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Hex position Q coordinate") int q,
            @ToolParam(description = "Hex position R coordinate") int r,
            @ToolParam(description = "Technical name for the hex grid") String name,
            @ToolParam(description = "Description of the hex grid area") String description,
            @ToolParam(description = "Display title", required = false) String title,
            @ToolParam(description = "Icon identifier", required = false) String icon,
            @ToolParam(description = "Generator parameters as key-value pairs", required = false) Map<String, String> parameters,
            @ToolParam(description = "Epoch numbers this hex grid belongs to (e.g. [0,1,2]). If not specified, defaults to empty list (= not visible in any epoch).", required = false) List<Integer> epoches) {
        log.debug("MCP: Create hex grid: worldId={}, q={}, r={}, name={}", worldId, q, r, name);

        if (Strings.isBlank(worldId) || Strings.isBlank(name) || Strings.isBlank(description)) {
            throw new McpToolException("worldId, name, and description are required");
        }

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            HexGrid publicData = HexGrid.builder()
                    .position(HexVector2.builder().q(q).r(r).build())
                    .name(name)
                    .description(description)
                    .title(title)
                    .icon(icon)
                    .build();

            WHexGrid created = hexGridService.create(worldId, publicData, parameters, null, epoches);

            return Map.of(
                    "id", created.getId(),
                    "worldId", worldId,
                    "position", q + ";" + r,
                    "status", "created"
            );
        } catch (IllegalStateException e) {
            throw new McpToolException("Conflict: " + e.getMessage());
        } catch (Exception e) {
            throw new McpToolException("Failed to create hex grid: " + e.getMessage());
        }
    }

    @Tool(name = "update_hexgrid", description = "Update an existing hex grid. Only provided fields are updated. Use the hex grid's MongoDB id for precise updates when multiple epoch variants exist at the same position.")
    public Map<String, Object> updateHexGrid(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Hex position Q coordinate") int q,
            @ToolParam(description = "Hex position R coordinate") int r,
            @ToolParam(description = "MongoDB document ID for precise update when multiple epoch variants exist at same position. Use list_hexgrids to find the id.", required = false) String id,
            @ToolParam(description = "New technical name", required = false) String name,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "New display title", required = false) String title,
            @ToolParam(description = "New icon", required = false) String icon,
            @ToolParam(description = "Whether the hex grid is enabled", required = false) Boolean enabled,
            @ToolParam(description = "Generator parameters (replaces existing)", required = false) Map<String, String> parameters,
            @ToolParam(description = "Epoch numbers this hex grid belongs to (replaces existing epoches)", required = false) List<Integer> epoches) {
        log.debug("MCP: Update hex grid: worldId={}, q={}, r={}, id={}", worldId, q, r, id);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            Optional<WHexGrid> updated;
            if (Strings.isNotBlank(id)) {
                updated = hexGridService.updateById(id, hexGrid -> {
                    applyUpdates(hexGrid, name, description, title, icon, enabled, parameters, epoches);
                });
            } else {
                HexVector2 hexPos = HexVector2.builder().q(q).r(r).build();
                updated = hexGridService.update(worldId, hexPos, hexGrid -> {
                    applyUpdates(hexGrid, name, description, title, icon, enabled, parameters, epoches);
                });
            }

            if (updated.isEmpty()) {
                throw new McpToolException("Hex grid not found at position " + q + ";" + r);
            }

            return Map.of(
                    "id", updated.get().getId(),
                    "worldId", worldId,
                    "position", q + ";" + r,
                    "status", "updated"
            );
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to update hex grid: " + e.getMessage());
        }
    }

    @Tool(name = "delete_hexgrid", description = "Delete a hex grid. Use id for precise deletion when multiple epoch variants exist at the same position.")
    public Map<String, Object> deleteHexGrid(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist'). Must be a world ID, not a collection.") String worldId,
            @ToolParam(description = "Hex position Q coordinate") int q,
            @ToolParam(description = "Hex position R coordinate") int r,
            @ToolParam(description = "MongoDB document ID for precise deletion. If not provided, deletes the first variant found at this position.", required = false) String id,
            @ToolParam(description = "If true, deletes ALL epoch variants at this position", required = false) Boolean deleteAll) {
        log.debug("MCP: Delete hex grid: worldId={}, q={}, r={}, id={}, deleteAll={}", worldId, q, r, id, deleteAll);

        if (Strings.isBlank(worldId)) {
            throw new McpToolException("worldId is required");
        }

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId));

        try {
            if (Strings.isNotBlank(id)) {
                boolean deleted = hexGridService.deleteById(id);
                if (!deleted) {
                    throw new McpToolException("Hex grid not found with id: " + id);
                }
                return Map.of("deleted", true, "id", id);
            }

            HexVector2 hexPos = HexVector2.builder().q(q).r(r).build();

            if (Boolean.TRUE.equals(deleteAll)) {
                int count = hexGridService.deleteAllAtPosition(worldId, hexPos);
                return Map.of("deleted", true, "count", count, "position", q + ";" + r);
            }

            boolean deleted = hexGridService.delete(worldId, hexPos);
            if (!deleted) {
                throw new McpToolException("Hex grid not found at position " + q + ";" + r);
            }
            return Map.of("deleted", true, "position", q + ";" + r);
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to delete hex grid: " + e.getMessage());
        }
    }

    private void applyUpdates(WHexGrid hexGrid, String name, String description, String title,
                              String icon, Boolean enabled, Map<String, String> parameters, List<Integer> epoches) {
        HexGrid publicData = hexGrid.getPublicData();
        if (publicData == null) {
            publicData = HexGrid.builder()
                    .position(HexVector2.builder().q(0).r(0).build())
                    .build();
        }
        if (name != null) publicData.setName(name);
        if (description != null) publicData.setDescription(description);
        if (title != null) publicData.setTitle(title);
        if (icon != null) publicData.setIcon(icon);
        hexGrid.setPublicData(publicData);

        if (enabled != null) hexGrid.setEnabled(enabled);
        if (parameters != null) hexGrid.setParameters(parameters);
        if (epoches != null) hexGrid.setEpoches(new ArrayList<>(epoches));
    }

    private Map<String, Object> toDto(WHexGrid hexGrid) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", hexGrid.getId());
        map.put("position", hexGrid.getPosition());
        if (hexGrid.getPublicData() != null) {
            map.put("name", hexGrid.getPublicData().getName() != null ? hexGrid.getPublicData().getName() : "");
            map.put("title", hexGrid.getPublicData().getTitle() != null ? hexGrid.getPublicData().getTitle() : "");
            map.put("description", hexGrid.getPublicData().getDescription() != null ? hexGrid.getPublicData().getDescription() : "");
            if (hexGrid.getPublicData().getIcon() != null) {
                map.put("icon", hexGrid.getPublicData().getIcon());
            }
        }
        map.put("enabled", hexGrid.isEnabled());
        map.put("epoches", hexGrid.getEpoches() != null ? hexGrid.getEpoches() : List.of());
        if (hexGrid.getParameters() != null && !hexGrid.getParameters().isEmpty()) {
            map.put("parameters", hexGrid.getParameters());
        }
        return map;
    }
}
