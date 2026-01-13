package de.mhus.nimbus.shared.utils;

import java.nio.file.Path;
import java.security.PublicKey;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.IOException;

public class ConfidentialUtil {
    private static Path ROOT_DIR = Path.of("./confidential");

    public static void save(String file, PublicKey key) {
        if (file == null || file.isBlank()) throw new IllegalArgumentException("file name blank");
        if (key == null) throw new IllegalArgumentException("public key null");
        Path target = ROOT_DIR.resolve(file).normalize();
        try {
            Files.createDirectories(ROOT_DIR);
            String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
            Files.writeString(target, base64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save public key: " + e.getMessage(), e);
        }
    }

    public static void save(String file, String content) {
        if (file == null || file.isBlank()) throw new IllegalArgumentException("file name blank");
        if (content == null) throw new IllegalArgumentException("content null");
        Path target = ROOT_DIR.resolve(file).normalize();
        try {
            Files.createDirectories(ROOT_DIR);
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save content: " + e.getMessage(), e);
        }
    }
}
