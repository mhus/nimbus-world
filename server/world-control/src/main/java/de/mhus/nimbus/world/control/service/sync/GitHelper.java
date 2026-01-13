package de.mhus.nimbus.world.control.service.sync;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helper service for Git operations using RepositoryControl.
 * Handles Git sync based on ExternalResourceDTO configuration.
 * Credentials must be provided in the DTO or stored in SSettingsService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHelper {

    private static final String DEFAULT_BRANCH = "main";

    private final RepositoryControl repositoryControl;
    private final SSettingsService settingsService;

    /**
     * Initialize or clone repository if needed.
     *
     * @param definition ExternalResource configuration
     * @throws IOException if init/clone fails
     */
    public void initOrClone(ExternalResourceDTO definition) throws IOException {
        if (!definition.isAutoGit()) {
            return;
        }

        Path localPath = Paths.get(definition.getLocalPath());

        repositoryControl.initOrClone(
                localPath,
                definition.getGitRepositoryUrl(),
                getEffectiveBranch(definition),
                getEffectiveUsername(definition),
                getEffectivePassword(definition)
        );
    }

    /**
     * Pull latest changes from remote.
     * Uses hard reset before pull to ensure clean state.
     *
     * @param definition ExternalResource configuration
     * @throws IOException if pull fails
     */
    public void pull(ExternalResourceDTO definition) throws IOException {
        if (!definition.isAutoGit()) {
            return;
        }

        Path localPath = Paths.get(definition.getLocalPath());

        // Ensure repository exists
        if (!repositoryControl.isGitRepository(localPath)) {
            log.warn("Not a git repository, skipping pull: {}", localPath);
            return;
        }

        repositoryControl.pull(
                localPath,
                getEffectiveUsername(definition),
                getEffectivePassword(definition)
        );
    }

    /**
     * Commit all changes and push to remote.
     *
     * @param definition ExternalResource configuration
     * @param message    Commit message
     * @throws IOException if commit/push fails
     */
    public void commitAndPush(ExternalResourceDTO definition, String message) throws IOException {
        if (!definition.isAutoGit()) {
            return;
        }

        Path localPath = Paths.get(definition.getLocalPath());

        // Ensure repository exists
        if (!repositoryControl.isGitRepository(localPath)) {
            log.warn("Not a git repository, skipping commit/push: {}", localPath);
            return;
        }

        repositoryControl.commitAndPush(
                localPath,
                message,
                getEffectiveUsername(definition),
                getEffectivePassword(definition)
        );
    }

    /**
     * Reset repository to clean state.
     *
     * @param definition ExternalResource configuration
     * @throws IOException if reset fails
     */
    public void resetHard(ExternalResourceDTO definition) throws IOException {
        if (!definition.isAutoGit()) {
            return;
        }

        Path localPath = Paths.get(definition.getLocalPath());

        if (!repositoryControl.isGitRepository(localPath)) {
            log.warn("Not a git repository, skipping reset: {}", localPath);
            return;
        }

        repositoryControl.resetHard(localPath);
    }

    /**
     * Validate Git configuration and connectivity.
     *
     * @param definition ExternalResource configuration
     * @return Validation result message
     */
    public String validate(ExternalResourceDTO definition) {
        Path localPath = Paths.get(definition.getLocalPath());

        String result = repositoryControl.validate(
                localPath,
                definition.getGitRepositoryUrl(),
                getEffectiveUsername(definition),
                getEffectivePassword(definition)
        );

        // Add credential source info
        StringBuilder enhanced = new StringBuilder(result);
        enhanced.append("\n=== Credential Source ===\n");

        if (definition.getGitUsername() != null && !definition.getGitUsername().isBlank()) {
            enhanced.append("Username: ").append(definition.getGitUsername()).append("\n");
        } else {
            enhanced.append("Username: not set (anonymous or public repository)\n");
        }

        if (definition.getGitPasswordSetting() != null && !definition.getGitPasswordSetting().isBlank()) {
            enhanced.append("Password: from SSettingsService (key: ").append(definition.getGitPasswordSetting()).append(")\n");
        } else {
            enhanced.append("Password: not set (anonymous or public repository)\n");
        }

        if (definition.getGitBranch() != null && !definition.getGitBranch().isBlank()) {
            enhanced.append("Branch: ").append(definition.getGitBranch()).append("\n");
        } else {
            enhanced.append("Branch: ").append(DEFAULT_BRANCH).append(" (default)\n");
        }

        return enhanced.toString();
    }

    /**
     * Get effective username from DTO.
     * Returns null if not provided (anonymous access or no authentication needed).
     */
    private String getEffectiveUsername(ExternalResourceDTO definition) {
        if (definition.getGitUsername() != null && !definition.getGitUsername().isBlank()) {
            return definition.getGitUsername();
        }
        return null;
    }

    /**
     * Get effective password from SSettingsService.
     * The gitPassword field in DTO is used as a key to read the encrypted password from SSettingsService.
     * Returns null if not provided (anonymous access or no authentication needed).
     */
    private String getEffectivePassword(ExternalResourceDTO definition) {
        if (definition.getGitPasswordSetting() != null && !definition.getGitPasswordSetting().isBlank()) {
            // gitPassword is the key to read from SSettingsService
            String decryptedPassword = settingsService.getDecryptedPassword(definition.getGitPasswordSetting());
            if (decryptedPassword != null) {
                return decryptedPassword;
            }
            log.warn("No encrypted password found in SSettingsService for key '{}'", definition.getGitPasswordSetting());
        }
        return null;
    }

    /**
     * Get effective branch from DTO or use default.
     */
    private String getEffectiveBranch(ExternalResourceDTO definition) {
        if (definition.getGitBranch() != null && !definition.getGitBranch().isBlank()) {
            return definition.getGitBranch();
        }
        return DEFAULT_BRANCH;
    }
}

