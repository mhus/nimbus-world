package de.mhus.nimbus.world.control.service.sync;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Git repository control implementation using JGit.
 * Provides robust Git operations with hard reset strategy for data safety.
 */
@Service
@Slf4j
public class RepositoryControlWithJGit implements RepositoryControl {

    @Override
    public void initOrClone(Path localPath, String repositoryUrl, String branch, String username, String password) throws IOException {
        if (isGitRepository(localPath)) {
            log.info("Git repository already exists: {}", localPath);
            return;
        }

        if (repositoryUrl != null && !repositoryUrl.isBlank()) {
            // Clone repository
            log.info("Cloning repository: {} to {}", repositoryUrl, localPath);
            try {
                var cloneCommand = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setDirectory(localPath.toFile())
                        .setCloneAllBranches(false);

                if (branch != null && !branch.isBlank()) {
                    cloneCommand.setBranch(branch);
                }

                if (username != null && !username.isBlank() && password != null) {
                    cloneCommand.setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(username, password)
                    );
                }

                try (Git git = cloneCommand.call()) {
                    log.info("Repository cloned successfully: {}", localPath);
                }
            } catch (GitAPIException e) {
                throw new IOException("Failed to clone repository: " + e.getMessage(), e);
            }
        } else if (Files.exists(localPath)) {
            // Initialize existing directory as git repo
            log.info("Initializing git repository: {}", localPath);
            try {
                try (Git git = Git.init().setDirectory(localPath.toFile()).call()) {
                    log.info("Git repository initialized: {}", localPath);
                }
            } catch (GitAPIException e) {
                throw new IOException("Failed to initialize repository: " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Cannot init/clone: path doesn't exist and no repositoryUrl provided");
        }
    }

    @Override
    public void pull(Path localPath, String username, String password) throws IOException {
        log.info("Pulling from remote: {}", localPath);

        try (Git git = openRepository(localPath)) {
            // First: reset hard to clean state
            resetHard(localPath);

            // Then: pull with rebase
            var pullCommand = git.pull()
                    .setRebase(true);

            if (username != null && !username.isBlank() && password != null) {
                pullCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(username, password)
                );
            }

            var result = pullCommand.call();

            if (result.isSuccessful()) {
                log.info("Pull successful: {}", localPath);
            } else {
                log.warn("Pull completed with issues: {}", result);
            }
        } catch (GitAPIException e) {
            throw new IOException("Failed to pull: " + e.getMessage(), e);
        }
    }

    @Override
    public void commitAndPush(Path localPath, String message, String username, String password) throws IOException {
        log.info("Committing and pushing: {}", localPath);

        try (Git git = openRepository(localPath)) {
            // Add all changes
            git.add()
                    .addFilepattern(".")
                    .call();

            // Check if there are changes to commit
            var status = git.status().call();
            if (status.isClean()) {
                log.info("No changes to commit: {}", localPath);
                return;
            }

            // Commit
            git.commit()
                    .setMessage(message)
                    .setAllowEmpty(false)
                    .call();

            log.info("Changes committed: {}", localPath);

            // Push
            var pushCommand = git.push();

            if (username != null && !username.isBlank() && password != null) {
                pushCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(username, password)
                );
            }

            pushCommand.call();
            log.info("Changes pushed to remote: {}", localPath);

        } catch (GitAPIException e) {
            throw new IOException("Failed to commit/push: " + e.getMessage(), e);
        }
    }

    @Override
    public void resetHard(Path localPath) throws IOException {
        log.info("Resetting repository to HEAD (hard): {}", localPath);

        try (Git git = openRepository(localPath)) {
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef("HEAD")
                    .call();

            log.info("Repository reset successful: {}", localPath);
        } catch (GitAPIException e) {
            throw new IOException("Failed to reset repository: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isGitRepository(Path localPath) {
        if (!Files.exists(localPath)) {
            return false;
        }

        File gitDir = localPath.resolve(".git").toFile();
        return gitDir.exists() && gitDir.isDirectory();
    }

    @Override
    public String validate(Path localPath, String repositoryUrl, String username, String password) {
        StringBuilder result = new StringBuilder();

        // Check if path exists
        if (!Files.exists(localPath)) {
            result.append("❌ Local path does not exist: ").append(localPath).append("\n");
        } else {
            result.append("✅ Local path exists: ").append(localPath).append("\n");
        }

        // Check if it's a git repository
        if (isGitRepository(localPath)) {
            result.append("✅ Valid Git repository\n");

            // Try to get repository info
            try (Git git = openRepository(localPath)) {
                Repository repo = git.getRepository();
                String branch = repo.getBranch();
                result.append("✅ Current branch: ").append(branch).append("\n");

                // Check remote
                var remotes = git.remoteList().call();
                if (remotes.isEmpty()) {
                    result.append("⚠️  No remote configured\n");
                } else {
                    result.append("✅ Remotes configured: ").append(remotes.size()).append("\n");
                    remotes.forEach(remote ->
                        result.append("   - ").append(remote.getName())
                              .append(": ").append(remote.getURIs()).append("\n")
                    );
                }

                // Test fetch (doesn't download, just checks connectivity)
                if (!remotes.isEmpty()) {
                    try {
                        var fetchCommand = git.fetch();
                        if (username != null && !username.isBlank() && password != null) {
                            fetchCommand.setCredentialsProvider(
                                    new UsernamePasswordCredentialsProvider(username, password)
                            );
                        }
                        fetchCommand.setDryRun(true).call();
                        result.append("✅ Remote connectivity OK\n");
                    } catch (Exception e) {
                        result.append("❌ Remote connectivity failed: ").append(e.getMessage()).append("\n");
                    }
                }

            } catch (Exception e) {
                result.append("❌ Failed to read repository info: ").append(e.getMessage()).append("\n");
            }
        } else {
            result.append("⚠️  Not a Git repository\n");

            if (repositoryUrl != null && !repositoryUrl.isBlank()) {
                result.append("ℹ️  Repository URL configured: ").append(repositoryUrl).append("\n");
                result.append("ℹ️  Will clone on first sync\n");
            } else {
                result.append("⚠️  No repository URL configured\n");
            }
        }

        // Check credentials
        if (username != null && !username.isBlank()) {
            result.append("✅ Username configured\n");
        } else {
            result.append("⚠️  No username configured\n");
        }

        if (password != null && !password.isBlank()) {
            result.append("✅ Password/token configured\n");
        } else {
            result.append("⚠️  No password/token configured\n");
        }

        return result.toString();
    }

    /**
     * Open existing Git repository.
     */
    private Git openRepository(Path localPath) throws IOException {
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .setGitDir(localPath.resolve(".git").toFile())
                    .readEnvironment()
                    .findGitDir()
                    .build();

            return new Git(repository);
        } catch (IOException e) {
            throw new IOException("Failed to open Git repository: " + localPath, e);
        }
    }
}
