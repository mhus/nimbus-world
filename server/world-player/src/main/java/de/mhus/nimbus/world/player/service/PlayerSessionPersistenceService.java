package de.mhus.nimbus.world.player.service;

import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionAuthenticatedConsumer;
import de.mhus.nimbus.world.player.session.SessionClosedConsumer;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for persisting player session state (position, rotation) to MongoDB.
 * Implements SessionAuthenticatedConsumer to start tick thread on authentication.
 * Implements SessionClosedConsumer to save on logout and stop tick thread.
 * Uses thread-based internal tick mechanism for periodic saves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerSessionPersistenceService implements SessionAuthenticatedConsumer, SessionClosedConsumer {

    private final WPlayerSessionService sessionService;

    // Tick threads per session (sessionId -> Thread)
    private final Map<String, Thread> tickThreads = new ConcurrentHashMap<>();

    // Tick counters per session (sessionId -> counter)
    private final Map<String, AtomicInteger> tickCounters = new ConcurrentHashMap<>();

    @Value("${world.player.session-save-interval-ticks:60}")
    private int saveIntervalTicks;  // Default: 60 ticks = 60s

    @Value("${world.player.session-save-enabled:true}")
    private boolean saveEnabled;

    /**
     * Called when session is authenticated.
     * Starts the tick thread for periodic saves.
     *
     * @param session The player session
     */
    @Override
    public void onSessionAuthenticated(PlayerSession session) {
        startTickThread(session);
    }

    /**
     * Start tick thread for a session.
     * Called when session is authenticated.
     *
     * @param session The player session
     */
    public void startTickThread(PlayerSession session) {
        if (!saveEnabled || session == null || !session.isAuthenticated()) {
            log.debug("Not starting tick thread: saveEnabled={}, session={}, authenticated={}",
                    saveEnabled, session != null, session != null && session.isAuthenticated());
            return;
        }

        String sessionId = session.getSessionId();
        if (tickThreads.containsKey(sessionId)) {
            log.debug("Tick thread already running for session: {}", sessionId);
            return;
        }

        AtomicInteger counter = new AtomicInteger(0);
        tickCounters.put(sessionId, counter);

        Thread tickThread = new Thread(() -> {
            log.debug("Tick thread started for session: {}", sessionId);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);  // Tick every ~1 second

                    // Check if session still valid
                    if (!session.isAuthenticated() || session.getLastPosition() == null) {
                        continue;
                    }

                    // Increment counter
                    int count = counter.incrementAndGet();

                    // Save every N ticks
                    if (count >= saveIntervalTicks) {
                        saveSessionSafely(session, "periodic");
                        counter.set(0);  // Reset counter
                    }
                }
            } catch (InterruptedException e) {
                log.debug("Tick thread interrupted for session: {}", sessionId);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in tick thread for session {}: {}", sessionId, e.getMessage(), e);
            } finally {
                log.debug("Tick thread stopped for session: {}", sessionId);
            }
        }, "session-tick-" + sessionId);

        tickThread.setDaemon(true);
        tickThread.start();
        tickThreads.put(sessionId, tickThread);

        log.info("Started tick thread for session: {}", sessionId);
    }

    /**
     * Stop tick thread for a session.
     * Called when session is closed.
     *
     * @param sessionId The session ID
     */
    public void stopTickThread(String sessionId) {
        Thread tickThread = tickThreads.remove(sessionId);
        if (tickThread != null) {
            tickThread.interrupt();
            log.info("Stopped tick thread for session: {}", sessionId);
        }
        tickCounters.remove(sessionId);
    }

    /**
     * Called when session is closed (logout, disconnect).
     * Saves final session state and stops tick thread.
     *
     * @param session The player session
     */
    @Override
    public void onSessionClosed(PlayerSession session) {
        if (!saveEnabled) {
            return;
        }

        // Save final state
        if (session.isAuthenticated() && session.getLastPosition() != null) {
            saveSessionSafely(session, "close");
        }

        // Stop and cleanup tick thread
        if (session.getSessionId() != null) {
            stopTickThread(session.getSessionId());
        }
    }

    /**
     * Save session with error handling (fire-and-forget).
     * Logs errors but doesn't throw exceptions to avoid crashing the session.
     *
     * @param session The player session
     * @param trigger The trigger type ("close" or "periodic")
     */
    private void saveSessionSafely(PlayerSession session, String trigger) {
        try {
            String worldId = session.getWorldId().getId();
            String playerId = session.getEntityId();
            Vector3 position = session.getLastPosition();
            Rotation rotation = session.getLastRotation();

            sessionService.updateSession(worldId, playerId, position, rotation);
            log.info("Saved player session ({}): worldId={}, playerId={}",
                    trigger, worldId, playerId);
        } catch (Exception e) {
            log.error("Failed to save player session ({}): {}", trigger, e.getMessage(), e);
        }
    }
}
