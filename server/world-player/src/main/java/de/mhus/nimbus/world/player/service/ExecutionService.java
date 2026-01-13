package de.mhus.nimbus.world.player.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Execution service using Java 21 Virtual Threads for async operations.
 * Used for non-blocking WebSocket message processing (chunk loading, entity updates, etc).
 */
@Service
@Slf4j
public class ExecutionService {

    private final ExecutorService executor;

    public ExecutionService() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        log.info("ExecutionService initialized with virtual threads");
    }

    /**
     * Execute action asynchronously using virtual thread.
     * @param action Runnable to execute
     */
    public void execute(Runnable action) {
        executor.execute(() -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Error executing async action", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ExecutionService");
        executor.shutdown();
    }
}
