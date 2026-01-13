package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.ActorRoles;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.access.AccessService;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.dto.DevLoginResponse;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.session.WPlayerSession;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.session.WSession;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for teleportation login.
 * Handles teleportation flow when player interacts with teleportation blocks.
 * This controller is under /control/public because the session may already be CLOSED.
 */
@RestController
@RequestMapping("/control/public")
@RequiredArgsConstructor
@Slf4j
public class PlayerTeleportController {

    private final WPlayerSessionService playerSessionService;
    private final WSessionService sessionService;
    private final WWorldService worldService;
    private final WWorldInstanceService worldInstanceService;
    private final RCharacterService characterService;
    private final AccessService accessService;
    private final AccessSettings accessSettings;

    /**
     * Teleport login endpoint.
     * Reads sessionId from cookie, checks for teleportation target in WSession,
     * creates new session in target world.
     *
     * @param request HttpServletRequest with sessionId cookie
     * @return DevLoginResponse with jumpUrl and accessUrls
     */
    @PostMapping("/teleport-login")
    @Transactional
    public ResponseEntity<?> teleportLogin(HttpServletRequest request) {
        log.debug("POST /control/player/teleport-login");

        try {
            // Get sessionId from request attributes (set by AccessFilter)
            String sessionId = (String) request.getAttribute(AccessFilterBase.ATTR_SESSION_ID);
            if (sessionId == null || sessionId.isBlank()) {
                log.warn("No sessionId in request attributes for teleport-login");
                return ResponseEntity.status(400)
                        .body(Map.of("error", "No session found"));
            }

            log.debug("Teleport-login for sessionId: {}", sessionId);

            // Get WSession from WSessionService
            Optional<WSession> wSessionOpt = sessionService.get(sessionId);
            if (wSessionOpt.isEmpty()) {
                log.warn("WSession not found for sessionId: {}", sessionId);
                return ResponseEntity.status(404)
                        .body(Map.of("error", "Session not found"));
            }

            WSession wSession = wSessionOpt.get();

            // Check if teleportation is set
            String teleportation = wSession.getTeleportation();
            if (teleportation == null || teleportation.isBlank()) {
                log.warn("No teleportation target set in session: {}", sessionId);
                return ResponseEntity.status(400)
                        .body(Map.of("error", "No teleportation target set"));
            }

            log.info("Teleportation found in WSession: sessionId={}, target={}", sessionId, teleportation);

            // Parse teleportation target
            WorldId currentWorldId = WorldId.of(wSession.getWorldId())
                    .orElseThrow(() -> new IllegalStateException("Invalid worldId in WSession: " + wSession.getWorldId()));
            TeleportTarget target = parseTeleportTarget(teleportation, currentWorldId);
            if (target == null) {
                log.warn("Failed to parse teleportation target: {}", teleportation);
                return ResponseEntity.status(400)
                        .body(Map.of("error", "Invalid teleportation target format"));
            }

            // Get player info from WSession
            PlayerId playerId = PlayerId.of(wSession.getPlayerId())
                    .orElseThrow(() -> new IllegalStateException("Invalid playerId in WSession: " + wSession.getPlayerId()));

            // Get actor from WSession
            String actor = wSession.getActor() != null ? wSession.getActor() : "PLAYER";

            // Validate target world exists
            WWorld targetWorld = worldService.getByWorldId(target.worldId)
                    .orElseThrow(() -> new IllegalArgumentException("Target world not found: " + target.worldId));

            log.info("Creating new session for teleport: playerId={}, targetWorld={}, entryPoint={}",
                    playerId.getId(), target.worldId, target.entryPoint);

            // Determine effective worldId (might be an instanceId for instanceable worlds)
            String effectiveWorldId = target.worldId;

            // Auto-create instance for PLAYER actors in instanceable worlds
            if (targetWorld.isInstanceable() && "PLAYER".equals(actor)) {
                RCharacter character = characterService.getCharacter(
                        playerId.getUserId(),
                        targetWorld.getRegionId(),
                        playerId.getCharacterId()
                ).orElseThrow(() -> new IllegalArgumentException("Character not found"));

                WWorldInstance instance = worldInstanceService.createInstanceForPlayer(
                        target.worldId,
                        targetWorld.getName(),
                        playerId.getId(),
                        character.getDisplay()
                );

                effectiveWorldId = instance.getWorldWithInstanceId();
                log.info("Auto-created world instance for teleport: instanceId={}, worldId={}, playerId={}",
                        instance.getInstanceId(), target.worldId, playerId.getId());
            }

            // Check if this is a teleportation to a different world or within the same world
            String sourceWorldId = wSession.getWorldId();
            boolean isCrossWorldTeleport = !sourceWorldId.equals(effectiveWorldId);

            // Load old WPlayerSession to get previous position/rotation (only for cross-world teleport)
            String previousWorldId = null;
            Vector3 previousPosition = null;
            Rotation previousRotation = null;

            if (isCrossWorldTeleport) {
                Optional<WPlayerSession> oldPlayerSessionOpt = playerSessionService.loadSession(
                        sourceWorldId,
                        playerId.getId()
                );

                if (oldPlayerSessionOpt.isPresent()) {
                    WPlayerSession oldPlayerSession = oldPlayerSessionOpt.get();
                    previousWorldId = oldPlayerSession.getWorldId();
                    previousPosition = oldPlayerSession.getPosition();
                    previousRotation = oldPlayerSession.getRotation();
                    log.debug("Loaded old player session for cross-world teleport: previousWorldId={}, previousPosition={}",
                            previousWorldId, previousPosition);
                } else {
                    // First teleport - use source worldId as previousWorldId (the world we're leaving)
                    previousWorldId = sourceWorldId;
                    log.debug("No previous player session found for cross-world teleport - using source worldId as previous: {}",
                            previousWorldId);
                }

                log.info("Cross-world teleportation: from {} to {}", sourceWorldId, effectiveWorldId);
            } else {
                log.info("Same-world teleportation within: {}", sourceWorldId);
            }

            // Create new WSession with effective worldId
            WorldId sessionWorldId = WorldId.unchecked(effectiveWorldId);
            WSession newSession = sessionService.create(sessionWorldId, playerId, actor);

            // Set entry point if provided
            if (target.entryPoint != null && !target.entryPoint.isBlank()) {
                sessionService.updateEntryPoint(newSession.getId(), target.entryPoint);
                log.debug("Entry point set for teleport session: sessionId={}, entryPoint={}",
                        newSession.getId(), target.entryPoint);
            }

            // Create/update WPlayerSession
            if (isCrossWorldTeleport) {
                // Cross-world teleportation: Create new player session with previous data
                Optional<WPlayerSession> oldPlayerSessionOpt = playerSessionService.loadSession(
                        sourceWorldId,
                        playerId.getId()
                );

                // Determine entry position - use world entry point or fallback to world start or default
                Vector3 position;
                var entryPoint = targetWorld.getPublicData().getEntryPoint();
                if (entryPoint != null && entryPoint.getArea() != null && entryPoint.getArea().getPosition() != null) {
                    // Use configured entry point
                    var entryPosition = entryPoint.getArea().getPosition();
                    position = Vector3.builder()
                            .x(entryPosition.getX())
                            .y(entryPosition.getY())
                            .z(entryPosition.getZ())
                            .build();
                    log.debug("Using world entry point for cross-world teleport: {}", position);
                } else if (targetWorld.getPublicData().getStart() != null) {
                    // Fallback to world start position
                    position = targetWorld.getPublicData().getStart();
                    log.debug("Using world start position as fallback for cross-world teleport: {}", position);
                } else {
                    // Fallback to default spawn position
                    position = Vector3.builder().x(0).y(64).z(0).build();
                    log.warn("No entry point or start position configured for world {}, using default: {}",
                            effectiveWorldId, position);
                }
                var rotation  = Rotation.builder().y(0).p(0).build();

                WPlayerSession newPlayerSession = playerSessionService.createTeleportSession(
                        effectiveWorldId,
                        playerId.getId(),
                        newSession.getId(),
                        actor,
                        position,
                        rotation,
                        previousWorldId,
                        previousPosition,
                        previousRotation
                );

                // Merge player status data from old session to new session
                if (oldPlayerSessionOpt.isPresent()) {
                    log.debug("Merging player data from old session to new session");
                    playerSessionService.mergePlayerData(newPlayerSession, oldPlayerSessionOpt.get());
                } else {
                    log.debug("No old player session found, skipping merge");
                }

                log.info("Created player session for cross-world teleport: newWorldId={}, previousWorldId={}",
                        effectiveWorldId, previousWorldId);
            } else {
                // Same-world teleportation: Just update entry point, player session will be updated when player enters
                log.debug("Skipping player session creation for same-world teleport - will be updated on entry");
            }

            // Create JWT token for new session
            // Extract regionId from effective worldId
            WorldId effectiveWorldIdObj = WorldId.unchecked(effectiveWorldId);
            String token = accessService.createSessionTokenForTeleport(
                    effectiveWorldIdObj.getRegionId(),
                    playerId.getUserId(),
                    effectiveWorldId,
                    playerId.getCharacterId(),
                    actor,
                    newSession.getId()
            );

            // Build response with URLs
            String jumpUrl = buildJumpUrl(
                    ActorRoles.valueOf(actor),
                    effectiveWorldId,
                    newSession.getId(),
                    playerId.getUserId(),
                    playerId.getCharacterId()
            );

            DevLoginResponse response = DevLoginResponse.builder()
                    .accessToken(token)
                    .accessUrls(accessSettings.getAccessUrls())
                    .jumpUrl(jumpUrl)
                    .sessionId(newSession.getId())
                    .playerId(playerId.getId())
                    .build();

            log.info("Teleport login successful: sessionId={}, newSessionId={}, targetWorld={}, playerId={}",
                    sessionId, newSession.getId(), target.worldId, playerId.getId());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Teleport login validation failed: {}", e.getMessage());
            return ResponseEntity.status(400)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Teleport login failed unexpectedly", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    /**
     * Parse teleportation target string.
     * Format: <worldId>@<position> or worldId@grid:q,r or worldId or @<position> or @grid:q,r
     *
     * @param target Teleportation target string
     * @param currentWorldId Current world ID (used when worldId is empty)
     * @return Parsed TeleportTarget or null if invalid
     */
    private TeleportTarget parseTeleportTarget(String target, WorldId currentWorldId) {
        if (target == null || target.isBlank()) {
            return null;
        }

        String worldIdPart = null;
        String entryPoint = null;

        int hashIndex = target.indexOf('@');
        if (hashIndex >= 0) {
            // Format: worldId#position or #position
            worldIdPart = target.substring(0, hashIndex);
            entryPoint = target.substring(hashIndex + 1);

            // Empty worldId part means use current world
            if (worldIdPart.isBlank()) {
                worldIdPart = currentWorldId != null ? currentWorldId.withoutInstance().getId() : null;
            }
        } else {
            // Format: worldId only (use default entry point)
            worldIdPart = target;
        }

        // Validate worldId
        if (worldIdPart == null || worldIdPart.isBlank()) {
            log.warn("Invalid teleportation target: worldId is empty");
            return null;
        }

        // Parse WorldId
        Optional<WorldId> worldIdOpt = WorldId.of(worldIdPart);
        if (worldIdOpt.isEmpty()) {
            log.warn("Invalid worldId format in teleportation target: {}", worldIdPart);
            return null;
        }

        WorldId worldId = worldIdOpt.get();

        // Ensure no instance part
        if (worldId.isInstance()) {
            log.warn("WorldId in teleportation target should not have instance part: {}", worldIdPart);
            return null;
        }

        return new TeleportTarget(worldId.getId(), entryPoint);
    }

    /**
     * Build jump URL for teleport.
     */
    private String buildJumpUrl(ActorRoles actor, String worldId, String sessionId, String userId, String characterId) {
        String url = actor == ActorRoles.EDITOR
                ? accessSettings.getJumpUrlEditor()
                : accessSettings.getJumpUrlViewer();

        url = url.replace("{worldId}", worldId);
        url = url.replace("{session}", sessionId);
        url = url.replace("{userId}", userId);
        url = url.replace("{characterId}", characterId);

        return url;
    }

    /**
     * Teleportation target record.
     */
    private record TeleportTarget(String worldId, String entryPoint) {
    }
}
