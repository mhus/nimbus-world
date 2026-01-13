package de.mhus.nimbus.tools.generatets.ts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight generic TypeScript source parser.
 *
 * It scans .ts files (excluding .d.ts) and extracts:
 * - import specifiers (the raw module string)
 * - declarations: interface, enum, class, type alias (names only for now)
 *
 * This is intentionally simple (regex + brace balancing) to keep the Java side
 * generic and robust. Detailed semantics can be enriched later.
 */
public class TsParser {

    private static final Pattern IMPORT_FROM = Pattern.compile("(?m)\\bimport\\s+[^;]*?from\\s*['\"]([^'\"]+)['\"];?");
    private static final Pattern IMPORT_SIDE_EFFECT = Pattern.compile("(?m)\\bimport\\s*['\"]([^'\"]+)['\"];?");

    private static final Pattern DECL_INTERFACE = Pattern.compile("\\bexport\\s+interface\\s+([A-Za-z0-9_]+)\\b|\\binterface\\s+([A-Za-z0-9_]+)\\b");
    private static final Pattern DECL_ENUM = Pattern.compile("(?m)^\\s*(?:export\\s+)?enum\\s+([A-Za-z0-9_]+)\\s*\\{");
    private static final Pattern DECL_CLASS = Pattern.compile("\\bexport\\s+class\\s+([A-Za-z0-9_]+)\\b|\\bclass\\s+([A-Za-z0-9_]+)\\b");
    private static final Pattern DECL_TYPE = Pattern.compile("(?m)\\bexport\\s+type\\s+([A-Za-z0-9_]+)\\s*=|\\btype\\s+([A-Za-z0-9_]+)\\s*=");
    public static final String DECL_STEP = "(?m)^[\\t ]*(public|private|protected)?[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(\\?)?[\\t ]*:[\\t ]*([^;\\r\\n]+?)\\s*;[\\t ]*(//.*)?[\\t ]*$";

    public TsModel parse(List<File> sourceDirs) throws IOException {
        TsModel model = new TsModel();
        List<File> files = new ArrayList<>();
        for (File dir : sourceDirs) {
            if (dir != null && dir.exists()) {
                collectFiles(dir, files);
            }
        }
        for (File f : files) {
            String content = readFile(f);
            String src = stripCommentBlocks(content);
            TsSourceFile ts = new TsSourceFile(relativize(f));
            // imports
            extractImports(src, ts);
            // declarations - pass both stripped and ORIGINAL (unstripped) source
            extractDeclarations(src, content, ts);  // content is the original with comments!
            model.addFile(ts);
        }
        // Post-processing: extract inline object types and create synthetic interfaces
        extractInlineObjectTypes(model);
        return model;
    }

