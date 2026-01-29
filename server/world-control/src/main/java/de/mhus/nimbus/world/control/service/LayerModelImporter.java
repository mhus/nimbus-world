package de.mhus.nimbus.world.control.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerModel;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class for importing WLayerModel from JSON data (e.g., from schematic-tool).
 * Uses builder pattern for configuration.
 *
 * Example usage:
 * <pre>
 * LayerModelImporter.builder()
 *     .layerService(layerService)
 *     .objectMapper(objectMapper)
 *     .worldId("earth616")
 *     .layerDataId("layer-uuid")
 *     .jsonData(jsonString)
 *     .build()
 *     .importModel();
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class LayerModelImporter {

    // Required dependencies
    private final WLayerService layerService;
    private final ObjectMapper objectMapper;

    // Import configuration
    private String worldId;
    private String layerDataId;
    private String jsonData;
    private WLayerModel modelData;

    // Optional overrides
    private String name;
    private String title;
    private Integer mountX;
    private Integer mountY;
    private Integer mountZ;
    private Integer rotation;
    private Integer order;
    private Map<String, String> groups;

    /**
     * Create a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Import the layer model.
     * Parses JSON data, applies overrides, and creates WLayerModel via service.
     *
     * @return Created WLayerModel
     * @throws IllegalStateException if import fails
     */
    public WLayerModel importModel() {
        validateConfiguration();

        try {
            // Parse JSON to WLayerModel if provided
            WLayerModel sourceModel;
            if (jsonData != null) {
                // Configure ObjectMapper to ignore unknown fields (for flexibility with different JSON formats)
                com.fasterxml.jackson.databind.ObjectReader reader = objectMapper
                        .readerFor(WLayerModel.class)
                        .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

                sourceModel = reader.readValue(jsonData);
                log.debug("Parsed layer model from JSON: name={} blocks={}",
                        sourceModel.getName(), sourceModel.getContent() != null ? sourceModel.getContent().size() : 0);
            } else if (modelData != null) {
                sourceModel = modelData;
                log.debug("Using provided layer model: name={} blocks={}",
                        sourceModel.getName(), sourceModel.getContent() != null ? sourceModel.getContent().size() : 0);
            } else {
                throw new IllegalStateException("Either jsonData or modelData must be provided");
            }

            // Verify layer exists and is MODEL type
            Optional<WLayer> layerOpt = layerService.findByWorldIdAndLayerDataId(worldId, layerDataId);
            if (layerOpt.isEmpty()) {
                throw new IllegalStateException("Layer not found: worldId=" + worldId + " layerDataId=" + layerDataId);
            }
            WLayer layer = layerOpt.get();
            if (layer.getLayerType() != de.mhus.nimbus.world.shared.layer.LayerType.MODEL) {
                throw new IllegalStateException("Layer is not MODEL type: " + layer.getLayerType());
            }

            // Apply overrides or use values from source model
            String finalName = name != null ? name : sourceModel.getName();
            String finalTitle = title != null ? title : sourceModel.getTitle();
            int finalMountX = mountX != null ? mountX : sourceModel.getMountX();
            int finalMountY = mountY != null ? mountY : sourceModel.getMountY();
            int finalMountZ = mountZ != null ? mountZ : sourceModel.getMountZ();
            int finalRotation = rotation != null ? rotation : sourceModel.getRotation();
            int finalOrder = order != null ? order : sourceModel.getOrder();
            Map<String, String> finalGroups = groups != null ? groups :
                    (sourceModel.getGroups() != null ? sourceModel.getGroups() : new HashMap<>());

            // Content must come from source model
            List<LayerBlock> content = sourceModel.getContent();
            if (content == null) {
                content = new ArrayList<>();
            }

            // Create model via service
            WLayerModel created = layerService.createModel(
                    worldId,
                    layerDataId,
                    finalName,
                    finalTitle,
                    finalMountX,
                    finalMountY,
                    finalMountZ,
                    finalRotation,
                    finalOrder,
                    content
            );

            // Set groups separately if needed
            if (!finalGroups.isEmpty()) {
                layerService.updateModel(created.getId(), model -> model.setGroups(finalGroups));
            }

            log.info("Imported layer model: id={} name={} blocks={} worldId={} layerDataId={}",
                    created.getId(), created.getName(), content.size(), worldId, layerDataId);

            return created;

        } catch (IOException e) {
            log.error("Failed to parse JSON data for layer model import", e);
            throw new IllegalStateException("Failed to parse JSON data: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to import layer model: worldId={} layerDataId={}", worldId, layerDataId, e);
            throw new IllegalStateException("Failed to import layer model: " + e.getMessage(), e);
        }
    }

    /**
     * Validate configuration before import.
     */
    private void validateConfiguration() {
        if (layerService == null) {
            throw new IllegalStateException("WLayerService is required");
        }
        if (objectMapper == null) {
            throw new IllegalStateException("ObjectMapper is required");
        }
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalStateException("worldId is required");
        }
        if (layerDataId == null || layerDataId.isBlank()) {
            throw new IllegalStateException("layerDataId is required");
        }
        if (jsonData == null && modelData == null) {
            throw new IllegalStateException("Either jsonData or modelData is required");
        }
    }

    /**
     * Builder for LayerModelImporter.
     */
    public static class Builder {
        private WLayerService layerService;
        private ObjectMapper objectMapper;
        private String worldId;
        private String layerDataId;
        private String jsonData;
        private WLayerModel modelData;
        private String name;
        private String title;
        private Integer mountX;
        private Integer mountY;
        private Integer mountZ;
        private Integer rotation;
        private Integer order;
        private Map<String, String> groups;

        public Builder layerService(WLayerService layerService) {
            this.layerService = layerService;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder worldId(String worldId) {
            this.worldId = worldId;
            return this;
        }

        public Builder layerDataId(String layerDataId) {
            this.layerDataId = layerDataId;
            return this;
        }

        /**
         * Set JSON data to import (from schematic-tool output).
         */
        public Builder jsonData(String jsonData) {
            this.jsonData = jsonData;
            return this;
        }

        /**
         * Set model data to import directly.
         */
        public Builder modelData(WLayerModel modelData) {
            this.modelData = modelData;
            return this;
        }

        /**
         * Override model name.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Override model title.
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Override mount X coordinate.
         */
        public Builder mountX(int mountX) {
            this.mountX = mountX;
            return this;
        }

        /**
         * Override mount Y coordinate.
         */
        public Builder mountY(int mountY) {
            this.mountY = mountY;
            return this;
        }

        /**
         * Override mount Z coordinate.
         */
        public Builder mountZ(int mountZ) {
            this.mountZ = mountZ;
            return this;
        }

        /**
         * Override rotation (in 90 degree steps: 0-3).
         */
        public Builder rotation(int rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * Override order.
         */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /**
         * Override groups mapping.
         */
        public Builder groups(Map<String, String> groups) {
            this.groups = groups;
            return this;
        }

        /**
         * Build the importer instance.
         */
        public LayerModelImporter build() {
            LayerModelImporter importer = new LayerModelImporter(layerService, objectMapper);
            importer.worldId = this.worldId;
            importer.layerDataId = this.layerDataId;
            importer.jsonData = this.jsonData;
            importer.modelData = this.modelData;
            importer.name = this.name;
            importer.title = this.title;
            importer.mountX = this.mountX;
            importer.mountY = this.mountY;
            importer.mountZ = this.mountZ;
            importer.rotation = this.rotation;
            importer.order = this.order;
            importer.groups = this.groups;
            return importer;
        }
    }
}
