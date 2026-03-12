package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
@Validated
@Slf4j
public class RCharacterService {

    private final RCharacterRepository repository;
    private final RUserRepository userRepository;
    private final RegionCharacterSettings limitProperties;
    private final MongoTemplate mongoTemplate;

    public RCharacterService(RCharacterRepository repository, RUserRepository userRepository, RegionCharacterSettings limitProperties, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.limitProperties = limitProperties;
        this.mongoTemplate = mongoTemplate;
    }

    public RCharacter createCharacter(String username, String regionId, String name, String display) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("userId blank");
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        if (repository.existsByUserIdAndRegionIdAndName(username, regionId, name)) {
            throw new IllegalArgumentException("Character name already exists for user/region: " + name);
        }
        // Limit prüfen
        RUser user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        Integer userLimit = user.getCharacterLimitForRegion(regionId);
        int effectiveLimit = userLimit != null ? userLimit : limitProperties.getMaxPerRegion();
        int currentCount = repository.findByUserIdAndRegionId(username, regionId).size();
        if (currentCount >= effectiveLimit) {
            throw new IllegalStateException("Character limit exceeded for region=" + regionId + " (" + currentCount + "/" + effectiveLimit + ")");
        }
        PlayerInfo playerInfo = new PlayerInfo();
        playerInfo.setTitle(display != null ? display : name);
        fillWithDefaults(playerInfo);