    private void collectFiles(File dir, List<File> out) {
        if (dir.isFile()) {
            if (dir.getName().endsWith(".ts") && !dir.getName().endsWith(".d.ts")) {
                out.add(dir);
            }
            return;
        }
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collectFiles(f, out);
            else if (f.getName().endsWith(".ts") && !f.getName().endsWith(".d.ts")) out.add(f);
        }
    }

    private String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String stripCommentBlocks(String src) {
        // Remove /* */ comments
        return src.replaceAll("/\\*.*?\\*/", " ");
    }

    private void extractImports(String src, TsSourceFile file) {
        Matcher m = IMPORT_FROM.matcher(src);
        while (m.find()) {
            String module = m.group(1);
            if (module != null) file.getImports().add(module);
        }
        Matcher m2 = IMPORT_SIDE_EFFECT.matcher(src);
        while (m2.find()) {
            String module = m2.group(1);
            if (module != null && !file.getImports().contains(module)) file.getImports().add(module);
        }
    }

    private void extractDeclarations(String src, String originalSrc, TsSourceFile file) {
        // Interfaces
        for (NameOccur n : findNamed(src, DECL_INTERFACE, '{')) {
            TsDeclarations.TsInterface d = new TsDeclarations.TsInterface();
            d.name = n.name;
            // Parse header between name and opening '{' to capture extends list
            int braceIdx = src.indexOf('{', n.startIndex);
            if (braceIdx > n.startIndex) {
                String header = src.substring(n.startIndex, braceIdx);
                java.util.regex.Matcher em = java.util.regex.Pattern.compile("\\bextends\\s+([^\\{]+)").matcher(header);
                if (em.find()) {
                    String list = em.group(1);
                    if (list != null) {
                        for (String part : list.split(",")) {
                            String id = part.trim();
                            // strip generic args if any, keep simple identifier
                            int lt = id.indexOf('<');
                            if (lt > 0) id = id.substring(0, lt).trim();
                            id = id.replaceAll("\\s+", "");
                            if (!id.isEmpty()) d.extendsList.add(id);
                        }
                    }
                }
            }
            String body = safeSub(src, n.startIndex, n.endIndex);
            String originalBody = safeSub(originalSrc, n.startIndex, n.endIndex);
            extractPropertiesFromBodyWithOriginal(body, originalBody, d.properties);
            file.getInterfaces().add(d);
        }
        // Enums
        for (NameOccur n : findNamed(src, DECL_ENUM, '{')) {
            TsDeclarations.TsEnum d = new TsDeclarations.TsEnum();
            d.name = n.name;
            String body = safeSub(src, n.startIndex, n.endIndex);
            extractEnumValuesFromBody(body, d.values);
            extractEnumValuesAndAssignments(body, d.enumValues);
            file.getEnums().add(d);
        }
        // Classes
        for (NameOccur n : findNamed(src, DECL_CLASS, '{')) {
            TsDeclarations.TsClass d = new TsDeclarations.TsClass();
            d.name = n.name;
            String body = safeSub(src, n.startIndex, n.endIndex);
            String originalBody = safeSub(originalSrc, n.startIndex, n.endIndex);
            extractPropertiesFromBodyWithOriginal(body, originalBody, d.properties);
            file.getClasses().add(d);
        }
        // Type aliases (end by semicolon)
        for (NameOccur n : findNamed(src, DECL_TYPE, ';')) {
            TsDeclarations.TsTypeAlias d = new TsDeclarations.TsTypeAlias();
            d.name = n.name;
            // Extract target type between '=' and ';'
            String decl = safeSub(src, n.startIndex, n.endIndex);
            if (decl != null) {
                int eq = decl.indexOf('=');
                if (eq >= 0) {
                    String rhs = decl.substring(eq + 1).trim();
                    // remove trailing semicolon if present (safeSub ends before ';' but keep safety)
                    if (rhs.endsWith(";")) rhs = rhs.substring(0, rhs.length() - 1).trim();
                    // collapse multiple spaces
                    rhs = rhs.replaceAll("\n|\r", " ").trim();
                    d.target = rhs.isEmpty() ? null : rhs;
                }
            }
            file.getTypeAliases().add(d);
        }
    }

    private String safeSub(String s, int start, int end) {
        int a = Math.max(0, Math.min(s.length(), start));
        int b = Math.max(a, Math.min(s.length(), end));
        return s.substring(a, b);
    }

    private void extractPropertiesFromBody(String body, List<TsDeclarations.TsProperty> out) {
        // This method is called with stripped source, we need the original for javaType hints
        // For now, use the existing logic without javaType hints
        extractPropertiesFromBodyWithOriginal(body, body, out);
    }

    private void extractPropertiesFromBodyWithOriginal(String strippedBody, String originalBody, List<TsDeclarations.TsProperty> out) {
        if (strippedBody == null || out == null) return;

        // First: Match single-line TS property declarations of the form: [visibility]? name[?]: type;
        java.util.regex.Pattern propPat = java.util.regex.Pattern.compile(DECL_STEP);
        java.util.regex.Matcher m = propPat.matcher(strippedBody);
        while (m.find()) {
            int startIdx = m.start();
            // Only accept properties at top-level of the declaration body (depth == 1 inside outer braces)
            if (braceDepthAt(strippedBody, startIdx) != 1) {
                continue;
            }
            // Skip index signatures like [key: string]: any;
            try {
                int nameStart = m.start(2);
                if (nameStart > 0 && strippedBody.charAt(nameStart - 1) == '[') {
                    continue;
                }
                // Skip if there's a '(' before the ':' in the matched segment (likely a method signature)
                int colon = strippedBody.indexOf(':', m.start());
                if (colon > m.start()) {
                    String beforeColon = strippedBody.substring(m.start(), colon);
                    if (beforeColon.contains("(")) {
                        continue;
                    }
                }
            } catch (Exception ignored) {}

            String typeTxt = m.group(4) == null ? null : m.group(4).trim();
            // If type starts with an inline object '{', try to capture the full object literal up to matching '}'
            if (typeTxt != null && typeTxt.startsWith("{")) {
                int absTypeStart = m.start(4);
                int objEnd = findMatchingBrace(strippedBody, absTypeStart);
                if (objEnd > absTypeStart) {
                    typeTxt = strippedBody.substring(absTypeStart, objEnd).trim();
                }
            }
            // Do NOT skip inline object types here; we keep them so the generator can synthesize helper classes.
            // Still skip import(...) types that we cannot resolve sensibly.
            if (typeTxt != null && typeTxt.contains("import(")) {
                continue;
            }

            TsDeclarations.TsProperty pr = new TsDeclarations.TsProperty();
            pr.visibility = m.group(1);
            pr.name = m.group(2);
            pr.optional = m.group(3) != null && !m.group(3).isEmpty();
            pr.type = typeTxt;
            pr.comment = m.group(5) != null ? m.group(5).trim() : null;
            pr.javaTypeHint = extractJavaTypeHint(pr.comment);
            out.add(pr);
        }

        // Second: Find multiline inline object properties like: propertyName?: { ... };
        // These are not matched by the regex above because they don't have ';' on the first line
        extractMultilineInlineObjectProperties(strippedBody, out);
    }

    /**
     * Extract properties that are multiline inline objects.
     * Pattern: propertyName?: { ... };
     * These are missed by DECL_STEP regex because the first line doesn't end with ';'
     */
    private void extractMultilineInlineObjectProperties(String body, List<TsDeclarations.TsProperty> out) {
        if (body == null || out == null) return;

        // Pattern: optional visibility, property name, optional '?', ':', optional whitespace, '{'
        // Example: "owner?: {"
        java.util.regex.Pattern multilinePat = java.util.regex.Pattern.compile(
            "(?m)^[\\t ]*(public|private|protected)?[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(\\?)?[\\t ]*:[\\t ]*\\{"
        );

        java.util.regex.Matcher m = multilinePat.matcher(body);
        while (m.find()) {
            int startIdx = m.start();

            // Only accept properties at top-level (depth == 1)
            if (braceDepthAt(body, startIdx) != 1) {
                continue;
            }

            // Check if we already have this property (from single-line parsing)
            String propName = m.group(2);
            boolean alreadyExists = false;
            for (TsDeclarations.TsProperty existing : out) {
                if (propName.equals(existing.name)) {
                    alreadyExists = true;
                    break;
                }
            }
            if (alreadyExists) {
                continue;
            }

            // Find the opening brace position
            int braceStart = body.indexOf('{', m.start());
            if (braceStart < 0) continue;

            // Find the matching closing brace
            int braceEnd = findMatchingBrace(body, braceStart);
            if (braceEnd < 0) continue;

            // Extract the full inline object type including braces
            String inlineObjType = body.substring(braceStart, braceEnd).trim();

            // Create the property
            TsDeclarations.TsProperty pr = new TsDeclarations.TsProperty();
            pr.visibility = m.group(1);
            pr.name = m.group(2);
            pr.optional = m.group(3) != null && !m.group(3).isEmpty();
            pr.type = inlineObjType;
            pr.comment = null; // Could extract comment from line before if needed
            pr.javaTypeHint = null;

            out.add(pr);
        }
    }

    private String extractJavaTypeHint(String comment) {
        if (comment == null) return null;
        var pos = comment.indexOf("javaType:");
        if (pos == -1) return null;
        return comment.substring(pos + "javaType:".length()).trim().split(" ", 2)[0];
    }

    /**
     * Robust parsing of javaType hints from a TypeScript line
     * Handles various formats: //javaType:type, // javaType: type, //javaType=type
     */
    private String parseJavaTypeHintFromLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Find the last // comment in the line
        int commentStart = line.lastIndexOf("//");
        if (commentStart == -1) {
            return null;
        }

        String comment = line.substring(commentStart + 2).trim();
        String lowerComment = comment.toLowerCase();

        // Search for "javatype" (case insensitive) followed by : or =
        int javaTypeStart = lowerComment.indexOf("javatype");
        if (javaTypeStart == -1) {
            return null;
        }

        // Find the separator after "javatype"
        int separatorPos = javaTypeStart + "javatype".length();

        // Skip whitespace
        while (separatorPos < lowerComment.length() && Character.isWhitespace(lowerComment.charAt(separatorPos))) {
            separatorPos++;
        }

        // Check for : or =
        if (separatorPos >= lowerComment.length()) {
            return null;
        }

        char separator = lowerComment.charAt(separatorPos);
        if (separator != ':' && separator != '=') {
            return null;
        }

        // Extract the type after the separator
        int typeStart = separatorPos + 1;

        // Skip whitespace after the separator
        while (typeStart < comment.length() && Character.isWhitespace(comment.charAt(typeStart))) {
            typeStart++;
        }

        if (typeStart >= comment.length()) {
            return null;
        }

        String typeStr = comment.substring(typeStart);

        // Remove trailing comments (if any)
        int nextComment = typeStr.indexOf("//");
        if (nextComment != -1) {
            typeStr = typeStr.substring(0, nextComment).trim();
        }

        // Remove trailing whitespace and semicolon
        typeStr = typeStr.replaceAll("[\\s;]*$", "");

        return typeStr.isEmpty() ? null : typeStr;
    }

    /**
     * Find the start of the line containing the given position
     */
    private int findLineStart(String body, int fromPos) {
        for (int i = fromPos - 1; i >= 0; i--) {
            if (body.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return 0; // Beginning of file
    }

    /**
     * Find the start of the next line after the given position
     */
    private int findNextLineStart(String body, int fromPos) {
        for (int i = fromPos; i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                return i + 1 < body.length() ? i + 1 : -1;
            }
        }
        return -1;
    }

    /**
     * Find the end of the line starting from the given position
     */
    private int findLineEnd(String body, int fromPos) {
        for (int i = fromPos; i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                return i;
            }
        }
        return body.length();
    }

    /**
     * Compute the brace nesting depth at a given position within a block that includes outer braces.
     * Depth starts at 0 before the first '{'. After the first '{' it becomes 1 for the outer body.
     */
    private int braceDepthAt(String s, int pos) {
        int depth = 0;
        for (int i = 0; i < s.length() && i < pos; i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth = Math.max(0, depth - 1);
        }
        return depth;
    }

    private void extractEnumValuesFromBody(String body, List<String> out) {
        if (body == null || out == null) return;
        // Match enum member names: NAME [= ...] , or NAME at end
        Pattern p = Pattern.compile("(?m)^[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(?:=[^,\\r\\n]+)?[\\t ]*(?:,[\\t ]*)?(?://.*)?$");
        Matcher m = p.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            if (name == null || name.isEmpty()) continue;
            // Filter out potential keywords
            if (!Character.isJavaIdentifierStart(name.charAt(0))) continue;
            boolean ok = true;
            for (int i = 1; i < name.length(); i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) { ok = false; break; }
            }
            if (!ok) continue;
            out.add(name);
        }
    }

    private void extractEnumValuesAndAssignments(String body, List<TsDeclarations.TsEnumValue> out) {
        if (body == null || out == null) return;
        Pattern p = Pattern.compile("(?m)^[\\t ]*([A-Za-z_$][A-Za-z0-9_$]*)[\\t ]*(?:=[\\t ]*(?:(['\"])(.*?)\\2|([^,\\r\\n]+)))?[\\t ]*(?:,[\\t ]*)?(?://.*)?$");
        Matcher m = p.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            if (name == null || name.isEmpty()) continue;
            // Filter out potential keywords
            if (!Character.isJavaIdentifierStart(name.charAt(0))) continue;
            boolean ok = true;
            for (int i = 1; i < name.length(); i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) { ok = false; break; }
            }
            if (!ok) continue;

            String value = null;
            if (m.group(3) != null) {
                // String value in quotes
                value = m.group(3);
            } else if (m.group(4) != null) {
                // Other value (number, etc.) - trim whitespace
                value = m.group(4).trim();
            } else {
                // No assignment, use the name as default value
                value = name;
            }

            out.add(new TsDeclarations.TsEnumValue(name, value));
        }
    }

    private static class NameOccur {
        String name;
        int startIndex;
        int endIndex;
    }

    private List<NameOccur> findNamed(String src, Pattern pat, char endBy) {
        List<NameOccur> out = new ArrayList<>();
        Matcher m = pat.matcher(src);
        while (m.find()) {
            String n1 = m.group(1);
            String n2 = null;
            try { n2 = m.group(2); } catch (Exception ignored) {}
            String name = n1 != null ? n1 : n2;
            if (name == null) continue;

            int start, end;
            if (pat == DECL_ENUM) {
                // For the new ENUM pattern that includes the opening brace
                // The match already includes the opening brace, so find the matching closing brace
                int openBrace = src.lastIndexOf('{', m.end());
                if (openBrace >= 0) {
                    start = openBrace + 1; // Start after the opening brace
                    end = findMatchingBrace(src, openBrace);
                } else {
                    // Fallback if brace not found
                    start = m.end();
                    end = src.indexOf('}', start);
                }
            } else {
                // Original logic for other patterns
                start = m.end();
                end = (endBy == '{') ? findMatchingBrace(src, src.indexOf('{', start)) : src.indexOf(';', start);
            }

            NameOccur o = new NameOccur();
            o.name = name;
            o.startIndex = start;
            o.endIndex = end < 0 ? start : end;
            out.add(o);
        }
        return out;
    }

    private int findMatchingBrace(String src, int openIndex) {
        if (openIndex < 0) return -1;
        Deque<Character> stack = new ArrayDeque<>();
        for (int i = openIndex; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') stack.push(c);
            else if (c == '}') {
                if (stack.isEmpty()) return i; // unmatched, stop
                stack.pop();
                if (stack.isEmpty()) return i + 1; // end index exclusive
            }
        }
        return -1;
    }

    private String relativize(File f) {
        return f.getPath();
    }

    /**
     * Post-processing step: scan all interfaces for properties with inline object types
     * and create synthetic interfaces for them.
     */
    private void extractInlineObjectTypes(TsModel model) {
        if (model == null || model.getFiles() == null) return;

        List<TsSourceFile> filesToUpdate = new ArrayList<>();
        List<TsDeclarations.TsInterface> newInterfaces = new ArrayList<>();

        for (TsSourceFile file : model.getFiles()) {
            if (file.getInterfaces() == null) continue;

            for (TsDeclarations.TsInterface iface : file.getInterfaces()) {
                if (iface.properties == null) continue;

                for (TsDeclarations.TsProperty prop : iface.properties) {
                    if (prop.type == null) continue;

                    // Remove all whitespace including newlines for checking
                    String cleanedType = prop.type.replaceAll("\\s+", " ").trim();
                    String trimmedType = prop.type.trim();

                    // Check if this is an inline object type
                    if (cleanedType.startsWith("{") && cleanedType.contains(":")) {
                        // Generate a name for the synthetic interface
                        String syntheticName = generateSyntheticInterfaceName(iface.name, prop.name);

                        // Parse the inline object and create a new interface
                        TsDeclarations.TsInterface syntheticInterface = parseInlineObjectType(syntheticName, trimmedType, file, newInterfaces);
                        if (syntheticInterface != null) {
                            newInterfaces.add(syntheticInterface);

                            // Update the property type to reference the new interface
                            prop.type = syntheticName;
                        }
                    }
                    // Handle Array<inline object> types
                    else if (cleanedType.startsWith("Array<{") && cleanedType.endsWith("}>")) {
                        String inlineObj = cleanedType.substring("Array<".length(), cleanedType.length() - 1);
                        String syntheticName = generateSyntheticInterfaceName(iface.name, prop.name);

                        TsDeclarations.TsInterface syntheticInterface = parseInlineObjectType(syntheticName, inlineObj, file, newInterfaces);
                        if (syntheticInterface != null) {
                            newInterfaces.add(syntheticInterface);
                            prop.type = "Array<" + syntheticName + ">";
                        }
                    }
                }
            }

            // Add all new interfaces to this file
            if (!newInterfaces.isEmpty()) {
                file.getInterfaces().addAll(newInterfaces);
                newInterfaces.clear();
            }
        }
    }

    /**
     * Generate a name for a synthetic interface based on parent interface and property name.
     * Example: interface WorldInfo, property entryPoint -> WorldInfoEntryPointDTO
     */
    private String generateSyntheticInterfaceName(String parentName, String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return parentName + "InlineDTO";
        }
        // Capitalize first letter of property name
        String capitalizedProp = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        return parentName + capitalizedProp + "DTO";
    }

    /**
     * Parse an inline object type string and create a TsInterface from it.
     * Example input: "{ area: Area; grid: HexVector2; }"
     * Recursively handles nested inline objects.
     */
    private TsDeclarations.TsInterface parseInlineObjectType(String name, String objectType,
                                                             TsSourceFile file,
                                                             List<TsDeclarations.TsInterface> newInterfaces) {
        if (objectType == null || !objectType.contains(":")) return null;

        TsDeclarations.TsInterface iface = new TsDeclarations.TsInterface();
        iface.name = name;

        // Remove outer braces
        String body = objectType.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        body = body.trim();

        // Parse properties manually, handling nested braces
        int pos = 0;
        while (pos < body.length()) {
            // Skip whitespace
            while (pos < body.length() && Character.isWhitespace(body.charAt(pos))) pos++;
            if (pos >= body.length()) break;

            // Find property name and colon
            int colonPos = findNextColon(body, pos);
            if (colonPos < 0) break;

            String namePart = body.substring(pos, colonPos).trim();
            if (namePart.isEmpty()) break;

            boolean optional = false;
            if (namePart.endsWith("?")) {
                optional = true;
                namePart = namePart.substring(0, namePart.length() - 1).trim();
            }

            // Find the type value (up to semicolon, handling nested braces)
            int typeStart = colonPos + 1;
            int typeEnd = findPropertyEnd(body, typeStart);
            if (typeEnd < 0) typeEnd = body.length();

            String typePart = body.substring(typeStart, typeEnd).trim();

            // Extract javaType hint from inline comment (look in full line from typeStart to newline)
            String javaTypeHint = null;
            int lineEnd = body.indexOf('\n', typeStart);
            if (lineEnd < 0) lineEnd = body.length();
            String fullLine = body.substring(typeStart, Math.min(lineEnd, body.length()));
            int commentIdx = fullLine.indexOf("//");
            if (commentIdx >= 0) {
                String comment = fullLine.substring(commentIdx);
                javaTypeHint = extractJavaTypeHint(comment);
            }

            // Remove trailing semicolon if present
            if (typePart.endsWith(";")) {
                typePart = typePart.substring(0, typePart.length() - 1).trim();
            }

            // Check if this is a nested inline object (BEFORE removing comments!)
            boolean isNestedInline = typePart.startsWith("{") && typePart.contains(":");

            // Remove comments from type - BUT ONLY if it's NOT an inline object
            // For inline objects, preserve comments so they can be extracted during recursive parsing
            if (!isNestedInline) {
                commentIdx = typePart.indexOf("//");
                if (commentIdx >= 0) {
                    typePart = typePart.substring(0, commentIdx).trim();
                }
            }

            // Check if this property has a nested inline object
            if (isNestedInline) {
                String nestedName = generateSyntheticInterfaceName(name, namePart);
                TsDeclarations.TsInterface nestedInterface = parseInlineObjectType(nestedName, typePart, file, newInterfaces);
                if (nestedInterface != null) {
                    newInterfaces.add(nestedInterface);
                    typePart = nestedName;
                }
            }
            // Check for Array<inline object>
            else if (typePart.startsWith("Array<{") && typePart.endsWith("}>")) {
                String inlineObj = typePart.substring("Array<".length(), typePart.length() - 1);
                String nestedName = generateSyntheticInterfaceName(name, namePart);
                TsDeclarations.TsInterface nestedInterface = parseInlineObjectType(nestedName, inlineObj, file, newInterfaces);
                if (nestedInterface != null) {
                    newInterfaces.add(nestedInterface);
                    typePart = "Array<" + nestedName + ">";
                }
            }

            TsDeclarations.TsProperty prop = new TsDeclarations.TsProperty();
            prop.name = namePart;
            prop.type = typePart;
            prop.optional = optional;
            prop.javaTypeHint = javaTypeHint;

            iface.properties.add(prop);

            // Move past the semicolon
            pos = typeEnd + 1;
        }

        return iface.properties.isEmpty() ? null : iface;
    }

    /**
     * Find the next colon that's not inside braces or brackets
     */
    private int findNextColon(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[' || c == '<') depth++;
            else if (c == '}' || c == ']' || c == '>') depth--;
            else if (c == ':' && depth == 0) return i;
        }
        return -1;
    }

    /**
     * Find the end of a property value (semicolon, newline, or end of string), handling nested braces
     * TypeScript allows properties without semicolons, so we also stop at newlines when at depth 0
     */
    private int findPropertyEnd(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[' || c == '<') depth++;
            else if (c == '}' || c == ']' || c == '>') depth--;
            else if (c == ';' && depth == 0) return i;
            // Also stop at newline if we're at depth 0 (not inside braces)
            else if ((c == '\n' || c == '\r') && depth == 0) return i;
        }
        return s.length();
    }
}
