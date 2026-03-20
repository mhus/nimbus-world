package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.security.KeyId;
import de.mhus.nimbus.shared.security.KeyIntent;
import de.mhus.nimbus.shared.security.KeyService;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.shared.service.SSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;

/**
 * Client service for communication with the Universe server.
 * Manages the universe URL, pairing state and keys.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UniverseClientService {

    private static final String SETTING_UNIVERSE_URL = "universe.url";
    private static final String SETTING_UNIVERSE_NAME = "universe.name";
    private static final String SETTING_UNIVERSE_PAIRED = "universe.paired";

    private final SSettingsService settingsService;
    private final KeyService keyService;
    private final JwtService jwtService;
    private final RestTemplate restTemplate = new RestTemplate();

    // --- URL ---

    public String getUniverseUrl() {
        return settingsService.getStringValue(SETTING_UNIVERSE_URL, "");
    }

    public void setUniverseUrl(String url) {
        settingsService.setStringValue(SETTING_UNIVERSE_URL, url.trim());
        log.info("Universe URL saved: {}", url.trim());
    }

    // --- Status ---

    public String getUniverseName() {
        return settingsService.getStringValue(SETTING_UNIVERSE_NAME, "");
    }

    public boolean isPaired() {
        return settingsService.getBooleanValue(SETTING_UNIVERSE_PAIRED, false);
    }

    public UniverseStatus getStatus() {
        return new UniverseStatus(
                getUniverseUrl(),
                isPaired(),
                getUniverseName()
        );
    }

    // --- Ping ---

    /**
     * Pings the universe. If paired, performs an authenticated key-validated ping.
     * If not paired, falls back to a simple health check.
     */
    public PingResult ping() {
        String url = getUniverseUrl();
        if (url.isBlank()) {
            return PingResult.fail("Universe URL not configured");
        }

        // Simple health ping first
        PingResult healthResult = pingHealth(url);
        if (!healthResult.ok()) {
            return healthResult;
        }

        // If paired, also do authenticated key ping
        if (isPaired()) {
            String name = getUniverseName();
            if (name.isBlank()) {
                return PingResult.ok(healthResult.status() + " (paired but no sector name)");
            }
            PingResult keyResult = pingWithKeys(url, name);
            if (!keyResult.ok()) {
                return PingResult.fail("Health OK, but key ping failed: " + keyResult.error());
            }
            return PingResult.ok(healthResult.status() + " — keys verified");
        }

        return healthResult;
    }

    private PingResult pingHealth(String url) {
        try {
            var response = restTemplate.getForEntity(url + "/actuator/health", Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                var body = response.getBody();
                String status = body != null ? String.valueOf(body.get("status")) : "unknown";
                log.info("Universe health ping OK: {} status={}", url, status);
                return PingResult.ok(status);
            } else {
                return PingResult.fail("HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Universe health ping failed: {} — {}", url, e.getMessage());
            return PingResult.fail(e.getMessage());
        }
    }

    /**
     * Authenticated ping: creates a JWT signed with sector private key, sends to universe,
     * receives a JWT signed with universe private key, validates it with stored universe public key.
     */
    private PingResult pingWithKeys(String url, String sectorName) {
        try {
            // Create JWT signed with sector private key
            KeyIntent sectorIntent = KeyIntent.of(sectorName, KeyIntent.SECTOR_SERVER_JWT_TOKEN);
            String sectorToken = jwtService.createTokenWithPrivateKey(
                    KeyType.SECTOR,
                    sectorIntent,
                    "sector-ping:" + sectorName,
                    Map.of("sector", sectorName),
                    java.time.Instant.now().plusSeconds(60)
            );

            // Send to universe
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(sectorToken);
            var response = restTemplate.exchange(
                    url + "/universe/sector/" + sectorName + "/ping",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return PingResult.fail("Universe key ping HTTP " + response.getStatusCode());
            }

            Boolean ok = (Boolean) response.getBody().get("ok");
            if (!Boolean.TRUE.equals(ok)) {
                return PingResult.fail("Universe key ping returned not ok");
            }

            // Validate response JWT signed with universe private key
            String responseToken = (String) response.getBody().get("token");
            if (responseToken == null || responseToken.isBlank()) {
                return PingResult.fail("No response token from universe");
            }

            KeyIntent universeIntent = KeyIntent.of(sectorName, KeyIntent.MAIN_JWT_TOKEN);
            var claims = jwtService.validateTokenWithPublicKey(responseToken, KeyType.UNIVERSE, universeIntent);
            if (claims.isEmpty()) {
                return PingResult.fail("Universe response token validation failed — key mismatch");
            }

            log.info("Universe key ping OK for sector '{}'", sectorName);
            return PingResult.ok("keys verified");

        } catch (Exception e) {
            log.warn("Universe key ping failed for sector '{}': {}", sectorName, e.getMessage());
            return PingResult.fail(e.getMessage());
        }
    }

    // --- User Lookup ---

    /**
     * Queries the universe for user info by username.
     * @return UserInfo or null if not found or not paired
     */
    public UserInfo getUserInfo(String username) {
        String url = getUniverseUrl();
        String name = getUniverseName();
        if (url.isBlank() || name.isBlank() || !isPaired()) {
            return null;
        }
        try {
            String token = jwtService.createTokenWithPrivateKey(
                    KeyType.SECTOR,
                    KeyIntent.of(name, KeyIntent.SECTOR_SERVER_JWT_TOKEN),
                    "sector:" + name,
                    Map.of("action", "getUserInfo"),
                    java.time.Instant.now().plusSeconds(60)
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            var response = restTemplate.exchange(
                    url + "/universe/sector/" + name + "/user/" + username,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return new UserInfo(
                        (String) response.getBody().get("username"),
                        (String) response.getBody().get("email")
                );
            }
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("getUserInfo failed for '{}': {}", username, e.getMessage());
            return null;
        }
    }

    public record UserInfo(String username, String email) {}

    // --- Sync World ---

    /**
     * Syncs a single world with the universe based on its flags:
     * - universeSync=true + enabled=true → register/update at universe
     * - universeSync=false OR enabled=false → unregister from universe
     * Does nothing if not paired.
     */
    public PairResult syncWorld(de.mhus.nimbus.world.shared.world.WWorld world) {
        if (!isPaired()) return PairResult.fail("Not paired");
        if (world.isUniverseSync() && world.isEnabled()) {
            return registerWorld(world);
        } else {
            return removeWorldFromUniverse(world.getWorldId());
        }
    }

    /**
     * Removes a world from the universe regardless of sync flag.
     * Silently succeeds if the world doesn't exist at universe.
     */
    private PairResult removeWorldFromUniverse(String worldId) {
        String url = getUniverseUrl();
        String name = getUniverseName();
        try {
            String token = createSectorToken(name, "unregisterWorld");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(
                    url + "/universe/sector/" + name + "/world/" + worldId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Map.class
            );
            log.info("World '{}' removed from universe", worldId);
            return PairResult.ok(worldId);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.debug("World '{}' not found at universe (already removed)", worldId);
            return PairResult.ok(worldId);
        } catch (Exception e) {
            log.warn("removeWorldFromUniverse failed for '{}': {}", worldId, e.getMessage());
            return PairResult.fail(e.getMessage());
        }
    }

    // --- Pair ---

    /**
     * Pairs this sector with the universe using an invite token.
     * The invite token format is "sectorName:token".
     *
     * 1. Parse name from invite token, save to settings
     * 2. Create sector ECC key pair if not exists
     * 3. Send invite token + sector public key to universe
     * 4. Receive universe public key, store it
     * 5. Mark as paired
     */
    public PairResult pair(String inviteToken) {
        String url = getUniverseUrl();
        if (url.isBlank()) {
            return PairResult.fail("Universe URL not configured");
        }
        if (inviteToken == null || !inviteToken.contains(":")) {
            return PairResult.fail("Invalid invite token format (expected name:token)");
        }

        int colonIdx = inviteToken.indexOf(':');
        String sectorName = inviteToken.substring(0, colonIdx).trim();
        String token = inviteToken.substring(colonIdx + 1).trim();

        if (sectorName.isBlank() || token.isBlank()) {
            return PairResult.fail("Invalid invite token format");
        }

        // Save sector name
        settingsService.setStringValue(SETTING_UNIVERSE_NAME, sectorName);

        // Create or get sector key pair
        KeyIntent sectorIntent = KeyIntent.of(sectorName, KeyIntent.SECTOR_SERVER_JWT_TOKEN);
        if (keyService.getLatestPrivateKey(KeyType.SECTOR, sectorIntent).isEmpty()) {
            KeyPair keyPair = keyService.createECCKeys();
            keyService.storeKeyPair(KeyType.SECTOR, KeyId.newOf(sectorIntent), keyPair);
            log.info("Created sector key pair for '{}'", sectorName);
        }

        // Get sector public key as Base64
        var publicKeys = keyService.getPublicKeysForIntent(KeyType.SECTOR, sectorIntent);
        if (publicKeys.isEmpty()) {
            return PairResult.fail("Sector public key not found after creation");
        }
        String sectorPublicKeyBase64 = Base64.getEncoder().encodeToString(publicKeys.getFirst().getEncoded());

        // Send exchange request to universe
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> requestBody = Map.of(
                    "inviteToken", inviteToken,
                    "publicKey", sectorPublicKeyBase64
            );
            var response = restTemplate.postForEntity(
                    url + "/universe/sector/exchange",
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return PairResult.fail("Exchange failed: HTTP " + response.getStatusCode());
            }

            String universePublicKeyBase64 = (String) response.getBody().get("universePublicKey");
            if (universePublicKeyBase64 == null || universePublicKeyBase64.isBlank()) {
                return PairResult.fail("No universe public key in response");
            }

            // Store universe public key
            KeyIntent universeIntent = KeyIntent.of(sectorName, KeyIntent.MAIN_JWT_TOKEN);
            KeyId universeKeyId = KeyId.newOf(universeIntent);
            keyService.storePublicKey(KeyType.UNIVERSE, universeKeyId, universePublicKeyBase64);
            log.info("Stored universe public key for sector '{}'", sectorName);

            // Mark as paired
            settingsService.setBooleanValue(SETTING_UNIVERSE_PAIRED, true);
            log.info("Sector '{}' paired with universe", sectorName);

            return PairResult.ok(sectorName);

        } catch (Exception e) {
            log.error("Pairing failed for sector '{}': {}", sectorName, e.getMessage());
            return PairResult.fail(e.getMessage());
        }
    }

    // --- World Register/Unregister ---

    /**
     * Registers or updates a world at the universe.
     * Checks that the world has universeSync=true before sending.
     */
    public PairResult registerWorld(de.mhus.nimbus.world.shared.world.WWorld world) {
        if (!isPaired()) return PairResult.fail("Not paired");
        if (!world.isUniverseSync()) return PairResult.fail("World does not have universeSync enabled");
        if (!world.isEnabled()) return PairResult.fail("World is not enabled");

        String url = getUniverseUrl();
        String name = getUniverseName();

        boolean isPublic = world.isPublicFlag();
        java.util.List<String> members;
        if (isPublic) {
            members = java.util.List.of("*");
        } else {
            var all = new java.util.LinkedHashSet<String>();
            if (world.getOwner() != null) all.addAll(world.getOwner());
            if (world.getEditor() != null) all.addAll(world.getEditor());
            if (world.getSupporter() != null) all.addAll(world.getSupporter());
            if (world.getPlayer() != null) all.addAll(world.getPlayer());
            members = new java.util.ArrayList<>(all);
        }

        try {
            String token = createSectorToken(name, "registerWorld");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            var body = Map.of(
                    "worldId", world.getWorldId(),
                    "name", world.getPublicData() != null && world.getPublicData().getTitle() != null ? world.getPublicData().getTitle() : world.getWorldId(),
                    "description", world.getDescription() != null ? world.getDescription() : "",
                    "publicWorld", isPublic,
                    "members", members
            );

            restTemplate.exchange(
                    url + "/universe/sector/" + name + "/world",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            log.info("World '{}' registered at universe", world.getWorldId());
            return PairResult.ok(world.getWorldId());
        } catch (Exception e) {
            log.error("registerWorld failed for '{}': {}", world.getWorldId(), e.getMessage());
            return PairResult.fail(e.getMessage());
        }
    }

    /**
     * Unregisters a world from the universe.
     * Checks that the world has universeSync=true before sending.
     */
    public PairResult unregisterWorld(de.mhus.nimbus.world.shared.world.WWorld world) {
        if (!isPaired()) return PairResult.fail("Not paired");
        if (!world.isUniverseSync()) return PairResult.fail("World does not have universeSync enabled");

        String url = getUniverseUrl();
        String name = getUniverseName();

        try {
            String token = createSectorToken(name, "unregisterWorld");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            restTemplate.exchange(
                    url + "/universe/sector/" + name + "/world/" + world.getWorldId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Map.class
            );
            log.info("World '{}' unregistered from universe", world.getWorldId());
            return PairResult.ok(world.getWorldId());
        } catch (Exception e) {
            log.error("unregisterWorld failed for '{}': {}", world.getWorldId(), e.getMessage());
            return PairResult.fail(e.getMessage());
        }
    }

    private String createSectorToken(String sectorName, String action) {
        return jwtService.createTokenWithPrivateKey(
                KeyType.SECTOR,
                KeyIntent.of(sectorName, KeyIntent.SECTOR_SERVER_JWT_TOKEN),
                "sector:" + sectorName,
                Map.of("action", action),
                java.time.Instant.now().plusSeconds(60)
        );
    }

    // --- Unpair ---

    /**
     * Unpairs this sector from the universe.
     * 1. Calls universe to delete keys and worlds
     * 2. Deletes local universe public key
     * 3. Resets paired status
     */
    public PairResult unpair() {
        String url = getUniverseUrl();
        String name = getUniverseName();
        if (url.isBlank() || name.isBlank() || !isPaired()) {
            return PairResult.fail("Not paired");
        }

        // Call universe unpair endpoint
        try {
            String token = jwtService.createTokenWithPrivateKey(
                    KeyType.SECTOR,
                    KeyIntent.of(name, KeyIntent.SECTOR_SERVER_JWT_TOKEN),
                    "sector:" + name,
                    Map.of("action", "unpair"),
                    java.time.Instant.now().plusSeconds(60)
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(
                    url + "/universe/sector/" + name + "/unpair",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Map.class
            );
            log.info("Universe unpair successful for sector '{}'", name);
        } catch (Exception e) {
            log.warn("Universe unpair call failed (continuing with local cleanup): {}", e.getMessage());
        }

        // Delete local universe public key
        KeyIntent universeIntent = KeyIntent.of(name, KeyIntent.MAIN_JWT_TOKEN);
        keyService.deleteAllForIntent(KeyType.UNIVERSE, universeIntent);
        log.info("Deleted local universe public key for sector '{}'", name);

        // Reset status
        settingsService.setBooleanValue(SETTING_UNIVERSE_PAIRED, false);
        settingsService.setStringValue(SETTING_UNIVERSE_NAME, "");
        log.info("Sector '{}' unpaired", name);

        return PairResult.ok("Unpaired");
    }

    // --- Records ---

    public record UniverseStatus(String url, boolean paired, String name) {}

    public record PingResult(boolean ok, String status, String error) {
        public static PingResult ok(String status) { return new PingResult(true, status, null); }
        public static PingResult fail(String error) { return new PingResult(false, null, error); }
    }

    public record PairResult(boolean ok, String name, String error) {
        public static PairResult ok(String name) { return new PairResult(true, name, null); }
        public static PairResult fail(String error) { return new PairResult(false, null, error); }
    }
}
