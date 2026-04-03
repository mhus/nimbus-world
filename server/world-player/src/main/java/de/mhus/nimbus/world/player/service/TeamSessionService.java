package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.network.MessageType;
import de.mhus.nimbus.generated.types.TeamData;
import de.mhus.nimbus.generated.types.TeamMember;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionAuthenticatedConsumer;
import de.mhus.nimbus.world.player.session.SessionClosedConsumer;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.team.WTeam;
import de.mhus.nimbus.world.shared.team.WTeamService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Manages team state on PlayerSessions.
 * - On authenticate: looks up team, caches on session, sends TeamData to client
 * - On Redis team membership event: updates affected sessions
 * - Provides team status data for vital sign broadcasts
 *
 * Redis channel: world:global:t.m (team membership changes)
 * Message format: { "teamId": "...", "playerName": "...", "status": "JOINED|LEFT|OFFLINE|ONLINE|DEAD" }
 */
@Service
@Slf4j
public class TeamSessionService implements SessionAuthenticatedConsumer, SessionClosedConsumer {

    private static final String REDIS_CHANNEL_TEAM_MEMBERSHIP = "t.m";

    private final WTeamService teamService;
    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final EngineMapper engineMapper;
    private final ObjectMapper objectMapper;

    public TeamSessionService(
            WTeamService teamService,
            WorldRedisMessagingService redisMessaging,
            @Lazy SessionManager sessionManager,
            EngineMapper engineMapper,
            ObjectMapper objectMapper) {
        this.teamService = teamService;
        this.redisMessaging = redisMessaging;
        this.sessionManager = sessionManager;
        this.engineMapper = engineMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribeToTeamMembership() {
        redisMessaging.subscribeGlobal(REDIS_CHANNEL_TEAM_MEMBERSHIP, this::handleTeamMembershipEvent);
        log.info("Subscribed to global team membership events (channel: world:global:{})", REDIS_CHANNEL_TEAM_MEMBERSHIP);
    }

    // --- SessionAuthenticatedConsumer ---

    @Override
    public void onSessionAuthenticated(PlayerSession session) {
        updateTeam(session);
    }

    // --- SessionClosedConsumer ---

    @Override
    public void onSessionClosed(PlayerSession session) {
        if (session.getCachedTeamId() != null) {
            String playerName = session.getEntityId();
            if (playerName != null) {
                teamService.publishTeamMembershipEvent(session.getCachedTeamId(), playerName, "OFFLINE");
            }
            session.setCachedTeamId(null);
            session.setCachedTeamMembers(new HashSet<>());
        }
    }

    // --- Core team update logic ---

    /**
     * Look up the team for this session's player and cache it.
     * Sends TeamData message to the client.
     */
    public void updateTeam(PlayerSession session) {
        String playerName = session.getEntityId();
        if (playerName == null || session.getWorldId() == null) return;

        String worldId = session.getWorldId().getId();
        String mainInstanceId = session.getWorldId().getFullMainInstance().getId();

        try {
            Optional<WTeam> teamOpt = teamService.findActiveTeamForPlayer(worldId, mainInstanceId, playerName);
            if (teamOpt.isPresent()) {
                WTeam team = teamOpt.get();
                session.setCachedTeamId(team.getTeamId());
                session.setCachedTeamMembers(new HashSet<>(team.getMembers()));
                sendTeamDataToClient(session, team);
                log.debug("Team loaded for session {}: teamId={}, members={}",
                        session.getSessionId(), team.getTeamId(), team.getMembers().size());
            } else {
                // No team - clear cache and send empty team
                session.setCachedTeamId(null);
                session.setCachedTeamMembers(new HashSet<>());
                sendEmptyTeamToClient(session);
                log.debug("No team found for session {}", session.getSessionId());
            }
        } catch (Exception e) {
            log.error("Failed to update team for session {}: {}", session.getSessionId(), e.getMessage(), e);
        }
    }

    // --- Redis event handling ---

    /**
     * Handle team membership change events from Redis.
     * Format: { "teamId": "...", "playerName": "...", "status": "JOINED|LEFT|OFFLINE|ONLINE|DEAD" }
     */
    private void handleTeamMembershipEvent(String topic, String message) {
        try {
            var node = objectMapper.readTree(message);
            String teamId = node.has("teamId") ? node.get("teamId").asText() : null;
            String playerName = node.has("playerName") ? node.get("playerName").asText() : null;
            String status = node.has("status") ? node.get("status").asText() : null;

            if (teamId == null || playerName == null || status == null) {
                log.warn("Invalid team membership event: {}", message);
                return;
            }

            log.debug("Team membership event: teamId={}, player={}, status={}", teamId, playerName, status);

            // Find all sessions that are affected
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (!session.isAuthenticated()) continue;

                String sessionPlayer = session.getEntityId();
                if (sessionPlayer == null) continue;

                boolean isAffectedPlayer = sessionPlayer.equals(playerName);
                boolean isInSameTeam = teamId.equals(session.getCachedTeamId());

                if (isAffectedPlayer || isInSameTeam) {
                    // Re-fetch team from DB and update session cache
                    updateTeam(session);
                }
            }

        } catch (Exception e) {
            log.error("Failed to handle team membership event: {}", e.getMessage(), e);
        }
    }

