package de.mhus.nimbus.world.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * AI Tool for searching the web via DuckDuckGo HTML interface.
 */
@Slf4j
@Service
public class DuckDuckGoSearchToolService {

    private static final String DUCKDUCKGO_SEARCH_URL = "https://html.duckduckgo.com/html/?q=";

    @Tool("Search the web for information about a topic")
    public String searchWeb(
            @P("Search query") String query
    ) {
        log.info("Searching DuckDuckGo for: {}", query);
        try {
            Document doc = Jsoup.connect(DUCKDUCKGO_SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8))
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer(DUCKDUCKGO_SEARCH_URL)
                    .timeout(12000)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .get();
            Element linksElement = doc.getElementById("links");
            if (linksElement == null) {
                return "No results found";
            }
            Elements results = linksElement.getElementsByClass("results_links");

            StringBuilder sb = new StringBuilder();
            for (Element result : results) {
                Element linksMain = result.getElementsByClass("links_main").first();
                if (linksMain == null) continue;
                Element title = linksMain.getElementsByTag("a").first();
                if (title == null) continue;
                sb.append("URL:").append(title.attr("href")).append("\n");
                sb.append("Title:").append(title.text()).append("\n");
                Element snippet = result.getElementsByClass("result__snippet").first();
                if (snippet != null) {
                    sb.append("Snippet:").append(snippet.text()).append("\n");
                }
                sb.append("\n");
            }

            String resultText = sb.toString();
            log.info("Search returned {} results", results.size());
            return resultText.isBlank() ? "No results found" : resultText;
        } catch (IOException e) {
            log.error("Error searching DuckDuckGo", e);
            return "Error: " + e.getMessage();
        }
    }
}
