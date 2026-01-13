package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

import java.util.Map;

/**
 * DTO for updating an existing WLayerModel.
 * Used for API requests and TypeScript generation.
 *
 * All fields are optional - only provided fields will be updated.
 */
@GenerateTypeScript("dto")
public record UpdateLayerModelRequest(
        @TypeScript(optional = true)
        String name,

        @TypeScript(optional = true)
        String title,

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
