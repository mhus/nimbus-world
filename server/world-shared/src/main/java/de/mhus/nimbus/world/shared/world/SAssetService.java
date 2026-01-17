package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.WorldId;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service zur Verwaltung von Assets (Inline oder extern gespeichert).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SAssetService implements StorageProvider {

    public static final String STORAGE_SCHEMA = "SAssetStorage";
    public static final SchemaVersion STORAGE_SCHEMA_VERSION = SchemaVersion.create("1.0.0");

    private final SAssetRepository repository;
    private final StorageService storageService; // optional injected
    private final MongoTemplate mongoTemplate;

    // ExecutorService for background compression tasks
    private final ExecutorService compressionExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("asset-compression-" + t.getId());
        return t;
    });

    @Value("${nimbus.asset.compression.enabled:true}")
    private boolean compressionEnabled;

    /**
     * Compression threshold in bytes. Files below this size are stored uncompressed.
     * Default: 1000 bytes
     */
    @Value("${nimbus.asset.compression.threshold:1000}")
    private int compressionThreshold;

    /**
     * Speichert ein Asset mit Metadaten (publicData).
     * Für Import aus test_server mit .info Dateien.
     */
    @Transactional
    public SAsset saveAsset(WorldId worldId, String path, InputStream stream,
                           String createdBy, AssetMetadata publicData) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path required");
        if (stream == null) return null;

        // world lookup
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + worldId);
        }

        var collection = WorldCollection.of(worldId.mainWorld(), path);

        SAsset asset = SAsset.builder()
                .worldId(collection.worldId().getId())
                .path(collection.path())
                .name(extractName(collection.path()))
                .createdBy(createdBy)
                .enabled(true)
                .publicData(publicData)
                .build();
        asset.setCreatedAt(Instant.now());
        asset.removeWorldPrefix();

        // Read first threshold bytes to determine if we should compress
        byte[] initialBuffer = new byte[compressionThreshold];
        int bytesRead;
        try {
            bytesRead = stream.readNBytes(initialBuffer, 0, compressionThreshold);
        } catch (Exception e) {
            log.error("Failed to read asset content: path={}", collection.path(), e);
            throw new IllegalStateException("Failed to read asset content", e);
        }

        // Check if there's more data beyond threshold
        byte[] remainingData;
        try {
            remainingData = stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read remaining asset content: path={}", collection.path(), e);
            throw new IllegalStateException("Failed to read remaining asset content", e);
        }

        long originalSize = bytesRead + remainingData.length;

        // Compression if enabled and size exceeds threshold
        InputStream finalStream;
        if (compressionEnabled && originalSize >= compressionThreshold) {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                    gzip.write(initialBuffer, 0, bytesRead);
                    if (remainingData.length > 0) {
                        gzip.write(remainingData);
                    }
                    gzip.finish();
                }
                byte[] compressedData = buffer.toByteArray();
                finalStream = new ByteArrayInputStream(compressedData);
                asset.setCompressed(true);
                log.debug("Asset compressed: path={} original={} compressed={} ratio={} threshold={}",
                        collection.path(), originalSize, compressedData.length,
                        String.format("%.1f%%", 100.0 * compressedData.length / originalSize), compressionThreshold);
            } catch (Exception e) {
                log.warn("Failed to compress asset, storing uncompressed: path={}", collection.path(), e);
                ByteArrayOutputStream fallback = new ByteArrayOutputStream();
                fallback.write(initialBuffer, 0, bytesRead);
                if (remainingData.length > 0) {
                    fallback.write(remainingData, 0, remainingData.length);
                }
                finalStream = new ByteArrayInputStream(fallback.toByteArray());
                asset.setCompressed(false);
            }
        } else {
            // Store uncompressed (below threshold or compression disabled)
            ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
            try {
                uncompressed.write(initialBuffer, 0, bytesRead);
                if (remainingData.length > 0) {
                    uncompressed.write(remainingData, 0, remainingData.length);
                }
            } catch (Exception e) {
                log.error("Failed to write uncompressed data: path={}", collection.path(), e);
                throw new IllegalStateException("Failed to write uncompressed data", e);
            }
            finalStream = new ByteArrayInputStream(uncompressed.toByteArray());
            asset.setCompressed(false);
            if (compressionEnabled) {
                log.debug("Asset below compression threshold: path={} size={} threshold={}",
                        collection.path(), originalSize, compressionThreshold);
            }
        }

        var storageInfo = storageService.store(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, collection.worldId().getId(), "assets/" + collection.path(), finalStream);
        asset.setStorageId(storageInfo.id());
        // Always store original uncompressed size
        asset.setSize(originalSize);
        log.debug("Storing asset externally path={} originalSize={} storageSize={} storageId={} world={} compressed={}",
                collection.path(), asset.getSize(), storageInfo.size(), storageInfo.id(), collection.worldId(), asset.isCompressed());

        return repository.save(asset);
    }

    /**
     * Find all assets by worldId.
     * Assets are only stored in main worlds (no branches, no instances, no zones).
     * WARNING: This loads ALL assets into memory. Use searchAssets() for large result sets.
     */
    public List<SAsset> findByWorldId(WorldId worldId) {
        var lookupWorld = worldId.mainWorld();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * World instances and branches never own Assets.
     * Assets are only stored in main worlds.
     *
     * @param worldId
     * @param path
     * @return
     */
    public Optional<SAsset> findByPath(WorldId worldId, String path) {

        // world lookup - always use main world (no branches, no instances, no zones)
        var lookupWorld = worldId.mainWorld();
        var collection = WorldCollection.of(lookupWorld, path);
        return repository.findByWorldIdAndPath(collection.worldId().getId(), collection.path());
    }

    /** Lädt den Inhalt des Assets. */
    public InputStream loadContent(SAsset asset) {
        if (asset == null) return null;
        if (!asset.isEnabled()) throw new IllegalStateException("Asset disabled: " + asset.getId());

        InputStream stream = storageService.load(asset.getStorageId());
        if (stream == null) return null;

        // Decompression if needed
        // Note: If compressed field is not set in DB (legacy data), it defaults to false (uncompressed)
        if (asset.isCompressed()) {
            try {
                return new GZIPInputStream(stream);
            } catch (Exception e) {
                log.error("Failed to decompress asset: id={} path={}", asset.getId(), asset.getPath(), e);
                return new ByteArrayInputStream(new byte[0]);
            }
        }

        return stream;
    }

    @Transactional
    public void disable(SAsset asset) {

        // world lookup
        if (WorldId.of(asset.getWorldId()).get().isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + asset.getWorldId());
        }

        repository.findById(asset.getId()).ifPresent(a -> {
            if (!a.isEnabled()) return;
            a.setEnabled(false);
            repository.save(a);
            log.debug("Disabled asset id={}", asset.getId());
        });
    }

    @Transactional
    public void delete(SAsset asset) {

        // world lookup
        if (WorldId.of(asset.getWorldId()).get().isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + asset.getWorldId());
        }
        asset.removeWorldPrefix();
        repository.findById(asset.getId()).ifPresent(a -> {
            try {
                storageService.delete(a.getStorageId());
            } catch (Exception e) {
                log.warn("Failed to delete external storage {}", a.getStorageId(), e);
            }
            repository.delete(a);
            log.debug("Deleted asset id={} path={}", asset.getId(), a.getPath());
        });
    }

    @Transactional
    public SAsset updateContent(SAsset asset, InputStream stream) {

        if (stream == null) return null;
        // world lookup
        if (WorldId.of(asset.getWorldId()).get().isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + asset.getWorldId());
        }
        asset.removeWorldPrefix();

        return repository.findById(asset.getId()).map(a -> {
            if (!a.isEnabled()) throw new IllegalStateException("Asset disabled: " + a.getId());

            // Read first threshold bytes to determine if we should compress
            byte[] initialBuffer = new byte[compressionThreshold + 1];
            int bytesRead;
            try {
                bytesRead = stream.readNBytes(initialBuffer, 0, initialBuffer.length);
            } catch (Exception e) {
                log.error("Failed to read asset content: path={}", a.getPath(), e);
                throw new IllegalStateException("Failed to read asset content", e);
            }

            InputStream finalStream;
            final AtomicLong totalBytesCounter = new AtomicLong(0);

            if (bytesRead > compressionThreshold && compressionEnabled) {
                // Large file with compression enabled - stream compression without loading all into memory
                // Use PipedInputStream/PipedOutputStream to compress in background thread
                try {
                    PipedInputStream pipedInput = new PipedInputStream(64 * 1024); // 64KB buffer
                    PipedOutputStream pipedOutput = new PipedOutputStream(pipedInput);

                    // Capture bytesRead for lambda
                    final int initialBytesRead = bytesRead;

                    // Compress in background thread using executor
                    compressionExecutor.submit(() -> {
                        try (GZIPOutputStream gzip = new GZIPOutputStream(pipedOutput)) {
                            gzip.write(initialBuffer, 0, initialBytesRead);
                            totalBytesCounter.addAndGet(initialBytesRead);

                            // Copy remaining stream in chunks
                            byte[] chunk = new byte[8192];
                            int n;
                            while ((n = stream.read(chunk)) > 0) {
                                gzip.write(chunk, 0, n);
                                totalBytesCounter.addAndGet(n);
                            }
                            gzip.finish();
                            log.debug("Asset compression completed: path={} original={}", a.getPath(), totalBytesCounter.get());
                        } catch (Exception e) {
                            log.error("Failed to compress asset in background thread: path={}", a.getPath(), e);
                            try {
                                pipedOutput.close();
                            } catch (Exception ignored) {}
                        }
                    });

                    finalStream = pipedInput;
                    a.setCompressed(true);
                    log.debug("Large asset compression started (streaming): path={}", a.getPath());
                } catch (IOException e) {
                    log.error("Failed to create piped streams for compression: path={}", a.getPath(), e);
                    throw new IllegalStateException("Failed to create piped streams", e);
                }
            } else {
                // All other cases: store uncompressed (streaming without full memory load)
                if (bytesRead > compressionThreshold) {
                    // Large file uncompressed: combine initialBuffer + remaining stream
                    // Use SequenceInputStream to avoid loading everything into memory
                    // Wrap in CountingInputStream to track size
                    ByteArrayInputStream initialStream = new ByteArrayInputStream(initialBuffer, 0, bytesRead);
                    InputStream sequenceStream = new java.io.SequenceInputStream(initialStream, stream);

                    // Wrap with CountingInputStream to track bytes
                    totalBytesCounter.set(0);
                    finalStream = new FilterInputStream(sequenceStream) {
                        @Override
                        public int read() throws IOException {
                            int b = super.read();
                            if (b != -1) totalBytesCounter.incrementAndGet();
                            return b;
                        }

                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            int n = super.read(b, off, len);
                            if (n > 0) totalBytesCounter.addAndGet(n);
                            return n;
                        }
                    };
                    a.setCompressed(false);
                    log.debug("Large asset stored uncompressed (streaming): path={}", a.getPath());
                } else {
                    // Small file: all data in initialBuffer
                    finalStream = new ByteArrayInputStream(initialBuffer, 0, bytesRead);
                    a.setCompressed(false);
                    totalBytesCounter.set(bytesRead);
                    if (compressionEnabled && bytesRead > 0) {
                        log.debug("Asset below compression threshold: path={} threshold={}",
                                a.getPath(), compressionThreshold);
                    }
                }
            }

            if (StringUtils.isNotEmpty(a.getStorageId())  && storageService.exists(a.getStorageId()) ) {
                var storageId = storageService.update(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, a.getStorageId(), finalStream);
                // Always store original uncompressed size
                a.setSize(totalBytesCounter.get());
                a.setStorageId(storageId.id());
                log.debug("Updated external content id={} originalSize={} storageSize={} compressed={}",
                        storageId.id(), a.getSize(), storageId.size(), a.isCompressed());
            } else {
                var worldId = a.getWorldId();
                var path = a.getPath();
                var storageId = storageService.store(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, worldId, "assets/" + path, finalStream);
                // Always store original uncompressed size
                a.setSize(totalBytesCounter.get());
                a.setStorageId(storageId.id());
                log.debug("Updated/Created external content id={} originalSize={} storageSize={} compressed={}",
                        storageId.id(), a.getSize(), storageId.size(), a.isCompressed());
            }
            return repository.save(a);
        }).orElse(null);
    }

    @Transactional
    public Optional<SAsset> updateMetadata(SAsset asset, AssetMetadata metadata) {
        // world lookup
        if (WorldId.of(asset.getWorldId()).get().isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + asset.getWorldId());
        }
        asset.removeWorldPrefix();

        return repository.findById(asset.getId()).map(a -> {
            if (!a.isEnabled()) throw new IllegalStateException("Asset disabled: " + a.getId());
            a.setPublicData(metadata);
            log.debug("Updated metadata id={}", asset.getId());
            return repository.save(a);
        });
    }

    /**
     * Duplicates an asset with a new path.
     * Creates a copy of the content in storage and a new database entry.
     */
    @Transactional
    public SAsset duplicateAsset(SAsset source, String newPath, String createdBy) {
        // world lookup
        if (WorldId.of(source.getWorldId()).get().isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + source.getWorldId());
        }

        return duplicateAssetToWorld(source, WorldId.of(source.getWorldId()).orElseThrow(), newPath, createdBy);
    }

    /**
     * Duplicates an asset to a target world with a new path (supports cross-world copy).
     * Creates a copy of the content in storage and a new database entry.
     * All metadata (publicData) is preserved.
     * The content is copied as-is (preserving compression state and size).
     */
    @Transactional
    public SAsset duplicateAssetToWorld(SAsset source, WorldId targetWorldId, String newPath, String createdBy) {
        if (source == null) throw new IllegalArgumentException("source asset required");
        if (targetWorldId == null) throw new IllegalArgumentException("targetWorldId required");
        if (newPath == null || newPath.isBlank()) throw new IllegalArgumentException("newPath required");
        if (!source.isEnabled()) throw new IllegalStateException("Source asset disabled: " + source.getId());
        // world lookup
        if (WorldId.of(source.getWorldId()).get().isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be laoded to a world instance: " + source.getWorldId());
        }
        // world lookup
        if (targetWorldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + targetWorldId);
        }
        source.removeWorldPrefix();

        // Load RAW content from storage (without decompression) to preserve exact binary data
        InputStream sourceContent = storageService.load(source.getStorageId());
        if (sourceContent == null) {
            throw new IllegalStateException("Failed to load source asset content: " + source.getId());
        }

        // Handle world collection prefix in newPath
        var collection = WorldCollection.of(targetWorldId.mainWorld(), newPath);

        // Create new asset entity with target worldId
        // Copy compression state and metadata from source
        SAsset duplicate = SAsset.builder()
                .worldId(collection.worldId().getId())
                .path(collection.path())
                .name(extractName(collection.path()))
                .createdBy(createdBy)
                .enabled(true)
                .compressed(source.isCompressed()) // Preserve compression state
                .publicData(source.getPublicData()) // Copy metadata
                .build();
        duplicate.setCreatedAt(Instant.now());
        duplicate.removeWorldPrefix();

        // Store content in new location (use target worldId)
        // Store as-is without re-compression to preserve exact size
        StorageService.StorageInfo storageInfo = storageService.store(
                STORAGE_SCHEMA,
                STORAGE_SCHEMA_VERSION,
                collection.worldId().getId(),
                "assets/" + collection.path(),
                sourceContent
        );

        duplicate.setStorageId(storageInfo.id());
        // Copy original size from source (source.size is already the uncompressed size)
        duplicate.setSize(source.getSize());

        log.debug("Duplicated asset: sourcePath={}, sourceWorldId={}, newPath={}, targetWorldId={}, originalSize={}, storageSize={}, storageId={}, compressed={}",
                  source.getPath(), source.getWorldId(), collection.path(), collection.worldId().getId(), duplicate.getSize(), storageInfo.size(), storageInfo.id(), duplicate.isCompressed());

        return repository.save(duplicate);
    }

    private String extractName(String path) {
        if (path == null) return null;
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /**
     * Search assets with database-level filtering and pagination.
     * Supports prefix-based search (w:, r:, p:, or shared collections).
     * Assets are only stored in main worlds (no branches, no instances, no zones).
     *
     * @param worldId The world identifier
     * @param query Search query (optional, prefix:path format, default prefix is "w:")
     * @param extension Extension filter (optional, e.g., ".png")
     * @param offset Pagination offset
     * @param limit Pagination limit
     * @return Page of assets with total count
     */
    public AssetSearchResult searchAssets(WorldId worldId, String query, String extension, int offset, int limit) {

        WorldId lookupWorld = worldId.mainWorld();
        if (Strings.isNotBlank(query)) {
            int pos = query.indexOf(':');
            if (pos > 0) {
                var collection = WorldCollection.of(lookupWorld, query);
                lookupWorld = collection.worldId();
                query = query.substring(pos + 1);
            }
            query = ".*" + java.util.regex.Pattern.quote(query) + ".*";
        } else {
            query = ".*";
        }

        // Direct search - assets are only in main worlds
        return searchInWorldId(lookupWorld.getId(), query, offset, limit);
    }

    /**
     * Search assets in a specific worldId with filtering and pagination.
     */
    private AssetSearchResult searchInWorldId(String worldId, String pathPattern, int offset, int limit) {
        // Calculate page number from offset (Spring Data uses 0-based page numbers)
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit);
        Page<SAsset> page;

        if (pathPattern != null) {
            page = repository.findByWorldIdAndPathContaining(worldId, pathPattern, pageable);
        } else {
            page = repository.findByWorldId(worldId, pageable);
        }

        return new AssetSearchResult(
                page.getContent(),
                (int) page.getTotalElements(),
                offset,
                limit
        );
    }

    /**
     * Result wrapper for asset search with pagination info.
     */
    public record AssetSearchResult(
            List<SAsset> assets,
            int totalCount,
            int offset,
            int limit
    ) {}

    /**
     * Extract unique folder paths from assets in a world.
     * Folders are virtual - they are derived from asset paths and don't exist as entities in MongoDB.
     * Example: Asset "textures/block/stone.png" creates folders "textures" and "textures/block".
     *
     * @param worldId The world identifier
     * @param parentPath Optional parent path filter (e.g., "textures/" to get only subfolders of textures)
     * @return List of folder metadata, sorted alphabetically by path
     */
    public List<FolderInfo> extractFolders(WorldId worldId, String parentPath) {
        if (worldId == null) throw new IllegalArgumentException("worldId required");

        // Normalize parent path (remove trailing slash)
        String normalizedParent = parentPath != null && !parentPath.isEmpty()
                ? parentPath.replaceAll("/+$", "")
                : null;

        // Load all assets for the world
        WorldId lookupWorld = worldId.mainWorld();
        List<SAsset> assets = repository.findByWorldId(lookupWorld.getId());

        log.debug("Extracting folders from {} assets (worldId={}, parent={})",
                assets.size(), lookupWorld.getId(), normalizedParent);

        // Extract folder paths and count assets per folder
        Map<String, FolderStats> folderMap = new HashMap<>();

        for (SAsset asset : assets) {
            Set<String> folders = extractFolderPathsFromAssetPath(asset.getPath());

            for (String folder : folders) {
                // Filter by parent path if specified
                if (normalizedParent != null) {
                    if (!folder.startsWith(normalizedParent + "/") && !folder.equals(normalizedParent)) {
                        continue;
                    }
                }

                // Track folder statistics
                folderMap.computeIfAbsent(folder, k -> new FolderStats()).incrementAssetCount();
            }
        }

        // Count subfolders for each folder
        for (String folder : folderMap.keySet()) {
            long subfoldersCount = folderMap.keySet().stream()
                    .filter(f -> !f.equals(folder) && f.startsWith(folder + "/"))
                    .filter(f -> f.substring(folder.length() + 1).indexOf('/') == -1) // Only direct children
                    .count();
            folderMap.get(folder).setSubfolderCount((int) subfoldersCount);
        }

        // Convert to FolderInfo DTOs and sort
        List<FolderInfo> result = folderMap.entrySet().stream()
                .map(entry -> buildFolderInfo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(FolderInfo::path))
                .collect(Collectors.toList());

        log.debug("Extracted {} folders", result.size());
        return result;
    }

    /**
     * Extract all folder paths from a single asset path.
     * Handles collection prefixes (w:, r:, p:, xyz:) - removes them before extracting folders.
     * Examples:
     *   "w:textures/block/stone.png" → ["textures", "textures/block"]
     *   "textures/magic/book.png" → ["textures", "textures/magic"] (legacy)
     */
    private Set<String> extractFolderPathsFromAssetPath(String assetPath) {
        if (assetPath == null || assetPath.isEmpty()) return Collections.emptySet();

        // Remove collection prefix (w:, r:, p:, xyz:)
        int colonPos = assetPath.indexOf(':');
        if (colonPos > 0) {
            assetPath = assetPath.substring(colonPos + 1);
        }

        Set<String> folders = new HashSet<>();
        String[] parts = assetPath.split("/");

        // Build incremental paths (exclude the last part which is the filename)
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) current.append("/");
            current.append(parts[i]);
            folders.add(current.toString());
        }

        return folders;
    }

    /**
     * Build FolderInfo DTO from folder path and statistics.
     */
    private FolderInfo buildFolderInfo(String path, FolderStats stats) {
        String name = path.contains("/")
                ? path.substring(path.lastIndexOf("/") + 1)
                : path;

        String parentPath = path.contains("/")
                ? path.substring(0, path.lastIndexOf("/"))
                : "";

        return new FolderInfo(
                path,
                name,
                stats.getAssetCount(),
                stats.getTotalAssetCount(),
                stats.getSubfolderCount(),
                parentPath
        );
    }

    /**
     * Internal statistics holder for folder metadata calculation.
     */
    private static class FolderStats {
        private int assetCount = 0;
        private int totalAssetCount = 0;
        private int subfolderCount = 0;

        void incrementAssetCount() {
            assetCount++;
            totalAssetCount++;
        }

        int getAssetCount() { return assetCount; }
        int getTotalAssetCount() { return totalAssetCount; }
        int getSubfolderCount() { return subfolderCount; }
        void setSubfolderCount(int count) { this.subfolderCount = count; }
    }

    /**
     * Update all asset paths with a given prefix (folder rename/move).
     * This is a bulk operation that affects all assets in a folder and its subfolders.
     *
     * WARNING: This is a potentially dangerous operation that can break references.
     * Use with caution.
     *
     * @param worldId The world identifier
     * @param oldPrefix The old folder path prefix (e.g., "old_textures/")
     * @param newPrefix The new folder path prefix (e.g., "textures/")
     * @return Number of assets updated
     */
    @Transactional
    public int updatePathPrefix(WorldId worldId, String oldPrefix, String newPrefix) {
        if (worldId == null) throw new IllegalArgumentException("worldId required");
        if (oldPrefix == null || oldPrefix.isEmpty()) throw new IllegalArgumentException("oldPrefix required");
        if (newPrefix == null || newPrefix.isEmpty()) throw new IllegalArgumentException("newPrefix required");
        if (oldPrefix.equals(newPrefix)) throw new IllegalArgumentException("oldPrefix and newPrefix must be different");
        // world lookup
        if (worldId.isInstanceOrZone()) {
            throw new IllegalArgumentException("can't be save to a world instance: " + worldId);
        }

        // Normalize paths (remove trailing slashes) - make final for lambda
        final String normalizedOldPrefix = oldPrefix.replaceAll("/+$", "");
        final String normalizedNewPrefix = newPrefix.replaceAll("/+$", "");

        WorldId lookupWorld = worldId.mainWorld();

        log.debug("Updating path prefix: worldId={}, oldPrefix='{}', newPrefix='{}'",
                lookupWorld.getId(), normalizedOldPrefix, normalizedNewPrefix);

        // 1. Find all assets with path starting with oldPrefix
        List<SAsset> assetsToUpdate = repository.findByWorldId(lookupWorld.getId())
                .stream()
                .filter(asset -> asset.getPath().startsWith(normalizedOldPrefix + "/") || asset.getPath().equals(normalizedOldPrefix))
                .collect(Collectors.toList());

        if (assetsToUpdate.isEmpty()) {
            log.debug("No assets found with prefix '{}'", normalizedOldPrefix);
            return 0;
        }

        log.info("Found {} assets to update for prefix change", assetsToUpdate.size());

        // 2. Check for conflicts (newPath already exists)
        List<String> conflicts = new ArrayList<>();
        for (SAsset asset : assetsToUpdate) {
            String newPath = generateNewPath(asset.getPath(), normalizedOldPrefix, normalizedNewPrefix);

            // Check if target path already exists
            Optional<SAsset> existingAsset = repository.findByWorldIdAndPath(
                    lookupWorld.getId(),
                    newPath
            );

            if (existingAsset.isPresent() && !existingAsset.get().getId().equals(asset.getId())) {
                conflicts.add(asset.getPath() + " -> " + newPath);
            }
        }

        if (!conflicts.isEmpty()) {
            String conflictList = String.join(", ", conflicts.subList(0, Math.min(5, conflicts.size())));
            throw new IllegalStateException(
                    String.format("Path conflicts detected: %d conflicts. Examples: %s",
                            conflicts.size(), conflictList)
            );
        }

        // 3. Update all assets (path and name)
        int updatedCount = 0;
        for (SAsset asset : assetsToUpdate) {
            String oldPath = asset.getPath();
            String newPath = generateNewPath(oldPath, normalizedOldPrefix, normalizedNewPrefix);

            asset.setPath(newPath);
            asset.setName(extractName(newPath));

            repository.save(asset);
            updatedCount++;

            log.debug("Updated asset path: '{}' -> '{}'", oldPath, newPath);
        }

        log.info("Successfully updated {} asset paths from '{}' to '{}'",
                updatedCount, normalizedOldPrefix, normalizedNewPrefix);

        return updatedCount;
    }

    /**
     * Generate new path by replacing prefix.
     * Example: generateNewPath("old/sub/file.png", "old", "new") => "new/sub/file.png"
     */
    private String generateNewPath(String originalPath, String oldPrefix, String newPrefix) {
        if (originalPath.equals(oldPrefix)) {
            return newPrefix;
        }

        // Replace prefix
        if (originalPath.startsWith(oldPrefix + "/")) {
            return newPrefix + originalPath.substring(oldPrefix.length());
        }

        // Should not happen (validated earlier)
        throw new IllegalStateException("Path does not start with prefix: " + originalPath);
    }

    /**
     * Find all distinct worldIds in assets.
     * Returns a list of unique worldId values from all SAsset documents.
     *
     * @return List of distinct worldIds (excludes null values)
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(
                new Query(),
                "worldId",
                SAsset.class,
                String.class
        );
    }

    /**
     * Find all distinct storageIds for a given world.
     * Returns a list of unique storageId values from SAsset documents.
     *
     * @param worldId World identifier
     * @return List of distinct storageIds (excludes null values)
     */
    @Override
    public List<String> findDistinctStorageIds(WorldId worldId) {
        var lookupWorld = worldId.mainWorld();
        var query = new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("worldId").is(lookupWorld.getId())
        );
        return mongoTemplate.findDistinct(
                query,
                "storageId",
                SAsset.class,
                String.class
        );
    }
}
