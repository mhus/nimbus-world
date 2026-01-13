package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.storage.StorageData;
import de.mhus.nimbus.shared.storage.StorageDataRepository;
import de.mhus.nimbus.shared.storage.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/shared/storage")
@RequiredArgsConstructor
@Slf4j
public class StorageController {

    private final StorageService storageService;
    private final StorageDataRepository storageDataRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * List storage entries (grouped by UUID, showing only final chunks)
     *
     * @param query  Search query (optional, searches in uuid, path, schema, worldId)
     * @param offset Pagination offset (default: 0)
     * @param limit  Pagination limit (default: 50, max: 100)
     * @return Paginated list of storage entries
     */
    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        // Limit max page size to avoid performance issues
        if (limit > 100) limit = 100;
        if (limit < 1) limit = 1;
        if (offset < 0) offset = 0;

        // Build MongoDB query for final chunks only
        Query mongoQuery = new Query();
        mongoQuery.addCriteria(Criteria.where("isFinal").is(true));

        // Add search criteria if query provided
        if (query != null && !query.trim().isEmpty()) {
            String searchTerm = query.trim();
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("uuid").regex(searchTerm, "i"),
                    Criteria.where("path").regex(searchTerm, "i"),
                    Criteria.where("schema").regex(searchTerm, "i"),
                    Criteria.where("worldId").regex(searchTerm, "i")
            );
            mongoQuery.addCriteria(searchCriteria);
        }

        // Sort by createdAt descending (newest first)
        mongoQuery.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        // Count total matching documents
        long total = mongoTemplate.count(mongoQuery, StorageData.class);

        // Apply pagination
        mongoQuery.skip(offset).limit(limit);

        // Execute query
        List<StorageData> storageList = mongoTemplate.find(mongoQuery, StorageData.class);

        // Build response DTOs
        List<Map<String, Object>> items = new ArrayList<>();
        for (StorageData storage : storageList) {
            Map<String, Object> item = new HashMap<>();
            item.put("uuid", storage.getUuid());
            item.put("schema", storage.getSchema());
            item.put("schemaVersion", storage.getSchemaVersion());
            item.put("worldId", storage.getWorldId());
            item.put("path", storage.getPath());
            item.put("size", storage.getSize());
            item.put("createdAt", storage.getCreatedAt());
            items.add(item);
        }

        // Build paginated response
        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("count", total);
        response.put("offset", offset);
        response.put("limit", limit);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/info/{id}")
    public ResponseEntity<?> getInfo(@PathVariable String id) {
        var info = storageService.info(id);
        if (info == null) {
            return ResponseEntity.status(HttpStatusCode.valueOf(404))
                    .body(Map.of("error", "Storage id not found"));
        }
        return ResponseEntity.ok(Map.of(
                "id", info.id(),
                "size", info.size(),
                "createdAt", info.createdAt(),
                "worldId", info.worldId(),
                "path", info.path(),
                "schema", info.schema(),
                "schemaVersion", info.schemaVersion()
        ));
    }

    @GetMapping("/content/{id}")
    public void getContent(
            @PathVariable String id,
            HttpServletResponse response

    ) {
        var info = storageService.info(id);
        if (info == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Storage id not found");
        }
        response.setContentType(findContentType(info.path()));
        var stream = storageService.load(id);
        if (stream == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Storage content not found");
        }
        try (stream) {
            stream.transferTo(response.getOutputStream());
        } catch (Exception e) {
            log.warn("Cannot stream storage id {}", id, e);
            throw new ResponseStatusException(HttpStatusCode.valueOf(500), "Cannot stream content");
        }
    }

    private String findContentType(String path) {
        if (path == null) return "application/octet-stream";
        path = path.toLowerCase();
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg")) return "image/jpeg";
        if (path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".txt")) return "text/plain";
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".pdf")) return "application/pdf";
        if (path.endsWith(".off")) return "audio/ogg";
        return "application/octet-stream";
    }

}
