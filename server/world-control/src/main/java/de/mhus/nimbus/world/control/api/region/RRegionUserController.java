package de.mhus.nimbus.world.control.api.region;

import de.mhus.nimbus.world.shared.region.RRegion;
import de.mhus.nimbus.world.shared.region.RRegionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(RRegionUserController.BASE_PATH)
public class RRegionUserController {

    public static final String BASE_PATH = "/region/user/region";

    private final RRegionService service;

    public RRegionUserController(RRegionService service) {
        this.service = service;
    }

    // DTOs
    public record RegionRequest(String name, String maintainers) {}
    public record RegionResponse(String id, String name, boolean enabled, List<String> maintainers) {}
    public record MaintainerRequest(String userId) {}

    private RegionResponse toResponse(RRegion r) {
        List<String> maint = r.getMaintainers().stream().toList();
        return new RegionResponse(r.getId(), r.getName(), r.isEnabled(), maint);
    }

    // LIST
    @GetMapping
    public ResponseEntity<List<RegionResponse>> list(@RequestParam(name="name", required=false) String name,
                                                     @RequestParam(name="enabled", required=false) Boolean enabled) {
        List<RegionResponse> out = service.listAll().stream()
                .filter(r -> name == null || name.equals(r.getName()))
                .filter(r -> enabled == null || r.isEnabled() == enabled)
                .map(this::toResponse).toList();
        return ResponseEntity.ok(out);
    }

    // GET by ID
    @GetMapping("/{id}")
    public ResponseEntity<RegionResponse> get(@PathVariable String id) {
        return service.getById(id)
                .map(r -> ResponseEntity.ok(toResponse(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    // CREATE
    @PostMapping
    public ResponseEntity<?> create(@RequestBody RegionRequest req) {
        try {
            RRegion created = service.create(req.name(), req.maintainers());
            return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.getId()))
                    .body(toResponse(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // UPDATE FULL inkl. enabled
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody RegionRequest req,
                                    @RequestParam(name="enabled", required=false) Boolean enabled) {
        try {
            if (service.getById(id).isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            RRegion updated = service.updateFull(id, req.name(), req.maintainers(), enabled);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<?> enable(@PathVariable String id) {
        try {
            RRegion updated = service.setEnabled(id, true);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<?> disable(@PathVariable String id) {
        try {
            RRegion updated = service.setEnabled(id, false);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (service.getById(id).isEmpty()) return ResponseEntity.notFound().build();
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ADD MAINTAINER
    @PostMapping("/{id}/maintainers")
    public ResponseEntity<?> addMaintainer(@PathVariable String id, @RequestBody MaintainerRequest req) {
        if (req.userId() == null || req.userId().isBlank()) return ResponseEntity.badRequest().body("userId blank");
        try {
            RRegion updated = service.addMaintainer(id, req.userId().trim());
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // REMOVE MAINTAINER
    @DeleteMapping("/{id}/maintainers/{userId}")
    public ResponseEntity<?> removeMaintainer(@PathVariable String id, @PathVariable String userId) {
        try {
            RRegion updated = service.removeMaintainer(id, userId);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // LIST MAINTAINERS
    @GetMapping("/{id}/maintainers")
    public ResponseEntity<?> listMaintainers(@PathVariable String id) {
        return service.getById(id)
                .map(r -> ResponseEntity.ok(r.getMaintainers().stream().toList()))
                .orElse(ResponseEntity.notFound().build());
    }
}
