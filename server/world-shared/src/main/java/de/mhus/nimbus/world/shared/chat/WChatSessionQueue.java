package de.mhus.nimbus.world.shared.chat;

import java.util.concurrent.TimeUnit;

/**
 * Queue abstraction for agents that need to consume additional messages
 * during processing. Allows agents to react to follow-up messages
 * while still working on a previous request (e.g., generator agents).
 */
public interface WChatSessionQueue {

    /**
     * Poll for the next message, blocking up to the given timeout.
     *
     * @param timeout maximum time to wait
     * @param unit time unit
     * @return the next message, or null if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    WChatSessionMessage poll(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Poll for the next message without blocking.
     *
     * @return the next message, or null if queue is empty
     */
    WChatSessionMessage poll();

    /**
     * Peek at the next message without removing it.
     *
     * @return the next message, or null if queue is empty
     */
    WChatSessionMessage peek();

    /**
     * Check if there are messages waiting in the queue.
     *
     * @return true if at least one message is available
     */
    boolean hasNext();
}
