package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetService;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/control/player/character")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Character", description = "Player character profile management")
public class PlayerCharacterController extends BaseEditorController {

    private static final Set<String> VALID_GENDERS = Set.of("", "M", "F", "D");
    private static final WorldId SHARED_PUBLIC = WorldId.of("@shared:p").orElseThrow();
    private static final String PORTRAITS_MALE = "textures/portraits/male";
    private static final String PORTRAITS_FEMALE = "textures/portraits/female";
    private static final String PORTRAITS_COMMON = "textures/portraits/common";
    private static final String PORTRAIT_DEFAULT = "textures/portraits/unknown.png";
    private static final String PORTRAIT_ASSET_PREFIX = "p:";

    private static final WorldId SHARED_N = WorldId.of("@shared:n").orElseThrow();
    private static final String MODEL_PREFIX = "n:";
    private static final String AVATAR_TYPE = "avatar";

    private final RCharacterService characterService;
    private final SAssetService assetService;
    private final WEntityModelService entityModelService;

    @GetMapping
    @Operation(summary = "Get player character profile")
    public ResponseEntity<?> getCharacter(HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(userId) || Strings.isBlank(worldId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        RCharacter character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        var pd = character.getPublicData();
        String title = pd != null ? pd.getTitle() : null;
        String gender = pd != null ? pd.getGender() : null;
        String portraitPath = pd != null ? pd.getPortraitPath() : null;
        String thirdPersonModelId = pd != null ? pd.getThirdPersonModelId() : null;

        var modifiers = pd != null ? pd.getThirdPersonModelModifiers() : null;

        Map<String, Object> result = new HashMap<>();
        result.put("title", title != null ? title : "");
        result.put("gender", gender != null ? gender : "");
        result.put("portraitPath", portraitPath != null ? portraitPath : "");
        result.put("thirdPersonModelId", thirdPersonModelId != null ? thirdPersonModelId : "");
        result.put("thirdPersonModelModifiers", modifiers != null ? modifiers : Map.of());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/portraits")
    @Operation(summary = "List available portrait images")
    public ResponseEntity<?> listPortraits(HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        List<Map<String, String>> portraits = new ArrayList<>();

        var maleResult = assetService.searchAssets(SHARED_PUBLIC, PORTRAITS_MALE, "png", 0, 200);
        for (SAsset asset : maleResult.assets()) {
            portraits.add(Map.of(
                    "path", asset.getPath(),
                    "name", asset.getName(),
                    "category", "male"
            ));
        }

        var femaleResult = assetService.searchAssets(SHARED_PUBLIC, PORTRAITS_FEMALE, "png", 0, 200);
        for (SAsset asset : femaleResult.assets()) {
            portraits.add(Map.of(
                    "path", asset.getPath(),
                    "name", asset.getName(),
                    "category", "female"
            ));
        }

        var commonResult = assetService.searchAssets(SHARED_PUBLIC, PORTRAITS_COMMON, "png", 0, 200);
        for (SAsset asset : commonResult.assets()) {
            portraits.add(Map.of(
                    "path", asset.getPath(),
                    "name", asset.getName(),
                    "category", "common"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "portraits", portraits,
                "defaultPortrait", PORTRAIT_DEFAULT,
                "assetPrefix", PORTRAIT_ASSET_PREFIX
        ));
    }

    @PutMapping("/title")
    @Operation(summary = "Update character display title")
    public ResponseEntity<?> updateTitle(
            @RequestBody UpdateTitleRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(userId) || Strings.isBlank(worldId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.title() == null) {
            return bad("title required");
        }

        String title = body.title().trim();
        if (title.length() > 50) {
            return bad("title too long (max 50 characters)");
        }

        RCharacter character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        log.debug("PUT character title: characterId={}, title={}", character.getId(), title);

        boolean updated = characterService.updateTitle(character.getId(), title);
        if (!updated) {
            return notFound("Character not found");
        }

        log.info("Updated character title: characterId={}, title={}", character.getId(), title);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/gender")
    @Operation(summary = "Update character gender")
    public ResponseEntity<?> updateGender(
            @RequestBody UpdateGenderRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(userId) || Strings.isBlank(worldId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.gender() == null) {
            return bad("gender required");
        }

        String gender = body.gender().trim().toUpperCase();
        if (!VALID_GENDERS.contains(gender)) {
            return bad("Invalid gender. Allowed: '', 'M', 'F', 'D'");
        }

        RCharacter character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        log.debug("PUT character gender: characterId={}, gender={}", character.getId(), gender);

        boolean updated = characterService.updateGender(character.getId(), gender);
        if (!updated) {
            return notFound("Character not found");
        }

        log.info("Updated character gender: characterId={}, gender={}", character.getId(), gender);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/portrait")
    @Operation(summary = "Update character portrait")
    public ResponseEntity<?> updatePortrait(
            @RequestBody UpdatePortraitRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(userId) || Strings.isBlank(worldId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.portraitPath() == null) {
            return bad("portraitPath required");
        }

        String portraitPath = body.portraitPath().trim();
        if (!portraitPath.isEmpty() && !portraitPath.startsWith("textures/portraits/")) {
            return bad("Invalid portrait path");
        }

        RCharacter character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        log.debug("PUT character portrait: characterId={}, portraitPath={}", character.getId(), portraitPath);

        boolean updated = characterService.updatePortraitPath(character.getId(), portraitPath);
        if (!updated) {
            return notFound("Character not found");
        }

        log.info("Updated character portrait: characterId={}, portraitPath={}", character.getId(), portraitPath);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/models")
    @Operation(summary = "List available avatar models")
    public ResponseEntity<?> listModels(HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        if (Strings.isBlank(userId)) {
            return bad("Not authenticated");
        }

        List<WEntityModel> avatarModels = entityModelService.findByWorldIdAndType(SHARED_N, AVATAR_TYPE);

        List<Map<String, Object>> models = new ArrayList<>();
        for (WEntityModel wem : avatarModels) {
            var pd = wem.getPublicData();
            if (pd == null) continue;
            String modelId = MODEL_PREFIX + wem.getModelId();
            String name = wem.getTitle() != null ? wem.getTitle() : wem.getModelId();
            String gender = pd.getGender() != null ? pd.getGender() : "";
            var modifierMapping = pd.getModelModifierMapping();
            List<String> modifierKeys = modifierMapping != null ? new ArrayList<>(modifierMapping.keySet()) : List.of();

            Map<String, Object> model = new HashMap<>();
            model.put("id", modelId);
            model.put("name", name);
            model.put("gender", gender);
            model.put("modifierKeys", modifierKeys);
            models.add(model);
        }

        return ResponseEntity.ok(Map.of("models", models));
    }

    @PutMapping("/model")
    @Operation(summary = "Update character avatar model")
    public ResponseEntity<?> updateModel(
            @RequestBody UpdateModelRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(userId) || Strings.isBlank(worldId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.thirdPersonModelId() == null) {
            return bad("thirdPersonModelId required");
        }

        String modelId = body.thirdPersonModelId().trim();
        if (!modelId.isEmpty()) {
            var model = entityModelService.findByModelId(SHARED_N, modelId);
            if (model.isEmpty()) {
                return bad("Unknown model: " + modelId);
            }
        }

        RCharacter character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        log.debug("PUT character model: characterId={}, thirdPersonModelId={}", character.getId(), modelId);

        boolean updated = characterService.updateThirdPersonModelId(character.getId(), modelId);
        if (!updated) {
            return notFound("Character not found");
        }

        log.info("Updated character model: characterId={}, thirdPersonModelId={}", character.getId(), modelId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/modifiers")
    @Operation(summary = "Update character model modifiers")
    public ResponseEntity<?> updateModifiers(
            @RequestBody UpdateModifiersRequest body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (Strings.isBlank(userId) || Strings.isBlank(worldId) || Strings.isBlank(characterId)) {
            return bad("Not authenticated");
        }

        if (body == null || body.modifiers() == null) {
            return bad("modifiers required");
        }

        // Validate: max 20 entries, keys max 50 chars, values max 100 chars
        var modifiers = body.modifiers();
        if (modifiers.size() > 20) {
            return bad("Too many modifiers (max 20)");
        }
        for (var entry : modifiers.entrySet()) {
            if (entry.getKey().length() > 50) return bad("Modifier key too long");
            if (entry.getValue() != null && entry.getValue().length() > 100) return bad("Modifier value too long");
        }

        RCharacter character = findCharacter(worldId, userId, characterId);
        if (character == null) {
            return notFound("Character not found");
        }

        log.debug("PUT character modifiers: characterId={}, modifiers={}", character.getId(), modifiers);

        boolean updated = characterService.updateThirdPersonModelModifiers(character.getId(), modifiers);
        if (!updated) {
            return notFound("Character not found");
        }

        log.info("Updated character modifiers: characterId={}", character.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    private RCharacter findCharacter(String worldId, String userId, String characterId) {
        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return null;
        return characterService.getCharacter(userId, parsedWorldId.getRegionId(), characterId).orElse(null);
    }

    record UpdateTitleRequest(String title) {}
    record UpdateGenderRequest(String gender) {}
    record UpdatePortraitRequest(String portraitPath) {}
    record UpdateModelRequest(String thirdPersonModelId) {}
    record UpdateModifiersRequest(Map<String, String> modifiers) {}
}
