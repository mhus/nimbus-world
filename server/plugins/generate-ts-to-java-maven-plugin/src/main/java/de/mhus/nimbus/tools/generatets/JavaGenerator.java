package de.mhus.nimbus.tools.generatets;

import de.mhus.nimbus.tools.generatets.java.JavaKind;
import de.mhus.nimbus.tools.generatets.java.JavaModel;
import de.mhus.nimbus.tools.generatets.java.JavaProperty;
import de.mhus.nimbus.tools.generatets.java.JavaType;
import de.mhus.nimbus.tools.generatets.ts.TsDeclarations;
import de.mhus.nimbus.tools.generatets.ts.TsModel;
import de.mhus.nimbus.tools.generatets.ts.TsSourceFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a JavaModel from the parsed TypeScript model in two steps:
 * 1) Create items (types) from TS declarations, including raw reference names
 * 2) Link references between the created items (extends/implements/alias target)
 */
public class JavaGenerator {

    private final Configuration configuration;

    public JavaGenerator() {
        this.configuration = null;
    }

    public JavaGenerator(Configuration configuration) {
        this.configuration = configuration;
    }

    public JavaModel generate(TsModel tsModel) {
        JavaModel jm = new JavaModel();
        if (tsModel == null || tsModel.getFiles() == null) return jm;

        // Step 1: create items and capture raw reference names
        for (TsSourceFile f : tsModel.getFiles()) {
            String srcPath = f.getPath();
            // Interfaces
            if (f.getInterfaces() != null) {
                for (TsDeclarations.TsInterface i : f.getInterfaces()) {
                    if (i == null || i.name == null) continue;
                    // Convert TS interface to Java class with fields
                    JavaType t = new JavaType(i.name, JavaKind.CLASS, srcPath);
                    t.setOriginalTsKind("interface");
                    // capture extends list; TS allows multiple, Java class supports one extends
                    if (i.extendsList != null && !i.extendsList.isEmpty()) {
                        t.setExtendsName(i.extendsList.get(0));
                        t.setOriginalTsExtends(new java.util.ArrayList<>(i.extendsList));
                    }
                    // Properties -> fields (with special handling for inline backdrop object)
                    if (i.properties != null) {
                        for (TsDeclarations.TsProperty p : i.properties) {
                            if (p == null || p.name == null) continue;
                            String jt = (p.javaTypeHint != null && !p.javaTypeHint.isBlank()) ? p.javaTypeHint : mapTsTypeToJava(p.type, p.optional);
                            t.getProperties().add(new JavaProperty(p.name, jt, p.optional, p.visibility));
                        }
                    }
                    jm.addType(t);
                }
            }
            // Enums
            if (f.getEnums() != null) {
                for (TsDeclarations.TsEnum e : f.getEnums()) {
                    if (e == null || e.name == null) continue;
                    JavaType t = new JavaType(e.name, JavaKind.ENUM, srcPath);
                    t.setOriginalTsKind("enum");
                    if (e.values != null && !e.values.isEmpty()) {
                        t.getEnumValues().addAll(e.values);
                    }
                    if (e.enumValues != null && !e.enumValues.isEmpty()) {
                        t.getEnumValuesWithAssignments().addAll(e.enumValues);
                    }
                    jm.addType(t);
                }
            }
            // Classes
            if (f.getClasses() != null) {
                for (TsDeclarations.TsClass c : f.getClasses()) {
                    if (c == null || c.name == null) continue;
                    JavaType t = new JavaType(c.name, JavaKind.CLASS, srcPath);
                    t.setOriginalTsKind("class");
                    if (c.extendsClass != null && !c.extendsClass.isEmpty()) {
                        t.setExtendsName(c.extendsClass);
                    }
                    if (c.implementsList != null) {
                        for (String n : c.implementsList) {
                            if (n != null && !n.isEmpty()) t.getImplementsNames().add(n);
                        }
                    }
                    // Properties (with special handling for inline backdrop object)
                    if (c.properties != null) {
                        for (TsDeclarations.TsProperty p : c.properties) {
                            if (p == null || p.name == null) continue;
                            String propType = p.type;
                            if (propType != null && propType.contains("{") && "backdrop".equals(p.name)) {
                                String helperName = c.name + "Backdrop";
                                ensureBackdropHelper(jm, helperName, srcPath);
                                t.getProperties().add(new JavaProperty(p.name, helperName, p.optional, p.visibility));
                            } else {
                                String jt = mapTsTypeToJava(propType, p.optional);
                                t.getProperties().add(new JavaProperty(p.name, jt, p.optional, p.visibility));
                            }
                        }
                    }
                    jm.addType(t);
                }
            }
            // Type Aliases
            if (f.getTypeAliases() != null) {
                for (TsDeclarations.TsTypeAlias a : f.getTypeAliases()) {
                    if (a == null || a.name == null) continue;
                    // Convert TS type alias to Java class with a single field 'value'
                    JavaType t = new JavaType(a.name, JavaKind.CLASS, srcPath);
                    t.setOriginalTsKind("type");
                    if (a.target != null && !a.target.isEmpty()) {
                        t.setAliasTargetName(a.target);
                        String jt = mapTsTypeToJava(a.target, true);
                        t.getProperties().add(new JavaProperty("value", jt, true, "public"));
                    }
                    jm.addType(t);
                }
            }
        }

        // Step 2: link references (resolve names to JavaType references)
        Map<String, JavaType> idx = jm.getIndexByName();
        for (JavaType t : jm.getTypes()) {
            // extends
            if (t.getExtendsName() != null) {
                JavaType ref = idx.get(t.getExtendsName());
                if (ref != null) t.setExtendsType(ref);
            }
            // implements
            if (t.getImplementsNames() != null && !t.getImplementsNames().isEmpty()) {
                // de-dup
                Set<String> seen = new HashSet<>();
                for (String n : t.getImplementsNames()) {
                    if (n == null || n.isEmpty()) continue;
                    if (!seen.add(n)) continue;
                    JavaType ref = idx.get(n);
                    if (ref != null) t.getImplementsTypes().add(ref);
                }
            }
            // alias
            if (t.getAliasTargetName() != null) {
                JavaType ref = idx.get(t.getAliasTargetName());
                if (ref != null) t.setAliasTargetType(ref);
            }
        }

        return jm;
    }

