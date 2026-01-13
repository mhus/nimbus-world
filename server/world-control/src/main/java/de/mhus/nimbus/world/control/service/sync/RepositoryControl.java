package de.mhus.nimbus.world.control.service.sync;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for Git repository control operations.
 * Provides abstraction for different Git implementations (JGit, CLI, etc.).
 */
public interface RepositoryControl {

    /**
     * Initialize or clone a Git repository.
     * If localPath exists and is a git repo, does nothing.
     * If localPath doesn't exist and repositoryUrl is provided, clones the repo.
     * If localPath exists but is not a git repo and repositoryUrl is provided, initializes and adds remote.
     *
     * @param localPath      Local filesystem path
     * @param repositoryUrl  Git repository URL (optional)
     * @param branch         Branch name (optional, defaults to main/master)
     * @param username       Git username for authentication (optional)
     * @param password       Git password/token for authentication (optional)
     * @throws IOException if initialization fails
     */
    void initOrClone(Path localPath, String repositoryUrl, String branch, String username, String password) throws IOException;

    /**
     * Pull latest changes from remote.
     * Uses hard reset to ensure clean state.
     *
     * @param localPath Local repository path
     * @param username  Git username for authentication (optional)
     * @param password  Git password/token for authentication (optional)
     * @throws IOException if pull fails
     */
    void pull(Path localPath, String username, String password) throws IOException;

    /**
     * Commit all changes and push to remote.
     * Adds all changes, commits with message, and pushes.
     *
     * @param localPath Local repository path
     * @param message   Commit message
     * @param username  Git username for authentication (optional)
     * @param password  Git password/token for authentication (optional)
     * @throws IOException if commit/push fails
     */
    void commitAndPush(Path localPath, String message, String username, String password) throws IOException;

    /**
     * Reset repository to clean state (git reset --hard HEAD).
     * Discards all local changes.
     *
     * @param localPath Local repository path
     * @throws IOException if reset fails
     */
    void resetHard(Path localPath) throws IOException;

    /**
     * Check if path is a valid Git repository.
     *
     * @param localPath Path to check
     * @return true if valid Git repository
     */
    boolean isGitRepository(Path localPath);

    /**
     * Validate Git configuration and connectivity.
     * Tests if repository can be accessed with provided credentials.
     *
     * @param localPath     Local repository path
     * @param repositoryUrl Remote repository URL (optional)
     * @param username      Git username (optional)
     * @param password      Git password/token (optional)
     * @return Validation result message
     */
    String validate(Path localPath, String repositoryUrl, String username, String password);
}
