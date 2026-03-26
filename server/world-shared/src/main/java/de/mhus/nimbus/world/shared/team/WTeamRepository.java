package de.mhus.nimbus.world.shared.team;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WTeamRepository extends MongoRepository<WTeam, String> {

    Optional<WTeam> findByTeamId(String teamId);

    List<WTeam> findByWorldId(String worldId);

    List<WTeam> findByWorldIdAndStatus(String worldId, WTeamStatus status);

    List<WTeam> findByMembersContaining(String playerName);

    Optional<WTeam> findByWorldIdAndMembersContaining(String worldId, String playerName);

    List<WTeam> findByWorldIdAndInvitationContaining(String worldId, String playerName);

    void deleteByTeamId(String teamId);

    void deleteByWorldId(String worldId);

    boolean existsByTeamId(String teamId);
}
