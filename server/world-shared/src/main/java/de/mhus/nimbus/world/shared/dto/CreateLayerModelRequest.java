package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

import java.util.Map;

/**
 * DTO for creating a new WLayerModel.
 * Used for API requests and TypeScript generation.
 */
@GenerateTypeScript("dto")
public record CreateLayerModelRequest(
        @TypeScript(optional = true)
        String name,

        @TypeScript(optional = true)
        String title,

        String layerDataId,

        @TypeScript(optional = true)
        Integer mountX,

        @TypeScript(optional = true)
        Integer mountY,

        @TypeScript(optional = true)
        Integer mountZ,

        @TypeScript(optional = true)
        Integer rotation,

        @TypeScript(optional = true)
        String referenceModelId,

        @TypeScript(optional = true)
        Integer order,

        @TypeScript(optional = true)
        Map<String, Integer> groups
) {
}
