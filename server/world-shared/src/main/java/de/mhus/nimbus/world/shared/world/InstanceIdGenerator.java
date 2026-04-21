package de.mhus.nimbus.world.shared.world;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Generates human-readable instance IDs from UUIDs.
 *
 * Format: i-{word1}-{word2}-{word3}-{hex8}
 * Example: UUID 5984797a-0eac-4782-9ada-2f7c76af911a -> i-gorge-scarlet-bridge-5984797a
 *
 * The words are derived deterministically from UUID segments using a word list.
 * The trailing 8 hex chars ensure uniqueness.
 * Prefix "i-" marks player instance IDs (vs "e-" for editor instances).
 */
@Component
@Slf4j
public class InstanceIdGenerator {

    private static final String WORD_LIST_RESOURCE = "instance-words.txt";
    private static final String PLAYER_PREFIX = "i";

    private List<String> words;

    @PostConstruct
    void init() {
        try {
            var resource = new ClassPathResource(WORD_LIST_RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                words = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .toList();
            }
            log.info("Loaded {} words for instance ID generation", words.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load word list from " + WORD_LIST_RESOURCE, e);
        }
    }

    /**
     * Generate a human-readable instance ID from a UUID.
     *
     * @return ID in format: i-{word1}-{word2}-{word3}-{hex8}
     */
    public String generate() {
        UUID uuid = UUID.randomUUID();
        return fromUuid(uuid);
    }

    /**
     * Generate a human-readable instance ID from an existing UUID.
     * Deterministic: same UUID always produces the same ID.
     */
    String fromUuid(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        // Extract 16-bit segments for word selection
        int seg1 = (int) ((msb >> 16) & 0xFFFF);  // from UUID segment 3 (4782)
        int seg2 = (int) ((lsb >> 48) & 0xFFFF);   // from UUID segment 4 (9ada)
        int seg3 = (int) ((lsb >> 32) & 0xFFFF);   // from UUID segment 5 first part (2f7c)

        // Trailing 8 hex chars from first UUID segment
        String trailingHex = String.format("%08x", (int) (msb >> 32));

        int size = words.size();
        String word1 = words.get(Math.floorMod(seg1, size));
        String word2 = words.get(Math.floorMod(seg2, size));
        String word3 = words.get(Math.floorMod(seg3, size));

        return PLAYER_PREFIX + "-" + word1 + "-" + word2 + "-" + word3 + "-" + trailingHex;
    }
}
