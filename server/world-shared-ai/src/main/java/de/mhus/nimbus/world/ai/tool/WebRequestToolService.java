package de.mhus.nimbus.world.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * AI Tool for downloading and extracting text content from web URLs.
 *
 * <p>The target URL is attacker-influenced (LLM/prompt controlled), so every
 * request is guarded against SSRF: only {@code http}/{@code https} is allowed,
 * and the resolved host must not point at a loopback, link-local, site-local,
 * private, any-local, multicast or IPv6 unique-local address (which would let a
 * prompt reach cloud metadata endpoints, cluster-internal services or
 * ServiceAccount tokens). Redirects are followed manually so each hop is
 * re-validated instead of trusting Jsoup's automatic follow.
 */
@Slf4j
@Service
public class WebRequestToolService {

    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_MS = 12000;

    @Tool("Download a web page and return its text content")
    public String fetchWebPage(
            @P("The URL to download") String url
    ) {
        log.info("Downloading web page: {}", url);
        if (url == null || url.isBlank()) {
            return "URL is empty";
        }
        try {
            String current = url;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                validateUrl(current);
                Connection.Response response = Jsoup.connect(current)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                        .referrer("http://www.google.com")
                        .timeout(TIMEOUT_MS)
                        .followRedirects(false)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .execute();

                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        return "Error: redirect without location";
                    }
                    // Resolve relative redirects against the current URL and re-validate next loop.
                    current = new URL(new URL(current), location).toString();
                    continue;
                }

                String result = response.parse().body().text();
                log.info("Downloaded {} characters from {}", result.length(), current);
                return result;
            }
            return "Error: too many redirects";
        } catch (SecurityException e) {
            log.warn("Blocked SSRF-suspicious request to {}: {}", url, e.getMessage());
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            log.error("Error downloading web page: {}", url, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Rejects non-http(s) schemes and hosts that resolve to an internal address.
     *
     * @throws SecurityException if the URL is not safe to fetch
     */
    private void validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new SecurityException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new SecurityException("Only http/https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("URL has no host");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SecurityException("Host cannot be resolved");
        }
        for (InetAddress addr : addresses) {
            if (isInternal(addr)) {
                throw new SecurityException("Access to internal address is not allowed");
            }
        }
    }

    private boolean isInternal(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local addresses (fc00::/7) are not covered by isSiteLocalAddress().
        if (addr instanceof Inet6Address) {
            byte first = addr.getAddress()[0];
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }
}
