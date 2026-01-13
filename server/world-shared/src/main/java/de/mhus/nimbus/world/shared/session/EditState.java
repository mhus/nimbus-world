package de.mhus.nimbus.world.shared.session;

import de.mhus.nimbus.generated.types.EditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Edit state for a session.
 * Stored in Redis for sharing across pods.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditState {

    /**
     * Edit mode enabled flag.
     */
    @Builder.Default
    private boolean editMode = false;

    /**
     * Current edit action (default: OPEN_CONFIG_DIALOG).
     */
    private EditAction editAction;

    /**
     * Selected layer name for editing (null = legacy mode, all layers).
     */
    private String selectedLayer;

    /**
     * Layer data ID for the selected layer (cached from WLayer.layerDataId).
     * This is the ID used for WEditCache operations.
     */
    private String layerDataId;

    /**
     * Selected model ID for editing (only for MODEL type layers).
     * When a MODEL layer is selected, this specifies which WLayerModel to edit.
     */
    private String selectedModelId;

    /**
     * Model name for MODEL type layers (cached from WLayerModel.name).
     * This is used for WEditCache operations with MODEL layers.
     */
    private String modelName;

    /**
     * Mount point X coordinate (for ModelLayer editing).
     * @deprecated Use selectedModelId instead - mount points are now in WLayerModel
     */
    @Deprecated
    private Integer mountX;

    /**
     * Mount point Y coordinate (for ModelLayer editing).
     * @deprecated Use selectedModelId instead - mount points are now in WLayerModel
     */
    @Deprecated
    private Integer mountY;

    /**
     * Mount point Z coordinate (for ModelLayer editing).
     * @deprecated Use selectedModelId instead - mount points are now in WLayerModel
     */
    @Deprecated
    private Integer mountZ;

    /**
     * Selected group number (default: 0 = no group).
     */
    @Builder.Default
    private int selectedGroup = 0;

    /**
     * Last update timestamp.
     */
    private Instant lastUpdated;

    /**
     * World ID (for validation).
     */
    private String worldId;

}
