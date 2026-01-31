package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;

/**
 * DTO for importing a model JSON directly to GROUND layer terrain.
 * Used when importing large .model.json files that exceed MongoDB's 16MB limit.
 */
@GenerateTypeScript("dto")
public record ImportLayerTerrainRequest(
    String jsonData,              // Required: JSON from .model.json file
    @TypeScript(optional = true) Integer mountX,   // Required: mount point X
    @TypeScript(optional = true) Integer mountY,   // Required: mount point Y
    @TypeScript(optional = true) Integer mountZ,   // Required: mount point Z
    @TypeScript(optional = true) Boolean markChunksDirty  // Optional: default true
) {}
