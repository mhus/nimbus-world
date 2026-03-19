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
