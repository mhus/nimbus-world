package de.mhus.nimbus.world.player.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.mhus.nimbus.generated.network.messages.ChunkDataTransferObject;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.service.ExecutionService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.layer.WEditCache;
import de.mhus.nimbus.world.shared.layer.WEditCacheService;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Central service for sending chunks to clients.
 * Handles chunk loading, overlay application, and network transmission.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkSenderService {

    private final WChunkService chunkService;
    private final WEditCacheService editCacheService;
    private final WProgressService progressService;
    private final ExecutionService executionService;
    private final ObjectMapper objectMapper;

    /**
     * Send chunks to a client session asynchronously.
     *
     * @param session Player session
     * @param chunks  List of chunk coordinates
     * @return CompletableFuture that completes when chunks are sent
     */
    public CompletableFuture<Void> sendChunksAsync(PlayerSession session, List<ChunkCoord> chunks) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        executionService.execute(() -> {
            try {
                sendChunks(session, chunks);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Send chunks to a client session synchronously.
     *
     * @param session Player session
     * @param chunks  List of chunk coordinates
     */
    public void sendChunks(PlayerSession session, List<ChunkCoord> chunks) {
        try {
            ArrayNode responseChunks = objectMapper.createArrayNode();

            // Batch-load block status for all requested chunks
            List<String> chunkKeys = chunks.stream()
                    .map(c -> BlockUtil.toChunkKey(c.cx(), c.cz()))
                    .toList();
            Map<String, Map<String, Object>> blockStatusMap =
                    progressService.findBlockStatusForChunks(session.getWorldId().getId(), chunkKeys);

            for (ChunkCoord coord : chunks) {
                String chunkKey = BlockUtil.toChunkKey(coord.cx(), coord.cz());

                // First find WChunk entity
                var chunkOpt = chunkService.find(session.getWorldId(), chunkKey);
                if (chunkOpt.isEmpty()) {
                    // Generate default chunk if not found (but don't save it)
                    var chunkDataOpt = chunkService.loadChunkData(session.getWorldId(), chunkKey, true);
                    if (chunkDataOpt.isEmpty()) {
                        log.debug("Chunk not found and could not generate: cx={}, cz={}", coord.cx(), coord.cz());
                        continue;
                    }

                    // Convert generated ChunkData directly to transfer object (without saving)
                    ChunkDataTransferObject dto = chunkService.chunkDataToTransferObject(session.getWorldId(), chunkDataOpt.get());
                    if (dto == null) {
                        log.warn("Failed to convert generated chunk to transfer object: chunkKey={}", chunkKey);
                        continue;
                    }

                    // Apply block status and send the generated chunk directly (uncompressed, not saved to DB)
                    applyBlockStatus(dto, blockStatusMap, chunkKey);
                    responseChunks.add(objectMapper.valueToTree(dto));
                    log.debug("Sent generated chunk (not saved): cx={}, cz={}, blocks={}",
                            coord.cx(), coord.cz(), dto.getB() != null ? dto.getB().size() : 0);
                    continue;
                }

                var chunk = chunkOpt.get();

                // Convert to transfer object (uses compressed storage if available)
                ChunkDataTransferObject dto = chunkService.toTransferObject(session.getWorldId(), chunk);
                if (dto == null) {
                    log.warn("Failed to convert chunk to transfer object: chunkKey={}", chunkKey);
                    continue;
                }

                // Apply block status overrides
                applyBlockStatus(dto, blockStatusMap, chunkKey);

                // Handle EDITOR overlays from WEditCache (requires loading ChunkData)
                if (session.isEditActor() && hasOverlayData(session.getWorldId(), chunkKey)) {
                    var chunkDataOpt = chunkService.loadChunkData(session.getWorldId(), chunkKey, false); // laod 2 times ... hmm
                    if (chunkDataOpt.isPresent()) {
                        var chunkData = chunkDataOpt.get();
                        // Apply WEditCache overlays (decompresses, merges, sets c=null)
                        applyWEditCacheOverlays(session.getWorldId().getId(), chunkData);
                        // send as JSON (uncompressed)
                        dto.setBackdrop(chunkService.convertBackdrop(chunkData.getBackdrop()));
                        dto.setB(chunkData.getBlocks());
                        dto.setH(chunkData.getHeightData());
                        dto.setDeny(chunkData.getDeny());
                        dto.setC(null);
                        responseChunks.add(objectMapper.valueToTree(dto));
                        continue;
                    }
                }

                // Send as binary frame if compressed, otherwise add to JSON array
                if (dto.getC() != null && dto.getC().length > 0) {
                    try {
                        sendCompressedChunkBinary(session, dto);
                        log.trace("Sent binary compressed chunk: cx={}, cz={}, compressed={} bytes",
                                coord.cx(), coord.cz(), dto.getC().length);
                    } catch (Exception e) {
                        log.error("Failed to send binary chunk, falling back to text: cx={}, cz={}",
                                coord.cx(), coord.cz(), e);
                        // Decompress server-side for JSON fallback (base64-encoded c field is not valid gzip for client)
                        var fallbackData = chunkService.loadChunkData(session.getWorldId(), chunkKey, false);
                        if (fallbackData.isPresent()) {
                            var cd = fallbackData.get();
                            dto.setB(cd.getBlocks());
                            dto.setH(cd.getHeightData());
                            dto.setDeny(cd.getDeny());
                            dto.setBackdrop(chunkService.convertBackdrop(cd.getBackdrop()));
                            dto.setA(cd.getA());
                            dto.setC(null);
                        }
                        responseChunks.add(objectMapper.valueToTree(dto));
                    }
                } else {
                    responseChunks.add(objectMapper.valueToTree(dto));
                    log.trace("Sent uncompressed chunk: cx={}, cz={}, blocks={}",
                            coord.cx(), coord.cz(), dto.getB() != null ? dto.getB().size() : 0);
                }
            }

            // Send chunk update if any chunks loaded
            if (responseChunks.size() > 0) {
                NetworkMessage response = NetworkMessage.builder()
                        .t("c.u")
                        .d(responseChunks)
                        .build();

                String json = objectMapper.writeValueAsString(response);
                session.sendMessage(new TextMessage(json));

                log.debug("Sent {} chunks to session={}", responseChunks.size(),
                        session.getWebSocketSession().getId());
            }
        } catch (Exception e) {
            log.error("Error sending chunks to session={}", session.getWebSocketSession().getId(), e);
            throw new RuntimeException("Failed to send chunks", e);
        }
    }

    private boolean hasOverlayData(WorldId worldId, String chunkKey) {
        return editCacheService.existsByWorldIdAndChunk(worldId.getId(), chunkKey);
    }

    /**
     * Send compressed chunk as binary WebSocket frame.
     * Format: [4 bytes header length][header JSON][GZIP compressed data]
     */
    private void sendCompressedChunkBinary(PlayerSession session, ChunkDataTransferObject dto) throws Exception {
        // 1. Build header with metadata (small data, stays JSON)
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("cx", dto.getCx());
        header.put("cz", dto.getCz());
        if (dto.getI() != null && !dto.getI().isEmpty()) {
            header.put("i", dto.getI());
        }
        if (dto.getS() != null && !dto.getS().isEmpty()) {
            header.put("s", dto.getS());
        }

        String headerJson = objectMapper.writeValueAsString(header);
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);

        // 2. Build binary frame: [4 bytes length][header][compressed data]
        ByteBuffer buffer = ByteBuffer.allocate(4 + headerBytes.length + dto.getC().length);
        buffer.putInt(headerBytes.length);  // Header length as int32 (big-endian)
        buffer.put(headerBytes);             // Header JSON
        buffer.put(dto.getC());              // GZIP compressed data

        // 3. Send as binary WebSocket frame
        session.sendMessage(new BinaryMessage(buffer.array()));

        log.debug("Sent binary chunk: cx={}, cz={}, header={} bytes, compressed={} bytes, total={} bytes",
                dto.getCx(), dto.getCz(), headerBytes.length, dto.getC().length, buffer.position());
    }

    /**
     * Apply WEditCache overlays to chunk data for EDITOR sessions.
     * Modifies the chunk data in-place by:
     * 1. Decompressing chunk if compressed (ChunkData.c)
     * 2. Overlaying blocks from WEditCache
     * 3. Removing blocks marked as AIR in overlay
     * 4. Setting ChunkData.c = null (send uncompressed)
     *
     * @param worldId World ID
     * @param chunkData ChunkData to modify
     */
    private void applyWEditCacheOverlays(String worldId, ChunkData chunkData) {
        try {
            String chunkKey = chunkData.getCx() + ":" + chunkData.getCz();

            // Get WEditCache overlays for this chunk
            List<WEditCache> overlays = editCacheService.findByWorldIdAndChunk(worldId, chunkKey);

            if (overlays.isEmpty()) {
                log.trace("No WEditCache overlays for chunk: cx={}, cz={}, worldId={}",
                        chunkData.getCx(), chunkData.getCz(), worldId);
                return;
            }

            log.debug("Applying {} WEditCache overlays to chunk: cx={}, cz={}, worldId={}",
                    overlays.size(), chunkData.getCx(), chunkData.getCz(), worldId);

            // Ensure blocks are decompressed
            // Note: ChunkData.c is compressed, ChunkData.blocks is uncompressed
            // If c is set, we need to decompress it first (handled by chunkService.loadChunkData)
            // Here we assume chunkData is already loaded via loadChunkData()

            // Build position index of existing blocks
            List<Block> blocks = chunkData.getBlocks();
            if (blocks == null) {
                blocks = new ArrayList<>();
                chunkData.setBlocks(blocks);
            }

            Map<String, Block> blockIndex = new HashMap<>();
            for (Block block : blocks) {
                String posKey = BlockUtil.positionKey(block);
                blockIndex.put(posKey, block);
            }

            // Apply overlays from WEditCache
            for (WEditCache overlay : overlays) {
                Block overlayBlock = overlay.getBlock().getBlock();
                String posKey = BlockUtil.positionKey(overlayBlock);

                if (BlockUtil.isAirType(overlayBlock.getBlockTypeId())) {
                    // AIR overlay = remove block
                    blockIndex.remove(posKey);
                    log.trace("Removed block at {} (AIR overlay)", posKey);
                } else {
                    // Non-AIR overlay = add or replace block
                    blockIndex.put(posKey, overlayBlock);
                    log.trace("Overlayed block at {} with type {}",
                            posKey, overlayBlock.getBlockTypeId());
                }
            }

            // Rebuild block list
            chunkData.setBlocks(new ArrayList<>(blockIndex.values()));

            // IMPORTANT: Set c = null to send uncompressed
            chunkData.setC(null);

            log.debug("Applied WEditCache overlays: chunk={}:{}, original={}, overlay={}, final={}, uncompressed=true",
                    chunkData.getCx(), chunkData.getCz(),
                    blocks.size(), overlays.size(), chunkData.getBlocks().size());

        } catch (Exception e) {
            log.error("Failed to apply WEditCache overlays: chunk={}:{}, worldId={}",
                    chunkData.getCx(), chunkData.getCz(), worldId, e);
        }
    }

    /**
     * Apply block status from WProgress to DTO if available.
     */
    @SuppressWarnings("unchecked")
    private void applyBlockStatus(ChunkDataTransferObject dto, Map<String, Map<String, Object>> blockStatusMap, String chunkKey) {
        var statusData = blockStatusMap.get(chunkKey);
        if (statusData != null && !statusData.isEmpty()) {
            try {
                dto.setS((Map<String, String>) (Map<?, ?>) statusData);
            } catch (ClassCastException e) {
                log.warn("Block status data contains non-String values for chunkKey={}, falling back to copy", chunkKey);
                Map<String, String> s = new HashMap<>();
                for (var entry : statusData.entrySet()) {
                    s.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                dto.setS(s);
            }
            log.debug("Applied block status: chunkKey={}, entries={}", chunkKey, statusData.size());
        }
    }

    /**
     * Chunk coordinate record.
     */
    public record ChunkCoord(int cx, int cz) {}
}
