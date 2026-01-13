package de.mhus.nimbus.shared.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Custom InputStream that lazy-loads chunks from MongoDB on-demand.
 *
 * CRITICAL: Files don't fit in memory, so chunks are loaded ONE AT A TIME.
 * This stream only keeps the current chunk in memory (max 512KB), loading the next
 * chunk only when the current one is exhausted.
 *
 * Memory usage: O(chunk-size) - maximum 512KB regardless of file size.
 * Works with files of ANY size (1GB, 10GB, etc.) without memory issues.
 */
@Slf4j
public class ChunkedInputStream extends InputStream {

    private final StorageDataRepository repository;
    private final String uuid;

    private int currentChunkIndex = 0;
    private byte[] currentChunkData = null;
    private int positionInChunk = 0;
    private boolean isEOF = false;
    private boolean closed = false;

    /**
     * Creates a new ChunkedInputStream.
     * Immediately loads the first chunk to verify data exists.
     *
     * @param repository Repository for loading chunks
     * @param uuid       Logical storage identifier (UUID)
     * @throws IllegalStateException If no chunks found for the UUID
     */
    public ChunkedInputStream(StorageDataRepository repository, String uuid) {
        this.repository = repository;
        this.uuid = uuid;

        // Load only the first chunk (not all chunks!)
        loadNextChunk();

        log.trace("ChunkedInputStream created: uuid={}", uuid);
    }

    @Override
    public int read() throws IOException {
        ensureOpen();

        if (currentChunkData == null) {
            return -1; // EOF
        }

        if (positionInChunk >= currentChunkData.length) {
            if (isEOF) {
                return -1; // No more chunks
            }
            loadNextChunk(); // Load next chunk on-demand
            if (currentChunkData == null) {
                return -1; // EOF
            }
        }

        return currentChunkData[positionInChunk++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();

        if (b == null) {
            throw new NullPointerException("Byte array is null");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("Invalid offset or length");
        }
        if (len == 0) {
            return 0;
        }

        if (currentChunkData == null) {
            return -1; // EOF
        }

        int totalRead = 0;

        while (totalRead < len) {
            if (positionInChunk >= currentChunkData.length) {
                if (isEOF) {
                    break; // No more chunks
                }
                loadNextChunk(); // Load next chunk on-demand
                if (currentChunkData == null) {
                    break; // EOF
                }
            }

            int remaining = currentChunkData.length - positionInChunk;
            int bytesToRead = Math.min(remaining, len - totalRead);

            System.arraycopy(currentChunkData, positionInChunk, b, off + totalRead, bytesToRead);
            positionInChunk += bytesToRead;
            totalRead += bytesToRead;
        }

        return totalRead > 0 ? totalRead : -1;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();

        if (currentChunkData == null) {
            return 0;
        }

        return currentChunkData.length - positionInChunk;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            currentChunkData = null;
            log.trace("ChunkedInputStream closed: uuid={}", uuid);
        }
    }

    /**
     * Load the next chunk from MongoDB.
     * Only called when the current chunk is exhausted.
     */
    private void loadNextChunk() {
        try {
            StorageData chunk = null;
            try {
                chunk = repository.findByUuidAndIndex(uuid, currentChunkIndex);
            } catch (IncorrectResultSizeDataAccessException e) {
                log.error("Multiple final chunks found for storageId: uuid={}, index={}", uuid, currentChunkIndex, e);
                // get all
                List<StorageData> chunks = repository.findAllByUuidAndIndex(uuid, currentChunkIndex);
                chunks.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                // get youngest and delete the rest
                chunk = chunks.removeLast();
                chunks.forEach(c -> {
                    log.info("Deleting duplicate final chunk id={} createdAt={}", c.getId(), c.getCreatedAt());
                    repository.delete(c);
                });
            }
            if (chunk == null) {
                if (currentChunkIndex == 0) {
                    throw new IllegalStateException("No chunks found for uuid: " + uuid);
                }
                // EOF reached
                currentChunkData = null;
                isEOF = true;
                log.trace("EOF reached: uuid={} totalChunks={}", uuid, currentChunkIndex);
                return;
            }

            // Validate chunk sequence integrity
            if (chunk.getIndex() != currentChunkIndex) {
                throw new IllegalStateException(
                        String.format("Chunk sequence error: expected index %d but got %d for uuid: %s",
                                currentChunkIndex, chunk.getIndex(), uuid)
                );
            }

            // Load ONLY this chunk's data (critical for memory efficiency)
            currentChunkData = chunk.getData();
            positionInChunk = 0;

            log.trace("Loaded chunk: uuid={} index={} size={}",
                    uuid, currentChunkIndex, currentChunkData.length);

            // Check if this was the final chunk
            if (chunk.isFinal()) {
                isEOF = true;
                log.trace("Final chunk loaded: uuid={} totalChunks={}", uuid, currentChunkIndex + 1);
            }

            currentChunkIndex++;

        } catch (IllegalStateException e) {
            // Re-throw validation errors directly
            throw e;
        } catch (Exception e) {
            log.error("Failed to load chunk: uuid={} index={}", uuid, currentChunkIndex, e);
            throw new RuntimeException("Failed to load chunk from MongoDB", e);
        }
    }

    /**
     * Ensure the stream is still open.
     *
     * @throws IOException If stream is closed
     */
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
    }
}
