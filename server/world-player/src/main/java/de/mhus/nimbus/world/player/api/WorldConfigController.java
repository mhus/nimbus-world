package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.generated.configs.EngineConfiguration;
import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.configs.ServerInfo;
import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.generated.types.*;
import de.mhus.nimbus.shared.types.PlayerData;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.config.ServerSettings;
import de.mhus.nimbus.world.player.service.PlayerService;
import de.mhus.nimbus.world.shared.access.AccessValidator;
import de.mhus.nimbus.world.shared.session.WPlayerSession;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * World Configuration REST API
 * Provides complete EngineConfiguration for client initialization.
 */
@RestController
@RequestMapping("/player/world")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "World Config", description = "World configuration for client initialization")
public class WorldConfigController {

    private final WWorldService worldService;
    private final PlayerService playerService;
    private final AccessValidator accessUtil;
    private final ServerSettings serverSettings;
    private final WSessionService sessionService;
    private final WPlayerSessionService playerSessionService;

    @GetMapping("/config")
    @Operation(summary = "Get complete EngineConfiguration",
               description = "Returns worldInfo, playerInfo, playerBackpack, and settings")
    public ResponseEntity<?> getConfig(
            HttpServletRequest request,
            @RequestParam(required = false) String client) {

        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        var playerId = accessUtil.getPlayerId(request).orElseThrow(
                () -> new IllegalStateException("Player ID not found in request")
        );

        String clientVariant = client != null ? client : "viewer";
        log.info("Loading config for world: {}, player: {}, clientVariant: {}", worldId, playerId, clientVariant);

        // Load world from database
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "World not found"));
        }

        WWorld world = worldOpt.get();
        WorldInfo worldInfo = world.getPublicData();
        patchWorldInfo(worldInfo, worldId, playerId, request);

        // Load player data (always use WEB as ClientType for now)
        Optional<PlayerData> playerDataOpt = playerService.getPlayer(playerId, ClientType.WEB, worldId.getRegionId());
        if (playerDataOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Player not found"));
        }

        PlayerData playerData = playerDataOpt.get();
        PlayerInfo playerInfo = playerData.character().getPublicData();
        PlayerBackpack playerBackpack = playerData.character().getBackpack();
        Settings settings = playerData.settings();

        // Ensure all required fields are set with defaults
        setPlayerInfoDefaults(playerInfo);
        setPlayerBackpackDefaults(playerBackpack);
        setSettingsDefaults(settings);

        // Build ServerInfo from ServerSettings
        ServerInfo serverInfo = ServerInfo.builder()
                .websocketUrl(serverSettings.getWebsocketUrl())
                .build();

        // Build complete configuration
        EngineConfiguration config = EngineConfiguration.builder()
                .serverInfo(serverInfo)
                .worldInfo(worldInfo)
                .playerInfo(playerInfo)
                .playerBackpack(playerBackpack)
                .settings(settings)
                .build();

        return ResponseEntity.ok(config);
    }

    /**
     * Patch WorldInfo with entry point specific data.
     * Determines spawn position/rotation based on entry point setting in WSession.
     *
     * @param worldInfo The WorldInfo to patch
     * @param worldId The worldId
     * @param playerId The playerId
     * @param request The HTTP request (for session lookup)
     */
    private void patchWorldInfo(WorldInfo worldInfo, de.mhus.nimbus.shared.types.WorldId worldId, PlayerId playerId, HttpServletRequest request) {
        // Set editor URL
        worldInfo.setEditorUrl(serverSettings.getControlsBaseUrl() + "/");

        // Get session to determine entry point
        String sessionId = accessUtil.getSessionId(request);
        if (sessionId == null || sessionId.isBlank()) {
            log.debug("No session ID found, using default world entry point");
            return;
        }

        Optional<WSession> sessionOpt = sessionService.get(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Session not found: sessionId={}", sessionId);
            return;
        }

        WSession session = sessionOpt.get();
        String entryPoint = session.getEntryPoint();

        if (entryPoint == null || entryPoint.isBlank() || "world".equals(entryPoint)) {
            // Use world default spawn point (no changes needed)
            log.debug("Using world default spawn point for sessionId={}", sessionId);
            return;
        }

        if ("last".equals(entryPoint)) {
            // Load from last saved position (WPlayerSession)
            handleLastEntryPoint(worldInfo, worldId.getId(), playerId.getId());
        } else if (entryPoint.startsWith("grid:")) {
            // Load from hex grid coordinates
            handleGridEntryPoint(worldInfo, entryPoint);
        } else if (entryPoint.startsWith("position:")) {
            // Load from explicit position coordinates
            handlePositionEntryPoint(worldInfo, entryPoint);
        } else {
            log.warn("Unknown entry point type: {} for sessionId={}", entryPoint, sessionId);
        }
    }

    /**
     * Handle "last" entry point - load from WPlayerSession.
     * Falls back to world default if no saved session found.
     */
    private void handleLastEntryPoint(WorldInfo worldInfo, String worldId, String playerId) {
        Optional<WPlayerSession> playerSessionOpt = playerSessionService.loadSession(worldId, playerId);

        if (playerSessionOpt.isEmpty()) {
            log.debug("No saved session found for worldId={}, playerId={}, using world default", worldId, playerId);
            return;
        }

        WPlayerSession playerSession = playerSessionOpt.get();
        Vector3 position = playerSession.getPosition();
        // Rotation rotation = playerSession.getRotation(); // TODO: Add rotation support when needed

        if (position != null) {
            // Create Area from saved position
            Vector3Int posInt = Vector3Int.builder()
                    .x((int) Math.floor(position.getX()))
                    .y((int) Math.floor(position.getY()))
                    .z((int) Math.floor(position.getZ()))
                    .build();

            Area area = Area.builder()
                    .position(posInt)
                    .size(Vector3Int.builder().x(1).y(1).z(1).build()) // Single point
                    .build();

            WorldInfoEntryPointDTO entryPointDTO = WorldInfoEntryPointDTO.builder()
                    .area(area)
                    .build();

            worldInfo.setEntryPoint(entryPointDTO);
            log.info("Restored entry point from saved session: worldId={}, playerId={}, position={}",
                    worldId, playerId, position);
        }
    }

    /**
     * Handle "grid:q,r" entry point - set hex grid coordinates.
     * Falls back to world default if coordinates invalid.
     */
    private void handleGridEntryPoint(WorldInfo worldInfo, String entryPoint) {
        // Parse grid coordinates from "grid:q,r" format
        String coordsPart = entryPoint.substring(5); // Remove "grid:" prefix
        String[] coords = coordsPart.split(",");

        if (coords.length != 2) {
            log.warn("Invalid grid coordinates format: {}, expected 'grid:q,r'", entryPoint);
            return;
        }

        try {
            int q = Integer.parseInt(coords[0].trim());
            int r = Integer.parseInt(coords[1].trim());

            // Create HexVector2 from coordinates
            HexVector2 hexGrid = HexVector2.builder()
                    .q(q)
                    .r(r)
                    .build();

            WorldInfoEntryPointDTO entryPointDTO = WorldInfoEntryPointDTO.builder()
                    .grid(hexGrid)
                    .build();

            worldInfo.setEntryPoint(entryPointDTO);
            log.info("Set hex grid entry point: q={}, r={}", q, r);

        } catch (NumberFormatException e) {
            log.warn("Invalid grid coordinates (not numbers): {}", entryPoint, e);
        }
    }

    /**
     * Handle "position:x,y,z" entry point - set explicit position coordinates.
     * Falls back to world default if coordinates invalid.
     */
    private void handlePositionEntryPoint(WorldInfo worldInfo, String entryPoint) {
        // Parse position coordinates from "position:x,y,z" format
        String coordsPart = entryPoint.substring(9); // Remove "position:" prefix
        String[] coords = coordsPart.split(",");

        if (coords.length != 3) {
            log.warn("Invalid position coordinates format: {}, expected 'position:x,y,z'", entryPoint);
            return;
        }

        try {
            double x = Double.parseDouble(coords[0].trim());
            double y = Double.parseDouble(coords[1].trim());
            double z = Double.parseDouble(coords[2].trim());

            // Create Vector3Int from coordinates (floor to integers)
            Vector3Int posInt = Vector3Int.builder()
                    .x((int) Math.floor(x))
                    .y((int) Math.floor(y))
                    .z((int) Math.floor(z))
                    .build();

            Area area = Area.builder()
                    .position(posInt)
                    .size(Vector3Int.builder().x(1).y(1).z(1).build()) // Single point
                    .build();

            WorldInfoEntryPointDTO entryPointDTO = WorldInfoEntryPointDTO.builder()
                    .area(area)
                    .build();

            worldInfo.setEntryPoint(entryPointDTO);
            log.info("Set position entry point: x={}, y={}, z={}", x, y, z);

        } catch (NumberFormatException e) {
            log.warn("Invalid position coordinates (not numbers): {}", entryPoint, e);
        }
    }

    @GetMapping("/config/worldinfo")
    @Operation(summary = "Get WorldInfo only")
    public ResponseEntity<?> getWorldInfo(HttpServletRequest request) {
        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "World not found"));
        }

        return ResponseEntity.ok(worldOpt.get().getPublicData());
    }

    @GetMapping("/config/playerinfo")
    @Operation(summary = "Get PlayerInfo only")
    public ResponseEntity<?> getPlayerInfo(HttpServletRequest request) {
        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        var playerId = accessUtil.getPlayerId(request).orElseThrow(
                () -> new IllegalStateException("Player ID not found in request")
        );

        Optional<PlayerData> playerDataOpt = playerService.getPlayer(playerId, ClientType.WEB, worldId.getRegionId());
        if (playerDataOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Player not found"));
        }

        return ResponseEntity.ok(playerDataOpt.get().character().getPublicData());
    }

    @GetMapping("/config/playerbackpack")
    @Operation(summary = "Get PlayerBackpack only")
    public ResponseEntity<?> getPlayerBackpack(HttpServletRequest request) {
        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        var playerId = accessUtil.getPlayerId(request).orElseThrow(
                () -> new IllegalStateException("Player ID not found in request")
        );

        Optional<PlayerData> playerDataOpt = playerService.getPlayer(playerId, ClientType.WEB, worldId.getRegionId());
        if (playerDataOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Player not found"));
        }

        return ResponseEntity.ok(playerDataOpt.get().character().getBackpack());
    }

    @GetMapping("/config/settings")
    @Operation(summary = "Get Settings only")
    public ResponseEntity<?> getSettings(
            HttpServletRequest request,
            @RequestParam(required = false) String client) {

        String clientVariant = client != null ? client : "viewer";

        var worldId = accessUtil.getWorldId(request).orElseThrow(
                () -> new IllegalStateException("World ID not found in request")
        );

        var playerId = accessUtil.getPlayerId(request).orElseThrow(
                () -> new IllegalStateException("Player ID not found in request")
        );

        // Always use WEB as ClientType for now
        Optional<PlayerData> playerDataOpt = playerService.getPlayer(playerId, ClientType.WEB, worldId.getRegionId());
        if (playerDataOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Player not found"));
        }

        return ResponseEntity.ok(playerDataOpt.get().settings());
    }

    /**
     * Set default values for PlayerInfo fields if they are null or missing.
     */
    private void setPlayerInfoDefaults(PlayerInfo playerInfo) {
        if (playerInfo == null) return;

        // Set basic fields
        if (playerInfo.getPlayerId() == null) {
            playerInfo.setPlayerId("default-player");
        }
        if (playerInfo.getTitle() == null) {
            playerInfo.setTitle("Player");
        }
        if (playerInfo.getThirdPersonModelId() == null) {
            playerInfo.setThirdPersonModelId("wizard1");
        }

        // Set movement speeds
        if (playerInfo.getBaseWalkSpeed() == 0.0) playerInfo.setBaseWalkSpeed(5.0);
        if (playerInfo.getBaseRunSpeed() == 0.0) playerInfo.setBaseRunSpeed(7.0);
        if (playerInfo.getBaseUnderwaterSpeed() == 0.0) playerInfo.setBaseUnderwaterSpeed(3.0);
        if (playerInfo.getBaseCrawlSpeed() == 0.0) playerInfo.setBaseCrawlSpeed(1.5);
        if (playerInfo.getBaseRidingSpeed() == 0.0) playerInfo.setBaseRidingSpeed(8.0);
        if (playerInfo.getBaseJumpSpeed() == 0.0) playerInfo.setBaseJumpSpeed(8.0);

        if (playerInfo.getEffectiveWalkSpeed() == 0.0) playerInfo.setEffectiveWalkSpeed(5.0);
        if (playerInfo.getEffectiveRunSpeed() == 0.0) playerInfo.setEffectiveRunSpeed(7.0);
        if (playerInfo.getEffectiveUnderwaterSpeed() == 0.0) playerInfo.setEffectiveUnderwaterSpeed(3.0);
        if (playerInfo.getEffectiveCrawlSpeed() == 0.0) playerInfo.setEffectiveCrawlSpeed(1.5);
        if (playerInfo.getEffectiveRidingSpeed() == 0.0) playerInfo.setEffectiveRidingSpeed(8.0);
        if (playerInfo.getEffectiveJumpSpeed() == 0.0) playerInfo.setEffectiveJumpSpeed(8.0);

        // Set other properties
        if (playerInfo.getEyeHeight() == 0.0) playerInfo.setEyeHeight(1.6);
        if (playerInfo.getStealthRange() == 0.0) playerInfo.setStealthRange(8.0);
        if (playerInfo.getSelectionRadius() == 0.0) playerInfo.setSelectionRadius(5.0);
        if (playerInfo.getBaseTurnSpeed() == 0.0) playerInfo.setBaseTurnSpeed(0.003);
        if (playerInfo.getEffectiveTurnSpeed() == 0.0) playerInfo.setEffectiveTurnSpeed(0.003);
        if (playerInfo.getBaseUnderwaterTurnSpeed() == 0.0) playerInfo.setBaseUnderwaterTurnSpeed(0.002);
        if (playerInfo.getEffectiveUnderwaterTurnSpeed() == 0.0) playerInfo.setEffectiveUnderwaterTurnSpeed(0.002);

        // Ensure stateValues exists and has 'default' state as fallback
        if (playerInfo.getStateValues() == null) {
            playerInfo.setStateValues(new HashMap<>());
        }

        // Create default dimensions
        MovementStateDomensions defaultDimensions = new MovementStateDomensions();
        defaultDimensions.setHeight(2.0);
        defaultDimensions.setWidth(0.6);
        defaultDimensions.setFootprint(0.3);

        // Ensure 'default' state exists
        if (!playerInfo.getStateValues().containsKey("default")) {
            MovementStateValues defaultState = new MovementStateValues();
            defaultState.setDimensions(defaultDimensions);
            defaultState.setBaseMoveSpeed(5.0);
            defaultState.setEffectiveMoveSpeed(5.0);
            defaultState.setBaseJumpSpeed(8.0);
            defaultState.setEffectiveJumpSpeed(8.0);
            defaultState.setEyeHeight(1.6);
            defaultState.setBaseTurnSpeed(0.003);
            defaultState.setEffectiveTurnSpeed(0.003);
            defaultState.setSelectionRadius(5.0);
            defaultState.setStealthRange(8.0);
            defaultState.setDistanceNotifyReduction(0.0);

            playerInfo.getStateValues().put("default", defaultState);
        } else {
            // Default state exists, but ensure dimensions are set
            MovementStateValues defaultState = playerInfo.getStateValues().get("default");
            if (defaultState.getDimensions() == null) {
                defaultState.setDimensions(defaultDimensions);
            }
        }

        // Ensure all states have complete values with defaults
        for (Map.Entry<String, MovementStateValues> entry : playerInfo.getStateValues().entrySet()) {
            String stateKey = entry.getKey();
            MovementStateValues state = entry.getValue();

            if (state == null) continue;

            // Set dimensions if missing
            if (state.getDimensions() == null) {
                MovementStateDomensions dimensions = new MovementStateDomensions();
                dimensions.setHeight(2.0);
                dimensions.setWidth(0.6);
                dimensions.setFootprint(0.3);
                state.setDimensions(dimensions);
            }

            // Set movement speeds if missing
            if (state.getBaseMoveSpeed() == 0.0) state.setBaseMoveSpeed(5.0);
            if (state.getEffectiveMoveSpeed() == 0.0) state.setEffectiveMoveSpeed(5.0);
            if (state.getBaseJumpSpeed() == 0.0) state.setBaseJumpSpeed(8.0);
            if (state.getEffectiveJumpSpeed() == 0.0) state.setEffectiveJumpSpeed(8.0);

            // Set other properties if missing
            if (state.getEyeHeight() == 0.0) state.setEyeHeight(1.6);
            if (state.getBaseTurnSpeed() == 0.0) state.setBaseTurnSpeed(0.003);
            if (state.getEffectiveTurnSpeed() == 0.0) state.setEffectiveTurnSpeed(0.003);
            if (state.getSelectionRadius() == 0.0) state.setSelectionRadius(5.0);
            if (state.getStealthRange() == 0.0) state.setStealthRange(8.0);
        }
    }

    /**
     * Set default values for PlayerBackpack fields if they are null or missing.
     */
    private void setPlayerBackpackDefaults(PlayerBackpack backpack) {
        if (backpack == null) return;

        if (backpack.getItemIds() == null) {
            backpack.setItemIds(new java.util.HashMap<>());
        }
        if (backpack.getWearingItemIds() == null) {
            backpack.setWearingItemIds(new java.util.HashMap<>());
        }
    }

    /**
     * Set default values for Settings fields if they are null or missing.
     */
    private void setSettingsDefaults(Settings settings) {
        if (settings == null) return;

        if (settings.getName() == null) {
            settings.setName("Player");
        }
        if (settings.getInputController() == null) {
            settings.setInputController("keyboard");
        }
        if (settings.getInputMappings() == null) {
            settings.setInputMappings(new java.util.HashMap<>());
        }
    }

}
