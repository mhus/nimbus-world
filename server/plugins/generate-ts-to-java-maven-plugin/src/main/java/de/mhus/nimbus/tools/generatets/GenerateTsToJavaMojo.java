package de.mhus.nimbus.tools.generatets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.mhus.nimbus.tools.generatets.ts.TsModel;
import de.mhus.nimbus.tools.generatets.ts.TsParser;
import de.mhus.nimbus.tools.generatets.java.JavaModel;
import de.mhus.nimbus.tools.generatets.java.JavaType;
import de.mhus.nimbus.tools.generatets.java.JavaKind;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mojo(name = "generate")
public class GenerateTsToJavaMojo extends AbstractMojo {

    /**
     * List of directories containing TypeScript sources.
     */
    @Parameter(defaultValue = "${project.basedir}/ts", property = "sourceDirs")
    private List<String> sourceDirs;

    /**
     * Output directory where generated sources would be placed in the future.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/generate-ts-to-java", property = "outputDir")
    private File outputDir;

    /**
     * Path to the TypeScript model file.
     */
    @Parameter(defaultValue = "${project.basedir}/model.json", property = "modelFile")
    private File modelFile;

    @Parameter(defaultValue = "${project.basedir}/ts-to-java.yaml", property = "configFile")
    private File configFile;

    @Override
    public void execute() {
        try {
            // Load configuration first
            Configuration configuration = loadConfiguration();
            int ignored = configuration.ignoreTsItems == null ? 0 : configuration.ignoreTsItems.size();
            getLog().info("Loaded configuration from " + (configFile == null ? "<none>" : configFile.getPath()) + ": ignoreTsItems=" + ignored);

            TsModel tsModel = parseTs();
            if (tsModel == null) return;

            // Exclude whole TS directories if configured
            filterExcludedDirs(tsModel, configuration);

            removeIgnoredItemsFromModel(tsModel, configuration.ignoreTsItems);

            writeModelToFile(tsModel);

            JavaGenerator generator = new JavaGenerator(configuration);
            JavaModel javaModel = generator.generate(tsModel);
            getLog().info("Java model created: types=" + (javaModel == null ? 0 : javaModel.getTypes().size()));

            // Resolve package for each type based on TS source relative directory and config rules
            if (javaModel != null) {
                List<File> roots = normalizeSourceDirs();
                for (JavaType t : javaModel.getTypes()) {
                    if (t == null) continue;
                    String resolved = resolvePackageFor(t, roots, configuration);
                    if (resolved != null && !resolved.isBlank()) {
//                        getLog().info("--- Resolved package for " + t.getName() + ": " + resolved);
//                        getLog().info("    Path: " + t.getSourcePath());
                        t.setPackageName(resolved);
                    }
                }

                // Apply enum interface mappings after packages are set
                if (configuration != null && configuration.enumInterfaceMapping != null) {
                    for (JavaType t : javaModel.getTypes()) {
                        if (t != null && t.getKind() == JavaKind.ENUM) {
                            applyEnumInterfaceMapping(t, configuration);
                        }
                    }
                }

                // Apply type name mappings from configuration to all model elements
                // Apply optional type mappings and default base class configuration
                if (configuration != null) {
                    for (JavaType t : javaModel.getTypes()) {
                        if (t == null) continue;

                        if (configuration.typeMappings != null && !configuration.typeMappings.isEmpty()) {
                            // extends
                            if (t.getExtendsName() != null) {
                                String mapped = mapTypeString(t.getExtendsName(), configuration);
                                if (mapped != null) t.setExtendsName(mapped);
                            }
                            // implements
                            if (t.getImplementsNames() != null) {
                                for (int i = 0; i < t.getImplementsNames().size(); i++) {
                                    String n = t.getImplementsNames().get(i);
                                    String mapped = mapTypeString(n, configuration);
                                    if (mapped != null) t.getImplementsNames().set(i, mapped);
                                }
                            }
                            // alias target
                            if (t.getAliasTargetName() != null) {
                                String mapped = mapTypeString(t.getAliasTargetName(), configuration);
                                if (mapped != null) t.setAliasTargetName(mapped);
                            }
                            // properties
                            if (t.getProperties() != null) {
                                for (de.mhus.nimbus.tools.generatets.java.JavaProperty p : t.getProperties()) {
                                    if (p == null) continue;
                                    String mapped = mapTypeString(p.getType(), configuration);
                                    if (mapped != null) p.setType(mapped);
                                }
                            }
                        }
                        // Apply field-specific type overrides if configured
                        if (configuration.fieldTypeMappings != null && !configuration.fieldTypeMappings.isEmpty()) {
                            if (t.getProperties() != null && !t.getProperties().isEmpty()) {
                                for (de.mhus.nimbus.tools.generatets.java.JavaProperty p : t.getProperties()) {
                                    if (p == null || p.getName() == null) continue;
                                    String override = resolveFieldTypeOverride(configuration, t.getPackageName(), t.getName(), p.getName());
                                    if (override != null && !override.isBlank()) {
                                        p.setType(override.trim());
                                    }
                                }
                            }
                        }
                    }
                    // Resolve unknown interface inheritance and apply interfaceExtendsMappings/defaultBaseClass
                    java.util.Map<String, JavaType> idx = javaModel.getIndexByName();
                    for (JavaType t : javaModel.getTypes()) {
                        if (t == null) continue;
                        // Only for TS interfaces converted to classes
                        if ("interface".equals(t.getOriginalTsKind())) {
                            String base = t.getExtendsName();
                            if (base != null && !base.isBlank()) {
                                boolean keep = false;
                                // If refers to another generated type by simple name
                                if (idx.containsKey(base)) {
                                    keep = true;
                                }
                                // If already FQCN, assume external type is valid
                                if (!keep && base.contains(".")) {
                                    keep = true;
                                }
                                if (!keep) {
                                    // Check configured replacement for unknown base
                                    String repl = configuration.interfaceExtendsMappings != null ? configuration.interfaceExtendsMappings.get(base) : null;
                                    if (repl != null && !repl.isBlank()) {
                                        t.setExtendsName(repl.trim());
                                        keep = true;
                                    }
                                }
                                if (!keep) {
                                    // Mark unresolved and drop extends
                                    java.util.List<String> unresolved = t.getUnresolvedTsExtends();
                                    if (unresolved != null) unresolved.add(base);
                                    t.setExtendsName(null);
                                }
                            }
                        }
                        // Apply default base if still none
                        if (t.getKind() == de.mhus.nimbus.tools.generatets.java.JavaKind.CLASS
                                && (t.getExtendsName() == null || t.getExtendsName().isBlank())
                                && configuration.defaultBaseClass != null && !configuration.defaultBaseClass.isBlank()) {
                            t.setExtendsName(configuration.defaultBaseClass.trim());
                        }
                    }
                }
            }

            new JavaModelWriter(javaModel, configuration).write(outputDir);

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse TypeScript sources", e);
        }
    }

