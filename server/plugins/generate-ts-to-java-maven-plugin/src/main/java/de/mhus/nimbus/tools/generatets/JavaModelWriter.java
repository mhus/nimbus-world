package de.mhus.nimbus.tools.generatets;

import de.mhus.nimbus.tools.generatets.java.JavaKind;
import de.mhus.nimbus.tools.generatets.java.JavaModel;
import de.mhus.nimbus.tools.generatets.java.JavaProperty;
import de.mhus.nimbus.tools.generatets.java.JavaType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Writes a simple set of .java files from the JavaModel.
 *
 * Emits type declarations and basic members derived from TS properties:
 * - Interfaces: getter method signatures for properties (Type getName())
 * - Classes: fields with given visibility (default public)
 */
public class JavaModelWriter {

    private final JavaModel model;
    private final Configuration configuration;
    private final java.util.Map<String, JavaType> indexByName;
    private final java.util.Set<String> typesExtendedByOthers;
    // Helper types that should be rendered as nested classes instead of separate top-level files
    private final java.util.Map<String, JavaType> nestedHelperOwnerByName = new java.util.HashMap<>();

    public JavaModelWriter(JavaModel model) {
        this(model, null);
    }

    public JavaModelWriter(JavaModel model, Configuration configuration) {
        this.model = model;
        this.configuration = configuration;
        java.util.Map<String, JavaType> idx = new java.util.HashMap<>();
        java.util.Set<String> extendedByOthers = new java.util.HashSet<>();
        if (model != null && model.getTypes() != null) {
            for (JavaType t : model.getTypes()) {
                if (t != null && t.getName() != null) idx.put(t.getName(), t);
            }
            // compute which types are extended by other generated types
            for (JavaType t : model.getTypes()) {
                if (t == null) continue;
                String ext = t.getExtendsName();
                if (ext == null || ext.isBlank()) continue;
                // ext might be FQCN; reduce to simple name for lookup
                String simple = ext;
                int lastDot = simple.lastIndexOf('.');
                if (lastDot >= 0) simple = simple.substring(lastDot + 1);
                if (idx.containsKey(simple)) {
                    extendedByOthers.add(simple);
                }
            }
        }
        this.indexByName = idx;
        this.typesExtendedByOthers = extendedByOthers;

        // Discover backdrop helper types that should be nested inside their parent class
        if (model != null && model.getTypes() != null) {
            for (JavaType parent : model.getTypes()) {
                if (parent == null) continue;
                if (parent.getKind() != JavaKind.CLASS) continue;
                if (parent.getProperties() == null) continue;
                String parentName = parent.getName();
                if (parentName == null || parentName.isBlank()) continue;
                String expectedHelper = parentName + "Backdrop";
                for (JavaProperty p : parent.getProperties()) {
                    if (p == null) continue;
                    if (!"backdrop".equals(p.getName())) continue;
                    String type = p.getType();
                    if (type == null) continue;
                    String base = baseType(type);
                    if (expectedHelper.equals(base)) {
                        // Record that this helper type should be nested under this parent
                        nestedHelperOwnerByName.put(expectedHelper, parent);
                    }
                }
            }
        }
    }

    public void write(File outputDir) throws IOException {
        if (model == null || model.getTypes() == null) return;
        if (outputDir == null) throw new IOException("outputDir is null");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Could not create output directory: " + outputDir.getAbsolutePath());
        }

