package de.mhus.nimbus.world.control.service.sync;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Helper methods for document transformation and lookup.
 * Used by ResourceSyncType implementations to handle unique constraints correctly.
 */
public class DocumentHelper {

    /**
     * Find existing document by unique constraint fields.
     * Updates or creates new document respecting unique constraints.
     *
     * @param mongoTemplate MongoDB template
     * @param doc           Document to save
     * @param collection    Collection name
     * @param fields        Field names that form the unique constraint
     * @return Document ready to save (with correct _id)
     */
    public static Document prepareDocumentForSave(
            MongoTemplate mongoTemplate,
            Document doc,
            String collection,
            String... fields) {

        // Build query for unique constraint fields
        Criteria criteria = new Criteria();
        for (String field : fields) {
            Object value = doc.get(field);
            criteria.and(field).is(value);
        }

        Query query = new Query(criteria);
        Document existing = mongoTemplate.findOne(query, Document.class, collection);

        if (existing != null) {
            // Use existing _id to update in place
            doc.put("_id", existing.get("_id"));
        } else {
            // Remove _id to let MongoDB generate a new one
            doc.remove("_id");
        }

        return doc;
    }

    /**
     * Check if document should be skipped based on timestamp comparison.
     *
     * @param existing       Existing document in database
     * @param imported       Imported document from file
     * @param timestampField Field name for timestamp comparison (e.g., "updatedAt", "createdAt")
     * @return true if should skip import (DB is newer), false otherwise
     */
    public static boolean shouldSkipImport(Document existing, Document imported, String timestampField) {
        if (existing == null) {
            return false;
        }

        Object fileTimestamp = imported.get(timestampField);
        Object dbTimestamp = existing.get(timestampField);

        if (fileTimestamp == null || dbTimestamp == null) {
            return false;
        }

        // Compare as strings (ISO timestamps)
        return dbTimestamp.toString().compareTo(fileTimestamp.toString()) > 0;
    }
}