    private String resolveFieldTypeOverride(Configuration configuration, String pkg, String className, String fieldName) {
        if (configuration == null || configuration.fieldTypeMappings == null || configuration.fieldTypeMappings.isEmpty()) return null;
        String fq = (pkg == null || pkg.isBlank()) ? (className + "." + fieldName) : (pkg + "." + className + "." + fieldName);
        String simple = className + "." + fieldName;

        // 1) Exact FQ match
        String exact = configuration.fieldTypeMappings.get(fq);
        if (exact != null && !exact.isBlank()) return exact;
        // 2) Simple class.field
        String simp = configuration.fieldTypeMappings.get(simple);
        if (simp != null && !simp.isBlank()) return simp;
        // 3) Suffix match (e.g., "dto.Class.field")
        for (java.util.Map.Entry<String, String> e : configuration.fieldTypeMappings.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) continue;
            if (fq.endsWith(key.trim())) {
                String val = e.getValue();
                if (val != null && !val.isBlank()) return val;
            }
        }
        return null;
    }

    private String mapTypeString(String input, Configuration cfg) {
        if (input == null || input.isBlank() || cfg == null || cfg.typeMappings == null || cfg.typeMappings.isEmpty()) {
            return null;
        }
        String s = input.trim();
        // Handle generics like A<B,C<D>>
        int lt = s.indexOf('<');
        if (lt >= 0 && s.endsWith(">")) {
            String raw = s.substring(0, lt).trim();
            String args = s.substring(lt + 1, s.length() - 1);
            String mappedRaw = mapSimpleType(raw, cfg);
            String[] parts = splitTopLevel(args, ',');
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(", ");
                String part = parts[i].trim();
                String mapped = mapTypeString(part, cfg);
                sb.append(mapped != null ? mapped : part);
            }
            String result = (mappedRaw != null ? mappedRaw : raw) + "<" + sb + ">";
            if (!result.equals(input)) return result; else return null;
        }
        // Arrays like T[] -> map T and convert back
        if (s.endsWith("[]")) {
            String elem = s.substring(0, s.length() - 2).trim();
            String mappedElem = mapTypeString(elem, cfg);
            String result = (mappedElem != null ? mappedElem : elem) + "[]";
            if (!result.equals(input)) return result; else return null;
        }
        // Simple
        String mapped = mapSimpleType(s, cfg);
        if (mapped != null && !mapped.equals(input)) return mapped;
        return null;
    }

    private String mapSimpleType(String name, Configuration cfg) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        // If already qualified, leave as is
        if (n.contains(".")) return null;
        String mapped = cfg.typeMappings.get(n);
        return (mapped == null || mapped.isBlank()) ? null : mapped.trim();
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

    private void deleteRecursively(File f) throws IOException {
        if (f == null || !f.exists()) return;
        if (f.isFile()) {
            if (!f.delete()) throw new IOException("Failed to delete file: " + f);
            return;
        }
        java.nio.file.Path root = f.toPath();
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
            java.util.List<java.nio.file.Path> list = walk.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
            for (java.nio.file.Path p : list) {
                java.io.File x = p.toFile();
                if (!x.delete() && x.exists()) throw new IOException("Failed to delete: " + x);
            }
        }
    }

    private void writeModelToFile(TsModel model) throws IOException {
        // Ensure target directory
        if (modelFile == null) {
            getLog().warn("modelFile is not configured; model will not be written.");
            return;
        }
        File parent = modelFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLog().warn("Could not create parent directory for model file: " + parent);
        }
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        om.writeValue(modelFile, model);
        getLog().info("Wrote TS model to: " + modelFile.getAbsolutePath() + " (files=" + model.getFiles().size() + ")");
    }

    private void filterExcludedDirs(TsModel model, Configuration cfg) {
        if (model == null || cfg == null) return;
        List<String> ex = cfg.excludeDirSuffixes;
        if (ex == null || ex.isEmpty()) return;
        List<File> roots = normalizeSourceDirs();
        File common = commonParent(roots);
        java.util.Set<String> suffixes = ex.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.replace('\\', '/')).collect(java.util.stream.Collectors.toSet());
        int removed = 0;
        java.util.Iterator<de.mhus.nimbus.tools.generatets.ts.TsSourceFile> it = model.getFiles().iterator();
        while (it.hasNext()) {
            de.mhus.nimbus.tools.generatets.ts.TsSourceFile f = it.next();
            if (f == null || f.getPath() == null) continue;
            File srcFile = new File(f.getPath());
            String relDir = null;
            // compute per-root relDir
            File matchedRoot = null;
            try {
                String sAbs = srcFile.getCanonicalPath();
                for (File r : roots) {
                    if (r == null) continue;
                    String rAbs = r.getCanonicalPath();
                    if (sAbs.startsWith(rAbs + File.separator) || sAbs.equals(rAbs)) { matchedRoot = r; break; }
                }
            } catch (IOException ignored) {}
            if (matchedRoot != null) {
                try {
                    java.nio.file.Path root = matchedRoot.getCanonicalFile().toPath();
                    java.nio.file.Path srcPath = srcFile.getCanonicalFile().toPath();
                    java.nio.file.Path rel = root.relativize(srcPath).getParent();
                    if (rel != null) relDir = rel.toString().replace('\\', '/');
                } catch (IOException ignored) {}
            }
            String relCommon = null;
            if (common != null) {
                try {
                    java.nio.file.Path root = common.getCanonicalFile().toPath();
                    java.nio.file.Path srcPath = srcFile.getCanonicalFile().toPath();
                    java.nio.file.Path rel = root.relativize(srcPath).getParent();
                    if (rel != null) relCommon = rel.toString().replace('\\', '/');
                } catch (IOException ignored) {}
            }
            boolean exclude = false;
            if (relDir != null && !relDir.isEmpty()) {
                for (String suf : suffixes) {
                    if (matchesDir(relDir, suf)) { exclude = true; break; }
                }
            }
            if (!exclude && relCommon != null && !relCommon.isEmpty()) {
                for (String suf : suffixes) {
                    if (matchesDir(relCommon, suf)) { exclude = true; break; }
                }
            }
            if (exclude) { it.remove(); removed++; }
        }
        if (removed > 0) getLog().info("Excluded TS files by excludeDirSuffixes: " + removed);
    }

    /** Matches a relative directory path against an exclusion suffix, treating suffix as a folder and any of its subfolders. */
    private boolean matchesDir(String relDir, String suffix) {
        if (relDir == null || relDir.isEmpty() || suffix == null || suffix.isEmpty()) return false;
        String rel = relDir.replace('\\', '/');
        String suf = suffix.replace('\\', '/');
        if (rel.equals(suf)) return true;
        if (rel.endsWith("/" + suf)) return true;
        if (rel.startsWith(suf + "/")) return true;
        return rel.contains("/" + suf + "/");
    }

    private void removeIgnoredItemsFromModel(TsModel model, List<String> ignoreTsItems) {
        if (model == null) return;
        if (ignoreTsItems == null || ignoreTsItems.isEmpty()) return;

        java.util.Set<String> ignore = new java.util.HashSet<>(ignoreTsItems);
        int removedTotal = 0;

        if (model.getFiles() != null) {
            for (de.mhus.nimbus.tools.generatets.ts.TsSourceFile f : model.getFiles()) {
                int before = 0;
                int after = 0;

                if (f.getInterfaces() != null) {
                    before += f.getInterfaces().size();
                    f.getInterfaces().removeIf(it -> it != null && it.name != null && ignore.contains(it.name));
                    after += f.getInterfaces().size();
                }
                if (f.getEnums() != null) {
                    before += f.getEnums().size();
                    f.getEnums().removeIf(it -> it != null && it.name != null && ignore.contains(it.name));
                    after += f.getEnums().size();
                }
                if (f.getClasses() != null) {
                    before += f.getClasses().size();
                    f.getClasses().removeIf(it -> it != null && it.name != null && ignore.contains(it.name));
                    after += f.getClasses().size();
                }
                if (f.getTypeAliases() != null) {
                    before += f.getTypeAliases().size();
                    f.getTypeAliases().removeIf(it -> it != null && it.name != null && ignore.contains(it.name));
                    after += f.getTypeAliases().size();
                }
                removedTotal += Math.max(0, before - after);
            }
        }

        if (removedTotal > 0) {
            getLog().info("Ignored TS items removed from model: " + removedTotal);
        } else {
            getLog().info("No TS items matched ignore list (" + ignore.size() + ")");
        }
    }

    private String resolvePackageFor(JavaType t, List<File> roots, Configuration cfg) {
        if (t == null) return null;
        String src = t.getSourcePath();
        if (src == null || src.isBlank()) return cfg != null ? cfg.basePackage : null;
        File srcFile = new File(src);
        File matchedRoot = null;
        try {
            String srcAbs = srcFile.getCanonicalPath();
            for (File r : roots) {
                if (r == null) continue;
                String rAbs = r.getCanonicalPath();
                if (srcAbs.startsWith(rAbs + File.separator) || srcAbs.equals(rAbs)) {
                    matchedRoot = r;
                    break;
                }
            }
        } catch (IOException e) {
            // fallback: try string startsWith using absolute paths
            for (File r : roots) {
                if (r == null) continue;
                String rAbs = r.getAbsolutePath();
                String sAbs = srcFile.getAbsolutePath();
                if (sAbs.startsWith(rAbs + File.separator) || sAbs.equals(rAbs)) {
                    matchedRoot = r;
                    break;
                }
            }
        }

        if (cfg == null) return null;

        String base = cfg.basePackage;
        String relDir = null;
        if (matchedRoot != null) {
            try {
                java.nio.file.Path root = matchedRoot.getCanonicalFile().toPath();
                java.nio.file.Path srcPath = srcFile.getCanonicalFile().toPath();
                java.nio.file.Path rel = root.relativize(srcPath).getParent();
                if (rel != null) relDir = rel.toString().replace('\\', '/');
            } catch (IOException ignored) {
                String rAbs = matchedRoot.getAbsolutePath().replace('\\', '/');
                String sAbs = srcFile.getAbsolutePath().replace('\\', '/');
                if (sAbs.startsWith(rAbs)) {
                    String tmp = sAbs.substring(rAbs.length());
                    int lastSlash = tmp.lastIndexOf('/');
                    relDir = lastSlash > 0 ? tmp.substring(1, lastSlash) : null;
                }
            }
        }

        // Also compute relative directory from the common parent of all configured sourceDirs
        String relDirCommon = null;
        File common = commonParent(roots);
        if (common != null) {
            try {
                java.nio.file.Path root = common.getCanonicalFile().toPath();
                java.nio.file.Path srcPath = srcFile.getCanonicalFile().toPath();
                java.nio.file.Path rel = root.relativize(srcPath).getParent();
                if (rel != null) relDirCommon = rel.toString().replace('\\', '/');
            } catch (IOException ignored) {
                String rAbs = common.getAbsolutePath().replace('\\', '/');
                String sAbs = srcFile.getAbsolutePath().replace('\\', '/');
                if (sAbs.startsWith(rAbs)) {
                    String tmp = sAbs.substring(rAbs.length());
                    int lastSlash = tmp.lastIndexOf('/');
                    relDirCommon = lastSlash > 0 ? tmp.substring(1, lastSlash) : null;
                }
            }
        }

        // Apply explicit package rules based on relative directory path from the TS root
        if (cfg.packageRules != null) {
            // First try per-root relative dir
            if (relDir != null && !relDir.isEmpty()) {
                for (Configuration.PackageRule rule : cfg.packageRules) {
                    if (rule == null) continue;
                    String suf = rule.dirEndsWith;
                    if (suf == null || suf.isBlank()) continue;
                    String sufNorm = suf.replace('\\', '/');
                    if (relDir.endsWith(sufNorm)) {
                        if (rule.pkg != null && !rule.pkg.isBlank()) return rule.pkg.trim();
                    }
                }
            }
            // Then try relative dir from the common parent (handles granular sourceDirs)
            if (relDirCommon != null && !relDirCommon.isEmpty()) {
                for (Configuration.PackageRule rule : cfg.packageRules) {
                    if (rule == null) continue;
                    String suf = rule.dirEndsWith;
                    if (suf == null || suf.isBlank()) continue;
                    String sufNorm = suf.replace('\\', '/');
                    if (relDirCommon.endsWith(sufNorm)) {
                        if (rule.pkg != null && !rule.pkg.isBlank()) return rule.pkg.trim();
                    }
                }
            }
        }

        // Derive package from basePackage + relative path if available
        if (base != null) {
            String relForBase = (relDir != null && !relDir.isEmpty()) ? relDir : relDirCommon;
            if (relForBase != null && !relForBase.isEmpty()) {
                String relPkg = relForBase.replace('/', '.');
                if (base.endsWith(".")) return base + relPkg;
                else return base + "." + relPkg;
            }
            return base;
        }
        return null;
    }

    /** Compute common parent directory for a list of roots. */
    private File commonParent(List<File> roots) {
        if (roots == null || roots.isEmpty()) return null;
        try {
            java.nio.file.Path common = roots.get(0).getCanonicalFile().toPath();
            for (int i = 1; i < roots.size(); i++) {
                File r = roots.get(i);
                if (r == null) continue;
                java.nio.file.Path p = r.getCanonicalFile().toPath();
                common = commonPrefix(common, p);
                if (common == null) return null;
            }
            return common.toFile();
        } catch (IOException e) {
            // Fallback without canonicalization
            java.nio.file.Path common = roots.get(0).getAbsoluteFile().toPath();
            for (int i = 1; i < roots.size(); i++) {
                File r = roots.get(i);
                if (r == null) continue;
                java.nio.file.Path p = r.getAbsoluteFile().toPath();
                common = commonPrefix(common, p);
                if (common == null) return null;
            }
            return common != null ? common.toFile() : null;
        }
    }

    private java.nio.file.Path commonPrefix(java.nio.file.Path a, java.nio.file.Path b) {
        if (a == null || b == null) return null;
        int min = Math.min(a.getNameCount(), b.getNameCount());
        int i = 0;
        // Align roots if different
        if (!a.getRoot().equals(b.getRoot())) return a.getRoot();
        while (i < min && a.getName(i).equals(b.getName(i))) {
            i++;
        }
        return a.getRoot().resolve(a.subpath(0, i));
    }

    private TsModel parseTs() throws IOException {
        List<File> dirs = normalizeSourceDirs();
        if (dirs.isEmpty()) {
            getLog().warn("No sourceDirs available; nothing to parse.");
            return null;
        }
        TsParser parser = new TsParser();
        TsModel model = parser.parse(dirs);
        return model;
    }

    private Configuration loadConfiguration() {
        Configuration c = new Configuration();
        c.ignoreTsItems = Collections.emptyList();
        c.excludeDirSuffixes = Collections.emptyList();
        if (configFile == null) {
            getLog().warn("configFile is not configured; using defaults.");
            return c;
        }
        if (!configFile.exists()) {
            getLog().warn("Config file not found: " + configFile.getAbsolutePath() + "; using defaults.");
            return c;
        }
        try (FileInputStream in = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Configuration loaded = yaml.loadAs(in, Configuration.class);
            if (loaded != null) {
                if (loaded.ignoreTsItems == null) loaded.ignoreTsItems = Collections.emptyList();
                if (loaded.excludeDirSuffixes == null) loaded.excludeDirSuffixes = Collections.emptyList();
                return loaded;
            }
        } catch (Exception e) {
            getLog().error("Failed to load config file '" + configFile + "': " + e.getMessage());
        }
        return c;
    }

    private List<File> normalizeSourceDirs() {
        List<File> result = new ArrayList<>();
        if (sourceDirs != null) {
            for (String s : sourceDirs) {
                if (s == null || s.trim().isEmpty()) continue;
                File f = new File(s);
                if (!f.exists()) {
                    getLog().warn("sourceDir does not exist: " + s);
                    continue;
                }
                result.add(f);
            }
        }
        // Logging summary
        if (!result.isEmpty()) {
            getLog().info("Parsing TS sources from: " + result.stream().map(File::getPath).collect(Collectors.joining(", ")));
        }
        return result;
    }

    /**
     * Apply enum interface mappings from configuration to the given enum type.
     * Supports specific enum names, package prefixes, and global mappings.
     */
    private void applyEnumInterfaceMapping(JavaType enumType, Configuration configuration) {
        if (configuration == null || configuration.enumInterfaceMapping == null) {
            getLog().info("DEBUG: No enum interface mapping configuration found");
            return;
        }

        String enumName = enumType.getName();
        String enumPackage = enumType.getPackageName();
        getLog().info("DEBUG: Applying interface mapping for enum: " + enumName + " (package: " + enumPackage + ")");

        // Try specific enum name mapping first
        String interfaceName = configuration.enumInterfaceMapping.get(enumName);
        getLog().info("DEBUG: Specific mapping for " + enumName + ": " + interfaceName);

        // Try package prefix mapping
        if (interfaceName == null && enumPackage != null) {
            for (java.util.Map.Entry<String, String> entry : configuration.enumInterfaceMapping.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(".*")) {
                    String packagePrefix = key.substring(0, key.length() - 2);
                    if (enumPackage.startsWith(packagePrefix)) {
                        interfaceName = entry.getValue();
                        getLog().info("DEBUG: Package prefix mapping for " + enumName + ": " + interfaceName);
                        break;
                    }
                }
            }
        }

        // Try global mapping (*)
        if (interfaceName == null) {
            interfaceName = configuration.enumInterfaceMapping.get("*");
            getLog().info("DEBUG: Global mapping for " + enumName + ": " + interfaceName);
        }

        // Add interface to implements list if found
        if (interfaceName != null && !interfaceName.trim().isEmpty()) {
            enumType.getImplementsNames().add(interfaceName.trim());
            getLog().info("DEBUG: Added interface " + interfaceName + " to enum " + enumName + ". Implements list size: " + enumType.getImplementsNames().size());
        } else {
            getLog().info("DEBUG: No interface mapping found for enum " + enumName);
        }
    }
}
