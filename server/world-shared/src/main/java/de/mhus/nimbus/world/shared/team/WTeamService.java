package de.mhus.nimbus.world.shared.team;

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

    private final WTeamRepository teamRepository;
    private final MongoTemplate mongoTemplate;

    public WTeamService(WTeamRepository teamRepository, MongoTemplate mongoTemplate) {
        this.teamRepository = teamRepository;
        this.mongoTemplate = mongoTemplate;
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

    // --- Atomic MongoDB Operations ---

    public boolean addMemberAtomic(String teamId, String playerName) {
        Update update = new Update()
                .addToSet("members", playerName)
                .pull("invitation", playerName)
                .set("updatedAt", Instant.now());
        var result = mongoTemplate.updateFirst(queryByTeamId(teamId), update, WTeam.class);
        if (result.getModifiedCount() > 0) {
            log.info("Added member {} to team {} (atomic)", playerName, teamId);
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

    @Transactional
    public void deleteTeam(String teamId) {
        teamRepository.deleteByTeamId(teamId);
        log.info("Deleted team {}", teamId);
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
