package de.mhus.nimbus.world.shared.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class WTeamService {

    private static final String REDIS_CHANNEL_TEAM_MEMBERSHIP = "t.m";

    private final WTeamRepository teamRepository;
    private final MongoTemplate mongoTemplate;
    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    public WTeamService(WTeamRepository teamRepository, MongoTemplate mongoTemplate,
                        WorldRedisMessagingService redisMessaging, ObjectMapper objectMapper) {
        this.teamRepository = teamRepository;
        this.mongoTemplate = mongoTemplate;
        this.redisMessaging = redisMessaging;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a team membership change event via Redis global channel.
     * Status: JOINED, LEFT, OFFLINE, ONLINE, DEAD
     */
    public void publishTeamMembershipEvent(String teamId, String playerName, String status) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("teamId", teamId);
            node.put("playerName", playerName);
            node.put("status", status);
            redisMessaging.publishGlobal(REDIS_CHANNEL_TEAM_MEMBERSHIP, objectMapper.writeValueAsString(node));
            log.debug("Published team membership event: teamId={}, player={}, status={}", teamId, playerName, status);
        } catch (Exception e) {
            log.error("Failed to publish team membership event: {}", e.getMessage(), e);
        }
    }

    private Query queryByTeamId(String teamId) {
        return new Query(Criteria.where("teamId").is(teamId));
    }

    @Transactional
    public WTeam createTeam(String worldId, String title, String creatorPlayerName) {
        WTeam team = WTeam.builder()
                .worldId(worldId)
                .teamId(UUID.randomUUID().toString())
                .title(title)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .status(WTeamStatus.LOBBY)
                .build();
        team.getMembers().add(creatorPlayerName);
        teamRepository.save(team);
        log.info("Created team: worldId={} teamId={} title={} creator={}",
                worldId, team.getTeamId(), title, creatorPlayerName);
        publishTeamMembershipEvent(team.getTeamId(), creatorPlayerName, "JOINED");
        return team;
    }

    @Transactional
    public WTeam save(WTeam team) {
        return teamRepository.save(team);
    }

    @Transactional(readOnly = true)
    public Optional<WTeam> findByTeamId(String teamId) {
        return teamRepository.findByTeamId(teamId);
    }

    @Transactional(readOnly = true)
    public List<WTeam> findByWorldId(String worldId) {
        return teamRepository.findByWorldId(worldId);
    }

    @Transactional(readOnly = true)
    public List<WTeam> findByWorldIdAndStatus(String worldId, WTeamStatus status) {
        return teamRepository.findByWorldIdAndStatus(worldId, status);
    }

    @Transactional(readOnly = true)
    public Optional<WTeam> findPlayerTeam(String worldId, String playerName) {
        return teamRepository.findByWorldIdAndMembersContaining(worldId, playerName);
    }

    @Transactional(readOnly = true)
    public List<WTeam> findAllTeamsOfPlayer(String playerName) {
        return teamRepository.findByMembersContaining(playerName);
    }

    /**
     * Find the active team for a player in a given world or its main instance.
     * Searches: exact worldId first, then main instance worldId
     * (e.g. earth616:westview::instanceId for zone instances).
     */
    @Transactional(readOnly = true)
    public Optional<WTeam> findActiveTeamForPlayer(String worldId, String mainInstanceId, String playerName) {
        // First try exact worldId
        var team = teamRepository.findByWorldIdAndMembersContaining(worldId, playerName);
        if (team.isPresent()) return team;
        // Then try main instance (strips zone, keeps instance)
        if (mainInstanceId != null && !mainInstanceId.equals(worldId)) {
            return teamRepository.findByWorldIdAndMembersContaining(mainInstanceId, playerName);
        }
        return Optional.empty();
    }

    // --- Atomic MongoDB Operations ---

    public boolean addMemberAtomic(String teamId, String playerName) {
        Update update = new Update()
                .addToSet("members", playerName)
                .pull("invitation", playerName)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.info("Added member {} to team {} (atomic)", playerName, teamId);
            publishTeamMembershipEvent(teamId, playerName, "JOINED");
            return true;
        }
        return false;
    }

    public boolean removeMemberAtomic(String teamId, String playerName) {
        Update update = new Update()
                .pull("members", playerName)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.info("Removed member {} from team {} (atomic)", playerName, teamId);
            publishTeamMembershipEvent(teamId, playerName, "LEFT");
            deleteIfEmpty(teamId);
            return true;
        }
        return false;
    }

    public boolean addInvitationAtomic(String teamId, String playerName) {
        Update update = new Update()
                .addToSet("invitation", playerName)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.info("Invited {} to team {} (atomic)", playerName, teamId);
            return true;
        }
        return false;
    }

    public boolean removeInvitationAtomic(String teamId, String playerName) {
        Update update = new Update()
                .pull("invitation", playerName)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.info("Removed invitation for {} from team {} (atomic)", playerName, teamId);
            deleteIfEmpty(teamId);
            return true;
        }
        return false;
    }

    @Transactional
    public WTeam updateStatus(String teamId, WTeamStatus status) {
        WTeam team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        team.setStatus(status);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);
        log.info("Updated team {} status to {}", teamId, status);
        return team;
    }

    private void deleteIfEmpty(String teamId) {
        teamRepository.findByTeamId(teamId).ifPresent(team -> {
            if ((team.getMembers() == null || team.getMembers().isEmpty())
                    && (team.getInvitation() == null || team.getInvitation().isEmpty())) {
                teamRepository.deleteByTeamId(teamId);
                log.info("Auto-deleted empty team {}", teamId);
            }
        });
    }

    @Transactional
    public void deleteTeam(String teamId) {
        teamRepository.deleteByTeamId(teamId);
        log.info("Deleted team {}", teamId);
    }

    /**
     * Set a single team parameter atomically.
     */
    public boolean setParameterAtomic(String teamId, String key, String value) {
        Update update = new Update()
                .set("parameters." + key, value)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.debug("Set parameter {}={} on team {}", key, value, teamId);
            return true;
        }
        return false;
    }

    /**
     * Remove a single team parameter atomically.
     */
    public boolean removeParameterAtomic(String teamId, String key) {
        Update update = new Update()
                .unset("parameters." + key)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.debug("Removed parameter {} from team {}", key, teamId);
            return true;
        }
        return false;
    }

    /**
     * Increment a numeric team parameter atomically.
     * If the parameter does not exist yet, it is initialized to the given delta.
     */
    public boolean incrementParameterAtomic(String teamId, String key, long delta) {
        Update update = new Update()
                .inc("parameters." + key, delta)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.debug("Incremented parameter {} by {} on team {}", key, delta, teamId);
            return true;
        }
        return false;
    }

    @Transactional
    public void deleteByWorldId(String worldId) {
        teamRepository.deleteByWorldId(worldId);
        log.info("Deleted all teams for worldId {}", worldId);
    }

    @Transactional
    public WTeam emigrateToInstance(String teamId, String instanceWorldId) {
        WTeam team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        if (team.getStatus() != WTeamStatus.LOBBY) {
            throw new IllegalStateException("Only LOBBY teams can be emigrated, current status: " + team.getStatus());
        }
        String oldWorldId = team.getWorldId();
        team.setWorldId(instanceWorldId);
        team.setStatus(WTeamStatus.ACTIVE);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);
        log.info("Emigrated team {} from world {} to instance {}, status set to ACTIVE", teamId, oldWorldId, instanceWorldId);
        return team;
    }
}
