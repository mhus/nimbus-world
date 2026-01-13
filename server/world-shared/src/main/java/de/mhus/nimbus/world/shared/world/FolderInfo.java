package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;

/**
 * Metadata for a virtual folder derived from asset paths.
 * Folders don't exist as entities in the database - they are computed from asset paths.
 * Example: Asset path "textures/block/stone.png" implies folders "textures" and "textures/block".
 *
 * @param path Full folder path (e.g., "textures/block")
 * @param name Just the folder name without parent path (e.g., "block")
 * @param assetCount Number of direct assets in this folder (not including subfolders)
 * @param totalAssetCount Total number of assets including all subfolders
 * @param subfolderCount Number of immediate subfolders
 * @param parentPath Parent folder path (e.g., "textures" for "textures/block", empty string for root folders)
 */
@GenerateTypeScript("entities")
public record FolderInfo(
    String path,
    String name,
    int assetCount,
    int totalAssetCount,
    int subfolderCount,
    String parentPath
) {}
