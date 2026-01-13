package de.mhus.nimbus.shared.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for raw MongoDB document operations without Java object deserialization.
 * Allows reading and writing MongoDB documents as JSON strings.
 *
 * <p>This is useful for schema migrations where you want to transform documents
 * without loading them into entity objects.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Load document as JSON string
 * String json = rawDocumentService.findDocumentById("users", "507f1f77bcf86cd799439011");
 *
 * // Modify JSON string
 * String modified = modifyJson(json);
 *
 * // Save back to MongoDB
 * rawDocumentService.replaceDocument("users", "507f1f77bcf86cd799439011", modified);
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MongoRawDocumentService {

    private final MongoTemplate mongoTemplate;

    /**
     * Finds a document by ID and returns it as a JSON string.
     *
     * @param collectionName the MongoDB collection name
     * @param id the document ID (ObjectId as string or direct ID)
     * @return the document as JSON string, or null if not found
     */
    public String findDocumentById(String collectionName, String id) {
        Query query;
        if (ObjectId.isValid(id)) {
            query = new Query(Criteria.where("_id").is(new ObjectId(id)));
        } else {
            query = new Query(Criteria.where("_id").is(id));
        }

        Document document = mongoTemplate.findOne(query, Document.class, collectionName);
        if (document == null) {
            log.debug("Document not found in {}: {}", collectionName, id);
            return null;
        }

        return document.toJson();
    }

    /**
     * Finds all documents in a collection and returns them as JSON strings.
     *
     * @param collectionName the MongoDB collection name
     * @return list of documents as JSON strings
     */
    public List<String> findAllDocuments(String collectionName) {
        List<Document> documents = mongoTemplate.findAll(Document.class, collectionName);
        List<String> results = new ArrayList<>();

        for (Document doc : documents) {
            results.add(doc.toJson());
        }

        log.debug("Found {} documents in {}", results.size(), collectionName);
        return results;
    }

    /**
     * Finds all documents in a collection matching a filter.
     *
     * @param collectionName the MongoDB collection name
     * @param query the Spring Data MongoDB Query
     * @return list of documents as JSON strings
     */
    public List<String> findDocuments(String collectionName, Query query) {
        List<Document> documents = mongoTemplate.find(query, Document.class, collectionName);
        List<String> results = new ArrayList<>();

        for (Document doc : documents) {
            results.add(doc.toJson());
        }

        log.debug("Found {} documents in {} matching filter", results.size(), collectionName);
        return results;
    }

    /**
     * Finds documents without a specific field (e.g., documents without _schema field).
     *
     * @param collectionName the MongoDB collection name
     * @param fieldName the field name to check
     * @return list of documents as JSON strings
     */
    public List<String> findDocumentsWithoutField(String collectionName, String fieldName) {
        Query query = new Query(Criteria.where(fieldName).exists(false));
        return findDocuments(collectionName, query);
    }

    /**
     * Finds documents where a field has a specific value.
     *
     * @param collectionName the MongoDB collection name
     * @param fieldName the field name
     * @param value the field value
     * @return list of documents as JSON strings
     */
    public List<String> findDocumentsByField(String collectionName, String fieldName, Object value) {
        Query query = new Query(Criteria.where(fieldName).is(value));
        return findDocuments(collectionName, query);
    }

    /**
     * Replaces a document by ID with new JSON content.
     *
     * @param collectionName the MongoDB collection name
     * @param id the document ID
     * @param jsonContent the new document content as JSON string
     * @return true if the document was replaced, false if not found
     */
    public boolean replaceDocument(String collectionName, String id, String jsonContent) {
        Document newDocument = Document.parse(jsonContent);

        Query query;
        if (ObjectId.isValid(id)) {
            query = new Query(Criteria.where("_id").is(new ObjectId(id)));
            // Ensure _id is correct in the document
            newDocument.put("_id", new ObjectId(id));
        } else {
            query = new Query(Criteria.where("_id").is(id));
            newDocument.put("_id", id);
        }

        // Remove the old document and insert the new one
        Document oldDocument = mongoTemplate.findAndRemove(query, Document.class, collectionName);
        if (oldDocument != null) {
            mongoTemplate.insert(newDocument, collectionName);
            log.debug("Replaced document in {}: {}", collectionName, id);
            return true;
        }

        log.debug("Document not found for replacement in {}: {}", collectionName, id);
        return false;
    }

    /**
     * Inserts a new document from JSON content.
     *
     * @param collectionName the MongoDB collection name
     * @param jsonContent the document content as JSON string
     * @return the ID of the inserted document
     */
    public String insertDocument(String collectionName, String jsonContent) {
        Document document = Document.parse(jsonContent);
        mongoTemplate.insert(document, collectionName);

        Object id = document.get("_id");
        log.debug("Inserted document in {}: {}", collectionName, id);
        return id != null ? id.toString() : null;
    }

    /**
     * Deletes a document by ID.
     *
     * @param collectionName the MongoDB collection name
     * @param id the document ID
     * @return true if the document was deleted, false if not found
     */
    public boolean deleteDocument(String collectionName, String id) {
        Query query;
        if (ObjectId.isValid(id)) {
            query = new Query(Criteria.where("_id").is(new ObjectId(id)));
        } else {
            query = new Query(Criteria.where("_id").is(id));
        }

        Document deleted = mongoTemplate.findAndRemove(query, Document.class, collectionName);

        log.debug("Deleted document in {}: {} (deleted: {})", collectionName, id, deleted != null);
        return deleted != null;
    }

    /**
     * Counts documents in a collection.
     *
     * @param collectionName the MongoDB collection name
     * @return the number of documents
     */
    public long countDocuments(String collectionName) {
        return mongoTemplate.count(new Query(), collectionName);
    }

    /**
     * Counts documents matching a query.
     *
     * @param collectionName the MongoDB collection name
     * @param query the Spring Data MongoDB Query
     * @return the number of matching documents
     */
    public long countDocuments(String collectionName, Query query) {
        return mongoTemplate.count(query, collectionName);
    }

    /**
     * Extracts the document ID from a JSON string.
     *
     * @param jsonContent the document as JSON string
     * @return the document ID, or null if not found
     */
    public String extractDocumentId(String jsonContent) {
        Document document = Document.parse(jsonContent);
        Object id = document.get("_id");
        if (id == null) {
            return null;
        }

        if (id instanceof ObjectId) {
            return ((ObjectId) id).toHexString();
        }

        return id.toString();
    }

    /**
     * Lists all collection names in the current database.
     *
     * @return list of collection names
     */
    public List<String> listCollections() {
        return new ArrayList<>(mongoTemplate.getCollectionNames());
    }
}
