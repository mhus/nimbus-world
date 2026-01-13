package de.mhus.nimbus.shared.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for exporting MongoDB collections to JSON files.
 *
 * <p>This service exports all entities from a specified collection into a single JSON file,
 * where each line contains one entity as a JSON object (JSON Lines format).</p>
 *
 * <p>Export format:</p>
 * <ul>
 *   <li>One entity per line (JSON Lines format)</li>
 *   <li>Each line is a complete JSON object representing one MongoDB document</li>
 *   <li>Files use .jsonl extension</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * ExportResult result = exportService.exportCollection("w_entities", WEntity.class, outputPath);
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Exports all entities from a collection to a JSON Lines file.
     *
     * @param collectionName the name of the MongoDB collection to export
     * @param outputFile     the path to the output file (will be created or overwritten)
     * @return ExportResult containing statistics about the export
     * @throws IOException if file operations fail
     */
    public ExportResult exportCollection(String collectionName, Path outputFile) throws IOException {
        return exportCollection(collectionName, outputFile, null);
    }

    /**
     * Exports entities from a collection to a JSON Lines file with optional worldId filter.
     * Exports data directly from MongoDB as JSON to preserve all fields including _schema.
     *
     * @param collectionName the name of the MongoDB collection to export
     * @param outputFile     the path to the output file (will be created or overwritten)
     * @param worldId        optional worldId filter (null or "*" for all worlds)
     * @return ExportResult containing statistics about the export
     * @throws IOException if file operations fail
     */
    public ExportResult exportCollection(String collectionName, Path outputFile, String worldId) throws IOException {
        log.info("Starting export of collection '{}' to file: {} (worldId: {})", collectionName, outputFile, worldId == null || "*".equals(worldId) ? "all" : worldId);

        // Ensure parent directory exists
        Files.createDirectories(outputFile.getParent());

        long startTime = System.currentTimeMillis();
        int totalCount = 0;
        int successCount = 0;
        int errorCount = 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile.toFile()))) {
            // Fetch documents directly from MongoDB to preserve all fields including _schema
            List<Document> documents;
            if (worldId == null || "*".equals(worldId)) {
                documents = mongoTemplate.findAll(Document.class, collectionName);
            } else {
                Query query = new Query(Criteria.where("worldId").is(worldId));
                documents = mongoTemplate.find(query, Document.class, collectionName);
            }
            totalCount = documents.size();

            log.debug("Found {} documents in collection '{}'", totalCount, collectionName);

            // Write each document as one line (JSON)
            for (Document document : documents) {
                try {
                    String json = document.toJson();
                    writer.write(json);
                    writer.newLine();
                    successCount++;

                    if (successCount % 1000 == 0) {
                        log.debug("Exported {} / {} documents", successCount, totalCount);
                    }
                } catch (Exception e) {
                    log.error("Failed to export document: {}", document.get("_id"), e);
                    errorCount++;
                }
            }

            writer.flush();
        }

        long duration = System.currentTimeMillis() - startTime;

        ExportResult result = ExportResult.builder()
                .collectionName(collectionName)
                .totalCount(totalCount)
                .successCount(successCount)
                .errorCount(errorCount)
                .durationMs(duration)
                .outputFile(outputFile.toString())
                .build();

        log.info("Export completed: {} - {} entities exported, {} errors in {} ms",
                collectionName, successCount, errorCount, duration);

        return result;
    }

    /**
     * Result of an export operation.
     */
    @lombok.Builder
    @lombok.Data
    public static class ExportResult {
        private String collectionName;
        private int totalCount;
        private int successCount;
        private int errorCount;
        private long durationMs;
        private String outputFile;

        public boolean hasErrors() {
            return errorCount > 0;
        }

        public boolean isSuccess() {
            return successCount > 0 && errorCount == 0;
        }
    }
}
