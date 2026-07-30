package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.persistence.SKey;
import de.mhus.nimbus.shared.security.KeyKind;
import de.mhus.nimbus.shared.security.KeyService;
import de.mhus.nimbus.shared.security.KeyType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.mhus.nimbus.shared.user.SectorRoles;
import de.mhus.nimbus.world.shared.access.RequireSectorRole;

import java.util.List;

/**
 * Shared REST Controller für Schlüsselverwaltung unter /shared/key.
 * CRUD ähnlich wie UKeysController aber ohne Rollen-Annotation (Filter schützt).
 */
@RestController
@RequestMapping("/control/key")
@Validated
@RequireSectorRole(SectorRoles.ADMIN)
public class SharedKeyController {

    private final KeyService keyService;

    public SharedKeyController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping
    public List<SharedSKeyDto> list(@RequestParam(name = "type", required = false) String type,
                              @RequestParam(name = "kind", required = false) String kind,
                              @RequestParam(name = "name", required = false) String name,
                              @RequestParam(name = "algorithm", required = false) String algorithm) {
        return keyService.searchKeys(type, kind, name, algorithm).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SharedSKeyDto> get(@PathVariable("id") String id) {
        return keyService.findKeyById(id)
                .map(e -> ResponseEntity.ok(toDto(e)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SharedSKeyDto> create(@Valid @RequestBody SharedCreateKeyRequest req) {
        if (req.getType() == null || req.getKind() == null) return ResponseEntity.badRequest().build();
        KeyType keyType;
        KeyKind keyKind;
        try {
            keyType = KeyType.valueOf(req.getType().trim().toUpperCase());
            keyKind = KeyKind.valueOf(req.getKind().trim().toUpperCase());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
        SKey saved = keyService.createKey(keyType, keyKind, req.getAlgorithm(), req.getName(), req.getKey(), req.getOwner(), req.getIntent());
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SharedSKeyDto> updateName(@PathVariable("id") String id, @Valid @RequestBody SharedUpdateSKeyNameRequest req) {
        return keyService.renameKey(id, req.getName())
                .map(e -> ResponseEntity.ok(toDto(e)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (!keyService.deleteKey(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists")
    public java.util.Map<String,Object> exists(@RequestParam("type") String type,
                                               @RequestParam("kind") String kind,
                                               @RequestParam("owner") String owner,
                                               @RequestParam("intent") String intent) {
        boolean ex = keyService.existsByTypeKindOwnerIntent(type.toUpperCase(), kind.toUpperCase(), owner, intent);
        return java.util.Map.of("exists", ex);
    }

    private SharedSKeyDto toDto(SKey e) {
        return SharedSKeyDto.builder()
            .id(e.getId())
            .type(e.getType() != null ? e.getType().name() : null)
            .kind(e.getKind() != null ? e.getKind().name() : null)
            .algorithm(e.getAlgorithm())
            .keyId(e.getKeyId())
            .owner(e.getOwner())
            .intent(e.getIntent())
            .createdAt(e.getCreatedAt() == null ? null : e.getCreatedAt().toString())
            .build();
    }
}

class SharedCreateKeyRequest {
    private String type; private String kind; private String algorithm; private String name; private String key; private String owner; private String intent;
    public String getType() {return type;} public String getKind(){return kind;} public String getAlgorithm(){return algorithm;}
    public String getName(){return name;} public String getKey(){return key;} public String getOwner(){return owner;} public String getIntent(){return intent;}
    public void setType(String v){type=v;} public void setKind(String v){kind=v;} public void setAlgorithm(String v){algorithm=v;} public void setName(String v){name=v;} public void setKey(String v){key=v;} public void setOwner(String v){owner=v;} public void setIntent(String v){intent=v;}
}
class SharedUpdateSKeyNameRequest { private String name; public String getName(){return name;} public void setName(String v){name=v;} }
@lombok.Data @lombok.Builder class SharedSKeyDto { private String id; private String type; private String kind; private String algorithm; private String keyId; private String owner; private String intent; private String createdAt; }
