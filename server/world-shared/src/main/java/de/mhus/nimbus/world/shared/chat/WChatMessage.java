package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Entity for chat messages.
 * Each message is stored as a separate entity.
 * Supports different message types: text, command, system, defaults.
 */
@Document(collection = "w_chat_messages")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_chatId_messageId_idx", def = "{ 'worldId': 1, 'chatId': 1, 'messageId': 1 }", unique = true),
        @CompoundIndex(name = "worldId_chatId_createdAt_idx", def = "{ 'worldId': 1, 'chatId': 1, 'createdAt': -1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WChatMessage implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier where this message exists.
     * Required for all messages.
     */
    @Indexed
    private String worldId;

    /**
     * Chat identifier (UUID format) this message belongs to.
     * References WChat.chatId.
     */
    @Indexed
    private String chatId;

    /**
     * Unique message identifier (UUID format).
     * Combined with worldId and chatId forms a unique constraint.
     */
    private String messageId;

    /**
     * Sender player ID.
     * References the player who sent this message.
     */
    @Indexed
    private String senderId;

    /**
     * Message content.
     */
    private String message;

    /**
     * Message type: 'text', 'command', 'system', 'defaults'.
     * Indexed for filtering by type.
     */
    @Indexed
    private String type;

    /**
     * Indicates if this message is a command execution result.
     * Used to distinguish command responses from regular chat messages.
     */
    @Builder.Default
    private boolean command = false;

    /**
     * Timestamp when the message was created.
     * Indexed for chronological ordering.
     */
    @Indexed
    private Instant createdAt;

    /**
     * Initialize timestamp for new message.
     */
    public void touchCreate() {
        createdAt = Instant.now();
    }
}
