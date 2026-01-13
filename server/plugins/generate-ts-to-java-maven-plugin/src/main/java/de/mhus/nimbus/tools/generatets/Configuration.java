package de.mhus.nimbus.tools.generatets;

import java.util.List;
import java.util.Map;

public class Configuration {

    public List<String> ignoreTsItems;
    public String basePackage;

    /**
     * Optional list of additional annotations to add to every generated Java class
     * (non-enum). Entries can be fully qualified or simple annotation names.
     * They will be emitted as-is; a leading '@' is optional.
     */
    public List<String> additionalClassAnnotations;

    /**
     * Optional list of additional annotations to add to every generated field
     * (in classes). Entries can be fully qualified or simple names; a leading
     * '@' is optional and will be added during emission.
     */
    public List<String> additionalFieldAnnotations;

    /**
     * Optional list of additional annotations to add only to fields that were
     * optional in TypeScript (e.g., name?: string). Useful for things like
     * JsonInclude or custom validation hints.
     */
    public List<String> additionalOptionalFieldAnnotations;

    /**
     * Optional list of additional annotations to add only to fields that were
     * non-optional (required) in TypeScript.
     */
    public List<String> additionalNonOptionalFieldAnnotations;

    /**
     * Optional: exclude entire TS subdirectories by suffix. Any source file whose
     * relative directory (to its configured sourceDir) ends with one of these suffixes
     * will be ignored completely. Example entries: "errors", "logger", "network/messages".
     */
    public List<String> excludeDirSuffixes;

    /**
     * Optional package mapping rules. If a TS source directory (relative to a configured sourceDir)
     * ends with {@code dirEndsWith}, all types from that directory will be generated into {@code pkg}.
     */
    public List<PackageRule> packageRules;

    /**
     * Optional mapping from simple type names (e.g. ClientType) to fully-qualified Java types
     * (e.g. de.mhus.nimbus.types.ClientType). When provided, the generator will rewrite
     * occurrences of these simple names in property types and references (extends/implements/alias)
     * to the configured fully-qualified names. Generic arguments are also processed.
     */
    public Map<String, String> typeMappings;

    /**
     * Optional field-specific type mappings. Allows overriding the Java type of a concrete field.
     * Key format variants supported (matched in this order):
     * - Fully qualified: "com.example.pkg.ClassName.fieldName"
     * - Simple: "ClassName.fieldName"
     * - Suffix match: any suffix of the fully qualified form (e.g. "dto.ClassName.fieldName")
     * The value is the Java type to use (fully qualified or simple if java.lang or imported).
     *
     * Example:
     *   fieldTypeMappings:
     *     "dto.CreateSKeyRequest.kind": "java.lang.String"
     */
    public Map<String, String> fieldTypeMappings;

    /**
     * Optional mapping to replace unknown TS interface base types (extends) with concrete Java classes.
     * Key: TS interface name used in "extends" clause; Value: fully-qualified Java class to use instead.
     * If a TS interface extends an unknown type and a mapping exists here, the generated Java class will
     * extend the mapped Java class; otherwise it will fall back to defaultBaseClass/Object.
     */
    public Map<String, String> interfaceExtendsMappings;

    /**
     * Optional fully qualified Java class name that generated classes should extend by default
     * (instead of implicitly extending java.lang.Object). Only applied if a generated class
     * does not already define an explicit extends in the model.
     * Example: de.mhus.nimbus.shared.base.BaseModel
     */
    public String defaultBaseClass;

    /**
     * Optional mapping to specify which interface generated enums should implement.
     * This allows for identification and extension of enums at runtime.
     *
     * Key options:
     * - "*" - All enums implement this interface (global default)
     * - "EnumName" - Specific enum implements this interface
     * - "package.prefix.*" - All enums in packages starting with this prefix
     *
     * Value: Fully qualified interface name that the enum should implement
     *
     * Example:
     *   enumInterfaceMapping:
     *     "*": "de.mhus.nimbus.types.TsEnum"
     *     "MessageType": "de.mhus.nimbus.network.NetworkEnum"
     */
    public Map<String, String> enumInterfaceMapping;

    public static class PackageRule {
        /** Suffix of the TS source directory to match (e.g. "types"). */
        public String dirEndsWith;
        /** Target Java package to use for matched sources. */
        public String pkg;
    }
}