    /**
     * Ensure the presence of a helper class for inline backdrop objects with fields n,e,s,w.
     * The generated type will be named {@code helperName} and placed into the model once.
     */
    private void ensureBackdropHelper(JavaModel jm, String helperName, String srcPath) {
        if (jm == null || helperName == null || helperName.isEmpty()) return;
        Map<String, JavaType> idx = jm.getIndexByName();
        if (idx != null && idx.containsKey(helperName)) return;
        JavaType helper = new JavaType(helperName, JavaKind.CLASS, srcPath);
        helper.setOriginalTsKind("type");
        // Four optional arrays: n,e,s,w -> List<Backdrop>
        helper.getProperties().add(new JavaProperty("n", "java.util.List<Backdrop>", true, "public"));
        helper.getProperties().add(new JavaProperty("e", "java.util.List<Backdrop>", true, "public"));
        helper.getProperties().add(new JavaProperty("s", "java.util.List<Backdrop>", true, "public"));
        helper.getProperties().add(new JavaProperty("w", "java.util.List<Backdrop>", true, "public"));
        jm.addType(helper);
    }


    private String boxIfPrimitive(String type) {
        if (type == null) return null;
        switch (type) {
            case "int": return "java.lang.Integer";
            case "long": return "java.lang.Long";
            case "double": return "java.lang.Double";
            case "float": return "java.lang.Float";
            case "short": return "java.lang.Short";
            case "byte": return "java.lang.Byte";
            case "char": return "java.lang.Character";
            case "boolean": return "java.lang.Boolean";
            default: return type;
        }
    }

