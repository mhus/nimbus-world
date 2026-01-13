package de.mhus.nimbus.shared.security;

import de.mhus.nimbus.shared.utils.RestTemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * REST Client für den Zugriff auf einen entfernten SharedKeyController.
 * Basis-URL muss konfiguriert werden (z.B. http://universe-host:port).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SharedClientService {

    public Optional<SharedKeyDto> createKey(String baseUrl,
                                            String token,
                                            String type,
                                            String kind,
                                            String algorithm,
                                            String owner,
                                            String intent,
                                            String name,
                                            String base64Key) {
        try {
            var req = new CreateKeyRequest(type, kind, algorithm, name, base64Key, owner, intent);
            URI uri = URI.create(baseUrl + "/shared/key");
            var restTemplate = RestTemplateUtil.create(token);
            ResponseEntity<SharedKeyDto> resp = restTemplate.postForEntity(uri, req, SharedKeyDto.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                log.info("Key angelegt: type={} kind={} owner={} intent={} id={}", type, kind, owner, intent, resp.getBody().getId());
                return Optional.of(resp.getBody());
            }
            log.warn("createKey fehlgeschlagen HTTP {}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("Fehler createKey: {}", e.toString());
        }
        return Optional.empty();
    }

    public Optional<SharedKeyDto> getKey(String baseUrl, String token, String id) {
        try {
            URI uri = URI.create(baseUrl + "/shared/key/" + id);
            var restTemplate = RestTemplateUtil.create(token);
            ResponseEntity<SharedKeyDto> resp = restTemplate.getForEntity(uri, SharedKeyDto.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) return Optional.of(resp.getBody());
        } catch (Exception e) {
            log.debug("getKey Fehler: {}", e.toString());
        }
        return Optional.empty();
    }

    public boolean updateKeyName(String baseUrl, String token, String id, String newName) {
        try {
            URI uri = URI.create(baseUrl + "/shared/key/" + id);
            var body = java.util.Map.of("name", newName);
            var restTemplate = RestTemplateUtil.create(token);
            restTemplate.put(uri, body);
            return true;
        } catch (Exception e) {
            log.warn("updateKeyName Fehler: {}", e.toString());
            return false;
        }
    }

    public boolean deleteKey(String baseUrl, String token, String id) {
        try {
            URI uri = URI.create(baseUrl + "/shared/key/" + id);
            var restTemplate = RestTemplateUtil.create(token);
            restTemplate.delete(uri);
            return true;
        } catch (Exception e) {
            log.warn("deleteKey Fehler: {}", e.toString());
            return false;
        }
    }

    public boolean existsKey(String baseUrl, String token, String type, String kind, String owner, String intent) {
        try {
            URI uri = URI.create(baseUrl + "/shared/key/exists?type=" + type + "&kind=" + kind + "&owner=" + owner + "&intent=" + intent);
            var restTemplate = RestTemplateUtil.create(token);

            ResponseEntity<Map> resp = restTemplate.getForEntity(uri, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object val = resp.getBody().get("exists");
                return Boolean.TRUE.equals(val) || (val instanceof Boolean b && b);
            }
        } catch (Exception e) {
            log.debug("existsKey Fehler", e);
        }
        return false;
    }

    public Optional<PublicKey> readPublicKeyFromFile(Path file) {
        try {
            if (!Files.exists(file) || !Files.isRegularFile(file)) return Optional.empty();
            String raw = Files.readString(file);
            String base64;
            if (raw.contains("BEGIN PUBLIC KEY")) {
                StringBuilder sb = new StringBuilder();
                boolean inside = false;
                for (String line : raw.split("\\R")) {
                    line = line.trim();
                    if (line.startsWith("-----BEGIN") && line.contains("PUBLIC KEY")) { inside = true; continue; }
                    if (line.startsWith("-----END") && line.contains("PUBLIC KEY")) { break; }
                    if (inside && !line.isEmpty() && !line.startsWith("#")) sb.append(line);
                }
                base64 = sb.toString();
            } else {
                base64 = raw.replaceAll("\\s+", "");
            }
            byte[] der = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            try {
                return Optional.of(KeyFactory.getInstance("EC").generatePublic(spec));
            } catch (Exception ignore) {}
            return Optional.of(KeyFactory.getInstance("RSA").generatePublic(spec));
        } catch (Exception e) {
            log.warn("Kann Public Key Datei nicht lesen: {}", e.toString());
            return Optional.empty();
        }
    }

    public Optional<String> toBase64PublicKey(PublicKey key) {
        try {
            return Optional.of(Base64.getEncoder().encodeToString(key.getEncoded()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Minimale lokale DTOs
    @lombok.Data @lombok.NoArgsConstructor @lombok.AllArgsConstructor

    public static class SharedKeyDto { private String id; private String type; private String kind; private String algorithm; private String keyId; private String owner; private String intent; private String createdAt; }
    @lombok.Data @lombok.AllArgsConstructor
    public static class CreateKeyRequest { private String type; private String kind; private String algorithm; private String name; private String key; private String owner; private String intent; }
}
