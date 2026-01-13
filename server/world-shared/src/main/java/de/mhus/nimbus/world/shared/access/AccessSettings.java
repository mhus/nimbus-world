package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingInteger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for AccessService.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class AccessSettings {

    private final SSettingsService settingsService;

    private SettingInteger tokenExpirationSeconds;
    private SettingInteger sessionTokenTtlSeconds;
    private SettingInteger agentTokenTtlSeconds;
    private SettingBoolean secureCookies;

    @Value( "${nimbus.access.accessUrls:}")
    private String accessUrls;
    @Value( "${nimbus.access.jumpUrlAgent:}")
    private String jumpUrlAgent;
    @Value( "${nimbus.access.jumpUrlEditor:}")
    private String jumpUrlEditor;
    @Value( "${nimbus.access.jumpUrlViewer:}")
    private String jumpUrlViewer;
    @Value( "${nimbus.access.loginUrl:}")
    private String loginUrl;
    @Value( "${nimbus.access.logoutUrl:}")
    private String logoutUrl;
    @Value( "${nimbus.access.teleportUrl:}")
    private String teleportUrl;
    @Value( "${nimbus.access.cookieDomain:}")
    private String cookieDomain;
    @Value( "${nimbus.access.editorUrl:}")
    private String editorUrl;

    @PostConstruct
    private void init() {
        tokenExpirationSeconds = settingsService.getInteger(
                "access.tokenExpirationSeconds",
                300
        );
        sessionTokenTtlSeconds = settingsService.getInteger(
                "access.sessionTokenTtlSeconds",
                86400
        );
        agentTokenTtlSeconds = settingsService.getInteger(
                "access.agentTokenTtlSeconds",
                3600
        );
        secureCookies = settingsService.getBoolean(
                "access.secureCookies",
                false
        );
    }

    /**
     * Access token expiration in seconds.
     * Default: 300 seconds (5 minutes)
     */
    public long getTokenExpirationSeconds() {
        return tokenExpirationSeconds.get();
    }

    /**
     * Cookie URLs for multi-domain cookie setup.
     */
    public List<String> getAccessUrls() {
        String urls = Strings.isBlank(accessUrls) ?
                "http://localhost:9042/player/aaa/authorize,http://localhost:9043/control/aaa/authorize"
                :
                accessUrls;
        if (urls == null || urls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(urls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Jump URL to redirect after login (agent mode).
     * {worldId} placeholder will be replaced with actual worldId.
     */
    public String getJumpUrlAgent() {
        return Strings.isBlank(jumpUrlAgent) ?
                "http://localhost:3002?worldId={worldId}"
                :
                jumpUrlAgent;
    }

    /**
     * Jump URL to redirect after login (session mode).
     * {worldId} and {session} placeholders will be replaced.
     */
    public String getJumpUrlEditor() {
        return Strings.isBlank(jumpUrlEditor) ?
            "http://localhost:3001?worldId={worldId}&session={session}"
                :
            jumpUrlEditor;
    }

    /**
     * Jump URL to redirect after login (session mode).
     * {worldId} and {session} placeholders will be replaced.
     */
    public String getJumpUrlViewer() {
        return Strings.isBlank(jumpUrlViewer) ?
            "http://localhost:3000?worldId={worldId}&session={session}"
                :
            jumpUrlViewer;
    }

    /**
     * Session token TTL in seconds (for agent=false).
     * Default: 86400 seconds (24 hours)
     */
    public long getSessionTokenTtlSeconds() {
        return sessionTokenTtlSeconds.get();
    }

    /**
     * Agent token TTL in seconds (for agent=true).
     * Default: 3600 seconds (1 hour)
     */
    public long getAgentTokenTtlSeconds() {
        return agentTokenTtlSeconds.get();
    }

    /**
     * Whether to use secure cookies (HTTPS only).
     * Should be true in production, false for local development.
     * Default: false
     */
    public boolean isSecureCookies() {
        return secureCookies.get();
    }

    /**
     * Cookie domain for multi-domain setup.
     * If null/empty, cookies are set for the current domain only.
     * Example: ".example.com" for *.example.com
     */
    public String getCookieDomain() {
        return Strings.isBlank(cookieDomain) ?
            null
                :
            cookieDomain;
    }

    public String getLoginUrl() {
        return Strings.isBlank(loginUrl) ?
            "http://localhost:3002/controls/dev-login.html"
                :
            loginUrl;
    }

    public String getLogoutUrl() {
        return Strings.isBlank(logoutUrl) ?
            "http://localhost:3002/controls/dev-login.html"
                :
            logoutUrl;
    }

    public String getTeleportUrl() {
        return Strings.isBlank(teleportUrl) ?
            "http://localhost:3002/controls/teleport-login.html"
                :
            teleportUrl;
    }
}
