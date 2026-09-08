package de.mhus.nimbus.world.shared.world;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB Repository for WTrader entities.
 */
@Repository
public interface WTraderRepository extends MongoRepository<WTrader, String> {

    Optional<WTrader> findByWorldIdAndEntityId(String worldId, String entityId);

    List<WTrader> findByWorldId(String worldId);

    List<WTrader> findByWorldIdAndTraderType(String worldId, TraderType traderType);

    List<WTrader> findByWorldIdAndEnabled(String worldId, boolean enabled);

    void deleteByWorldId(String worldId);
}
