package de.mhus.nimbus.world.shared.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters forbidden words from player messages by replacing them with ***.
 * Words are loaded from forbidden-words.txt in classpath (one word per line).
 * Lines starting with # are comments. Matching is case-insensitive on whole words.
 */
@Service
@Slf4j
public class ForbiddenWordFilter {

    private final List<Pattern> patterns = new ArrayList<>();

    @PostConstruct
    void init() {
        try {
            var resource = new ClassPathResource("forbidden-words.txt");
            try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    patterns.add(Pattern.compile("(?i)\\b" + Pattern.quote(line) + "\\b"));
                }
            }
            log.info("Loaded {} forbidden word patterns", patterns.size());
        } catch (Exception e) {
            log.warn("Could not load forbidden-words.txt: {}", e.getMessage());
        }
    }

    /**
     * Replace all forbidden words in the text with ***.
     */
    public String filter(String text) {
        if (text == null || text.isEmpty() || patterns.isEmpty()) return text;
        for (Pattern pattern : patterns) {
            text = pattern.matcher(text).replaceAll("***");
        }
        return text;
    }
}
