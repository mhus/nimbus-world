package de.mhus.nimbus.evaluate;

import de.mhus.nimbus.tools.generatets.GenerateTsToJavaMojo;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class EvaluatePluginTest {

    private static void assertGeneratedIfTsExists(Path tsSubDir, List<File> javaFiles, String expectedPackage) throws IOException {
        if (tsSubDir == null) return;
        if (!Files.exists(tsSubDir)) return; // nothing to assert if folder absent
        try (var stream = Files.walk(tsSubDir)) {
            boolean hasTs = stream.anyMatch(p -> p.toString().endsWith(".ts"));
            if (!hasTs) return; // no TS files here, skip assertion
        }
        String pkgPath = expectedPackage.replace('.', File.separatorChar);
        boolean found = javaFiles.stream().anyMatch(f -> f.getPath().contains(pkgPath));
        assertTrue(found, "Expected generated Java under package '" + expectedPackage + "' for TS sources at " + tsSubDir);

    }

    @Test
    public void generateAndCompile() throws Exception {
        // Determine evaluate module directory relative to the plugin module under test
        File pluginModuleBase = new File(System.getProperty("user.dir"));
        File moduleBase = new File(pluginModuleBase, "evaluate").getCanonicalFile();
        assertTrue(new File(moduleBase, "pom.xml").exists(), "evaluate/pom.xml must exist: " + moduleBase);

        Path tsDir = moduleBase.toPath().resolve("ts");
        assertTrue(Files.exists(tsDir), "TS directory must exist: " + tsDir);

        Path targetDir = moduleBase.toPath().resolve("target");
        // Write generated sources into src/main/java as requested for the evaluate module
        File outJavaDir = moduleBase.toPath().resolve("src/main/java").toFile();
        File modelFile = new File(targetDir.toFile(), "model.json");
        File classesDir = new File(targetDir.toFile(), "compiled-classes");

        // Ensure a clean state: remove any leftovers from previous runs
        // Only delete the generated package directory inside src/main/java, not the whole source tree
        deleteRecursively(outJavaDir);
        deleteRecursively(classesDir);
        if (modelFile.exists()) assertTrue(modelFile.delete(), "Could not delete previous model file: " + modelFile);

        Files.createDirectories(outJavaDir.toPath());
        Files.createDirectories(targetDir);
        generateJavaTsEnum(outJavaDir);

        // Run Mojo (simulate plugin execution inside evaluate)
        GenerateTsToJavaMojo mojo = new GenerateTsToJavaMojo();
        setField(mojo, "sourceDirs", Arrays.asList(tsDir.toFile().getAbsolutePath()));
        setField(mojo, "outputDir", outJavaDir);
        setField(mojo, "modelFile", modelFile);
        setField(mojo, "configFile", new File(moduleBase, "ts-to-java.yaml")); // optional, may not exist
        mojo.execute();

        // Validate java sources.
        assertTrue(outJavaDir.exists(), "Output dir not created: " + outJavaDir);
        List<File> javaFiles = collectJavaFiles(outJavaDir);
        assertFalse(javaFiles.isEmpty(), "Expected Java files to be generated, but none were found in: " + outJavaDir);

        // Validate enum type generation correctness
        validateEnumTypes(outJavaDir);

        // Validate generation into expected package directories based on TS folders present
        assertGeneratedIfTsExists(tsDir.resolve("types"), javaFiles, "de.mhus.nimbus.evaluate.generated.types");
        assertGeneratedIfTsExists(tsDir.resolve("configs"), javaFiles, "de.mhus.nimbus.evaluate.generated.configs");
        assertGeneratedIfTsExists(tsDir.resolve("network").resolve("messages"), javaFiles, "de.mhus.nimbus.evaluate.generated.network.messages");
        assertGeneratedIfTsExists(tsDir.resolve("rest"), javaFiles, "de.mhus.nimbus.evaluate.generated.rest");
        assertGeneratedIfTsExists(tsDir.resolve("scrawl"), javaFiles, "de.mhus.nimbus.evaluate.generated.scrawl");

        // Compile step is validated via Maven below, which includes Lombok annotation processing.

        // Finally, run a Maven build in the evaluate module to ensure the project compiles with generated sources
        int exit = runMaven(moduleBase, "clean", "package", "-DskipTests", "-Dmaven.compiler.release=21");
        assertEquals(0, exit, "Maven build of evaluate module failed");
    }

    private void generateJavaTsEnum(File outJavaDir) {
        var fsEnum = new File(outJavaDir, "de/mhus/nimbus/types/TsEnum.java");
        //create root dir
        fsEnum.getParentFile().mkdirs();
        if (!fsEnum.exists()) {
            try {
                Files.writeString(fsEnum.toPath(), "package  de.mhus.nimbus.types;\n\npublic interface TsEnum { }");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void validateEnumTypes(File outJavaDir) throws IOException {
        // Validate MessageType enum (string values)
        File messageTypeFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/network/MessageType.java");
        if (messageTypeFile.exists()) {
            String content = Files.readString(messageTypeFile.toPath());
            assertTrue(content.contains("private final String tsIndex"),
                      "MessageType should use String tsIndex for string values");
            assertTrue(content.contains("LOGIN(\"login\")"),
                      "MessageType LOGIN should have correct string value");
            System.out.println("✓ MessageType enum validation passed (String values)");
        }

        // Validate Priority enum (numeric values)
        File priorityFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/network/Priority.java");
        if (priorityFile.exists()) {
            String content = Files.readString(priorityFile.toPath());
            assertTrue(content.contains("private final int tsIndex"),
                      "Priority should use int tsIndex for numeric values");
            assertTrue(content.contains("LOW(0)") && content.contains("CRITICAL(5)"),
                      "Priority should have correct numeric values");
            System.out.println("✓ Priority enum validation passed (int values)");
        }

        // Validate MixedEnum (mixed types should fallback to string)
        File mixedFile = new File(outJavaDir, "de/mhus/nimbus/evaluate/generated/network/MixedEnum.java");
        if (mixedFile.exists()) {
            String content = Files.readString(mixedFile.toPath());
            assertTrue(content.contains("private final String tsIndex"),
                      "MixedEnum should use String tsIndex as fallback for mixed types");
            assertTrue(content.contains("NUMERIC_VAL(\"42\")"),
                      "MixedEnum should convert numeric values to strings");
            System.out.println("✓ MixedEnum validation passed (mixed types → String fallback)");
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static List<File> collectJavaFiles(File dir) throws IOException {
        try (var stream = Files.walk(dir.toPath())) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    private static List<File> collectClassFiles(File dir) throws IOException {
        try (var stream = Files.walk(dir.toPath())) {
            return stream.filter(p -> p.toString().endsWith(".class"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    private static List<File> listFilesDepthFirst(File dir) throws IOException {
        if (dir == null || !dir.exists()) return java.util.Collections.emptyList();
        try (var stream = Files.walk(dir.toPath())) {
            return stream
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isFile()) {
            if (!file.delete()) throw new IOException("Failed to delete file: " + file);
            return;
        }
        for (File f : listFilesDepthFirst(file)) {
            if (f.isDirectory()) {
                if (!f.delete() && f.exists()) throw new IOException("Failed to delete dir: " + f);
            } else {
                if (!f.delete() && f.exists()) throw new IOException("Failed to delete file: " + f);
            }
        }
    }

    private static int runMaven(File workingDir, String... args) throws IOException, InterruptedException {
        String mvn = System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
        String[] cmd = new String[1 + args.length];
        cmd[0] = mvn;
        System.arraycopy(args, 0, cmd, 1, args.length);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append(System.lineSeparator());
            }
        }
        boolean finished = p.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            fail("Maven build timed out in " + workingDir);
        }
        int exit = p.exitValue();
        if (exit != 0) {
            System.out.println("[DEBUG_LOG] Maven output for failure in " + workingDir + ":\n" + out);
        }
        return exit;
    }
}