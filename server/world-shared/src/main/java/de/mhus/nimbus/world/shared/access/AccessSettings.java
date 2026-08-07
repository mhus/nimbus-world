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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private SettingString devLoginAccessKeySetting;
    /** Key read from (or generated into) the key file; only used when nothing is configured explicitly. */
    private String devLoginAccessKeyFromFile;

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

    /**
     * Off-by-default env gate for the whole dev-login feature ({@code nimbus.devlogin.enabled}, same
     * flag {@code ControlAccessFilter} uses). The dev-login key is only ever needed when this is on;
     * when off (the production default) we never touch the filesystem for the key.
     */
    @Value("${nimbus.devlogin.enabled:false}")
    private boolean devLoginEnvEnabled;

    /**
     * Explicitly configured dev-login access key ({@code nimbus.devlogin.key} /
     * {@code NIMBUS_DEVLOGIN_KEY}). Highest precedence — this is the way to inject the key into a
     * container (Secret/ConfigMap) or a test harness, where the key file is not available and the
     * filesystem may be read-only. Empty = not configured.
     */
    @Value("${nimbus.devlogin.key:}")
    private String devLoginKeyProperty;

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
        devLoginAccessKeySetting = settingsService.getString(
                "access.devLoginAccessKey",
                ""
        );
        // Only fall back to the key file when dev-login is enabled for this process AND no key is
        // configured explicitly. In production (dev-login off) we must not touch the filesystem at
        // all: a read-only container FS would otherwise fail bean init and crash-loop the pod for a
        // feature that is never used.
        String configured = devLoginEnvEnabled ? configuredDevLoginAccessKey() : "";
        if (Strings.isNotBlank(configured)) {
            // Warn instead of failing: an explicitly configured key is a deliberate operator choice,
            // and a hard failure here would take the whole service down over a dev-only feature.
            if (configured.length() < DEV_LOGIN_KEY_MIN_LENGTH) {
                log.warn("SECURITY: configured dev-login access key is shorter than {} characters",
                        DEV_LOGIN_KEY_MIN_LENGTH);
            }
            log.info("Using explicitly configured dev-login access key (key file not used)");
            devLoginAccessKeyFromFile = null;
        } else {
            devLoginAccessKeyFromFile = devLoginEnvEnabled ? resolveDevLoginAccessKeyFromFile() : null;
        }
    }

    /** Confidential file holding the dev-login access key (git-ignored, written in the process CWD). */
    private static final Path DEV_LOGIN_KEY_FILE = Path.of("confidential", "dev-login-key.txt");
    private static final int DEV_LOGIN_KEY_MIN_LENGTH = 16;

    /**
     * The explicitly configured key, in precedence order: the {@code nimbus.devlogin.key} property
     * (env {@code NIMBUS_DEVLOGIN_KEY}) wins over the {@code access.devLoginAccessKey} setting.
     * Blank when nothing is configured — then the key file is used.
     */
    private String configuredDevLoginAccessKey() {
        if (Strings.isNotBlank(devLoginKeyProperty)) {
            return devLoginKeyProperty.strip();
        }
        String fromSettings = devLoginAccessKeySetting == null ? null : devLoginAccessKeySetting.get();
        return Strings.isBlank(fromSettings) ? "" : fromSettings.strip();
    }

    /**
     * Resolves the dev-login access key from {@link #DEV_LOGIN_KEY_FILE}: if the file exists,
     * its single non-empty line is used (validated for length); otherwise a new key is generated
     * and written to the file. The key value itself is never written to the log.
     */
    private String resolveDevLoginAccessKeyFromFile() {
        try {
            if (Files.exists(DEV_LOGIN_KEY_FILE)) {
                List<String> lines = Files.readAllLines(DEV_LOGIN_KEY_FILE).stream()
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (lines.size() != 1) {
                    throw new IllegalStateException("Dev-login key file must contain exactly one non-empty line: "
                            + DEV_LOGIN_KEY_FILE.toAbsolutePath());
                }
                String key = lines.get(0);
                if (key.length() < DEV_LOGIN_KEY_MIN_LENGTH) {
                    throw new IllegalStateException("Dev-login key is too short (min " + DEV_LOGIN_KEY_MIN_LENGTH
                            + " characters): " + DEV_LOGIN_KEY_FILE.toAbsolutePath());
                }
                log.info("Using dev-login access key from {}", DEV_LOGIN_KEY_FILE.toAbsolutePath());
                return key;
            }
            String key = UUID.randomUUID() + "-" + UUID.randomUUID();
            if (DEV_LOGIN_KEY_FILE.getParent() != null) {
                Files.createDirectories(DEV_LOGIN_KEY_FILE.getParent());
            }
            Files.writeString(DEV_LOGIN_KEY_FILE, key + System.lineSeparator());
            log.warn("Generated new dev-login access key -> {}", DEV_LOGIN_KEY_FILE.toAbsolutePath());
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to access dev-login key file "
                    + DEV_LOGIN_KEY_FILE.toAbsolutePath(), e);
        }
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
     * Access key required in addition to a valid dev-login request. Resolved in this order:
     * <ol>
     *   <li>{@code nimbus.devlogin.key} property / {@code NIMBUS_DEVLOGIN_KEY} env — for containers
     *       and test harnesses (Secret/ConfigMap), read live;</li>
     *   <li>{@code access.devLoginAccessKey} setting — same purpose, configurable at runtime;</li>
     *   <li>the confidential dev-login key file, read or generated once at startup — the local
     *       development convenience path.</li>
     * </ol>
     * Always {@code null} when dev-login is disabled ({@code nimbus.devlogin.enabled=false}, the
     * production default), so the feature cannot be reached and the filesystem is never touched.
     */
    public String getDevLoginAccessKey() {
        if (!devLoginEnvEnabled) {
            return null;
        }
        String configured = configuredDevLoginAccessKey();
        return Strings.isNotBlank(configured) ? configured : devLoginAccessKeyFromFile;
    }
}
