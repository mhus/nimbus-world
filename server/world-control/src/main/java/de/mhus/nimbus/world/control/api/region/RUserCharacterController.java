package de.mhus.nimbus.world.control.api.region;

import de.mhus.nimbus.generated.types.RegionItemInfo;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(RUserCharacterController.BASE_PATH)
@Validated
@Slf4j
public class RUserCharacterController {

    public static final String BASE_PATH = "/region/user/character";

    private final RCharacterService characterService;

    public RUserCharacterController(RCharacterService characterService) {
        this.characterService = characterService;
    }

    // DTOs
    public record CreateCharacterRequest(String userId, String regionId, String name, String display) {}
    public record UpdateDisplayRequest(String display) {}
    public record AddBackpackItemRequest(String key, RegionItemInfo item) {}
    public record WearItemRequest(Integer slot, RegionItemInfo item) {}
    public record SkillRequest(String skill, Integer level, Integer delta) {}

//    @GetMapping("/{regionId}/{characterId}")
//    public ResponseEntity<?> get(@PathVariable String regionId, @PathVariable String characterId) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        return ResponseEntity.ok(toResponse(c));
//    }
//
//    @GetMapping("/{regionId}/list/{userId}")
//    public ResponseEntity<?> list(@PathVariable String regionId, @PathVariable String userId) {
//        List<RCharacter> list = characterService.listCharacters(userId, regionId);
//        return ResponseEntity.ok(list.stream().map(this::toResponse).toList());
//    }
//
//    @PostMapping
//    public ResponseEntity<?> create(@RequestBody CreateCharacterRequest req) {
//        try {
//            var c = characterService.createCharacter(req.userId(), req.regionId(), req.name(), req.display());
//            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(c));
//        } catch (IllegalArgumentException | IllegalStateException ex) {
//            log.warn("create character {} failed", req, ex);
//            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
//        }
//    }
//
//    @PutMapping("/{regionId}/{characterId}/display")
//    public ResponseEntity<?> updateDisplay(@PathVariable String regionId, @PathVariable String characterId, @RequestBody UpdateDisplayRequest req) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        var updated = characterService.updateDisplay(c.getUserId(), regionId, c.getName(), req.display());
//        return ResponseEntity.ok(toResponse(updated));
//    }
//
//    @PostMapping("/{regionId}/{characterId}/backpack")
//    public ResponseEntity<?> addBackpackItem(@PathVariable String regionId, @PathVariable String characterId, @RequestBody AddBackpackItemRequest req) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        var updated = characterService.addBackpackItem(c.getUserId(), regionId, c.getName(), req.key(), req.item());
//        return ResponseEntity.ok(toResponse(updated));
//    }
//
//    @DeleteMapping("/{regionId}/{characterId}/backpack/{key}")
//    public ResponseEntity<?> removeBackpackItem(@PathVariable String regionId, @PathVariable String characterId, @PathVariable String key) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        var updated = characterService.removeBackpackItem(c.getUserId(), regionId, c.getName(), key);
//        return ResponseEntity.ok(toResponse(updated));
//    }
//
//    @PostMapping("/{regionId}/{characterId}/wear")
//    public ResponseEntity<?> wear(@PathVariable String regionId, @PathVariable String characterId, @RequestBody WearItemRequest req) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        var updated = characterService.wearItem(c.getUserId(), regionId, c.getName(), req.slot(), req.item());
//        return ResponseEntity.ok(toResponse(updated));
//    }
//
//    @DeleteMapping("/{regionId}/{characterId}/wear/{slot}")
//    public ResponseEntity<?> removeWear(@PathVariable String regionId, @PathVariable String characterId, @PathVariable Integer slot) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        var updated = characterService.removeWearingItem(c.getUserId(), regionId, c.getName(), slot);
//        return ResponseEntity.ok(toResponse(updated));
//    }
//
//    @PostMapping("/{regionId}/{characterId}/skill")
//    public ResponseEntity<?> setSkill(@PathVariable String regionId, @PathVariable String characterId, @RequestBody SkillRequest req) {
//        if (req.skill() == null || req.skill().isBlank()) return ResponseEntity.badRequest().body(Map.of("error","skill blank"));
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        RCharacter updated;
//        if (req.level() != null) {
//            updated = characterService.setSkill(c.getUserId(), regionId, c.getName(), req.skill(), req.level());
//        } else if (req.delta() != null) {
//            updated = characterService.incrementSkill(c.getUserId(), regionId, c.getName(), req.skill(), req.delta());
//        } else {
//            return ResponseEntity.badRequest().body(Map.of("error","either level or delta required"));
//        }
//        return ResponseEntity.ok(toResponse(updated));
//    }
//
//    @DeleteMapping("/{regionId}/{characterId}")
//    public ResponseEntity<?> delete(@PathVariable String regionId, @PathVariable String characterId) {
//        var opt = characterService.getByIdAndRegion(characterId, regionId);
//        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","character not found"));
//        var c = opt.get();
//        characterService.deleteCharacter(c.getUserId(), regionId, c.getName());
//        return ResponseEntity.noContent().build();
//    }
//
//    private RegionCharacterResponse toResponse(RCharacter c) {
//        java.util.Map<String, RegionItemInfo> backpack = c.getBackpack();
//        java.util.Map<java.lang.Integer, RegionItemInfo> wearingConv = new java.util.HashMap<>();
//        c.getWearing().forEach((k,v) -> wearingConv.put(k.intValue(), v));
//        java.util.Map<String, java.lang.Integer> skillsConv = new java.util.HashMap<>();
//        c.getSkills().forEach((k,v) -> skillsConv.put(k, v.intValue()));
//        return RegionCharacterResponse.builder()
//                .id(c.getId())
//                .userId(c.getUserId())
//                .regionId(c.getRegionId())
//                .name(c.getName())
//                .display(c.getDisplay())
//                .backpack(backpack)
//                .wearing(wearingConv)
//                .skills(skillsConv)
//                .build();
//    }
}
