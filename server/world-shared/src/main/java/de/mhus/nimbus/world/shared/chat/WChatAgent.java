package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface for chat agent implementations.
 * Chat agents can process player messages and generate responses.
 *
 * <h2>Lifecycle</h2>
 * Each chat has an async session (WChatSession) managed by WChatExecutorService.
 * The session runs as a virtual thread with a message queue and has two states:
 *
 * <pre>
 *   INACTIVE                          ACTIVE
 *   (no thread,                       (virtual thread running,
 *    state in MongoDB)                 queue processing messages)
 *
 *       ──── player sends message ────►
 *            onSessionStarted(chat, queue) ← agent restores state, stores queue reference
 *                                          ← chat()/chatWithSession()/chatWithQueue() per message
 *                                          ← onIdle(queue) when queue empty (~10s interval)
 *                                            return BUSY → reset idle timeout
 *                                            return IDLE → normal timeout check
 *                                          ← queue.requestSleep()   → agent ends session
 *                                          ← queue.requestArchive() → agent archives chat + ends
 *       ◄──── idle timeout (1 min) ────
 *            onSessionEnded(chat)          ← agent persists state via chat.setAgentState(...)
 *                                            chat is saved to MongoDB automatically
 * </pre>
 *
 * <h2>Queue-based processing</h2>
 * Agents that need to react to follow-up messages while processing (e.g., generator agents)
 * can override {@link #supportsQueue()} to return true. The session will then call
 * {@link #chatWithQueue} instead of {@link #chatWithSession}, passing a {@link WChatSessionQueue}
 * that allows the agent to pull additional messages from the queue during processing.
 *
 * <h2>Method hierarchy</h2>
 * The session calls the most specific method available:
 * <ol>
 *   <li>{@link #chatWithQueue} — if {@link #supportsQueue()} returns true</li>
 *   <li>{@link #chatWithSession} — if a sessionId is present</li>
 *   <li>{@link #chat} — fallback</li>
 * </ol>
 * Each level delegates to the next by default, so implementing only {@link #chat} is sufficient
 * for simple agents.
 */
public interface WChatAgent {

    /**
     * Get the technical name of the agent.
     * This is used as an identifier in the system.
     *
     * @return Technical name (e.g., "eliza", "gpt-assistant")
     */
    String getName();

    /**
     * Get the display title of the agent.
     * This is shown to users in the UI.
     *
     * @return Display title (e.g., "Eliza Chatbot", "GPT Assistant")
     */
    String getTitle();

    /**
     * Get the scope of this agent.
     * Determines which users can see and interact with this agent.
     *
     * @return the agent scope
     */
    WChatAgentScope getScope();

    /**
     * Process a chat message and generate responses.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the message is sent
     * @param playerId The player ID sending the message
     * @param message The message content
     * @return List of response messages from the agent
     */
    List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message);

    /**
     * Process a chat message with session context and generate responses.
     * Default implementation delegates to chat() without sessionId for backwards compatibility.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the message is sent
     * @param playerId The player ID sending the message
     * @param message The message content
     * @param sessionId The session ID for accessing session-specific context (optional)
     * @return List of response messages from the agent
     */
    default List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId, String message, String sessionId) {
        return chat(worldId, chatId, playerId, message);
    }

    /**
     * Process a chat message with access to the session queue.
     * Agents that support consuming follow-up messages during processing
     * (e.g., generator agents) should override this method.
     * The agent can call queue.poll() to pull additional messages while working.
     *
     * Default implementation delegates to chatWithSession() ignoring the queue.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the message is sent
     * @param playerId The player ID sending the message
     * @param message The message content
     * @param sessionId The session ID for accessing session-specific context (optional)
     * @param queue The session queue to consume further messages from
     * @return List of response messages from the agent
     */
    default List<WChatMessage> chatWithQueue(WorldId worldId, String chatId, String playerId,
                                             String message, String sessionId, WChatSessionQueue queue) {
        return chatWithSession(worldId, chatId, playerId, message, sessionId);
    }

    /**
     * Whether this agent supports consuming messages from the queue during processing.
     * If true, chatWithQueue() will be called instead of chatWithSession().
     *
     * @return true if the agent handles queue-based processing
     */
    default boolean supportsQueue() {
        return false;
    }

    /**
     * Result of an onIdle() call.
     */
    enum IdleResult {
        /** Agent has nothing to do — normal idle timeout applies. */
        IDLE,
        /** Agent is still working — reset idle timeout, keep session alive. */
        BUSY
    }

    /**
     * Called periodically when the message queue is empty (every ~10 seconds).
     * The agent can use this to perform async work (poll external APIs, send progress
     * messages, run background computations). The session queue is provided so the agent
     * can also react to incoming messages during this time.
     *
     * Return {@link IdleResult#BUSY} to prevent the idle timeout from ending the session.
     * Return {@link IdleResult#IDLE} for normal timeout behavior.
     *
     * Default implementation returns IDLE.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID
     * @param queue The session queue — check for new messages via queue.poll()/hasNext()
     * @return BUSY to keep the session alive, IDLE for normal timeout
     */
    default IdleResult onIdle(WorldId worldId, String chatId, WChatSessionQueue queue) {
        return IdleResult.IDLE;
    }

    /**
     * Called when a chat session starts (or resumes after idle).
     * The agent can restore its internal state from chat.getAgentState().
     * The queue provides session control: {@link WChatSessionQueue#requestSleep()} and
     * {@link WChatSessionQueue#requestArchive()} to end or archive the chat from within the agent.
     * Default implementation does nothing.
     *
     * @param chat The chat entity with persisted agentState
     * @param queue The session queue — store this reference for session control and message access
     */
    default void onSessionStarted(WChat chat, WChatSessionQueue queue) {
    }

    /**
     * Called when a chat session ends (idle timeout or shutdown).
     * The agent should persist its state into chat.setAgentState(...).
     * The chat will be saved to MongoDB after this call returns.
     * Default implementation does nothing.
     *
     * @param chat The chat entity — set agentState here for persistence
     */
    default void onSessionEnded(WChat chat) {
    }

    /**
     * Whether this agent runs locally on this pod.
     * Remote agents (proxied from another pod) return false.
     * Used by WChatService to decide whether to create a local session
     * or route the message to the remote pod.
     *
     * @return true for local agents, false for remote agents
     */
    default boolean isLocal() {
        return true;
    }

    /**
     * Route a session message to the remote pod for async processing.
     * Only called for non-local agents (isLocal() == false).
     * The remote pod will create a local session with the real agent.
     *
     * @param msg The session message to route
     */
    default void routeMessage(WChatSessionMessage msg) {
        throw new UnsupportedOperationException("Agent is not remote: " + getName());
    }

    /**
     * Execute a command on the agent and generate responses.
     * This allows structured interaction with the agent beyond simple chat messages.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the command is executed
     * @param playerId The player ID executing the command
     * @param command The command to execute
     * @param params Command parameters
     * @return List of response messages from the agent
     */
    default List<WChatMessage> executeCommand(WorldId worldId, String chatId, String playerId,
                                             String command, Map<String, Object> params) {
        // Default implementation: return error message
        WChatMessage errorMessage = WChatMessage.builder()
                .worldId(worldId.toBaseWorldId().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(getName() + "-agent")
                .message("Command execution not supported by this agent: " + command)
                .type("error")
                .createdAt(Instant.now())
                .build();
        return List.of(errorMessage);
    }
}
