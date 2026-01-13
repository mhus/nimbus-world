package de.mhus.nimbus.world.shared.chat;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WChat entities.
 */
@Repository
public interface WChatRepository extends MongoRepository<WChat, String> {

    Optional<WChat> findByWorldIdAndChatId(String worldId, String chatId);

    List<WChat> findByWorldId(String worldId);

    List<WChat> findByWorldIdAndType(String worldId, String type);

    List<WChat> findByWorldIdAndArchived(String worldId, boolean archived);

    List<WChat> findByWorldIdAndOwnerId(String worldId, String ownerId);

    List<WChat> findByWorldIdAndTypeAndArchived(String worldId, String type, boolean archived);

    List<WChat> findByWorldIdAndOwnerIdAndArchived(String worldId, String ownerId, boolean archived);

    List<WChat> findByWorldIdAndTypeAndOwnerId(String worldId, String type, String ownerId);

    List<WChat> findByWorldIdAndTypeAndOwnerIdAndArchived(String worldId, String type, String ownerId, boolean archived);

    boolean existsByWorldIdAndChatId(String worldId, String chatId);

    void deleteByWorldIdAndChatId(String worldId, String chatId);
}
