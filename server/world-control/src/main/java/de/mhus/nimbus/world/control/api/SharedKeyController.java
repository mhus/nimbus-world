package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.persistence.SKey;
import de.mhus.nimbus.shared.persistence.SKeyRepository;
import de.mhus.nimbus.shared.security.KeyKind;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Shared REST Controller für Schlüsselverwaltung unter /shared/key.
 * CRUD ähnlich wie UKeysController aber ohne Rollen-Annotation (Filter schützt).
 */
@RestController
@RequestMapping("/control/key")
@Validated
public class SharedKeyController {

    private final SKeyRepository repository;

    public SharedKeyController(SKeyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SharedSKeyDto> list(@RequestParam(name = "type", required = false) String type,
                              @RequestParam(name = "kind", required = false) String kind,
                              @RequestParam(name = "name", required = false) String name,
                              @RequestParam(name = "algorithm", required = false) String algorithm) {
        Iterable<SKey> all = repository.findAll();
        List<SharedSKeyDto> out = new ArrayList<>();
        StreamSupport.stream(all.spliterator(), false)
            .filter(e -> type == null || (e.getType() != null && type.equalsIgnoreCase(e.getType().name())))
            .filter(e -> kind == null || (e.getKind() != null && kind.equalsIgnoreCase(e.getKind().name())))
            .filter(e -> name == null || name.equals(e.getKeyId()))
            .filter(e -> algorithm == null || algorithm.equals(e.getAlgorithm()))
            .map(this::toDto)
            .forEach(out::add);
        return out;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SharedSKeyDto> get(@PathVariable("id") String id) {
        Optional<SKey> opt = repository.findById(id);
        return opt.map(e -> ResponseEntity.ok(toDto(e))).orElseGet(() -> ResponseEntity.notFound().build());
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
        SKey e = new SKey();
        e.setType(keyType);
        e.setKind(keyKind);
        e.setAlgorithm(req.getAlgorithm());
        e.setKeyId(req.getName());
        e.setKey(req.getKey());
        // owner & intent explizit oder aus name parsen
        if (req.getOwner() != null && !req.getOwner().isBlank()) e.setOwner(req.getOwner().trim());
        if (req.getIntent() != null && !req.getIntent().isBlank()) e.setIntent(req.getIntent().trim());
        if ((e.getOwner() == null || e.getIntent() == null) && req.getName() != null) {
            String[] parts = req.getName().split(";",3);
            if (parts.length == 3) {
                if (e.getOwner() == null) e.setOwner(parts[0]);
                if (e.getIntent() == null) e.setIntent(parts[1]);
                // keyId bleibt voller name; alternativ parts[2] als keyId setzen? Nur ändern falls gewünscht
            }
        }
        SKey saved = repository.save(e);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SharedSKeyDto> updateName(@PathVariable("id") String id, @Valid @RequestBody SharedUpdateSKeyNameRequest req) {
        Optional<SKey> opt = repository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        SKey e = opt.get();
        e.setKeyId(req.getName());
        SKey saved = repository.save(e);
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists")
    public java.util.Map<String,Object> exists(@RequestParam("type") String type,
                                               @RequestParam("kind") String kind,
                                               @RequestParam("owner") String owner,
                                               @RequestParam("intent") String intent) {
        boolean ex = repository.existsByTypeAndKindAndOwnerAndIntent(type.toUpperCase(), kind.toUpperCase(), owner, intent);
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