        RCharacter c = new RCharacter(username, regionId, name);
        c.setAttributes(new HashMap<>());
        c.setBackpack(new de.mhus.nimbus.generated.configs.PlayerBackpack());
        c.setPublicData(playerInfo);
        c.touchCreate();
        return repository.save(c);
    }

    public Optional<RCharacter> getCharacter(String userId, String regionId, String name) {
        return repository.findByUserIdAndRegionIdAndName(userId, regionId, name);
    }

    public List<RCharacter> listCharacters(String userId, String regionId) {
        return repository.findByUserIdAndRegionId(userId, regionId);
    }

    public List<RCharacter> listCharactersByRegion(String regionId) {
        return repository.findByRegionId(regionId);
    }

    public Optional<RCharacter> findByRegionAndName(String regionId, String name) {
        return repository.findByRegionIdAndName(regionId, name);
    }

    public RCharacter updateDisplay(String userId, String regionId, String name, String display) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        if (display != null && !display.isBlank()) c.getPublicData().setTitle(display);
        return repository.save(c);
    }


    public RCharacter setSkill(String userId, String regionId, String name, String skill, int level) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        c.setSkill(skill, level);
        c.touchUpdate();
        return repository.save(c);
    }

    public RCharacter incrementSkill(String userId, String regionId, String name, String skill, int delta) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        c.incrementSkill(skill, delta);
        c.touchUpdate();
        return repository.save(c);
    }

    public void deleteCharacter(String userId, String regionId, String name) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        repository.delete(c);
    }

    public void updateCharater(RCharacter character) {
        character.touchUpdate();
        repository.save(character);
    }

    /**
     * Atomically move an item from backpack to a wearing slot.
     * If the target slot is already occupied, the old item is moved back to backpack.
     *
     * @param characterId MongoDB document id
     * @param itemId      the item to equip from backpack
     * @param slot        the target wearing slot
     * @param oldItemId   the item currently in the slot (null if empty)
     * @return true if the update was applied (item was in backpack and character exists)
     */
    public boolean equipItem(String characterId, String itemId, WEARABLE_SLOT slot, String oldItemId) {
        Query query = new Query(Criteria.where("id").is(characterId)
                .and("backpack.itemIds." + itemId).gte(1));

        Update update = new Update()
                .set("backpack.wearingItemIds." + slot.name(), itemId)
                .inc("backpack.itemIds." + itemId, -1)
                .set("modifiedAt", Instant.now());

        if (oldItemId != null) {
            update.inc("backpack.itemIds." + oldItemId, 1);
        }

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            // Cleanup: remove item key if count dropped to 0
            Query cleanupQuery = new Query(Criteria.where("id").is(characterId)
                    .and("backpack.itemIds." + itemId).lte(0));
            Update cleanupUpdate = new Update()
                    .unset("backpack.itemIds." + itemId)
                    .set("modifiedAt", Instant.now());
            mongoTemplate.updateFirst(cleanupQuery, cleanupUpdate, RCharacter.class);
            return true;
        }

        log.warn("equipItem failed: characterId={}, itemId={}, slot={} - item not in backpack or character not found",
                characterId, itemId, slot);
        return false;
    }

    /**
     * Atomically move an item from a wearing slot back to backpack.
     *
     * @param characterId MongoDB document id
     * @param slot        the wearing slot to unequip
     * @param itemId      the item expected in the slot (for atomic verification)
     * @return true if the update was applied
     */
    public boolean unequipItem(String characterId, WEARABLE_SLOT slot, String itemId) {
        Query query = new Query(Criteria.where("id").is(characterId)
                .and("backpack.wearingItemIds." + slot.name()).is(itemId));

        Update update = new Update()
                .unset("backpack.wearingItemIds." + slot.name())
                .inc("backpack.itemIds." + itemId, 1)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("unequipItem failed: characterId={}, slot={}, itemId={} - slot empty or item mismatch",
                characterId, slot, itemId);
        return false;
    }

    /**
     * Atomically assign a shortcut definition to a shortcut slot.
     *
     * @param characterId MongoDB document id
     * @param slotKey     the shortcut slot key (e.g. "1", "2", ...)
     * @param shortcut    the ShortcutDefinition to assign
     * @return true if the update was applied
     */
    public boolean assignShortcut(String characterId, String slotKey, ShortcutDefinition shortcut) {
        Query query = new Query(Criteria.where("id").is(characterId));

        Update update = new Update()
                .set("publicData.shortcuts." + slotKey, shortcut)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("assignShortcut failed: characterId={}, slotKey={}", characterId, slotKey);
        return false;
    }

    /**
     * Atomically clear a shortcut slot.
     *
     * @param characterId MongoDB document id
     * @param slotKey     the shortcut slot key to clear
     * @return true if the update was applied
     */
    public boolean clearShortcut(String characterId, String slotKey) {
        Query query = new Query(Criteria.where("id").is(characterId)
                .and("publicData.shortcuts." + slotKey).exists(true));

        Update update = new Update()
                .unset("publicData.shortcuts." + slotKey)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("clearShortcut failed: characterId={}, slotKey={} - slot not found", characterId, slotKey);
        return false;
    }

    /**
     * Atomically add (or increase) an item in the backpack.
     *
     * @param characterId MongoDB document id
     * @param itemId      the item to add
     * @param amount      amount to add (positive)
     * @return true if the update was applied
     */
    public boolean addBackpackItem(String characterId, String itemId, int amount) {
        Query query = new Query(Criteria.where("id").is(characterId));

        Update update = new Update()
                .inc("backpack.itemIds." + itemId, amount)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("addBackpackItem failed: characterId={}, itemId={}, amount={}", characterId, itemId, amount);
        return false;
    }

    /**
     * Atomically remove (or decrease) an item from the backpack.
     * Verifies the item exists with at least the requested amount.
     * Cleans up the key if count drops to 0.
     *
     * @param characterId MongoDB document id
     * @param itemId      the item to remove
     * @param amount      amount to remove (positive)
     * @return true if the update was applied
     */
    public boolean removeBackpackItem(String characterId, String itemId, int amount) {
        Query query = new Query(Criteria.where("id").is(characterId)
                .and("backpack.itemIds." + itemId).gte(amount));

        Update update = new Update()
                .inc("backpack.itemIds." + itemId, -amount)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            // Cleanup: remove item key if count dropped to 0
            Query cleanupQuery = new Query(Criteria.where("id").is(characterId)
                    .and("backpack.itemIds." + itemId).lte(0));
            Update cleanupUpdate = new Update()
                    .unset("backpack.itemIds." + itemId)
                    .set("modifiedAt", Instant.now());
            mongoTemplate.updateFirst(cleanupQuery, cleanupUpdate, RCharacter.class);
            return true;
        }

        log.warn("removeBackpackItem failed: characterId={}, itemId={}, amount={} - insufficient quantity or not found",
                characterId, itemId, amount);
        return false;
    }

    /**
     * Atomically set a skill to a specific level.
     *
     * @param characterId MongoDB document id
     * @param skill       skill name
     * @param level       skill level (clamped to >= 0)
     * @return true if the update was applied
     */
    public boolean setSkillAtomic(String characterId, String skill, int level) {
        Query query = new Query(Criteria.where("id").is(characterId));

        Update update = new Update()
                .set("skills." + skill, Math.max(0, level))
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("setSkillAtomic failed: characterId={}, skill={}, level={}", characterId, skill, level);
        return false;
    }

    /**
     * Atomically increment a skill by a delta.
     * The result is clamped to >= 0 via a subsequent cleanup step.
     *
     * @param characterId MongoDB document id
     * @param skill       skill name
     * @param delta       amount to add (can be negative)
     * @return true if the update was applied
     */
    public boolean incrementSkillAtomic(String characterId, String skill, int delta) {
        Query query = new Query(Criteria.where("id").is(characterId));

        Update update = new Update()
                .inc("skills." + skill, delta)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            // Clamp to 0: if value went negative, set to 0
            if (delta < 0) {
                Query clampQuery = new Query(Criteria.where("id").is(characterId)
                        .and("skills." + skill).lt(0));
                Update clampUpdate = new Update()
                        .set("skills." + skill, 0)
                        .set("modifiedAt", Instant.now());
                mongoTemplate.updateFirst(clampQuery, clampUpdate, RCharacter.class);
            }
            return true;
        }

        log.warn("incrementSkillAtomic failed: characterId={}, skill={}, delta={}", characterId, skill, delta);
        return false;
    }

    /**
     * Atomically remove a skill.
     *
     * @param characterId MongoDB document id
     * @param skill       skill name to remove
     * @return true if the update was applied
     */
    public boolean removeSkillAtomic(String characterId, String skill) {
        Query query = new Query(Criteria.where("id").is(characterId)
                .and("skills." + skill).exists(true));

        Update update = new Update()
                .unset("skills." + skill)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("removeSkillAtomic failed: characterId={}, skill={} - skill not found", characterId, skill);
        return false;
    }

    /**
     * Atomically reduce a constitution value by a delta.
     * If the key doesn't exist yet, it is initialized to 1.0 first.
     * The value is clamped to [0.0, 1.0].
     *
     * @param characterId MongoDB document id
     * @param category    constitution category (e.g. "weapon", "armor", "magic")
     * @param delta       amount to reduce (positive value, will be subtracted)
     * @return true if the update was applied
     */
    public boolean reduceConstitution(String characterId, String category, double delta) {
        if (delta <= 0) return false;

        // First ensure the key exists with default 1.0 if missing
        Query initQuery = new Query(Criteria.where("id").is(characterId)
                .and("constitution." + category).exists(false));
        Update initUpdate = new Update()
                .set("constitution." + category, 1.0)
                .set("modifiedAt", Instant.now());
        mongoTemplate.updateFirst(initQuery, initUpdate, RCharacter.class);

        // Now atomically decrement
        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .inc("constitution." + category, -delta)
                .set("modifiedAt", Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            // Clamp to 0
            Query clampQuery = new Query(Criteria.where("id").is(characterId)
                    .and("constitution." + category).lt(0));
            Update clampUpdate = new Update()
                    .set("constitution." + category, 0.0)
                    .set("modifiedAt", Instant.now());
            mongoTemplate.updateFirst(clampQuery, clampUpdate, RCharacter.class);
            return true;
        }
        return false;
    }

    /**
     * Atomically set a single constitution value.
     * Clamps to [0.0, 1.0].
     *
     * @param characterId MongoDB document id
     * @param category    constitution category
     * @param value       new value (clamped to 0.0-1.0)
     * @return true if the update was applied
     */
    public boolean setConstitution(String characterId, String category, double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .set("constitution." + category, value)
                .set("modifiedAt", Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically reset all constitution values to 1.0 (full recovery).
     *
     * @param characterId MongoDB document id
     * @return true if the update was applied
     */
    public boolean restoreAllConstitution(String characterId) {
        Query query = new Query(Criteria.where("id").is(characterId));

        RCharacter character = mongoTemplate.findById(characterId, RCharacter.class);
        if (character == null) return false;

        var constitution = character.getConstitution();
        if (constitution.isEmpty()) return true;

        Update update = new Update().set("modifiedAt", Instant.now());
        for (String key : constitution.keySet()) {
            update.set("constitution." + key, 1.0);
        }

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically change the silver amount for a character.
     * If amount is negative, verifies the character has enough silver first.
     *
     * @param characterId MongoDB document id
     * @param amount      amount to add (positive) or subtract (negative)
     * @return true if the update was applied
     */
    public boolean changeSilver(String characterId, long amount) {
        if (amount == 0) return true;

        Query query;
        if (amount < 0) {
            query = new Query(Criteria.where("id").is(characterId)
                    .and("silver").gte(-amount));
        } else {
            query = new Query(Criteria.where("id").is(characterId));
        }

        Update update = new Update()
                .inc("silver", amount)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        if (amount < 0) {
            log.warn("changeSilver failed: characterId={}, amount={} - insufficient silver", characterId, amount);
        }
        return false;
    }

    /**
     * Calculate the experience required for the next skill point.
     * Based on the total number of skill points already earned (quadratic formula).
     *
     * @param totalSkillPoints current total skill points (already earned + available)
     * @return experience needed to earn the next skill point
     */
    public long calculateSkillExperienceToNext(int totalSkillPoints) {
        return (long) totalSkillPoints * totalSkillPoints * 100;
    }

    /**
     * Calculate total skill points from a character's current skills and available points.
     * Sum of all invested skill levels above their start values, plus unspent skill points.
     *
     * @param character the character
     * @param skillDefinitions list of skill definitions to calculate invested points
     * @return total skill points (invested + available)
     */
    public int calculateTotalSkillPoints(RCharacter character, java.util.function.Function<String, int[]> skillStartLookup) {
        int invested = 0;
        for (var entry : character.getSkills().entrySet()) {
            int[] startMinMax = skillStartLookup.apply(entry.getKey());
            if (startMinMax != null) {
                invested += entry.getValue() - startMinMax[0];
            }
        }
        return invested + character.getSkillPoints();
    }

    /**
     * Atomically try to convert experience into a skill point.
     * Checks if skillExperience >= experienceToNext, then increments skillPoints
     * and decrements skillExperience by experienceToNext.
     *
     * @param characterId MongoDB document id
     * @param experienceToNext the experience threshold for the next point
     * @return true if a skill point was earned
     */
    public boolean convertExperienceToSkillPoint(String characterId, long experienceToNext) {
        if (experienceToNext <= 0) experienceToNext = 100;

        Query query = new Query(Criteria.where("id").is(characterId)
                .and("skillExperience").gte(experienceToNext));

        Update update = new Update()
                .inc("skillPoints", 1)
                .inc("skillExperience", -experienceToNext)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically add skill points to a character.
     *
     * @param characterId MongoDB document id
     * @param points      number of points to add (positive)
     * @return true if updated
     */
    public boolean addSkillPoints(String characterId, int points) {
        if (points <= 0) return false;

        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .inc("skillPoints", points)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically add experience to a character.
     *
     * @param characterId MongoDB document id
     * @param experience  amount to add (positive)
     * @return true if updated
     */
    public boolean addSkillExperience(String characterId, long experience) {
        if (experience <= 0) return false;

        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .inc("skillExperience", experience)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically spend one skill point to increment a skill by 1.
     * Requires at least 1 skillPoint available.
     *
     * @param characterId MongoDB document id
     * @param skill       skill name to increment
     * @return true if the update was applied
     */
    public boolean spendSkillPoint(String characterId, String skill) {
        Query query = new Query(Criteria.where("id").is(characterId)
                .and("skillPoints").gte(1));

        Update update = new Update()
                .inc("skillPoints", -1)
                .inc("skills." + skill, 1)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    private void fillWithDefaults(PlayerInfo playerInfo) {
        var stateValues = new HashMap<String, de.mhus.nimbus.generated.types.MovementStateValues>();

        // default state
        stateValues.put("default", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(5)
            .effectiveMoveSpeed(5)
            .baseJumpSpeed(8)
            .effectiveJumpSpeed(8)
            .eyeHeight(1.6)
            .baseTurnSpeed(0.003)
            .effectiveTurnSpeed(0.003)
            .selectionRadius(5)
            .stealthRange(8)
            .distanceNotifyReduction(0)
            .build());

        // walk state
        stateValues.put("walk", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(5)
            .effectiveMoveSpeed(5)
            .baseJumpSpeed(8)
            .effectiveJumpSpeed(8)
            .eyeHeight(1.6)
            .baseTurnSpeed(0.003)
            .effectiveTurnSpeed(0.003)
            .selectionRadius(5)
            .stealthRange(8)
            .distanceNotifyReduction(0)
            .build());

        // sprint state
        stateValues.put("sprint", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(7)
            .effectiveMoveSpeed(7)
            .baseJumpSpeed(8)
            .effectiveJumpSpeed(8)
            .eyeHeight(1.6)
            .baseTurnSpeed(0.003)
            .effectiveTurnSpeed(0.003)
            .selectionRadius(5)
            .stealthRange(12)
            .distanceNotifyReduction(0)
            .build());

        // crouch state
        stateValues.put("crouch", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(1.5)
            .effectiveMoveSpeed(1.5)
            .baseJumpSpeed(4)
            .effectiveJumpSpeed(4)
            .eyeHeight(0.8)
            .baseTurnSpeed(0.002)
            .effectiveTurnSpeed(0.002)
            .selectionRadius(4)
            .stealthRange(4)
            .distanceNotifyReduction(0.5)
            .build());

        // swim state
        stateValues.put("swim", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(3)
            .effectiveMoveSpeed(3)
            .baseJumpSpeed(4)
            .effectiveJumpSpeed(4)
            .eyeHeight(1.4)
            .baseTurnSpeed(0.002)
            .effectiveTurnSpeed(0.002)
            .selectionRadius(4)
            .stealthRange(6)
            .distanceNotifyReduction(0.3)
            .build());

        // climb state
        stateValues.put("climb", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(2.5)
            .effectiveMoveSpeed(2.5)
            .baseJumpSpeed(0)
            .effectiveJumpSpeed(0)
            .eyeHeight(1.5)
            .baseTurnSpeed(0.002)
            .effectiveTurnSpeed(0.002)
            .selectionRadius(4)
            .stealthRange(6)
            .distanceNotifyReduction(0.2)
            .build());

        // free_fly state
        stateValues.put("free_fly", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(10)
            .effectiveMoveSpeed(10)
            .baseJumpSpeed(0)
            .effectiveJumpSpeed(0)
            .eyeHeight(1.6)
            .baseTurnSpeed(0.004)
            .effectiveTurnSpeed(0.004)
            .selectionRadius(8)
            .stealthRange(15)
            .distanceNotifyReduction(0)
            .build());

        // fly state
        stateValues.put("fly", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(10)
            .effectiveMoveSpeed(10)
            .baseJumpSpeed(0)
            .effectiveJumpSpeed(0)
            .eyeHeight(1.6)
            .baseTurnSpeed(0.004)
            .effectiveTurnSpeed(0.004)
            .selectionRadius(8)
            .stealthRange(15)
            .distanceNotifyReduction(0)
            .build());

        // teleport state
        stateValues.put("teleport", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(20)
            .effectiveMoveSpeed(20)
            .baseJumpSpeed(0)
            .effectiveJumpSpeed(0)
            .eyeHeight(1.6)
            .baseTurnSpeed(0.005)
            .effectiveTurnSpeed(0.005)
            .selectionRadius(10)
            .stealthRange(20)
            .distanceNotifyReduction(0)
            .build());

        // riding state
        stateValues.put("riding", de.mhus.nimbus.generated.types.MovementStateValues.builder()
            .baseMoveSpeed(8)
            .effectiveMoveSpeed(8)
            .baseJumpSpeed(10)
            .effectiveJumpSpeed(10)
            .eyeHeight(2)
            .baseTurnSpeed(0.003)
            .effectiveTurnSpeed(0.003)
            .selectionRadius(6)
            .stealthRange(10)
            .distanceNotifyReduction(0)
            .build());

        playerInfo.setStateValues(stateValues);
    }

    /**
     * Atomically change the reputation value for a faction/group.
     *
     * @param characterId MongoDB document id
     * @param faction     faction or group name (e.g. "villagers", "bandits")
     * @param delta       amount to add (positive) or subtract (negative)
     * @return true if the update was applied
     */
    public boolean changeReputation(String characterId, String faction, int delta) {
        if (delta == 0) return true;

        Query query = new Query(Criteria.where("id").is(characterId));

        Update update = new Update()
                .inc("reputation." + faction, delta)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        log.warn("changeReputation failed: characterId={}, faction={}, delta={}", characterId, faction, delta);
        return false;
    }

    /**
     * Atomically update the character's display title.
     */
    public boolean updateTitle(String characterId, String title) {
        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .set("publicData.title", title)
                .set("modifiedAt", Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically update the character's gender.
     */
    public boolean updateGender(String characterId, String gender) {
        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .set("publicData.gender", gender)
                .set("modifiedAt", Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically update the character's portrait path.
     */
    public boolean updatePortraitPath(String characterId, String portraitPath) {
        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .set("publicData.portraitPath", portraitPath)
                .set("modifiedAt", Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically update the character's third person model ID.
     */
    public boolean updateThirdPersonModelId(String characterId, String thirdPersonModelId) {
        Query query = new Query(Criteria.where("id").is(characterId));
        Update update = new Update()
                .set("publicData.thirdPersonModelId", thirdPersonModelId)
                .set("modifiedAt", Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RCharacter.class);
        return result.getModifiedCount() > 0;
    }

    public long getCharacterCount() {
        return repository.count();
    }
}
