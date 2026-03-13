package de.mhus.nimbus.world.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/**
 * AI Tool for downloading and extracting text content from web URLs.
 */
@Slf4j
@Service
public class WebRequestToolService {

    @Tool("Download a web page and return its text content")
    public String fetchWebPage(
            @P("The URL to download") String url
    ) {
        log.info("Downloading web page: {}", url);
        if (url == null || url.isBlank()) {
            return "URL is empty";
        }
        if (url.contains("://localhost") || url.contains("://127.")) {
            return "Access to localhost is not allowed";
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .timeout(12000)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .get();
            var result = doc.body().text();
            log.info("Downloaded {} characters from {}", result.length(), url);
            return result;
        } catch (Exception e) {
            log.error("Error downloading web page: {}", url, e);
            return "Error: " + e.getMessage();
        }
    }
}
