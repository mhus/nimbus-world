package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.shared.dto.universe.URegionRequest;
import de.mhus.nimbus.shared.dto.universe.URegionResponse;
import de.mhus.nimbus.shared.security.FormattedKey;
import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.types.UniverseWorldDto;
import de.mhus.nimbus.shared.utils.RestTemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client zum Universe-Server für Welt-Erstellung / Abfrage über Region.
 * Endpunkte:
 *  GET  /universe/region/{regionId}/world/{worldId}
 *  POST /universe/region/{regionId}/world/{worldId}
 */
@Service
@Slf4j
@Component
@RequiredArgsConstructor
public class RUniverseClientService {

    private final JwtService jwtService;
    private final RegionSettings regionProperties;

    public Optional<UniverseWorldDto> fetch(String regionId, String worldId) {
        String url = regionProperties.getUniverseBaseUrl() + "/universe/region/" + encode(regionId) + "/world/" + encode(worldId);
        try {
            var restTemplate = RestTemplateUtil.create(jwtService.createTokenForRegion(regionId));
            @SuppressWarnings("unchecked") ResponseEntity<Map<String,Object>> resp = (ResponseEntity) restTemplate.getForEntity(URI.create(url), Map.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                Map<String,Object> b = resp.getBody();
                return Optional.of(new UniverseWorldDto(
                        (String)b.get("id"),
                        (String)b.get("name"),
                        (String)b.get("description"),
                        (String)b.get("regionId"),
                        (String)b.get("worldId"),
                        (String)b.get("coordinates")
                ));
            }
            if (resp.getStatusCode() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw new IllegalStateException("Unexpected status " + resp.getStatusCode());
        } catch (RestClientException e) { throw new RuntimeException("Universe world fetch failed: " + e.getMessage(), e); }
    }

    public UniverseWorldDto create(String regionId, String worldId, String name, String description, String coordinates) {
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId blank");
        if (worldId == null || worldId.isBlank()) throw new IllegalArgumentException("worldId blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        String url = regionProperties.getUniverseBaseUrl() + "/universe/region/" + encode(regionId) + "/world/" + encode(worldId);
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("name", name);
        if (description != null) body.put("description", description);
        if (coordinates != null) body.put("coordinates", coordinates);
        try {
            var request = new org.springframework.http.HttpEntity<>(body, buildHeaders(null));
            var restTemplate = RestTemplateUtil.create(jwtService.createTokenForRegion(regionId));
            @SuppressWarnings("unchecked") ResponseEntity<UniverseWorldDto> resp = restTemplate.postForEntity(url, request, UniverseWorldDto.class);
            if (resp.getStatusCode() == HttpStatus.CREATED && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new IllegalStateException("Unexpected status " + resp.getStatusCode());
        } catch (RestClientException e) { throw new RuntimeException("Universe world create failed: " + e.getMessage(), e); }
    }

    public boolean createRegion(String token, String name, String apiUrl, FormattedKey publicSignKey, String maintainers) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        if (apiUrl == null || apiUrl.isBlank()) throw new IllegalArgumentException("apiUrl blank");
        String url = regionProperties.getUniverseBaseUrl() + "/universe/region";
        try {
            var body = new URegionRequest(name, apiUrl, publicSignKey.toString(), maintainers);
            log.debug("Sending region request: {}", body);
            var request = new org.springframework.http.HttpEntity<>(body, buildHeaders(token));
            log.debug("POST to URL: {} with headers: {}", url, request.getHeaders());
            var restTemplate = RestTemplateUtil.create(jwtService.createTokenForRegionServer(regionProperties.getSectorServerId()));
            ResponseEntity<URegionResponse> resp = restTemplate.postForEntity(url, request, URegionResponse.class);
            if (resp.getStatusCode() == HttpStatus.CREATED || resp.getStatusCode() == HttpStatus.OK) {
                log.info("Universe Region Registrierung erfolgreich: name={} status={}", name, resp.getStatusCode());
                return true;
            }
            log.warn("Universe Region Registrierung unerwarteter Status {} für name={}", resp.getStatusCode(), name);
            return false;
        } catch (RestClientException e) {
            log.warn("Universe Region Registrierung fehlgeschlagen name={}", name, e);
            return false;
        }
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null && !token.isBlank()) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String encode(String v) { return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8); }
}
