package de.mhus.nimbus.world.ai.tool;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingString;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AI Tool for web search via Serper.dev API (Google results as JSON).
 * Requires API key configured in SSettings as "serper.apiKey" (encrypted).
 * Free tier: 2500 queries. Register at https://serper.dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchToolService {

    private static final String SERPER_API_URL = "https://google.serper.dev/search";

    private final SSettingsService settingsService;
    private final ObjectMapper objectMapper;

    // Reusable client: each HttpClient instance holds its own selector/connection-pool
    // threads, so creating one per request leaks resources until GC.
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private SettingString apiKey;

    @PostConstruct
    void init() {
        apiKey = settingsService.getString("serper.apiKey", null);
        log.info("WebSearchToolService initialized, serper.apiKey available={}", isAvailable());
    }

    public boolean isAvailable() {
        String key = apiKey.get();
        boolean available = key != null && !key.isBlank();
        log.debug("WebSearchToolService.isAvailable(): available={}", available);
        return available;
    }

    @Tool("Search the web for information about a topic. Returns titles, URLs, and snippets.")
    public String searchWeb(
            @P("Search query") String query
    ) {
        if (!isAvailable()) {
            log.warn("Serper API key not configured");
            return "Error: Serper API key not configured";
        }
        log.info("Searching Serper for: {}", query);

        try {
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of("q", query, "num", 5)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERPER_API_URL))
                    .header("X-API-KEY", apiKey.get())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Serper returned status {}: {}", response.statusCode(), response.body());
                return "Error: Search returned status " + response.statusCode();
            }

            return parseResults(response.body());
        } catch (Exception e) {
            log.error("Error searching Serper", e);
            return "Error: " + e.getMessage();
        }
    }

    private String parseResults(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode organic = root.path("organic");

            if (organic.isMissingNode() || !organic.isArray() || organic.isEmpty()) {
                return "No results found";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (JsonNode item : organic) {
                String title = item.path("title").asText("");
                String link = item.path("link").asText("");
                String snippet = item.path("snippet").asText("");

                if (!title.isBlank()) {
                    sb.append("Title: ").append(title).append("\n");
                    sb.append("URL: ").append(link).append("\n");
                    if (!snippet.isBlank()) {
                        sb.append("Snippet: ").append(snippet).append("\n");
                    }
                    sb.append("\n");
                    count++;
                }
            }

            log.info("Serper returned {} results for query", count);
            return sb.isEmpty() ? "No results found" : sb.toString();
        } catch (Exception e) {
            log.error("Error parsing Serper response", e);
            return "Error parsing results: " + e.getMessage();
        }
    }
}