        for (JavaType t : model.getTypes()) {
            if (t == null) continue;
            String name = t.getName();
            if (!isValidJavaIdentifier(name)) continue;

            // Skip helper types that will be emitted as nested inside their owner class
            if (nestedHelperOwnerByName.containsKey(name)) {
                continue;
            }

            File pkgDir = outputDir;
            String pkg = t.getPackageName();
            if (pkg != null && !pkg.isBlank()) {
                String rel = pkg.replace('.', File.separatorChar);
                pkgDir = new File(outputDir, rel);
                if (!pkgDir.exists() && !pkgDir.mkdirs()) {
                    throw new IOException("Could not create package directory: " + pkgDir.getAbsolutePath());
                }
            }

            File javaFile = new File(pkgDir, name + ".java");
            try (FileWriter w = new FileWriter(javaFile, false)) {
                w.write(renderType(t));
            }
        }
    }

    private String renderType(JavaType t) {
        StringBuilder sb = new StringBuilder();
        // Header comment with TS source filename and original TS declaration kind/name
        String tsFileName = null;
        if (t.getSourcePath() != null && !t.getSourcePath().isBlank()) {
            tsFileName = new java.io.File(t.getSourcePath()).getName();
        }
        String tsKind = t.getOriginalTsKind();
        if (tsKind == null || tsKind.isBlank()) {
            // Fallback to JavaKind mapping
            switch (t.getKind()) {
                case ENUM: tsKind = "enum"; break;
                case INTERFACE: tsKind = "interface"; break;
                case CLASS: tsKind = "class"; break;
                case TYPE_ALIAS: tsKind = "type"; break;
                default: tsKind = "type"; break;
            }
        }
        String tsDecl = tsKind + " " + nullToEmpty(t.getName());
        sb.append("/*\n");
        if (tsFileName != null) sb.append(" * Source TS: ").append(tsFileName).append("\n");
        sb.append(" * Original TS: '").append(tsDecl).append("'\n");
        // Note unresolved TS extends (for interfaces converted to classes)
        List<String> unresolved = t.getUnresolvedTsExtends();
        if (unresolved != null && !unresolved.isEmpty()) {
            sb.append(" * NOTE: Unresolved TS extends: ").append(String.join(", ", unresolved)).append("\n");
            sb.append(" *       Consider mapping them via configuration 'interfaceExtendsMappings'.\n");
        }
        sb.append(" */\n");

        String pkg = t.getPackageName();
        if (pkg != null && !pkg.isBlank()) {
            sb.append("package ").append(pkg).append(";\n\n");
        }
        String name = t.getName();
        String currentPkg = pkg == null ? "" : pkg;
        if (t.getKind() == JavaKind.ENUM) {
            sb.append("public enum ").append(name);

            // Add implements clause if specified
            String implementsCsv = renderEnumImplementsCsv(t.getImplementsNames(), currentPkg);
            if (!implementsCsv.isEmpty()) {
                sb.append(" implements ").append(implementsCsv);
            }

            sb.append(" {\n");

            // Use enumValuesWithAssignments if available, otherwise fall back to enumValues
            java.util.List<de.mhus.nimbus.tools.generatets.ts.TsDeclarations.TsEnumValue> valuesWithAssignments = t.getEnumValuesWithAssignments();
            java.util.List<String> vals = t.getEnumValues();

            if (valuesWithAssignments != null && !valuesWithAssignments.isEmpty()) {
                // Determine if we have string or numeric values
                boolean hasStringValues = false;
                boolean hasNumericValues = false;

                for (de.mhus.nimbus.tools.generatets.ts.TsDeclarations.TsEnumValue enumValue : valuesWithAssignments) {
                    String value = enumValue.value;
                    if (value != null) {
                        // Check if value is numeric (integer)
                        try {
                            Integer.parseInt(value.trim());
                            hasNumericValues = true;
                        } catch (NumberFormatException e) {
                            hasStringValues = true;
                        }
                    }
                }

                // If we have mixed types or only strings, use String type
                boolean useStringType = hasStringValues || (!hasNumericValues && !hasStringValues);

                // Generate enum constants
                for (int i = 0; i < valuesWithAssignments.size(); i++) {
                    de.mhus.nimbus.tools.generatets.ts.TsDeclarations.TsEnumValue enumValue = valuesWithAssignments.get(i);
                    String enumName = enumValue.name;
                    String enumVal = enumValue.value;
                    if (!isValidJavaIdentifier(enumName)) continue;
                    if (i > 0) sb.append(",\n");

                    sb.append("    ").append(enumName).append("(");
                    if (useStringType) {
                        sb.append("\"").append(enumVal.replace("\"", "\\\"")).append("\"");
                    } else {
                        // Numeric value
                        try {
                            Integer.parseInt(enumVal.trim());
                            sb.append(enumVal.trim());
                        } catch (NumberFormatException e) {
                            // Fallback to string if parsing fails
                            sb.append("\"").append(enumVal.replace("\"", "\\\"")).append("\"");
                            useStringType = true;
                        }
                    }
                    sb.append(")");
                }
                sb.append(";\n\n");
                sb.append("    @lombok.Getter\n");
                if (useStringType) {
                    sb.append("    private final String tsIndex;\n");
                    sb.append("    ").append(name).append("(String tsIndex) { this.tsIndex = tsIndex; }\n");
                    sb.append("    public String tsString() { return this.tsIndex; }\n");
                } else {
                    sb.append("    private final int tsIndex;\n");
                    sb.append("    private final String tsString;\n");
                    sb.append("    ").append(name).append("(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }\n");
                    sb.append("    public String tsString() { return this.tsString; }\n");
                }
            } else if (vals != null && !vals.isEmpty()) {
                // Fall back to the old behavior with integer indices
                for (int i = 0; i < vals.size(); i++) {
                    String v = vals.get(i);
                    if (!isValidJavaIdentifier(v)) continue;
                    if (i > 0) sb.append(",\n");
                    sb.append("    ").append(v).append("(").append(i + 1).append(")");
                }
                sb.append(";\n\n");
                sb.append("    @lombok.Getter\n");
                sb.append("    private final int tsIndex;\n");
                sb.append("    private final String tsString;\n");
                sb.append("    ").append(name).append("(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }\n");
                sb.append("    public String tsString() { return this.tsString; }\n");
            }
            sb.append("}\n");
        } else if (t.getKind() == JavaKind.INTERFACE) {
            sb.append("public interface ").append(name);
            String extCsv = renderInterfaceExtendsCsv(t.getExtendsName(), t.getImplementsNames(), currentPkg);
            if (!extCsv.isEmpty()) {
                sb.append(" extends ").append(extCsv);
            }
            sb.append(" {\n");
            // interface members: getters (deduplicate by property name)
            if (t.getProperties() != null) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (JavaProperty p : t.getProperties()) {
                    if (p == null || p.getName() == null) continue;
                    if (!seen.add(p.getName())) continue;
                    String methodName = "get" + capitalize(p.getName());
                    String type = p.getType() == null || p.getType().isBlank() ? "Object" : qualifyType(p.getType(), currentPkg);
                    if (isValidJavaIdentifier(methodName)) {
                        sb.append("    ").append(type).append(' ').append(methodName).append("();\n");
                    }
                }
            }
            sb.append("}\n");
        } else if (t.getKind() == JavaKind.CLASS) {
            // Add Lombok and Jackson annotations and emit class with private fields
            // Additional configured annotations for classes (non-enum)
            emitAdditionalAnnotations(sb);
            sb.append("@lombok.Data\n");
            // Always use SuperBuilder to support inheritance builder chains
            sb.append("@lombok.experimental.SuperBuilder\n");
            // Always provide a no-args constructor
            sb.append("@lombok.NoArgsConstructor\n");
            // Add protected all-args constructor only if the class declares at least one field to avoid duplicate no-arg constructors
            boolean hasAnyField = (t.getProperties() != null && !t.getProperties().isEmpty())
                    || (t.getAliasTargetName() != null && !t.getAliasTargetName().isBlank());
            if (hasAnyField) {
                sb.append("@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)\n");
            }
            sb.append("public class ").append(name);
            String ext = renderExtends(t.getExtendsName(), currentPkg);
            if (!ext.isEmpty()) sb.append(" ").append(ext);
            String impls = renderImplements(t.getImplementsNames(), currentPkg);
            if (!impls.isEmpty()) sb.append(" ").append(impls);
            sb.append(" {\n");
            // class members: fields (deduplicate by property name)
            boolean emittedAnyField = false;
            if (t.getProperties() != null) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (JavaProperty p : t.getProperties()) {
                    if (p == null || p.getName() == null) continue;
                    if (!isValidJavaIdentifier(p.getName())) continue;
                    if (!seen.add(p.getName())) continue;
                    String type = resolveFieldTypeConsideringNested(p.getType(), currentPkg, name);
                    // Emit configured annotations for fields
                    emitFieldAnnotations(sb, p.isOptional(), p.getName());
                    sb.append("    private ").append(type).append(' ').append(p.getName()).append(";\n");
                    emittedAnyField = true;
                }
            }
            // If this CLASS actually comes from a TS type alias (no properties) then emit a single 'value' field
            if (!emittedAnyField && t.getAliasTargetName() != null && !t.getAliasTargetName().isBlank()) {
                String qualified = qualifyType(t.getAliasTargetName(), currentPkg);
                emitFieldAnnotations(sb, /*optional=*/false);
                sb.append("    private ")
                        .append(qualified == null || qualified.isBlank() ? "Object" : qualified)
                        .append(" value;\n");
            }
            // Fallback: If this CLASS originates from a TS type alias but aliasTargetName couldn't be parsed,
            // still generate a value field so the alias is usable. Default to String as the most common alias target.
            if (!emittedAnyField && ("type".equalsIgnoreCase(t.getOriginalTsKind()))) {
                String qualified = t.getAliasTargetName() == null ? "String" : qualifyType(t.getAliasTargetName(), currentPkg);
                emitFieldAnnotations(sb, /*optional=*/false);
                sb.append("    private ")
                        .append(qualified == null || qualified.isBlank() ? "String" : qualified)
                        .append(" value;\n");
            }
            // If there is a nested backdrop helper for this class, render it inside as a public static class
            JavaType nested = indexByName.get(name + "Backdrop");
            if (nested != null && !nested.getName().isBlank() && !name.isBlank()) {
                // Only render if this helper is registered to be nested
                if (!nestedHelperOwnerByName.containsKey(nested.getName())) {
                    nested = null;
                }
            }
            if (nested != null) {
                sb.append('\n');
                sb.append(renderNestedBackdropHelper(nested, currentPkg));
            }
            sb.append("}\n");
        } else if (t.getKind() == JavaKind.TYPE_ALIAS) {
            sb.append("/** Type alias for: ").append(nullToEmpty(t.getAliasTargetName())).append(" */\n");
            // Additional configured annotations for classes (non-enum)
            emitAdditionalAnnotations(sb);
            sb.append("@lombok.Data\n");
            sb.append("@lombok.experimental.SuperBuilder\n");
            sb.append("@lombok.NoArgsConstructor\n");
            sb.append("@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)\n");
            sb.append("public class ").append(name).append(" {\n");
            // For TS type aliases, represent the aliased value as a single field named 'value'
            String aliasTarget = t.getAliasTargetName();
            String qualified = qualifyType(aliasTarget, currentPkg);
            // Apply configured field annotations; treat alias value as required (non-optional)
            emitFieldAnnotations(sb, /*optional=*/false);
            sb.append("    private ").append(qualified == null || qualified.isBlank() ? "Object" : qualified).append(" value;\n");
            sb.append("}\n");
        } else {
            sb.append("public class ").append(name).append(" {\n}\n");
        }
        return sb.toString();
    }

    private String renderNestedBackdropHelper(JavaType helper, String currentPkg) {
        StringBuilder sb = new StringBuilder();
        String helperName = helper.getName();
        if (helperName == null || helperName.isBlank()) return "";
        // Header comment mirrors top-level but simpler for nested
        sb.append("    /* Nested helper for inline 'backdrop' */\n");
        // Apply additional class annotations if configured
        if (configuration != null && configuration.additionalClassAnnotations != null) {
            for (String raw : configuration.additionalClassAnnotations) {
                if (raw == null) continue;
                String ann = raw.trim();
                if (ann.isEmpty()) continue;
                if (ann.charAt(0) != '@') ann = "@" + ann;
                sb.append("    ").append(ann).append('\n');
            }
        }
        sb.append("    @lombok.Data\n");
        sb.append("    @lombok.experimental.SuperBuilder\n");
        sb.append("    @lombok.NoArgsConstructor\n");
        sb.append("    @lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)\n");
        sb.append("    public static class ").append(helperName).append(" {\n");
        if (helper.getProperties() != null) {
            for (JavaProperty p : helper.getProperties()) {
                if (p == null || p.getName() == null) continue;
                if (!isValidJavaIdentifier(p.getName())) continue;
                String type = resolveFieldTypeConsideringNested(p.getType(), currentPkg, helper.getName());
                emitFieldAnnotations(sb, /*optional=*/p.isOptional());
                sb.append("        private ").append(type).append(' ').append(p.getName()).append(";\n");
            }
        }
        sb.append("    }\n");
        return sb.toString();
    }

    private String resolveFieldTypeConsideringNested(String rawType, String currentPkg, String currentClassName) {
        if (rawType == null || rawType.isBlank()) return "Object";
        // Preserve generics by handling base type only
        String type = rawType.trim();
        int lt = type.indexOf('<');
        String genericArgs = null;
        String base = type;
        if (lt >= 0 && type.endsWith(">")) {
            base = type.substring(0, lt);
            genericArgs = type.substring(lt); // includes '<...>'
        }
        String simpleBase = baseType(base);
        // If the base is a helper destined to be nested, and current class is not its owner, qualify as Owner.Helper
        if (nestedHelperOwnerByName.containsKey(simpleBase)) {
            JavaType owner = nestedHelperOwnerByName.get(simpleBase);
            String ownerName = owner != null ? owner.getName() : null;
            if (ownerName != null && !ownerName.equals(currentClassName)) {
                String ownerPkg = owner.getPackageName();
                String qualifiedOwner = (ownerPkg == null || ownerPkg.isBlank() || ownerPkg.equals(currentPkg))
                        ? ownerName
                        : ownerPkg + '.' + ownerName;
                String resolved = qualifiedOwner + '.' + simpleBase;
                return genericArgs == null ? resolved : resolved + genericArgs;
            }
        }
        // Default qualification
        String q = qualifyType(rawType, currentPkg);
        return (q == null || q.isBlank()) ? "Object" : q;
    }

    private void emitAdditionalAnnotations(StringBuilder sb) {
        if (configuration == null || configuration.additionalClassAnnotations == null) return;
        for (String raw : configuration.additionalClassAnnotations) {
            if (raw == null) continue;
            String ann = raw.trim();
            if (ann.isEmpty()) continue;
            if (ann.charAt(0) != '@') ann = "@" + ann;
            sb.append(ann).append('\n');
        }
    }

    private void emitFieldAnnotations(StringBuilder sb, boolean optional) {
        emitFieldAnnotations(sb, optional, null);
    }

    private void emitFieldAnnotations(StringBuilder sb, boolean optional, String fieldName) {
        // Add @JsonProperty for fields with problematic naming (camelCase with uppercase letters)
        if (fieldName != null && needsJsonPropertyAnnotation(fieldName)) {
            sb.append("    @com.fasterxml.jackson.annotation.JsonProperty(\"").append(fieldName).append("\")\n");
        }

        if (configuration == null) return;
        // Common field annotations (apply to all fields)
        appendAnnotations(sb, configuration.additionalFieldAnnotations);
        // Optional / Non-optional specific
        if (optional) {
            appendAnnotations(sb, configuration.additionalOptionalFieldAnnotations);
        } else {
            appendAnnotations(sb, configuration.additionalNonOptionalFieldAnnotations);
        }
    }

    /**
     * Determines if a field name needs @JsonProperty annotation.
     * Returns true for camelCase fields that contain uppercase letters after the first character.
     */
    private boolean needsJsonPropertyAnnotation(String fieldName) {
        if (fieldName == null || fieldName.length() <= 1) return false;

        // Check for camelCase pattern: starts lowercase, contains uppercase
        if (!Character.isLowerCase(fieldName.charAt(0))) return false;

        // Special handling for timestamp fields like cTs, sTs etc.
        if (fieldName.matches("^[a-z][A-Z][a-z]*$")) {
            return true;
        }

        for (int i = 1; i < fieldName.length(); i++) {
            if (Character.isUpperCase(fieldName.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void appendAnnotations(StringBuilder sb, java.util.List<String> list) {
        if (list == null || list.isEmpty()) return;
        for (String raw : list) {
            if (raw == null) continue;
            String ann = raw.trim();
            if (ann.isEmpty()) continue;
            if (ann.charAt(0) != '@') ann = "@" + ann;
            sb.append("    ").append(ann).append('\n');
        }
    }

    private String renderExtends(String name, String currentPkg) {
        String q = qualifyType(name, currentPkg);
        if (q == null || q.isBlank()) return "";
        // Do not emit explicit extends for Object to avoid Lombok @SuperBuilder looking for ObjectBuilder
        String base = baseType(q);
        if ("Object".equals(base) || "java.lang.Object".equals(base)) return "";
        if (!isValidJavaIdentifier(base)) return "";
        return "extends " + q;
    }

    private String renderImplements(List<String> names, String currentPkg) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            String q = qualifyType(n, currentPkg);
            if (!isValidJavaIdentifier(baseType(q))) continue;
            if (sb.length() > 0) sb.append(',').append(' ');
            sb.append(q);
        }
        if (sb.length() == 0) return "";
        return "implements " + sb;
    }

    // For interfaces, combine extends and implements lists into a single comma-separated extends list
    private String renderInterfaceExtendsCsv(String extendsName, List<String> implementsNames, String currentPkg) {
        StringBuilder sb = new StringBuilder();
        if (extendsName != null && !extendsName.isBlank()) {
            String e = qualifyType(extendsName, currentPkg);
            if (isValidJavaIdentifier(baseType(e))) sb.append(e);
        }
        if (implementsNames != null) {
            for (String n : implementsNames) {
                if (n == null || n.isBlank()) continue;
                String q = qualifyType(n, currentPkg);
                if (!isValidJavaIdentifier(baseType(q))) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(q);
            }
        }
        return sb.toString();
    }

    /**
     * Render the implements clause for enums as comma-separated qualified names.
     */
    private String renderEnumImplementsCsv(java.util.List<String> implementsNames, String currentPkg) {
        if (implementsNames == null || implementsNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : implementsNames) {
            if (name == null || name.trim().isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(qualifyType(name.trim(), currentPkg));
        }
        return sb.toString();
    }

    private String combineCsv(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        if (b == null || b.isEmpty()) return a;
        return a + ", " + b;
    }

    private boolean isValidJavaIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        if (isJavaKeyword(s)) return false;
        return true;
    }

    private boolean isJavaKeyword(String s) {
        // Java keywords and literals that cannot be used as identifiers
        switch (s) {
            case "abstract": case "assert": case "boolean": case "break": case "byte":
            case "case": case "catch": case "char": case "class": case "const":
            case "continue": case "default": case "do": case "double": case "else":
            case "enum": case "extends": case "final": case "finally": case "float":
            case "for": case "goto": case "if": case "implements": case "import":
            case "instanceof": case "int": case "interface": case "long": case "native":
            case "new": case "package": case "private": case "protected": case "public":
            case "return": case "short": case "static": case "strictfp": case "super":
            case "switch": case "synchronized": case "this": case "throw": case "throws":
            case "transient": case "try": case "void": case "volatile": case "while":
            case "true": case "false": case "null":
                return true;
            default:
                return false;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private String qualifyType(String type, String currentPkg) {
        if (type == null || type.isBlank()) return "Object";
        String s = type.trim();
        // Handle generics like A<B,C<D>>
        int lt = s.indexOf('<');
        if (lt >= 0 && s.endsWith(">")) {
            String raw = s.substring(0, lt).trim();
            String args = s.substring(lt + 1, s.length() - 1);
            String qRaw = qualifySimple(raw, currentPkg);
            String[] parts = splitTopLevel(args, ',');
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(qualifyType(parts[i].trim(), currentPkg));
            }
            return qRaw + "<" + sb + ">";
        }
        return qualifySimple(s, currentPkg);
    }

    private String qualifySimple(String name, String currentPkg) {
        if (name == null || name.isBlank()) return "Object";
        String n = name.trim();
        // Already fully qualified or java.*
        if (n.contains(".")) {
            return n;
        }
        // java.lang common types
        switch (n) {
            case "String": case "Integer": case "Long": case "Double": case "Float":
            case "Short": case "Byte": case "Character": case "Boolean": case "Object":
                return n;
        }
        // java.util common raw types
        if ("List".equals(n)) return "java.util.List";
        if ("Map".equals(n)) return "java.util.Map";
        if ("Set".equals(n)) return "java.util.Set";
        // Lookup generated type by simple name
        JavaType t = indexByName.get(n);
        if (t != null) {
            String pkg = t.getPackageName();
            if (pkg == null || pkg.isBlank() || pkg.equals(currentPkg)) return n;
            return pkg + '.' + n;
        }
        return n;
    }

    private String baseType(String type) {
        if (type == null) return null;
        int lt = type.indexOf('<');
        String raw = lt >= 0 ? type.substring(0, lt) : type;
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }

    private String[] splitTopLevel(String s, char delimiter) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == delimiter && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts.toArray(new String[0]);
    }
}
