package de.mhus.nimbus.world.control.service.sync;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Confines external-resource file paths to a configured base directory to
 * prevent path traversal / arbitrary filesystem access.
 *
 * <p>Relative paths are resolved under the base directory; absolute paths must
 * already reside within it. Any path escaping the base — via {@code ..}
 * segments after normalization or an out-of-tree absolute path — is rejected.
 * This is the single trust boundary for the {@code localPath} an editor can
 * supply through the external-resource API.
 */
@Component
public class ExternalResourcePathValidator {

    private final Path baseDir;

    public ExternalResourcePathValidator(
            @Value("${nimbus.external-resources.base-dir:/data/external-resources}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    /**
     * Returns the normalized, absolute path for {@code localPath}, guaranteed to
     * lie inside the configured base directory.
     *
     * @throws IllegalArgumentException if the path is blank or escapes the base directory
     */
    public Path confine(String localPath) {
        if (Strings.isBlank(localPath)) {
            throw new IllegalArgumentException("localPath is required");
        }
        Path candidate = Paths.get(localPath);
        Path resolved = (candidate.isAbsolute() ? candidate : baseDir.resolve(candidate)).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException(
                    "localPath '" + localPath + "' escapes the allowed base directory " + baseDir);
        }
        return resolved;
    }
}
