package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.world.shared.sector.RUser;
import de.mhus.nimbus.world.shared.sector.RUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

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
        RCharacter c = new RCharacter(username, regionId, name, display != null ? display : name);
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
        if (display != null && !display.isBlank()) c.setDisplay(display);
        return repository.save(c);
    }


    public RCharacter setSkill(String userId, String regionId, String name, String skill, int level) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        c.setSkill(skill, level);
        return repository.save(c);
    }

    public RCharacter incrementSkill(String userId, String regionId, String name, String skill, int delta) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        c.incrementSkill(skill, delta);
        return repository.save(c);
    }

    public void deleteCharacter(String userId, String regionId, String name) {
        RCharacter c = repository.findByUserIdAndRegionIdAndName(userId, regionId, name)
                .orElseThrow(() -> new IllegalArgumentException("Character not found"));
        repository.delete(c);
    }

    public void updateCharater(RCharacter character) {
        repository.save(character);
    }
}