    // --- Client messaging ---

    private void sendTeamDataToClient(PlayerSession session, WTeam team) {
        try {
            String sessionPlayer = session.getEntityId();

            // Build TeamData - exclude the current player from members list
            List<TeamMember> members = new ArrayList<>();
            for (String memberName : team.getMembers()) {
                if (memberName.equals(sessionPlayer)) continue;

                // Try to get display title from session (if on same pod), otherwise extract char name
                String displayName = memberName;
                PlayerSession memberSession = sessionManager.findByEntityId(memberName);
                if (memberSession != null && memberSession.getTitle() != null) {
                    displayName = memberSession.getTitle();
                } else {
                    int colonIdx = memberName.indexOf(':');
                    if (colonIdx >= 0) displayName = memberName.substring(colonIdx + 1);
                }

                TeamMember member = TeamMember.builder()
                        .playerId(memberName)
                        .name(displayName)
                        .status(1) // alive by default
                        .health(100)
                        .build();
                members.add(member);
            }

            TeamData teamData = TeamData.builder()
                    .id(team.getTeamId())
                    .name(team.getTitle())
                    .members(members)
                    .build();

            sendMessage(session, MessageType.TEAM_DATA.tsString(), engineMapper.valueToTree(teamData));
        } catch (Exception e) {
            log.error("Failed to send team data to session {}: {}", session.getSessionId(), e.getMessage(), e);
        }
    }

    private void sendEmptyTeamToClient(PlayerSession session) {
        try {
            TeamData teamData = TeamData.builder()
                    .id("")
                    .name("")
                    .members(List.of())
                    .build();
            sendMessage(session, MessageType.TEAM_DATA.tsString(), engineMapper.valueToTree(teamData));
        } catch (Exception e) {
            log.error("Failed to send empty team to session {}: {}", session.getSessionId(), e.getMessage(), e);
        }
    }

    private void sendMessage(PlayerSession session, String messageType, Object data) {
        try {
            NetworkMessage networkMessage = NetworkMessage.builder()
                    .t(messageType)
                    .d(data instanceof com.fasterxml.jackson.databind.JsonNode ?
                            (com.fasterxml.jackson.databind.JsonNode) data :
                            objectMapper.valueToTree(data))
                    .build();
            String json = objectMapper.writeValueAsString(networkMessage);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send {} to session {}", messageType, session.getSessionId(), e);
        }
    }

    // --- Team status for vital sign updates ---

    /**
     * Get cached team members for a session (for including in vital broadcasts).
     * Returns empty set if session has no team.
     */
    public Set<String> getTeamMembers(PlayerSession session) {
        return session.getCachedTeamMembers() != null ? session.getCachedTeamMembers() : Set.of();
    }

    /**
     * Check if a session is in a team.
     */
    public boolean hasTeam(PlayerSession session) {
        return session.getCachedTeamId() != null;
    }
}
