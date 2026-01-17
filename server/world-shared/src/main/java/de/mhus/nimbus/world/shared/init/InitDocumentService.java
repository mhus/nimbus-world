package de.mhus.nimbus.world.shared.init;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Service to import initial documents from resources/documents folder on startup.
 * <p>
 * Documents are imported into the @shared:n world collection.
 * Folder structure: resources/documents/{collection}/{filename}.md
 * <p>
 * Example: resources/documents/lore/dragon.md
 * - worldId = '@shared:n'
 * - collection = 'lore'
 * - documentId = generated UUID
 * - title = 'dragon'
 * - name = 'dragon.md'
 * - format = 'markdown'
 * - content = file content
 * <p>
 * This service works with both exploded and compiled (JAR) Spring Boot applications.
 * All projects that include world-shared can provide their own documents in resources/documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InitDocumentService {

    private static final String SHARED_WORLD_ID = "@shared:n";
    private static final String DOCUMENTS_PATTERN = "classpath*:documents/**/*.md";

    private final WDocumentService documentService;

    @PostConstruct
    public void init() {
        try {
            importDocuments();
        } catch (Exception e) {
            log.error("Failed to import initial documents", e);
        }
    }

    /**
     * Import documents from resources/documents folder.
     */
    private void importDocuments() {
        log.info("Importing initial documents from resources/documents...");

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        WorldId worldId = WorldId.of(SHARED_WORLD_ID)
                .orElseThrow(() -> new IllegalStateException("Invalid SHARED_WORLD_ID: " + SHARED_WORLD_ID));

        log.info("Using worldId='{}' (getId()='{}')", worldId, worldId.getId());

        try {
            Resource[] resources = resolver.getResources(DOCUMENTS_PATTERN);
            log.info("Found {} document files to process", resources.length);

            int imported = 0;
            int skipped = 0;

            for (Resource resource : resources) {
                try {
                    if (importDocument(worldId, resource)) {
                        imported++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.error("Failed to import document: {}", resource.getFilename(), e);
                }
            }

            log.info("Document import completed: {} imported, {} skipped (already exist)", imported, skipped);
        } catch (IOException e) {
            log.error("Failed to read document resources", e);
        }
    }

    /**
     * Import a single document.
     *
     * @param worldId  World ID
     * @param resource Document resource
     * @return true if imported, false if skipped (already exists)
     */
    private boolean importDocument(WorldId worldId, Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (filename == null) {
            log.warn("Skipping resource without filename: {}", resource);
            return false;
        }

        // Read content
        byte[] contentBytes = resource.getInputStream().readAllBytes();
        String content = new String(contentBytes, StandardCharsets.UTF_8);

        // Calculate hash
        String hash = calculateHash(content);

        // Extract collection from path
        // Path format: .../documents/{collection}/{filename}.md
        String uri = resource.getURI().toString();
        String collection = extractCollection(uri);
        if (collection == null) {
            log.warn("Could not extract collection from path: {}", uri);
            return false;
        }

        // Extract title (filename without extension)
        String title = filename;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            title = filename.substring(0, dotIndex);
        }

        // Check if document with same hash already exists in this collection
        var existingDocs = documentService.findByCollection(worldId, collection);
        boolean exists = existingDocs.stream()
                .anyMatch(doc -> hash.equals(doc.getHash()));

        if (exists) {
            log.debug("Document '{}' in collection '{}' already exists (same hash), skipping", filename, collection);
            return false;
        }

        // Generate new document ID
        String documentId = UUID.randomUUID().toString();
        final String finalTitle = title;

        // Save document
        documentService.save(worldId, collection, documentId, doc -> {
            doc.setName(filename);
            doc.setTitle(finalTitle);
            doc.setFormat("markdown");
            doc.setContent(content);
            doc.setHash(hash);
            doc.setMain(true);
            doc.setReadOnly(true);  // Mark imported documents as read-only
        });

        log.info("Imported document: collection='{}', name='{}', title='{}'", collection, filename, title);
        return true;
    }

    /**
     * Extract collection name from resource path.
     * Expected format: .../documents/{collection}/{filename}.md
     */
    private String extractCollection(String path) {
        // Find "documents/" in path
        int documentsIndex = path.indexOf("documents/");
        if (documentsIndex < 0) {
            return null;
        }

        // Extract everything after "documents/" up to the next "/"
        String afterDocuments = path.substring(documentsIndex + "documents/".length());
        int slashIndex = afterDocuments.indexOf('/');
        if (slashIndex < 0) {
            return null;
        }

        return afterDocuments.substring(0, slashIndex);
    }

    /**
     * Calculate SHA-256 hash of content.
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }
}
