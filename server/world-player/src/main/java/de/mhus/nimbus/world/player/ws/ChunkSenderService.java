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
import java.util.Base64;
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
                var chunkOpt = chunkService.find(session.getWorldId(), chunkKey, session.getEpoch());
                if (chunkOpt.isEmpty()) {
                    // Generate default chunk if not found (but don't save it)
                    var chunkDataOpt = chunkService.loadChunkData(session.getWorldId(), chunkKey, true, session.getEpoch());
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

                // Handle EDITOR overlays from WEditCache. These require the uncompressed
                // ChunkData, so build the transfer object directly from the loaded
                // ChunkData instead of calling toTransferObject() first (which would load
                // the chunk a second time only to have its result overwritten here).
                if (session.isEditActor() && hasOverlayData(session.getWorldId(), chunkKey)) {
                    var chunkDataOpt = chunkService.loadChunkData(session.getWorldId(), chunkKey, false, session.getEpoch());
                    if (chunkDataOpt.isPresent()) {
                        var chunkData = chunkDataOpt.get();
                        // Apply WEditCache overlays (decompresses, merges, sets c=null)
                        applyWEditCacheOverlays(session.getWorldId().getId(), chunkData);
                        ChunkDataTransferObject overlayDto =
                                chunkService.chunkDataToTransferObject(session.getWorldId(), chunkData);
                        if (overlayDto == null) {
                            log.warn("Failed to convert overlaid chunk to transfer object: chunkKey={}", chunkKey);
                            continue;
                        }
                        // Apply block status overrides and send as JSON (uncompressed)
                        applyBlockStatus(overlayDto, blockStatusMap, chunkKey);
                        overlayDto.setC(null);
                        responseChunks.add(objectMapper.valueToTree(overlayDto));
                        continue;
                    }
                }

                // Convert to transfer object (uses compressed storage if available)
                ChunkDataTransferObject dto = chunkService.toTransferObject(session.getWorldId(), chunk);
                if (dto == null) {
                    log.warn("Failed to convert chunk to transfer object: chunkKey={}", chunkKey);
                    continue;
                }

                // Apply block status overrides
                applyBlockStatus(dto, blockStatusMap, chunkKey);

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
                        var fallbackData = chunkService.loadChunkData(session.getWorldId(), chunkKey, false, session.getEpoch());
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
     * Send compressed chunk data to the client.
     * Uses binary WebSocket frames for most browsers, but falls back to base64-encoded
     * text messages for Safari which truncates large binary frames due to WebKit bugs.
     */
    private void sendCompressedChunkBinary(PlayerSession session, ChunkDataTransferObject dto) throws Exception {
        if (session.isSafariClient()) {
            sendChunkAsBase64Text(session, dto);
        } else {
            sendChunkAsBinary(session, dto);
        }
    }

    /**
     * Send chunk as binary WebSocket frame.
     * Format: [4 bytes header length][header JSON][GZIP compressed data]
     */
    private void sendChunkAsBinary(PlayerSession session, ChunkDataTransferObject dto) throws Exception {
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

        ByteBuffer buffer = ByteBuffer.allocate(4 + headerBytes.length + dto.getC().length);
        buffer.putInt(headerBytes.length);
        buffer.put(headerBytes);
        buffer.put(dto.getC());

        session.sendMessage(new BinaryMessage(buffer.array()));

        log.debug("Sent binary chunk: cx={}, cz={}, header={} bytes, compressed={} bytes, total={} bytes",
                dto.getCx(), dto.getCz(), headerBytes.length, dto.getC().length, buffer.capacity());
    }

    /**
     * Send chunk as base64-encoded JSON text message (Safari workaround).
     * Safari truncates large binary WebSocket frames due to WebKit fragmentation bugs,
     * so we encode the compressed data as base64 within a JSON text message.
     * Format: JSON {t: "CHUNK_BINARY", cx, cz, i?, s?, c: base64-gzip-data}
     */
    private void sendChunkAsBase64Text(PlayerSession session, ChunkDataTransferObject dto) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "CHUNK_BINARY");
        message.put("cx", dto.getCx());
        message.put("cz", dto.getCz());
        if (dto.getI() != null && !dto.getI().isEmpty()) {
            message.put("i", dto.getI());
        }
        if (dto.getS() != null && !dto.getS().isEmpty()) {
            message.put("s", dto.getS());
        }
        message.put("c", Base64.getEncoder().encodeToString(dto.getC()));

        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));

        log.debug("Sent chunk as base64 text: cx={}, cz={}, compressed={} bytes, json={} bytes",
                dto.getCx(), dto.getCz(), dto.getC().length, json.length());
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
