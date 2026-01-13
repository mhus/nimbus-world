package de.mhus.nimbus.world.shared.region;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.mhus.nimbus.shared.security.FormattedKey;
import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.security.KeyId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import de.mhus.nimbus.shared.security.KeyService;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.shared.security.KeyIntent;

@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class RRegionService {

    private final RRegionRepository repository;
    private final KeyService keyService; // neu
    private final JwtService jwtService; // neu
    private final RUniverseClientService universeClientService; // neu
    private final RegionSettings regionProperties;

    public RRegion create(String name, String maintainers) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name must not be blank");
        if (repository.existsByName(name)) throw new IllegalArgumentException("Region name exists: " + name);
        RRegion q = new RRegion(name);
        if (maintainers != null)
            q.setMaintainers(new HashSet<>(Arrays.asList(maintainers.trim())));
        RRegion saved = repository.save(q);
        // KeyPair für Region erzeugen (Intent main-jwt-token, Owner = Regionsname)
        try {
            var keys = keyService.createECCKeys();
            KeyId keyId = KeyId.of(saved.getName(), KeyIntent.REGION_JWT_TOKEN, UUID.randomUUID().toString());
            keyService.storeKeyPair(
                KeyType.REGION,
                keyId,
                keys
            );
            // Register to the Universe with region server key
            String token = jwtService.createTokenForRegionServer(regionProperties.getSectorServerId());
             var publicKey = keys.getPublic();
            String publicBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            boolean registered = universeClientService.createRegion(token, saved.getName(), regionProperties.getSectorServerUrl(),
                    FormattedKey.of(keyId, publicBase64).get(), maintainers);
            if (!registered) {
                log.warn("Region '{}' konnte im Universe nicht registriert werden", saved.getName());
            } else {
                log.info("Region '{}' erfolgreich im Universe registriert", saved.getName());
            }
        } catch (Exception e) {
            log.warn("Cannot create system auth key or register region {}: {}", saved.getName(), e.getMessage());
        }
        return saved;
    }

    public Optional<RRegion> getById(String id) {
        return repository.findById(id);
    }

    public Optional<RRegion> getByName(String name) {
        return repository.findByName(name);
    }

    public List<String> listAllIds() {
        return repository.findAllIds();
    }

    public List<RRegion> listAll() {
        return repository.findAll();
    }

    public RRegion update(String id, String name, String maintainers) {
        RRegion existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Region not found: " + id));
        if (name != null && !name.isBlank() && !name.equals(existing.getName())) {
            if (repository.existsByName(name)) throw new IllegalArgumentException("Region name exists: " + name);
            existing.setName(name);
        }
        if (maintainers != null)
            existing.setMaintainers(new HashSet<>(Arrays.asList(maintainers.trim())));
        return repository.save(existing);
    }

    public RRegion updateFull(String id, String name, String maintainers, Boolean enabled) {
        RRegion existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Region not found: " + id));
        if (name != null && !name.isBlank() && !name.equals(existing.getName())) {
            if (repository.existsByName(name)) throw new IllegalArgumentException("Region name exists: " + name);
            existing.setName(name);
        }
        if (maintainers != null)
            existing.setMaintainers(new HashSet<>(Arrays.asList(maintainers.trim())));
        if (enabled != null) existing.setEnabled(enabled);
        return repository.save(existing);
    }

    public RRegion addMaintainer(String id, String userId) {
        RRegion existing = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Region not found: " + id));
        existing.addMaintainer(userId);
        return repository.save(existing);
    }

    public RRegion removeMaintainer(String id, String userId) {
        RRegion existing = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Region not found: " + id));
        existing.removeMaintainer(userId);
        return repository.save(existing);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public Optional<String>  getRegionNameById(String regionId) {
        return repository.getRegionNameByIdAndEnabled(regionId, true);
    }

    public RRegion setEnabled(String id, boolean enabled) {
        RRegion existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Region not found: " + id));
        existing.setEnabled(enabled);
        return repository.save(existing);
    }
}
