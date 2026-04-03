package de.mhus.nimbus.world.shared.sector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.shared.types.PlayerUser;
import de.mhus.nimbus.shared.user.SectorRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import de.mhus.nimbus.shared.user.RegionRoles;

@Service
@RequiredArgsConstructor
@Slf4j
public class RUserService {

    private final RUserRepository repository;
    private final MongoTemplate mongoTemplate;

    public RUser createUser(PlayerUser publicData, String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is blank");
        if (repository.existsByName(publicData.getName())) {
            throw new IllegalArgumentException("Username already exists: " + publicData);
        }
        if (repository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        RUser user = RUser.builder()
                .name(publicData.getName())
                .publicData(publicData)
                .email(email)
                .enabled(true)
                .attributes(new HashMap<>())
                .build();
        user.addSectorRole(SectorRoles.USER); // Standardrolle global
        user.touchCreate();
        return repository.save(user);
    }

    public Optional<RUser> getByUsername(String username) { return repository.findByName(username); }
    public List<RUser> listAll() { return repository.findAll(); }

    public RUser save(RUser user) {
        // Try to load existing user from DB
        var optExisting = repository.findByName(user.getName());

        if (optExisting.isPresent()) {
            // Update existing user
            RUser existing = optExisting.get();
            existing.setEmail(user.getEmail());
            existing.setPublicData(user.getPublicData());
            existing.setSectorRoles(user.getSectorRoles());
            existing.setRegionRoles(user.getRegionRoles());
            existing.setCharacterLimits(user.getCharacterLimits());
            existing.setUserSettings(user.getUserSettings());
            existing.setAttributes(user.getAttributes());
            existing.touchUpdate();
            return repository.save(existing);
        } else {
            // Create new user
            user.touchCreate();
            return repository.save(user);
        }
    }

    public void disableUser(String username) {
        RUser existing = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        existing.disable();
        existing.touchUpdate();
        repository.save(existing);
    }

    // Globale Server-Rollen
    public RUser addSectorRoles(String username, SectorRoles role) {
        RUser existing = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        existing.touchUpdate();
        if (existing.addSectorRole(role)) existing = repository.save(existing);
        return existing;
    }

    public RUser removeSectorRole(String username, SectorRoles role) {
        RUser existing = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        existing.touchUpdate();
        if (existing.removeSectorRole(role)) existing = repository.save(existing);
        return existing;
    }

    // Legacy API methods (moved from deprecated RUser methods)
    public Set<SectorRoles> getRoles(String username) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getSectorRoles();
    }

    public boolean addRole(String username, SectorRoles role) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        boolean changed = user.addSectorRole(role);
        if (changed) {
            user.touchUpdate();
            repository.save(user);
        }
        return changed;
    }

    public boolean removeRole(String username, SectorRoles role) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        boolean changed = user.removeSectorRole(role);
        if (changed) {
            user.touchUpdate();
            repository.save(user);
        }
        return changed;
    }

    public boolean hasRole(String username, SectorRoles role) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.hasSectorRole(role);
    }

    public String getRolesRaw(String username) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getSectorRolesRaw();
    }

    public void setRolesRaw(String username, String raw) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setSectorRolesRaw(raw);
        user.touchUpdate();
        repository.save(user);
    }

    // Region-specific role management
    public Map<String, RegionRoles> getRegionRoles(String username) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getRegionRoles();
    }

    public void setRegionRoles(String username, Map<String, RegionRoles> roles) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setRegionRoles(roles);
        user.touchUpdate();
        repository.save(user);
    }

    public RegionRoles getRegionRole(String username, String regionId) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getRegionRole(regionId);
    }

    public boolean setRegionRole(String username, String regionId, RegionRoles role) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        boolean changed = user.setRegionRole(regionId, role);
        if (changed) {
            user.touchUpdate();
            repository.save(user);
        }
        return changed;
    }

    public boolean hasRegionRole(String username, String regionId, RegionRoles role) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.hasRegionRole(regionId, role);
    }

    public boolean removeRegionRole(String username, String regionId) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        boolean changed = user.removeRegionRole(regionId);
        if (changed) {
            user.touchUpdate();
            repository.save(user);
        }
        return changed;
    }

    public List<String> getRegionIdsWithRole(String username, RegionRoles role) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getRegionIdsWithRole(role);
    }

    public List<String> getUserIdsByRegionRole(String regionId, RegionRoles role) {
        String fieldPath = "regionRoles." + regionId;
        Query query = new Query(Criteria.where(fieldPath).is(role.name()));
        query.fields().include("_id");
        return mongoTemplate.find(query, RUser.class).stream()
            .map(RUser::getId)
            .collect(Collectors.toList());
    }

    // User Settings management
    public Map<String, Settings> getUserSettings(String username) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getUserSettings();
    }

    public Settings getSettingsForClientType(String username, String clientType) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.getSettingsForClientType(clientType);
    }

    public void setSettingsForClientType(String username, String clientType, Settings settings) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setSettingsForClientType(clientType, settings);
        user.touchUpdate();
        repository.save(user);
    }

    public boolean hasSettingsForClientType(String username, String clientType) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return user.hasSettingsForClientType(clientType);
    }

    public void setUserSettings(String username, Map<String, Settings> settings) {
        RUser user = repository.findByName(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setUserSettings(settings);
        user.touchUpdate();
        repository.save(user);
    }

    /**
     * Atomically update the user's public title.
     */
    public boolean updatePublicTitle(String username, String title) {
        Query query = new Query(Criteria.where("name").is(username));
        Update update = new Update()
                .set("publicData.title", title)
                .set("modifiedAt", java.time.Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RUser.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically update the user's portrait path.
     */
    public boolean updatePortraitPath(String username, String portraitPath) {
        Query query = new Query(Criteria.where("name").is(username));
        Update update = new Update()
                .set("publicData.portraitPath", portraitPath)
                .set("modifiedAt", java.time.Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RUser.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically update the user's third person model ID.
     */
    public boolean updateThirdPersonModelId(String username, String thirdPersonModelId) {
        Query query = new Query(Criteria.where("name").is(username));
        Update update = new Update()
                .set("publicData.thirdPersonModelId", thirdPersonModelId)
                .set("modifiedAt", java.time.Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RUser.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically update the user's gender.
     */
    public boolean updatePublicGender(String username, String gender) {
        Query query = new Query(Criteria.where("name").is(username));
        Update update = new Update()
                .set("publicData.gender", gender)
                .set("modifiedAt", java.time.Instant.now());
        var result = mongoTemplate.updateFirst(query, update, RUser.class);
        return result.getModifiedCount() > 0;
    }

    /**
     * Atomically change the gold amount for a user.
     * If amount is negative, verifies the user has enough gold first.
     *
     * @param userId MongoDB document id of the user
     * @param amount amount to add (positive) or subtract (negative)
     * @return true if the update was applied
     */
    public boolean changeGold(String userId, long amount) {
        if (amount == 0) return true;

        Query query;
        if (amount < 0) {
            query = new Query(Criteria.where("id").is(userId)
                    .and("gold").gte(-amount));
        } else {
            query = new Query(Criteria.where("id").is(userId));
        }

        Update update = new Update()
                .inc("gold", amount)
                .set("modifiedAt", java.time.Instant.now());

        var result = mongoTemplate.updateFirst(query, update, RUser.class);

        if (result.getModifiedCount() > 0) {
            return true;
        }

        if (amount < 0) {
            log.warn("changeGold failed: userId={}, amount={} - insufficient gold", userId, amount);
        }
        return false;
    }

    public long getUserCount() {
        return repository.count();
    }
}
