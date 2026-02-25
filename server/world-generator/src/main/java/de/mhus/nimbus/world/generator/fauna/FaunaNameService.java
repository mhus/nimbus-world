package de.mhus.nimbus.world.generator.fauna;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * Loads feminine and masculine name lists from classpath resources
 * and provides random name selection for fauna entities.
 */
@Service
@Slf4j
public class FaunaNameService {

    private List<String> feminineNames;
    private List<String> masculineNames;

    @PostConstruct
    public void init() {
        feminineNames = loadNames("names-feminine.txt");
        masculineNames = loadNames("names-masculine.txt");
        log.info("Loaded {} feminine and {} masculine names", feminineNames.size(), masculineNames.size());
    }

    private List<String> loadNames(String resourceName) {
        try {
            var resource = new ClassPathResource(resourceName);
            try (var reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();
            }
        } catch (Exception e) {
            log.error("Failed to load names from {}", resourceName, e);
            return List.of();
        }
    }

    public String randomFeminineName(Random random) {
        if (feminineNames.isEmpty()) return "Unknown";
        return feminineNames.get(random.nextInt(feminineNames.size()));
    }

    public String randomMasculineName(Random random) {
        if (masculineNames.isEmpty()) return "Unknown";
        return masculineNames.get(random.nextInt(masculineNames.size()));
    }

    /**
     * Pick a random name matching the given gender.
     * For D (diverse), randomly picks from either list.
     */
    public String randomNameForGender(FaunaGender gender, Random random) {
        return switch (gender) {
            case W -> randomFeminineName(random);
            case M -> randomMasculineName(random);
            case D -> random.nextBoolean() ? randomFeminineName(random) : randomMasculineName(random);
        };
    }

    /**
     * Pick a random name for the given gender that differs from the excluded name.
     */
    public String differentNameForGender(FaunaGender gender, String excludeName, Random random) {
        List<String> nameList = switch (gender) {
            case W -> feminineNames;
            case M -> masculineNames;
            case D -> random.nextBoolean() ? feminineNames : masculineNames;
        };
        if (nameList.isEmpty()) return "Unknown";
        if (nameList.size() == 1) return nameList.getFirst();
        for (int i = 0; i < 20; i++) {
            String name = nameList.get(random.nextInt(nameList.size()));
            if (!name.equals(excludeName)) return name;
        }
        return nameList.get(random.nextInt(nameList.size()));
    }
}
