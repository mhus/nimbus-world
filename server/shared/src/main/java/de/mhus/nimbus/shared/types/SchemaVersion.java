package de.mhus.nimbus.shared.types;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.util.Optional;

/**
 * SchemaVersion represents a version in the format "major[.minor[.patch]]".
 * The default value is "0.0.0". Even for the parts that are not specified.
 * Negative is not allowed.
 */
@Getter
public class SchemaVersion implements Comparable<SchemaVersion> {

    public static final SchemaVersion NULL = SchemaVersion.create("0");

    private int major = 0;
    private int minor = 0;
    private int patch = 0;

    public SchemaVersion(String version) {
        if (Strings.isNotEmpty(version)) {
            String[] parts = version.trim().split("\\.");
            this.major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            this.minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            this.patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        }
    }

    public boolean isNull() {
        return major == 0 && minor == 0 && patch == 0;
    }

    public boolean equals(Object other) {
        if (other == null) return false;
        if (other instanceof SchemaVersion schemaVersion) {
            return this.major == schemaVersion.major &&
                    this.minor == schemaVersion.minor &&
                    this.patch == schemaVersion.patch;
        }
        var otherVersion = new SchemaVersion(other.toString());
        return this.major == otherVersion.major &&
                this.minor == otherVersion.minor &&
                this.patch == otherVersion.patch;
    }

    public static SchemaVersion create(String version) {
        return new SchemaVersion(version);
    }

    public static Optional<SchemaVersion> of(String version) {
        if (!validate(version)) return Optional.empty();
        return Optional.of(new SchemaVersion(version));
    }

    public static boolean validate(String version) {
        if (Strings.isBlank(version)) return false;
//        String[] parts = version.trim().split("\\.", 3);
//        for (String part : parts) {
//            try {
//                if (Integer.parseInt(part) < 0) return false;
//            } catch (NumberFormatException e) {
//                return false;
//            }
//        }
        return version.matches("\\d+(\\.\\d+)?(\\.\\d+)?");
    }

    @Override
    public int compareTo(SchemaVersion o) {
        if (this.major != o.major) {
            return Integer.compare(this.major, o.major);
        }
        if (this.minor != o.minor) {
            return Integer.compare(this.minor, o.minor);
        }
        return Integer.compare(this.patch, o.patch);
    }

    public String toString() {
        return major + "." + minor + "." + patch;
    }

}
