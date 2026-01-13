package de.mhus.nimbus.world.shared.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WChatMessage entities.
 */
@Repository
public interface WChatMessageRepository extends MongoRepository<WChatMessage, String> {

    Optional<WChatMessage> findByWorldIdAndChatIdAndMessageId(String worldId, String chatId, String messageId);

    List<WChatMessage> findByWorldIdAndChatId(String worldId, String chatId);

    List<WChatMessage> findByWorldIdAndChatIdOrderByCreatedAtDesc(String worldId, String chatId, Pageable pageable);

    List<WChatMessage> findByWorldIdAndChatIdOrderByCreatedAtAsc(String worldId, String chatId, Pageable pageable);

    List<WChatMessage> findByWorldIdAndChatIdAndType(String worldId, String chatId, String type);

    List<WChatMessage> findByWorldIdAndChatIdAndCreatedAtAfter(String worldId, String chatId, Instant after);

    List<WChatMessage> findByWorldIdAndChatIdAndCreatedAtBefore(String worldId, String chatId, Instant before);

    List<WChatMessage> findByWorldIdAndSenderId(String worldId, String senderId);

    long countByWorldIdAndChatId(String worldId, String chatId);

    void deleteByWorldIdAndChatId(String worldId, String chatId);

    void deleteByWorldIdAndChatIdAndMessageId(String worldId, String chatId, String messageId);

    List<WChatMessage> findByWorldIdAndChatIdAndCreatedAtAfterOrderByCreatedAtAsc(String id, String chatId, Instant referenceTimestamp, Pageable pageable);
}
