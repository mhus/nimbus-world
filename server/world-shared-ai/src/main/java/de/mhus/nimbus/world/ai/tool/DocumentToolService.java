package de.mhus.nimbus.world.ai.tool;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentMetadata;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI Tool Service for document management.
 * Provides AI agents with tools to search, read, create, and update documents.
 *
 * Both worldId and collection parameters must always be provided by the agent for each tool call.
 */
@Slf4j
@Service
public class DocumentToolService {

    private final WDocumentService documentService;

    public DocumentToolService(WDocumentService documentService) {
        this.documentService = documentService;
        log.info("DocumentToolService created");
    }

    // ========== Summary Search Methods ==========

    /**
     * Search documents by their summaries in the specific world and collection.
     * Returns a list of document metadata (without content) where the summary contains the search term.
     * Use this to find relevant documents based on their summary information.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection to search in (required)
     * @param searchTerm The term to search for in document summaries
     * @return Formatted list of matching documents with their summaries
     */
    @Tool("Search documents by summary - finds documents where the summary contains the search term. Returns document ID, title, and summary.")
    public String searchDocumentsBySummary(String worldId, String collection, String searchTerm) {
        log.info("AI Tool: searchDocumentsBySummary - worldId={}, collection={}, searchTerm={}", worldId, collection, searchTerm);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(searchTerm)) {
            return "ERROR: searchTerm parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WDocumentMetadata> documents = documentService.findMetadataByCollection(wid, collection);

            // Filter by search term in summary
            String lowerSearchTerm = searchTerm.toLowerCase();
            List<WDocumentMetadata> matching = documents.stream()
                    .filter(doc -> doc.getSummary() != null &&
                                   doc.getSummary().toLowerCase().contains(lowerSearchTerm))
                    .collect(Collectors.toList());

            if (matching.isEmpty()) {
                return String.format("No documents found in collection '%s' with summary containing '%s'",
                                     collection, searchTerm);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d document(s) in collection '%s':\n\n",
                                        matching.size(), collection));

