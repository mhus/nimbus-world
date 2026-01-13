package de.mhus.nimbus.world.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Configuration for import/export definitions.
 * Stored in WAnything collection with collection="external-resources".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalResourceDTO {

    /**
     * World identifier (as String).
     */
    private String worldId;

    /**
     * Filesystem path for export/import (e.g., "/data/exports/world1").
     */
    private String localPath;

    /**
     * Timestamp of last successful sync.
     */
    private Instant lastSync;

    /**
     * Status or error message from last sync operation.
     */
    private String lastSyncResult;

    /**
     * Types to sync: "asset", "backdrop", "blocktype", "model", "ground".
     * Empty list means export all types.
     */
    private List<String> types;

    /**
     * Enable automatic git pull/commit/push operations.
     */
    private boolean autoGit;

    /**
     * Git repository URL for clone/remote operations.
     * Optional, only needed if repository doesn't exist yet or for remote sync.
     */
    private String gitRepositoryUrl;

    /**
     * Git branch name (default: "main" or "master").
     */
    private String gitBranch;

    /**
     * Git username for authentication.
     */
    private String gitUsername;

    /**
     * setting key where Git password/token for authentication is stored as password.
     */
    private String gitPasswordSetting;

    /**
     * Prefix mapping for import (alt=neu).
     * Empty string key means "no prefix".
     * Example: {"":"r", "w":"s"} replaces missing prefix with "r:" and "w:" with "s:"
     */
    private Map<String, String> prefixMapping;
}
