package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
@Validated
public class RCharacterService {

    private final RCharacterRepository repository;
    private final RUserRepository userRepository;
    private final RegionCharacterSettings limitProperties;

    public RCharacterService(RCharacterRepository repository, RUserRepository userRepository, RegionCharacterSettings limitProperties) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.limitProperties = limitProperties;
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

    public long getCharacterCount() {
        return repository.count();
    }
}
