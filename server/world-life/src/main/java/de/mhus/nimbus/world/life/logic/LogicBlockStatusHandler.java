package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.redis.BlockStatusPublisher;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Effect handler that changes block status and publishes it via Redis.
 *
 * Required parameters:
 *   - chunkKey: chunk coordinate key, e.g. "1:2"
 *   - blockKey: block position key, e.g. "5,3,8"
 *   - status:   new block status value, e.g. "open", "closed"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogicBlockStatusHandler implements LogicEffectHandler {

    public static final String TYPE = "LogicBlockStatus";

    private final WProgressService progressService;
    private final BlockStatusPublisher blockStatusPublisher;

    @Override
    public Set<String> execute(Map<String, Object> parameters, LogicContext context) {
        String chunkKey = (String) parameters.get("chunkKey");
        String blockKey = (String) parameters.get("blockKey");
        String status = (String) parameters.get("status");

        if (chunkKey == null || blockKey == null || status == null) {
            log.error("LogicBlockStatus: missing required parameters (chunkKey, blockKey, status) in {}", parameters);
            return Set.of();
        }

        // Persist block status in WProgress
        progressService.setBlockStatus(context.getWorldId(), chunkKey, blockKey, status);

        // Publish change to world-player pods via Redis
        blockStatusPublisher.publishStatusChange(context.getWorldId(), chunkKey, blockKey, status);

        log.debug("LogicBlockStatus: worldId={}, chunk={}, block={}, status={}",
                context.getWorldId(), chunkKey, blockKey, status);

        // Block status changes don't affect logic flags
        return Set.of();
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
