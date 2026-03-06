package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/control/player/map")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Map", description = "Player map panel endpoints")
public class PlayerMapController extends BaseEditorController {

    private final WPlayerSessionService playerSessionService;
    private final WHexGridService hexGridService;
    private final WWorldService worldService;
    private final WProgressService progressService;

    @GetMapping("/home")
    @Operation(summary = "Get player's current hex position, world position, and hex grid info")
    public ResponseEntity<?> getHome(HttpServletRequest request) {

        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var playerId = PlayerId.of(userId, characterId).orElse(null);
        if (playerId == null) {
            return bad("Invalid playerId");
        }

        var world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            return notFound("World not found");
        }

        int hexGridSize = world.getPublicData().getHexGridSize();
        if (hexGridSize <= 0) {
            return bad("World has no hex grid configured");
        }

        var session = playerSessionService.loadSession(worldId, playerId.getId()).orElse(null);
        if (session == null || session.getPosition() == null) {
            return notFound("Player session not found");
        }

        var pos = session.getPosition();
        int worldX = (int) Math.round(pos.getX());
        int worldZ = (int) Math.round(pos.getZ());

        HexVector2 hexPos = HexMathUtil.flatToHex(
                Vector2Int.builder().x(worldX).z(worldZ).build(),
                hexGridSize
        );

        var hexGrid = hexGridService.findByWorldIdAndPosition(worldId, hexPos).orElse(null);

        Map<String, Object> result = buildHexInfo(worldId, playerId.getId(), hexPos, hexGrid, hexGridSize);

        // Add player position relative to hex center for overlay
        int[] hexCenter = HexMathUtil.hexToCartesian(hexPos, hexGridSize);
        int gridWidth = HexMathUtil.getGridWidth(hexGridSize);
        result.put("playerWorldX", worldX);
        result.put("playerWorldZ", worldZ);
        result.put("hexCenterX", hexCenter[0]);
        result.put("hexCenterZ", hexCenter[1]);
        result.put("hexGridSize", hexGridSize);
        result.put("hexGridWidth", gridWidth);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/hex")
    @Operation(summary = "Get hex grid info for a specific hex position")
    public ResponseEntity<?> getHex(
            HttpServletRequest request,
            @RequestParam int q,
            @RequestParam int r
    ) {
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);

        if (Strings.isBlank(worldId) || Strings.isBlank(userId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        var playerId = PlayerId.of(userId, characterId).orElse(null);
        if (playerId == null) {
            return bad("Invalid playerId");
        }

        var world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            return notFound("World not found");
        }

        int hexGridSize = world.getPublicData().getHexGridSize();
        if (hexGridSize <= 0) {
            return bad("World has no hex grid configured");
        }

        HexVector2 hexPos = HexVector2.builder().q(q).r(r).build();

        var hexGrid = hexGridService.findByWorldIdAndPosition(worldId, hexPos).orElse(null);
        if (hexGrid == null) {
            return notFound("Hex grid not found");
        }

        Map<String, Object> result = buildHexInfo(worldId, playerId.getId(), hexPos, hexGrid, hexGridSize);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> buildHexInfo(String worldId, String playerId, HexVector2 hexPos, WHexGrid hexGrid, int hexGridSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("q", hexPos.getQ());
        result.put("r", hexPos.getR());

        if (hexGrid != null && hexGrid.getPublicData() != null) {
            result.put("name", hexGrid.getPublicData().getName());
            result.put("title", hexGrid.getPublicData().getTitle());
            result.put("description", hexGrid.getPublicData().getDescription());
            result.put("exists", true);
        } else {
            result.put("exists", false);
        }

        // Build neighbor info
        List<Map<String, Object>> neighbors = new ArrayList<>();
        for (WHexGrid.EDGE edge : WHexGrid.EDGE.values()) {
            HexVector2 neighborPos = HexMathUtil.getNeighborPosition(hexPos, edge);
            Map<String, Object> neighborInfo = new LinkedHashMap<>();
            neighborInfo.put("edge", edge.getShortName());
            neighborInfo.put("q", neighborPos.getQ());
            neighborInfo.put("r", neighborPos.getR());

            // Check if neighbor hex grid exists
            var neighborGrid = hexGridService.findByWorldIdAndPosition(worldId, neighborPos).orElse(null);
            neighborInfo.put("exists", neighborGrid != null);

            if (neighborGrid != null && neighborGrid.getPublicData() != null) {
                neighborInfo.put("name", neighborGrid.getPublicData().getName());
                neighborInfo.put("title", neighborGrid.getPublicData().getTitle());
            }

            // Check if player has explored this neighbor
            String hexKey = neighborPos.getQ() + ";" + neighborPos.getR();
            var explored = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, playerId, "EXPLORED_HEX", hexKey
            );
            neighborInfo.put("explored", explored.isPresent());

            neighbors.add(neighborInfo);
        }
        result.put("neighbors", neighbors);

        return result;
    }
}
