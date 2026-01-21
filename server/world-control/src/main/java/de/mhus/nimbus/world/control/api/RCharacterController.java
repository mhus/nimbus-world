package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing RCharacter entities.
 * Provides CRUD operations for characters within regions.
 */
@RestController
@RequestMapping("/control/regions/{regionId}/characters")
@RequiredArgsConstructor
public class RCharacterController extends BaseEditorController {

    private final RCharacterService characterService;

    // DTOs
    public record CharacterRequest(
            String userId,
            String name,
            String display,
            Map<String, Integer> skills,
            PlayerInfo publicData,
            PlayerBackpack backpack,
            Map<String, String> attributes
    ) {}
    public record CharacterResponse(
            String id,
            String userId,
            String regionId,
            String name,
            Instant createdAt,
            Instant modifiedAt,
            PlayerInfo publicData,
            PlayerBackpack backpack,
            Map<String, Integer> skills,
            Map<String, String> attributes
    ) {}
    public record SkillRequest(String skill, Integer level) {}

    private CharacterResponse toResponse(RCharacter character) {
        return new CharacterResponse(
                character.getId(),
                character.getUserId(),
                character.getRegionId(),
                character.getName(),
                character.getCreatedAt(),
                character.getModifiedAt(),
                character.getPublicData(),
                character.getBackpack(),
                character.getSkills(),
                character.getAttributes()
        );
    }

    /**
     * List all characters in a region
     * GET /control/regions/{regionId}/characters?userId=... (optional)
     * If userId is provided, filters characters by user
     * If userId is not provided, returns all characters in the region
     */
    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable String regionId,
            @RequestParam(name = "userId", required = false) String userId) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        try {
            List<CharacterResponse> result;
            if (Strings.isBlank(userId)) {
                // List all characters in region
                result = characterService.listCharactersByRegion(regionId).stream()
                        .map(this::toResponse)
                        .toList();
            } else {
                // List characters for specific user
                result = characterService.listCharacters(userId, regionId).stream()
                        .map(this::toResponse)
                        .toList();
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get character by ID
     * GET /control/regions/{regionId}/character/{characterId}
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<?> get(
            @PathVariable String regionId,
            @PathVariable String characterId,
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "name") String name) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(characterId, "characterId");
        if (error2 != null) return error2;

        if (Strings.isBlank(userId)) {
            return bad("userId parameter is required");
        }

        if (Strings.isBlank(name)) {
            return bad("title parameter is required");
        }

        return characterService.getCharacter(userId, regionId, name)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(toResponse(c)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Character not found")));
    }

    /**
     * Create new character
     * POST /control/regions/{regionId}/character
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String regionId,
            @RequestBody CharacterRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        if (Strings.isBlank(request.userId())) {
            return bad("userId is required");
        }

        if (Strings.isBlank(request.name())) {
            return bad("title is required");
        }

        try {
            RCharacter created = characterService.createCharacter(
                    request.userId(),
                    regionId,
                    request.name(),
                    request.display()
            );

            // Update publicData if provided (merge with defaults)
            if (request.publicData() != null) {
                created.setPublicData(request.publicData());
            }

            // Update backpack if provided (merge with defaults)
            if (request.backpack() != null) {
                created.setBackpack(request.backpack());
            }

            // Set attributes if provided
            if (request.attributes() != null && !request.attributes().isEmpty()) {
                created.setAttributes(request.attributes());
            }

            // Set skills if provided
            if (request.skills() != null && !request.skills().isEmpty()) {
                created.setSkills(request.skills());
            }

            characterService.updateCharater(created);

            return ResponseEntity.created(URI.create("/control/regions/" + regionId + "/character/" + created.getId()))
                    .body(toResponse(created));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Update character
     * PUT /control/regions/{regionId}/character/{characterId}
     */
    @PutMapping("/{characterId}")
    public ResponseEntity<?> update(
            @PathVariable String regionId,
            @PathVariable String characterId,
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "name") String name,
            @RequestBody CharacterRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(characterId, "characterId");
        if (error2 != null) return error2;

        if (Strings.isBlank(userId)) {
            return bad("userId parameter is required");
        }

        if (Strings.isBlank(name)) {
            return bad("title parameter is required");
        }

        try {
            // Load existing character
            RCharacter updated = characterService.getCharacter(userId, regionId, name)
                    .orElseThrow(() -> new IllegalArgumentException("Character not found"));

            // Update publicData if provided
            if (request.publicData() != null) {
                updated.setPublicData(request.publicData());
            }

            // Update backpack if provided
            if (request.backpack() != null) {
                updated.setBackpack(request.backpack());
            }

            // Update attributes if provided
            if (request.attributes() != null) {
                updated.setAttributes(request.attributes());
            }

            // Update skills if provided
            if (request.skills() != null) {
                updated.setSkills(request.skills());
            }

            // Save and return
            characterService.updateCharater(updated);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * Delete character
     * DELETE /control/regions/{regionId}/character/{characterId}
     */
    @DeleteMapping("/{characterId}")
    public ResponseEntity<?> delete(
            @PathVariable String regionId,
            @PathVariable String characterId,
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "name") String name) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(characterId, "characterId");
        if (error2 != null) return error2;

        if (Strings.isBlank(userId)) {
            return bad("userId parameter is required");
        }

        if (Strings.isBlank(name)) {
            return bad("title parameter is required");
        }

        try {
            characterService.deleteCharacter(userId, regionId, name);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * Set skill level
     * PUT /control/regions/{regionId}/character/{characterId}/skills/{skill}
     */
    @PutMapping("/{characterId}/skills/{skill}")
    public ResponseEntity<?> setSkill(
            @PathVariable String regionId,
            @PathVariable String characterId,
            @PathVariable String skill,
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "name") String name,
            @RequestBody SkillRequest request) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(characterId, "characterId");
        if (error2 != null) return error2;

        if (Strings.isBlank(userId) || Strings.isBlank(name)) {
            return bad("userId and title parameters are required");
        }

        if (request.level() == null) {
            return bad("level is required");
        }

        try {
            RCharacter updated = characterService.setSkill(userId, regionId, name, skill, request.level());
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    /**
     * Increment skill level
     * POST /control/regions/{regionId}/character/{characterId}/skills/{skill}/increment
     */
    @PostMapping("/{characterId}/skills/{skill}/increment")
    public ResponseEntity<?> incrementSkill(
            @PathVariable String regionId,
            @PathVariable String characterId,
            @PathVariable String skill,
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "name") String name,
            @RequestParam(name = "delta", defaultValue = "1") int delta) {

        var error = validateId(regionId, "regionId");
        if (error != null) return error;

        var error2 = validateId(characterId, "characterId");
        if (error2 != null) return error2;

        if (Strings.isBlank(userId) || Strings.isBlank(name)) {
            return bad("userId and title parameters are required");
        }

        try {
            RCharacter updated = characterService.incrementSkill(userId, regionId, name, skill, delta);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }
}
