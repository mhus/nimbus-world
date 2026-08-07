package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AccessLevel;
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
import java.util.Map;

/**
 * MongoDB Entity for chat instances in the world.
 * Supports different chat types: builder, global, team, private.
 */
@Document(collection = "w_chats")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_chatId_idx", def = "{ 'worldId': 1, 'chatId': 1 }", unique = true),
        @CompoundIndex(name = "worldId_ownerId_archived_idx", def = "{ 'worldId': 1, 'ownerId': 1, 'archived': 1 }"),
        @CompoundIndex(name = "worldId_parentChatId_idx", def = "{ 'worldId': 1, 'parentChatId': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WChat implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier where this chat exists.
     * Required for all chats.
     */
    @Indexed
    private String worldId;

    /**
     * Unique chat identifier (UUID format).
     * Combined with worldId forms a unique constraint.
     */
    private String chatId;

    /**
     * Technical name of the chat.
     * Unique identifier within the world context.
     */
    private String name;

    /**
     * Chat type: 'builder', 'global', 'team', 'private'.
     * Indexed for filtering by type.
     */
    @Indexed
    private String type;

    /**
     * Timestamp when the chat was created.
     */
    private Instant createdAt;

    /**
     * Timestamp when the chat was last modified.
     */
    private Instant modifiedAt;

    /**
     * Flag indicating if the chat is archived.
     * Archived chats are not shown in active lists.
     */
    @Indexed
    @Builder.Default
    private boolean archived = false;

    /**
     * Optional owner player ID.
     * Used for agent chats where a specific player owns the chat.
     */
    @Indexed
    private String ownerId;

    /**
     * Optional hint from the player when creating the chat.
     * Additional information or instructions for the agent (e.g., preferred model, context).
     */
    private String hint;

    /**
     * Optional parent chat ID for internal sub-chats.
     * When an agent creates sub-chats to delegate work to other agents,
     * this links the sub-chat to its parent. Used for cascading archive/delete.
     */
    private String parentChatId;

    /**
     * Flag indicating if this is an internal chat.
     * Internal chats are created by agents for delegation and are not visible in the UI.
     */
    @Builder.Default
    private boolean internal = false;

    /**
     * Agent state persisted across session sleep/wake cycles.
     * When a WChatSession ends (idle timeout), the agent can store its state here.
     * When a new session starts, the agent receives this state to restore itself.
     * Stored as a flexible map to allow each agent to define its own structure.
     */
    private Map<String, Object> agentState;

    /**
     * Initialize timestamps for new chat.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        modifiedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        modifiedAt = Instant.now();
    }
}
