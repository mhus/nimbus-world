package de.mhus.nimbus.world.control.service.sync;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalResourcePathValidatorTest {

    private static final String BASE = "/data/external-resources";
    private final ExternalResourcePathValidator validator = new ExternalResourcePathValidator(BASE);

    @Test
    void relativePathIsResolvedUnderBase() {
        Path result = validator.confine("world-main/assets");
        assertThat(result).isEqualTo(Paths.get(BASE, "world-main", "assets"));
    }

    @Test
    void absolutePathWithinBaseIsAccepted() {
        Path result = validator.confine(BASE + "/world-main");
        assertThat(result).isEqualTo(Paths.get(BASE, "world-main"));
    }

    @Test
    void traversalEscapeIsRejected() {
        assertThatThrownBy(() -> validator.confine("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absolutePathOutsideBaseIsRejected() {
        assertThatThrownBy(() -> validator.confine("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void traversalBackIntoBaseAfterEscapeIsRejected() {
        // Normalizes to /data/secret -> outside base
        assertThatThrownBy(() -> validator.confine("world/../../secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankPathIsRejected() {
        assertThatThrownBy(() -> validator.confine("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
