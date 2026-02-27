package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentTools {

    private final WDocumentService documentService;

    @Tool(name = "list_documents", description = "List documents by worldId and collection. Returns metadata without content.")
    public Map<String, Object> listDocuments(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Collection name (e.g. 'generator_instructions', 'generator_translations')") String collection) {
        log.debug("MCP: List documents: worldId={}, collection={}", worldId, collection);

        if (Strings.isBlank(worldId) || Strings.isBlank(collection)) {
            throw new McpToolException("worldId and collection are required");
        }

        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new McpToolException("Invalid worldId: " + worldId));

        List<WDocument> docs = documentService.findByCollection(wid, collection);

        List<Map<String, Object>> results = docs.stream()
                .map(doc -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("documentId", doc.getDocumentId());
                    dto.put("name", doc.getName() != null ? doc.getName() : "");
                    dto.put("title", doc.getTitle() != null ? doc.getTitle() : "");
                    dto.put("type", doc.getType() != null ? doc.getType() : "");
                    dto.put("format", doc.getFormat() != null ? doc.getFormat() : "");
                    dto.put("createdAt", doc.getCreatedAt());
                    dto.put("updatedAt", doc.getUpdatedAt());
                    return dto;
                })
                .toList();

        return Map.of(
                "worldId", worldId,
                "collection", collection,
                "count", results.size(),
                "documents", results
        );
    }

    @Tool(name = "get_document", description = "Get a document by worldId, collection and documentId. Returns full content.")
    public Map<String, Object> getDocument(
            @ToolParam(description = "World ID (e.g. 'ymir:Mist')") String worldId,
            @ToolParam(description = "Collection name") String collection,
            @ToolParam(description = "Document ID (UUID)") String documentId) {
        log.debug("MCP: Get document: worldId={}, collection={}, documentId={}", worldId, collection, documentId);

        if (Strings.isBlank(worldId) || Strings.isBlank(collection) || Strings.isBlank(documentId)) {
            throw new McpToolException("worldId, collection, and documentId are required");
        }

        WorldId wid = WorldId.of(worldId)
                .orElseThrow(() -> new McpToolException("Invalid worldId: " + worldId));

        Optional<WDocument> docOpt = documentService.findByDocumentId(wid, collection, documentId);

        if (docOpt.isEmpty()) {
            throw new McpToolException("Document not found: worldId=" + worldId
                    + ", collection=" + collection + ", documentId=" + documentId);
        }

        WDocument doc = docOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", doc.getDocumentId());
        result.put("worldId", doc.getWorldId());
        result.put("collection", doc.getCollection());
        result.put("name", doc.getName() != null ? doc.getName() : "");
        result.put("title", doc.getTitle() != null ? doc.getTitle() : "");
        result.put("type", doc.getType() != null ? doc.getType() : "");
        result.put("format", doc.getFormat() != null ? doc.getFormat() : "");
        result.put("content", doc.getContent());
        result.put("metadata", doc.getMetadata());
        result.put("createdAt", doc.getCreatedAt());
        result.put("updatedAt", doc.getUpdatedAt());
        return result;
    }

    @Tool(name = "search_readme", description = "Search README/HowTo documents by title or content. Documents are stored in the Nimbus shared collection under collection 'mcp'.")
    public Map<String, Object> searchReadme(
            @ToolParam(description = "Search query for title or content") String query) {
        log.debug("MCP: Search readme: query={}", query);

        if (Strings.isBlank(query)) {
            throw new McpToolException("query parameter is required");
        }

        WorldId nimbusShared = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                .orElseThrow(() -> new McpToolException("Failed to create Nimbus shared worldId"));

        List<WDocument> allDocs = documentService.findByCollection(nimbusShared, "mcp");

        String queryLower = query.toLowerCase();
        List<WDocument> filtered = allDocs.stream()
                .filter(doc -> {
                    boolean matchTitle = doc.getTitle() != null &&
                            doc.getTitle().toLowerCase().contains(queryLower);
                    boolean matchContent = doc.getContent() != null &&
                            doc.getContent().toLowerCase().contains(queryLower);
                    return matchTitle || matchContent;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> results = filtered.stream()
                .map(doc -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("name", doc.getName());
                    dto.put("title", doc.getTitle());
                    dto.put("summary", doc.getSummary());
                    return dto;
                })
                .collect(Collectors.toList());

        log.debug("MCP: Found {} documents matching query '{}'", results.size(), query);

        return Map.of(
                "documents", results,
                "count", results.size(),
                "query", query
        );
    }

    @Tool(name = "get_readme", description = "Get a specific README/HowTo document by name. Returns the full document including content.")
    public Map<String, Object> getReadme(
            @ToolParam(description = "Document name (technical identifier)") String name) {
        log.debug("MCP: Get readme: name={}", name);

        if (Strings.isBlank(name)) {
            throw new McpToolException("name parameter is required");
        }

        WorldId nimbusShared = WorldId.of(WorldId.COLLECTION_SHARED, "n")
                .orElseThrow(() -> new McpToolException("Failed to create Nimbus shared worldId"));

        Optional<WDocument> docOpt = documentService.findByName(nimbusShared, "mcp", name);

        if (docOpt.isEmpty()) {
            throw new McpToolException("document not found: " + name);
        }

        WDocument doc = docOpt.get();

        Map<String, Object> result = new HashMap<>();
        result.put("name", doc.getName());
        result.put("title", doc.getTitle());
        result.put("summary", doc.getSummary());
        result.put("content", doc.getContent());
        result.put("format", doc.getFormat());
        result.put("language", doc.getLanguage());
        result.put("type", doc.getType());
        result.put("createdAt", doc.getCreatedAt());
        result.put("updatedAt", doc.getUpdatedAt());

        return result;
    }
}
