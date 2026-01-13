package de.mhus.nimbus.world.control.service.sync;

import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transforms MongoDB documents during import.
 * Handles worldId replacement and prefix mapping for asset/texture paths.
 */
@Component
@Slf4j
public class DocumentTransformer {

    /**
     * Transform document for import.
     * - Replaces worldId with target worldId from DTO
     * - Applies prefix mapping to relevant fields
     *
     * @param doc        MongoDB document
     * @param definition ExternalResourceDTO with worldId and prefixMapping
     * @return Transformed document
     */
    public Document transformForImport(Document doc, ExternalResourceDTO definition) {
        // Clone document to avoid modifying original
        Document transformed = new Document(doc);

        // Replace worldId
        if (definition.getWorldId() != null) {
            transformed.put("worldId", definition.getWorldId());
        }

        // Apply prefix mapping if configured
        if (definition.getPrefixMapping() != null && !definition.getPrefixMapping().isEmpty()) {
            applyPrefixMapping(transformed, definition.getPrefixMapping());
        }

        return transformed;
    }

    /**
     * Apply prefix mapping to document fields.
     * Recursively processes nested documents and arrays.
     * Tracks context to avoid mapping fields like SAsset.path.
     */
    @SuppressWarnings("unchecked")
    private void applyPrefixMapping(Object obj, Map<String, String> prefixMapping) {
        applyPrefixMappingWithContext(obj, prefixMapping, null);
    }

    @SuppressWarnings("unchecked")
    private void applyPrefixMappingWithContext(Object obj, Map<String, String> prefixMapping, String parentKey) {
        if (obj == null) {
            return;
        }

        if (obj instanceof Document doc) {
            String entityType = doc.getString("_class");

            // Process all document fields
            for (String key : doc.keySet()) {
                Object value = doc.get(key);

                // Apply to specific known fields
                if (shouldMapField(key, parentKey, entityType) && value instanceof String) {
                    String mappedValue = applyPrefixToPath((String) value, prefixMapping);
                    if (!mappedValue.equals(value)) {
                        doc.put(key, mappedValue);
                        log.debug("Prefix mapped {}: {} -> {}", key, value, mappedValue);
                    }
                } else if (value instanceof Document || value instanceof java.util.List || value instanceof Map) {
                    // Recursively process nested structures
                    applyPrefixMappingWithContext(value, prefixMapping, key);
                }
            }
        } else if (obj instanceof java.util.List list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Document || item instanceof java.util.List || item instanceof Map) {
                    applyPrefixMappingWithContext(item, prefixMapping, parentKey);
                } else if (item instanceof String && list instanceof java.util.ArrayList) {
                    // For string lists that might be paths (only in specific contexts)
                    if (parentKey != null && (parentKey.equals("textures") || parentKey.contains("texture"))) {
                        String mappedValue = applyPrefixToPath((String) item, prefixMapping);
                        if (!mappedValue.equals(item)) {
                            ((java.util.ArrayList<Object>) list).set(i, mappedValue);
                        }
                    }
                }
            }
        } else if (obj instanceof Map map) {
            for (Object key : map.keySet()) {
                Object value = map.get(key);

                if (value instanceof String) {
                    // Check if this map entry should be mapped (considering parentKey and key)
                    if (shouldMapMapEntry(key.toString(), parentKey)) {
                        String mappedValue = applyPrefixToPath((String) value, prefixMapping);
                        if (!mappedValue.equals(value)) {
                            map.put(key, mappedValue);
                            log.debug("Prefix mapped {}[{}]: {} -> {}", parentKey, key, value, mappedValue);
                        }
                    }
                } else if (value instanceof Document || value instanceof java.util.List || value instanceof Map) {
                    applyPrefixMappingWithContext(value, prefixMapping, key.toString());
                }
            }
        }
    }

    /**
     * Check if field should be mapped based on field name and context.
     * Excludes SAsset.path from mapping.
     *
     * @param fieldName  Field name to check
     * @param parentKey  Parent field name (for context)
     * @param entityType Entity type (_class field), null if not in root document
     * @return true if field should be prefix-mapped
     */
    private boolean shouldMapField(String fieldName, String parentKey, String entityType) {
        // Never map 'path' in SAsset documents
        if ("de.mhus.nimbus.world.shared.world.SAsset".equals(entityType) && "path".equals(fieldName)) {
            return false;
        }

        // Map audio.path
        return fieldName.equals("path") && parentKey != null && parentKey.equals("audio");
    }

    /**
     * Check if a map entry should be mapped based on key and parent context.
     * Special handling for textures map where keys are numeric strings.
     *
     * @param key       Map key
     * @param parentKey Parent field name
     * @return true if map entry value should be prefix-mapped
     */
    private boolean shouldMapMapEntry(String key, String parentKey) {
        // If parent is 'textures' and key is numeric, map the value
        if ("textures".equals(parentKey)) {
            try {
                Integer.parseInt(key);
                return true; // Numeric key in textures map
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }


    /**
     * Apply prefix mapping to a path string.
     *
     * @param path          Original path (e.g., "some/path", "w:some/path", ":some/path")
     * @param prefixMapping Mapping rules (old -> new)
     * @return Mapped path
     */
    private String applyPrefixToPath(String path, Map<String, String> prefixMapping) {
        if (path == null || path.isBlank()) {
            return path;
        }

        // Extract current prefix
        String currentPrefix;
        String pathWithoutPrefix;

        if (path.contains(":")) {
            int colonIndex = path.indexOf(":");
            currentPrefix = path.substring(0, colonIndex);
            pathWithoutPrefix = path.substring(colonIndex + 1);

            // Handle leading slash after colon
            if (pathWithoutPrefix.startsWith("/")) {
                pathWithoutPrefix = pathWithoutPrefix.substring(1);
            }
        } else {
            // No prefix
            currentPrefix = "";
            pathWithoutPrefix = path;

            // Handle leading slash
            if (pathWithoutPrefix.startsWith("/")) {
                pathWithoutPrefix = pathWithoutPrefix.substring(1);
            }
        }

        // Check if mapping exists
        String newPrefix = prefixMapping.get(currentPrefix);
        if (newPrefix == null) {
            // No mapping for this prefix, return original
            return path;
        }

        // Build new path
        if (newPrefix.isEmpty()) {
            return pathWithoutPrefix;
        } else {
            return newPrefix + ":" + pathWithoutPrefix;
        }
    }
}
