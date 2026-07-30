package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingBoolean;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.settings.SettingString;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Configuration properties for AccessService.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessSettings {

    private final SSettingsService settingsService;

    private SettingInteger tokenExpirationSeconds;
    private SettingInteger sessionTokenTtlSeconds;
    private SettingInteger agentTokenTtlSeconds;
    private SettingBoolean secureCookies;
    private SettingInteger closeSessionTimeoutSeconds;
    private SettingBoolean devLoginEnabled;
    private SettingString devLoginAccessKey;

    @Value( "${nimbus.access.accessUrls:}")
    private String accessUrls;
    @Value( "${nimbus.access.jumpUrlAgent:}")
    private String jumpUrlAgent;
    @Value( "${nimbus.access.jumpUrlEditor:}")
    private String jumpUrlEditor;
    @Value( "${nimbus.access.jumpUrlViewer:}")
    private String jumpUrlViewer;
    @Value( "${nimbus.access.controlsBaseUrl:}")
    private String controlsBaseUrl;
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
        closeSessionTimeoutSeconds = settingsService.getInteger(
                "access.closeSessionTimeoutSeconds",
                10
        );
        devLoginEnabled = settingsService.getBoolean(
                "access.devLoginEnabled",
                true
        );
        devLoginAccessKey = settingsService.getString(
                "access.devLoginAccessKey",
                UUID.randomUUID() + "-" + UUID.randomUUID()
        );
        // Do not log the dev-login access key itself (secret). Only note that one is configured.
        log.warn("dev-login access key configured (value hidden)");
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

    private String getControlsBase() {
        return Strings.isBlank(controlsBaseUrl) ? "http://localhost:3002/controls" : controlsBaseUrl;
    }

    public String getLoginUrl() {
        return Strings.isBlank(loginUrl) ?
            getControlsBase() + "/dev-login.html"
                :
            loginUrl;
    }

    public String getLogoutUrl() {
        return Strings.isBlank(logoutUrl) ?
            getControlsBase() + "/login-forward.html"
                :
            logoutUrl;
    }

    public String getTeleportUrl() {
        return Strings.isBlank(teleportUrl) ?
            getControlsBase() + "/teleport-login.html"
                :
            teleportUrl;
    }

    /**
     * Timeout in seconds for waiting for a session to close gracefully.
     * Default: 10 seconds
     */
    public int getCloseSessionTimeoutSeconds() {
        return closeSessionTimeoutSeconds.get();
    }

    /**
     * Runtime toggle for dev-login. Acts as a soft-gate on top of the
     * environment-based hard-gate {@code nimbus.devlogin.enabled}.
     */
    public boolean isDevLoginEnabled() {
        return devLoginEnabled.get();
    }

    /**
     * Access key required in addition to a valid dev-login request.
     * Generated on first startup and logged at WARN level if not set.
     */
    public String getDevLoginAccessKey() {
        return devLoginAccessKey.get();
    }
}
