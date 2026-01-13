package de.mhus.nimbus.world.player.service;

import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.shared.types.PlayerCharacter;
import de.mhus.nimbus.shared.types.PlayerData;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.session.SessionPingConsumer;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.region.RUserItemsService;
import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.session.WPlayerSession;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerService implements SessionPingConsumer {

    private final RUserService rUserService;
    private final RCharacterService rCharacterService;
    private final RUserItemsService rUserItemsService;
    private final WorldRedisService worldRedisService;
    private final WWorldService worldService;
    private final WPlayerSessionService playerSessionService;
    private final WSessionService sessionService;
    private final AccessSettings accessSettings;
    private final ClientService clientService;

    /**
     * Get player data for a specific region.
     */
    public Optional<PlayerData> getPlayer(PlayerId playerId, ClientType clientType, String regionId) {
        String username = playerId.getUserId();
        String characterId = playerId.getCharacterId();

        // Get user from database
        Optional<RUser> userOpt = rUserService.getByUsername(username);
        if (userOpt.isEmpty()) {
            log.debug("User not found: {}", username);
            return Optional.empty();
        }

        RUser rUser = userOpt.get();

        // Get character from database
        Optional<RCharacter> characterOpt = rCharacterService.getCharacter(username, regionId, characterId);
        if (characterOpt.isEmpty()) {
            log.debug("Character not found: userId={}, regionId={}, characterId={}", username, regionId, characterId);
            return Optional.empty();
        }

        RCharacter rCharacter = characterOpt.get();

        // Get Settings for ClientType
        Settings settings = rUserService.getSettingsForClientType(username, clientType.getTsIndex());
        if (settings == null) {
            settings = new Settings(); // default empty settings
        }

        // Build PlayerCharacter
        PlayerCharacter playerCharacter = new PlayerCharacter(rCharacter.getPublicData(), rCharacter.getBackpack() );

        return Optional.of(new PlayerData(rUser.getPublicData(), playerCharacter, settings));
    }

    @Override
    public ACTION onSessionPing(PlayerSession session) {
        // TODO sync player data if needed, validate only one session etc.
        return ACTION.NONE;
    }

    public Optional<Entity> getPlayerAsEntity(PlayerId playerId, WorldId worldId) {
        // Extract regionId from worldId
        String regionId = worldId.getRegionId();

        var playerX = getPlayer(playerId, ClientType.WEB, regionId);
        if (playerX.isEmpty()) return Optional.empty();
        var player = playerX.get();

        // Check if player has public data
        if (player.character() == null || player.character().getPublicData() == null) {
            log.warn("Player character or publicData is null for playerId: {}", playerId);
            return Optional.empty();
        }

        var result = Entity.builder()
                .id(playerId.getId())
                .name(player.character().getPublicData().getTitle())
                .controlledBy("player")
                .model(player.character().getPublicData().getThirdPersonModelId())
                .clientPhysics(false)
                .modelModifier(
                        player.character().getPublicData().getThirdPersonModelModifiers()
                )
                .interactive(true)
                .movementType("dynamic")
                .physics(false)
                .notifyOnAttentionRange(player.character().getPublicData().getStealthRange())
                .notifyOnCollision(true)
//                .healthMax(500)
//                .health(400)
                .build();
        return Optional.of(result);
    }

    /**
     * Teleport player to target location.
     * Sets teleportation in session for /teleport redirect.
     * <p>
     * Format: &lt;worldId&gt;@&lt;position&gt; or worldId@grid:q,r or worldId or @&lt;position&gt; or @grid:q,r or "return"
     * - worldId can be empty, then only position: @&lt;position&gt; or @grid:q,r
     * - worldId can be a full WorldId (without instance part)
     * - "return" teleports back to previous world using previousWorldId/Position/Rotation
     *
     * @param session PlayerSession
     * @param target  Teleportation target string
     * @return true if teleportation was set, false if validation failed
     */
    public boolean teleportPlayer(PlayerSession session, String target) {
        if (target == null || target.isBlank()) {
            log.warn("Teleportation target is empty");
            clientService.sendSystemNotification(session, "Teleportation Failed", "Invalid teleportation target");
            return false;
        }

        // Handle "return" teleportation
        if ("return".equalsIgnoreCase(target.trim())) {
            return handleReturnTeleportation(session);
        }

        // Parse target string: <worldId>@<position> or worldId or @<position>
        String worldIdPart = null;
        String positionPart = null;

        int hashIndex = target.indexOf('@');
        if (hashIndex >= 0) {
            // Format: worldId@position or @position
            worldIdPart = target.substring(0, hashIndex);
            positionPart = target.substring(hashIndex + 1);

            // Empty worldId part is allowed (e.g., "@grid:1,2")
            if (worldIdPart.isBlank()) {
                worldIdPart = null;
            }
        } else {
            // Format: worldId only
            worldIdPart = target;
        }

        // Validate worldId if present
        if (worldIdPart != null && !worldIdPart.isBlank()) {
            // Parse WorldId (without instance)
            Optional<WorldId> worldIdOpt = WorldId.of(worldIdPart);
            if (worldIdOpt.isEmpty()) {
                log.warn("Invalid worldId format in teleportation target: {}", worldIdPart);
                clientService.sendSystemNotification(session, "Teleportation Failed",
                        "Invalid world ID: " + worldIdPart);
                return false;
            }

            WorldId worldId = worldIdOpt.get();

            // Ensure no instance part
            if (worldId.isInstance()) {
                log.warn("WorldId in teleportation target should not have instance part: {}", worldIdPart);
                clientService.sendSystemNotification(session, "Teleportation Failed",
                        "World ID cannot contain instance part");
                return false;
            }

            // Check if world exists
            if (worldService.getByWorldId(worldId.getId()).isEmpty()) {
                log.warn("World does not exist for teleportation: {}", worldId.getId());
                clientService.sendSystemNotification(session, "Teleportation Failed",
                        "World does not exist: " + worldId.getId());
                return false;
            }
        }

        // Set teleportation in WSession
        sessionService.updateTeleportation(session.getSessionId(), target);
        log.info("Teleportation set in WSession for player {}: sessionId={}, target={}",
                session.getPlayer(), session.getSessionId(), target);

        // Save current session to MongoDB before redirect
        try {
            if (session.getWorldId() != null && session.getPlayer() != null
                    && session.getPlayer().character() != null
                    && session.getPlayer().character().getPublicData() != null) {

                String playerId = session.getPlayer().character().getPublicData().getPlayerId();
                if (playerId != null && !playerId.isBlank()) {
                    // Save position and rotation to MongoDB
                    playerSessionService.updateSession(
                            session.getWorldId().getId(),
                            playerId,
                            session.getLastPosition(),
                            session.getLastRotation()
                    );

                    log.info("Saved player session to MongoDB before teleport: playerId={}, worldId={}",
                            playerId, session.getWorldId().getId());
                } else {
                    log.warn("Cannot save session: playerId is null");
                }
            } else {
                log.warn("Cannot save session: required session data is null");
            }
        } catch (Exception e) {
            log.error("Failed to save player session to MongoDB", e);
            // Continue with teleport even if session save fails
        }

        // Get teleport URL from AccessSettings
        String teleportUrl = accessSettings.getTeleportUrl();
        if (teleportUrl == null || teleportUrl.isBlank()) {
            log.error("TeleportUrl is not configured in AccessSettings");
            clientService.sendSystemNotification(session, "Teleportation Failed",
                    "Teleportation service is not configured");
            return false;
        }

        log.info("Triggering RedirectCommand for player: url={}", teleportUrl);

        // Trigger RedirectCommand via WebSocket
        clientService.sendRedirectCommand(session, teleportUrl);

        return true;
    }

    /**
     * Handle "return" teleportation by loading previous world data from WPlayerSession.
     *
     * @param session PlayerSession
     * @return true if return teleportation was set, false if failed
     */
    private boolean handleReturnTeleportation(PlayerSession session) {
        // Get current worldId and playerId
        if (session.getWorldId() == null) {
            log.warn("Cannot handle return teleportation: worldId is null in session");
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "Session data is incomplete");
            return false;
        }

        if (session.getPlayer() == null || session.getPlayer().character() == null
                || session.getPlayer().character().getPublicData() == null) {
            log.warn("Cannot handle return teleportation: player data is null in session");
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "Player data is incomplete");
            return false;
        }

        String playerId = session.getPlayer().character().getPublicData().getPlayerId();
        if (playerId == null || playerId.isBlank()) {
            log.warn("Cannot handle return teleportation: playerId is null");
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "Player ID is missing");
            return false;
        }

        String currentWorldId = session.getWorldId().getId();

        log.debug("Handling return teleportation: currentWorldId={}, playerId={}",
                currentWorldId, playerId);

        // Load WPlayerSession for current world
        Optional<WPlayerSession> playerSessionOpt = playerSessionService.loadSession(currentWorldId, playerId);
        if (playerSessionOpt.isEmpty()) {
            log.warn("Cannot handle return teleportation: no player session found for worldId={}, playerId={}",
                    currentWorldId, playerId);
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "No previous location found");
            return false;
        }

        WPlayerSession playerSession = playerSessionOpt.get();

        // Check if previous data is available
        if (playerSession.getPreviousWorldId() == null || playerSession.getPreviousWorldId().isBlank()) {
            log.warn("Cannot handle return teleportation: previousWorldId is not set for playerId={}",
                    playerId);
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "No previous world found - you haven't teleported from another world yet");
            return false;
        }

        // Build teleportation target string
        String previousWorldId = playerSession.getPreviousWorldId();
        String teleportTarget;

        if (playerSession.getPreviousPosition() != null) {
            // Return with saved position
            double x = playerSession.getPreviousPosition().getX();
            double y = playerSession.getPreviousPosition().getY();
            double z = playerSession.getPreviousPosition().getZ();

            teleportTarget = previousWorldId + "@position:" + x + "," + y + "," + z;

            log.info("Return teleportation resolved: currentWorldId={}, targetWorldId={}, position=({},{},{}), playerId={}",
                    currentWorldId, previousWorldId, x, y, z, playerId);
        } else {
            // No saved position - return to world entry point
            teleportTarget = previousWorldId;

            log.info("Return teleportation resolved: currentWorldId={}, targetWorldId={} (entry point), playerId={}",
                    currentWorldId, previousWorldId, playerId);
        }

        // Validate target world exists
        Optional<WorldId> targetWorldIdOpt = WorldId.of(previousWorldId);
        if (targetWorldIdOpt.isEmpty()) {
            log.warn("Cannot handle return teleportation: invalid previousWorldId format: {}", previousWorldId);
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "Invalid previous world ID: " + previousWorldId);
            return false;
        }

        WorldId targetWorldId = targetWorldIdOpt.get();
        if (worldService.getByWorldId(targetWorldId.withoutInstance().getId()).isEmpty()) {
            log.warn("Cannot handle return teleportation: target world does not exist: {}", previousWorldId);
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "Previous world no longer exists: " + previousWorldId);
            return false;
        }

        // Set teleportation in WSession
        sessionService.updateTeleportation(session.getSessionId(), teleportTarget);
        log.info("Return teleportation set in WSession for player {}: sessionId={}, target={}",
                session.getPlayer(), session.getSessionId(), teleportTarget);

        // Save current session to MongoDB before redirect
        try {
            if (session.getWorldId() != null && session.getPlayer() != null
                    && session.getPlayer().character() != null
                    && session.getPlayer().character().getPublicData() != null) {

                String playerIdStr = session.getPlayer().character().getPublicData().getPlayerId();
                if (playerIdStr != null && !playerIdStr.isBlank()) {
                    // Save position and rotation to MongoDB
                    playerSessionService.updateSession(
                            session.getWorldId().getId(),
                            playerIdStr,
                            session.getLastPosition(),
                            session.getLastRotation()
                    );

                    log.info("Saved player session to MongoDB before return teleport: playerId={}, worldId={}",
                            playerIdStr, session.getWorldId().getId());
                } else {
                    log.warn("Cannot save session: playerId is null");
                }
            } else {
                log.warn("Cannot save session: required session data is null");
            }
        } catch (Exception e) {
            log.error("Failed to save player session to MongoDB", e);
            // Continue with teleport even if session save fails
        }

        // Get teleport URL from AccessSettings
        String teleportUrl = accessSettings.getTeleportUrl();
        if (teleportUrl == null || teleportUrl.isBlank()) {
            log.error("TeleportUrl is not configured in AccessSettings");
            clientService.sendSystemNotification(session, "Return Teleport Failed",
                    "Teleportation service is not configured");
            return false;
        }

        log.info("Triggering RedirectCommand for return teleport: url={}", teleportUrl);

        // Trigger RedirectCommand via WebSocket
        clientService.sendRedirectCommand(session, teleportUrl);

        return true;
    }
}