    private String mapTsTypeToJava(String ts, boolean optional) {
        if (ts == null || ts.isBlank()) return "Object";
        String s = ts.trim();
        // Inline object type -> generic map
        if (s.contains("{") || s.contains("}")) {
            return "java.util.Map<String, Object>";
        }
        // Strip readonly or modifiers
        s = s.replaceAll("^readonly\\s+", "").trim();

        // If the incoming type is already a Java primitive hint (e.g. //javaType: int),
        // respect optional by boxing to the corresponding java.lang wrapper.
        switch (s) {
            case "int":
            case "long":
            case "double":
            case "float":
            case "short":
            case "byte":
            case "char":
            case "boolean":
                return optional ? boxIfPrimitive(s) : s;
            default:
                // continue normal mapping
        }
        // Unwrap utility types that don't change representation in Java
        if (s.startsWith("Readonly<") && s.endsWith(">")) {
            String inner = s.substring("Readonly<".length(), s.length() - 1).trim();
            return mapTsTypeToJava(inner, optional);
        }
        if (s.startsWith("Partial<") && s.endsWith(">")) {
            String inner = s.substring("Partial<".length(), s.length() - 1).trim();
            return mapTsTypeToJava(inner, true);
        }
        // Template literal/backtick types -> String
        if ((s.startsWith("`") && s.endsWith("`"))) {
            return "String";
        }
        // Function types -> java.util.function.Function or Object (simplify to Object)
        if (s.contains("=>") || s.matches(".*\\([^)]*\\)\\s*:\\s*.*")) {
            return "Object";
        }
        // Tuple types -> List<Object>
        if (s.startsWith("[") && s.endsWith("]")) {
            return "java.util.List<Object>";
        }
        // Array types
        if (s.endsWith("[]")) {
            String elem = mapTsTypeToJava(s.substring(0, s.length() - 2), false);
            elem = boxIfPrimitive(elem);
            return "java.util.List<" + elem + ">";
        }
        if (s.startsWith("Array<") && s.endsWith(">")) {
            String inner = s.substring("Array<".length(), s.length() - 1).trim();
            String elem = mapTsTypeToJava(inner, false);
            elem = boxIfPrimitive(elem);
            return "java.util.List<" + elem + ">";
        }
        if (s.startsWith("ReadonlyArray<") && s.endsWith(">")) {
            String inner = s.substring("ReadonlyArray<".length(), s.length() - 1).trim();
            String elem = mapTsTypeToJava(inner, false);
            elem = boxIfPrimitive(elem);
            return "java.util.List<" + elem + ">";
        }
        // Generic Map or Record
        if ((s.startsWith("Map<") || s.startsWith("Record<")) && s.endsWith(">")) {
            String inner = s.substring(s.indexOf('<') + 1, s.length() - 1);
            String[] kv = splitTopLevel(inner, ',');
            String k = kv.length > 0 ? mapTsTypeToJava(kv[0].trim(), true) : "String";
            String v = kv.length > 1 ? mapTsTypeToJava(kv[1].trim(), true) : "Object";
            k = boxIfPrimitive(k);
            v = boxIfPrimitive(v);
            return "java.util.Map<" + k + ", " + v + ">";
        }
        // String literal types like 'Parallel' or "User" -> String
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return "String";
        }
        // Union/intersection handling
        // - If all union parts are string-like (string or string literals/backticks or null/undefined), map to String
        // - Otherwise, fallback to Object
        if (s.contains("|") || s.contains("&")) {
            // Treat both union and intersection uniformly here; intersection maps to Object
            if (s.contains("&")) return "Object";
            String[] parts = s.split("\\|");
            boolean allStringLike = true;
            for (String part : parts) {
                String a = part.trim();
                // remove parentheses around parts like ("a")
                while (a.startsWith("(") && a.endsWith(")") && a.length() > 1) {
                    a = a.substring(1, a.length() - 1).trim();
                }
                if (!isStringLikeUnionAlt(a)) {
                    allStringLike = false;
                    break;
                }
            }
            if (allStringLike) return "String";
            return "Object";
        }
        switch (s) {
            case "string": return "String";
            case "number": return optional ? "java.lang.Double" : "double";
            case "boolean": return optional ? "java.lang.Boolean" : "boolean";
            case "true": return optional ? "java.lang.Boolean" : "boolean";
            case "false": return optional ? "java.lang.Boolean" : "boolean";
            case "any": return "Object";
            case "unknown": return "Object";
            case "null": return "Object";
            case "undefined": return "Object";
            default:
                // Record<...> handled above
                // Generic parameter heuristics: map common single-letter generics to Object
                if (s.matches("[A-Z]")) return "Object";
                // Strip generic import(...) wrapper in types like import('...').Type
                if (s.startsWith("import(")) return "Object";
                // Object type
                if ("object".equals(s)) return "java.util.Map<String, Object>";
                // Fallback: keep as-is
                return s;
        }
    }

    private boolean isStringLikeUnionAlt(String s) {
        if (s == null || s.isBlank()) return false;
        String t = s.trim();
        // string keyword
        if ("string".equals(t)) return true;
        // string literals: 'x', "x", `x`
        if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("`") && t.endsWith("`"))) {
            return true;
        }
        // allow null/undefined in union with string literals -> still String
        if ("null".equals(t) || "undefined".equals(t)) return true;
        return false;
    }

    // Split a generic argument list by a delimiter at top-level (not inside nested <>)
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
