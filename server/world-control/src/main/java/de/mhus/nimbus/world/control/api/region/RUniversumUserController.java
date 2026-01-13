package de.mhus.nimbus.world.control.api.region;

import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(RUniversumUserController.BASE_PATH)
@Tag(name = "QUsers", description = "Universum access to region users")
public class RUniversumUserController {

    public static final String BASE_PATH = "/region/universum/user";

    private final RUserService service;

    public RUniversumUserController(RUserService service) {
        this.service = service;
    }

    // DTOs
    public record QUserRequest(String username, String email, String roles) {}
    public record QUserResponse(String id, String username, String email, String roles) {}

    private QUserResponse toResponse(RUser u) {
        return new QUserResponse(u.getId(), u.getUsername(), u.getEmail(), service.getRolesRaw(u.getId()));
    }

    @Operation(summary = "List users")
    @ApiResponse(responseCode = "200", description = "List returned")
    @GetMapping
    public ResponseEntity<List<QUserResponse>> list() {
        List<QUserResponse> list = service.listAll().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Get user by id")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Found"), @ApiResponse(responseCode = "404", description = "Not found")})
    @GetMapping("/{id}")
    public ResponseEntity<QUserResponse> get(@PathVariable String id) {
        Optional<RUser> opt = service.getByUsername(id);
        return opt.map(u -> ResponseEntity.ok(toResponse(u)))
                .orElse(ResponseEntity.notFound().build());
    }

}