            for (WDocumentMetadata doc : matching) {
                result.append(String.format("ID: %s\n", doc.getDocumentId()));
                result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
                result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));
                result.append(String.format("Type: %s\n", doc.getType() != null ? doc.getType() : "(no type)"));
                result.append(String.format("Summary: %s\n", doc.getSummary()));
                result.append("---\n\n");
            }

            log.info("AI Tool: searchDocumentsBySummary - found {} documents", matching.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: searchDocumentsBySummary failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Lookup documents by their summaries in world, region, and shared collections.
     * Searches not only in the specific world, but also in @region:<id> and @shared:n collections.
     * Returns a list of document metadata (without content) where the summary contains the search term.
     * Use this to find relevant documents across multiple scopes.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection to search in (required)
     * @param searchTerm The term to search for in document summaries
     * @return Formatted list of matching documents with their summaries
     */
    @Tool("Lookup documents by summary - searches in world, region, and shared collections. Finds documents where the summary contains the search term.")
    public String lookupDocumentsBySummary(String worldId, String collection, String searchTerm) {
        log.info("AI Tool: lookupDocumentsBySummary - worldId={}, collection={}, searchTerm={}", worldId, collection, searchTerm);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(searchTerm)) {
            return "ERROR: searchTerm parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WDocumentMetadata> documents = documentService.lookupDocumentsMetadata(wid, collection);

            // Filter by search term in summary
            String lowerSearchTerm = searchTerm.toLowerCase();
            List<WDocumentMetadata> matching = documents.stream()
                    .filter(doc -> doc.getSummary() != null &&
                                   doc.getSummary().toLowerCase().contains(lowerSearchTerm))
                    .collect(Collectors.toList());

            if (matching.isEmpty()) {
                return String.format("No documents found in collection '%s' (including region and shared) with summary containing '%s'",
                                     collection, searchTerm);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d document(s) in collection '%s' (including region and shared):\n\n",
                                        matching.size(), collection));

            for (WDocumentMetadata doc : matching) {
                result.append(String.format("World: %s\n", doc.getWorldId()));
                result.append(String.format("ID: %s\n", doc.getDocumentId()));
                result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
                result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));
                result.append(String.format("Type: %s\n", doc.getType() != null ? doc.getType() : "(no type)"));
                result.append(String.format("Summary: %s\n", doc.getSummary()));
                result.append("---\n\n");
            }

            log.info("AI Tool: lookupDocumentsBySummary - found {} documents", matching.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: lookupDocumentsBySummary failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== Content Search Methods ==========

    /**
     * Search documents by their content in the specific world and collection.
     * Returns a list of documents where the content contains the search term.
     * Use this to find documents with specific content or extract information.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection to search in (required)
     * @param searchTerm The term to search for in document content
     * @return Formatted list of matching documents with excerpts
     */
    @Tool("Search documents by content - finds documents where the content contains the search term. Returns document ID, title, and content excerpt.")
    public String searchDocumentsByContent(String worldId, String collection, String searchTerm) {
        log.info("AI Tool: searchDocumentsByContent - worldId={}, collection={}, searchTerm={}", worldId, collection, searchTerm);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(searchTerm)) {
            return "ERROR: searchTerm parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WDocument> documents = documentService.findByCollection(wid, collection);

            // Filter by search term in content
            String lowerSearchTerm = searchTerm.toLowerCase();
            List<WDocument> matching = documents.stream()
                    .filter(doc -> doc.getContent() != null &&
                                   doc.getContent().toLowerCase().contains(lowerSearchTerm))
                    .collect(Collectors.toList());

            if (matching.isEmpty()) {
                return String.format("No documents found in collection '%s' with content containing '%s'",
                                     collection, searchTerm);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d document(s) in collection '%s':\n\n",
                                        matching.size(), collection));

            for (WDocument doc : matching) {
                result.append(String.format("ID: %s\n", doc.getDocumentId()));
                result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
                result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));

                // Extract excerpt around search term (up to 200 chars before and after)
                String content = doc.getContent();
                int index = content.toLowerCase().indexOf(lowerSearchTerm);
                int start = Math.max(0, index - 200);
                int end = Math.min(content.length(), index + searchTerm.length() + 200);
                String excerpt = content.substring(start, end);
                if (start > 0) excerpt = "..." + excerpt;
                if (end < content.length()) excerpt = excerpt + "...";

                result.append(String.format("Excerpt: %s\n", excerpt));
                result.append("---\n\n");
            }

            log.info("AI Tool: searchDocumentsByContent - found {} documents", matching.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: searchDocumentsByContent failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Lookup documents by their content in world, region, and shared collections.
     * Searches not only in the specific world, but also in @region:<id> and @shared:n collections.
     * Returns a list of documents where the content contains the search term.
     * Use this to find documents with specific content across multiple scopes.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection to search in (required)
     * @param searchTerm The term to search for in document content
     * @return Formatted list of matching documents with excerpts
     */
    @Tool("Lookup documents by content - searches in world, region, and shared collections. Finds documents where the content contains the search term.")
    public String lookupDocumentsByContent(String worldId, String collection, String searchTerm) {
        log.info("AI Tool: lookupDocumentsByContent - worldId={}, collection={}, searchTerm={}", worldId, collection, searchTerm);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(searchTerm)) {
            return "ERROR: searchTerm parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WDocument> documents = documentService.lookupDocuments(wid, collection);

            // Filter by search term in content
            String lowerSearchTerm = searchTerm.toLowerCase();
            List<WDocument> matching = documents.stream()
                    .filter(doc -> doc.getContent() != null &&
                                   doc.getContent().toLowerCase().contains(lowerSearchTerm))
                    .collect(Collectors.toList());

            if (matching.isEmpty()) {
                return String.format("No documents found in collection '%s' (including region and shared) with content containing '%s'",
                                     collection, searchTerm);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d document(s) in collection '%s' (including region and shared):\n\n",
                                        matching.size(), collection));

            for (WDocument doc : matching) {
                result.append(String.format("World: %s\n", doc.getWorldId()));
                result.append(String.format("ID: %s\n", doc.getDocumentId()));
                result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
                result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));

                // Extract excerpt around search term (up to 200 chars before and after)
                String content = doc.getContent();
                int index = content.toLowerCase().indexOf(lowerSearchTerm);
                int start = Math.max(0, index - 200);
                int end = Math.min(content.length(), index + searchTerm.length() + 200);
                String excerpt = content.substring(start, end);
                if (start > 0) excerpt = "..." + excerpt;
                if (end < content.length()) excerpt = excerpt + "...";

                result.append(String.format("Excerpt: %s\n", excerpt));
                result.append("---\n\n");
            }

            log.info("AI Tool: lookupDocumentsByContent - found {} documents", matching.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: lookupDocumentsByContent failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== Document Retrieval Methods ==========

    /**
     * Get the full content of a specific document.
     * Use this when you need to read the complete document content.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection (required)
     * @param documentId The document identifier
     * @return Full document content with metadata
     */
    @Tool("Get full document content - retrieves the complete content and metadata of a specific document by its ID.")
    public String getDocument(String worldId, String collection, String documentId) {
        log.info("AI Tool: getDocument - worldId={}, collection={}, documentId={}", worldId, collection, documentId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(documentId)) {
            return "ERROR: documentId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            Optional<WDocument> docOpt = documentService.findByDocumentId(wid, collection, documentId);

            if (docOpt.isEmpty()) {
                return String.format("ERROR: Document not found - collection='%s', documentId='%s'",
                                     collection, documentId);
            }

            WDocument doc = docOpt.get();
            StringBuilder result = new StringBuilder();
            result.append(String.format("Document ID: %s\n", doc.getDocumentId()));
            result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
            result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));
            result.append(String.format("Type: %s\n", doc.getType() != null ? doc.getType() : "(no type)"));
            result.append(String.format("Language: %s\n", doc.getLanguage() != null ? doc.getLanguage() : "(no language)"));
            result.append(String.format("Format: %s\n", doc.getFormat() != null ? doc.getFormat() : "plaintext"));
            result.append(String.format("Summary: %s\n", doc.getSummary() != null ? doc.getSummary() : "(no summary)"));
            result.append("\n--- Content ---\n\n");
            result.append(doc.getContent() != null ? doc.getContent() : "(no content)");
            result.append("\n");

            log.info("AI Tool: getDocument - document retrieved successfully");
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: getDocument failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Create a new document in the specified collection.
     * Use this to add new documents with content.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection (required)
     * @param title The document title
     * @param content The document content
     * @param summary Optional summary (if null, should be generated separately)
     * @param type Optional document type (e.g., 'lore', 'quest', 'item')
     * @return Success message with created document ID or error message
     */
    @Tool("Create new document - creates a new document with the specified title, content, and optional metadata. Returns the created document ID.")
    public String createDocument(String worldId, String collection, String title, String content, String summary, String type) {
        log.info("AI Tool: createDocument - worldId={}, collection={}, title={}", worldId, collection, title);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(title)) {
            return "ERROR: title parameter is required";
        }

        if (Strings.isBlank(content)) {
            return "ERROR: content parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );

            // Generate document ID
            String documentId = java.util.UUID.randomUUID().toString();

            WDocument created = documentService.save(wid, collection, documentId, doc -> {
                doc.setTitle(title);
                doc.setContent(content);
                if (!Strings.isBlank(summary)) {
                    doc.setSummary(summary);
                }
                if (!Strings.isBlank(type)) {
                    doc.setType(type);
                }
                doc.setFormat("plaintext");
            });

            log.info("AI Tool: createDocument - document created successfully: {}", documentId);
            return String.format("SUCCESS: Document created with ID '%s' in collection '%s'",
                                 created.getDocumentId(), collection);

        } catch (Exception e) {
            log.error("AI Tool: createDocument failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Update an existing document.
     * Use this to modify document content, title, summary, or other fields.
     * Only provide the fields you want to update - null values will not modify existing data.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection (required)
     * @param documentId The document identifier (required)
     * @param title New title (null = no change)
     * @param content New content (null = no change)
     * @param summary New summary (null = no change)
     * @param type New type (null = no change)
     * @return Success message or error message
     */
    @Tool("Update existing document - updates specified fields of an existing document. Only provide fields you want to change, others will remain unchanged.")
    public String updateDocument(String worldId, String collection, String documentId, String title, String content,
                                  String summary, String type) {
        log.info("AI Tool: updateDocument - worldId={}, collection={}, documentId={}", worldId, collection, documentId);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        if (Strings.isBlank(documentId)) {
            return "ERROR: documentId parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            Optional<WDocument> updated = documentService.update(wid, collection, documentId, doc -> {
                if (!Strings.isBlank(title)) {
                    doc.setTitle(title);
                }
                if (!Strings.isBlank(content)) {
                    doc.setContent(content);
                }
                if (!Strings.isBlank(summary)) {
                    doc.setSummary(summary);
                }
                if (!Strings.isBlank(type)) {
                    doc.setType(type);
                }
            });

            if (updated.isEmpty()) {
                return String.format("ERROR: Document not found - collection='%s', documentId='%s'",
                                     collection, documentId);
            }

            log.info("AI Tool: updateDocument - document updated successfully");
            return String.format("SUCCESS: Document '%s' updated in collection '%s'",
                                 documentId, collection);

        } catch (Exception e) {
            log.error("AI Tool: updateDocument failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * List all documents in a collection.
     * Returns metadata (without content) for all documents.
     * Use this to get an overview of available documents.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection (required)
     * @return Formatted list of all documents in the collection
     */
    @Tool("List all documents in collection - returns metadata (ID, title, summary) for all documents in the specified collection.")
    public String listDocuments(String worldId, String collection) {
        log.info("AI Tool: listDocuments - worldId={}, collection={}", worldId, collection);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WDocumentMetadata> documents = documentService.findMetadataByCollection(wid, collection);

            if (documents.isEmpty()) {
                return String.format("No documents found in collection '%s'", collection);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d document(s) in collection '%s':\n\n",
                                        documents.size(), collection));

            for (WDocumentMetadata doc : documents) {
                result.append(String.format("ID: %s\n", doc.getDocumentId()));
                result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
                result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));
                result.append(String.format("Type: %s\n", doc.getType() != null ? doc.getType() : "(no type)"));
                if (doc.getSummary() != null) {
                    result.append(String.format("Summary: %s\n", doc.getSummary()));
                }
                result.append("---\n\n");
            }

            log.info("AI Tool: listDocuments - listed {} documents", documents.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: listDocuments failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Lookup all documents in a collection across world, region, and shared collections.
     * Searches not only in the specific world, but also in @region:<id> and @shared:n collections.
     * Returns metadata (without content) for all documents.
     * Use this to get an overview of available documents across multiple scopes.
     *
     * @param worldId The world identifier (required)
     * @param collection The document collection (required)
     * @return Formatted list of all documents in the collection (including region and shared)
     */
    @Tool("Lookup all documents in collection - returns metadata for all documents in world, region, and shared collections.")
    public String lookupListDocuments(String worldId, String collection) {
        log.info("AI Tool: lookupListDocuments - worldId={}, collection={}", worldId, collection);

        if (Strings.isBlank(worldId)) {
            return "ERROR: worldId parameter is required";
        }

        if (Strings.isBlank(collection)) {
            return "ERROR: collection parameter is required";
        }

        try {
            WorldId wid = WorldId.of(worldId).orElseThrow(
                    () -> new IllegalArgumentException("Invalid worldId: " + worldId)
            );
            List<WDocumentMetadata> documents = documentService.lookupDocumentsMetadata(wid, collection);

            if (documents.isEmpty()) {
                return String.format("No documents found in collection '%s' (including region and shared)", collection);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d document(s) in collection '%s' (including region and shared):\n\n",
                                        documents.size(), collection));

            for (WDocumentMetadata doc : documents) {
                result.append(String.format("World: %s\n", doc.getWorldId()));
                result.append(String.format("ID: %s\n", doc.getDocumentId()));
                result.append(String.format("Title: %s\n", doc.getTitle() != null ? doc.getTitle() : "(no title)"));
                result.append(String.format("Name: %s\n", doc.getName() != null ? doc.getName() : "(no name)"));
                result.append(String.format("Type: %s\n", doc.getType() != null ? doc.getType() : "(no type)"));
                if (doc.getSummary() != null) {
                    result.append(String.format("Summary: %s\n", doc.getSummary()));
                }
                result.append("---\n\n");
            }

            log.info("AI Tool: lookupListDocuments - listed {} documents", documents.size());
            return result.toString();

        } catch (Exception e) {
            log.error("AI Tool: lookupListDocuments failed", e);
            return "ERROR: " + e.getMessage();
        }
    }
}
