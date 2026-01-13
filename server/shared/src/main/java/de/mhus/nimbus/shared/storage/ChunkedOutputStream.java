package de.mhus.nimbus.shared.storage;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;

/**
 * Custom OutputStream that automatically splits data into chunks and saves to MongoDB.
 *
 * This stream transparently handles chunking, allowing callers to use standard
 * OutputStream API without worrying about chunk boundaries. When the internal buffer
 * reaches the configured chunk size (default 512KB), it automatically flushes to MongoDB.
 *
 * Memory usage: O(chunk-size) - only one chunk buffer is kept in memory at a time.
 */
@Slf4j
public class ChunkedOutputStream extends OutputStream {

    private final StorageDataRepository repository;
    private final String uuid;
    private final String path;
    private final int chunkSize;
    private final Date createdAt;
    private final String worldId;
    private final String schema;
    private final String schemaVersion;

    private byte[] buffer;
    private int bufferPosition = 0;
    private int chunkIndex = 0;
    private long totalBytesWritten = 0;
    private boolean closed = false;
    private StorageData lastChunk = null; // Track last saved chunk to mark as final

    /**
     * Creates a new ChunkedOutputStream.
     *
     * @param repository Repository for saving chunks
     * @param uuid       Logical storage identifier (UUID)
     * @param path       Original file path
     * @param chunkSize  Maximum chunk size in bytes (typically 512KB)
     * @param createdAt  Creation timestamp for all chunks
     */
    public ChunkedOutputStream(StorageDataRepository repository, String uuid, String schema, String schemaVersion, String worldId, String path,
                               int chunkSize, Date createdAt) {
        this.repository = repository;
        this.uuid = uuid;
        this.worldId = worldId;
        this.schema = schema;
        this.schemaVersion = schemaVersion;
        this.path = path;
        this.chunkSize = chunkSize;
        this.createdAt = createdAt;
        this.buffer = new byte[chunkSize];
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();

        buffer[bufferPosition++] = (byte) b;
        totalBytesWritten++;

        if (bufferPosition >= chunkSize) {
            flushChunk(false);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();

        if (b == null) {
            throw new NullPointerException("Byte array is null");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("Invalid offset or length");
        }

        int remaining = len;
        int offset = off;

        while (remaining > 0) {
            int spaceInBuffer = chunkSize - bufferPosition;
            int bytesToWrite = Math.min(remaining, spaceInBuffer);

            System.arraycopy(b, offset, buffer, bufferPosition, bytesToWrite);
            bufferPosition += bytesToWrite;
            totalBytesWritten += bytesToWrite;
            offset += bytesToWrite;
            remaining -= bytesToWrite;

            if (bufferPosition >= chunkSize) {
                flushChunk(false);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        // Intentionally empty - chunks are flushed automatically when buffer is full
        // Manual flush before close would create incomplete chunks
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        try {
            // Save final chunk if buffer has data
            if (bufferPosition > 0) {
                flushChunk(true);
            } else if (lastChunk != null) {
                // Buffer is empty but we have saved chunks - create updated final chunk
                StorageData finalChunk = StorageData.builder()
                        .uuid(uuid)
                        .path(path)
                        .index(lastChunk.getIndex())
                        .data(lastChunk.getData())
                        .isFinal(true)
                        .size(totalBytesWritten)
                        .createdAt(createdAt)
                        .worldId(worldId)
                        .schema(schema)
                        .schemaVersion(schemaVersion)
                        .build();
                repository.save(finalChunk);
                log.trace("Updated last chunk as final: uuid={} index={}", uuid, lastChunk.getIndex());
            } else {
                // No chunks written at all - save empty final chunk
                flushChunk(true);
            }

            log.debug("ChunkedOutputStream closed: uuid={} chunks={} totalBytes={}",
                    uuid, chunkIndex, totalBytesWritten);

        } finally {
            closed = true;
            buffer = null;
        }
    }

    /**
     * Get the total number of bytes written to this stream.
     *
     * @return Total bytes written
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    /**
     * Flush the current buffer as a chunk to MongoDB.
     *
     * @param isFinal True if this is the last chunk
     * @throws IOException If saving to MongoDB fails
     */
    private void flushChunk(boolean isFinal) throws IOException {
        if (bufferPosition == 0 && !isFinal) {
            return; // Nothing to flush
        }

        byte[] chunkData = Arrays.copyOf(buffer, bufferPosition);

        StorageData chunk = StorageData.builder()
                .uuid(uuid)
                .path(path)
                .index(chunkIndex)
                .data(chunkData)
                .isFinal(isFinal)
                .size(isFinal ? totalBytesWritten : 0)
                .createdAt(createdAt)
                .worldId(worldId)
                .schema(schema)
                .schemaVersion(schemaVersion)
                .build();

        try {
            repository.save(chunk);
            lastChunk = chunk; // Track last saved chunk
            log.trace("Saved chunk: uuid={} index={} size={} final={}",
                    uuid, chunkIndex, chunkData.length, isFinal);

            chunkIndex++;
            bufferPosition = 0;

        } catch (Exception e) {
            log.error("Failed to save chunk: uuid={} index={}", uuid, chunkIndex, e);
            throw new IOException("Failed to save chunk to MongoDB", e);
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
