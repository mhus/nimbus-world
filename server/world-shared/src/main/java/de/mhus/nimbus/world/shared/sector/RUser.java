package de.mhus.nimbus.world.shared.sector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.mhus.nimbus.generated.configs.Settings;
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import de.mhus.nimbus.shared.types.PlayerUser;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.user.SectorRoles;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import de.mhus.nimbus.shared.user.RegionRoles;

@Document(collection = "r_users")
@ActualSchemaVersion("1.0.0")
@Data
@Builder
@AllArgsConstructor
@GenerateTypeScript("entities")
public class RUser implements Identifiable {

    public static final String DEFAULT_REGION_ROLE = "PLAYER";

    @Id
    @TypeScript(ignore = true)
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    @CreatedDate
    private Instant createdAt;

    @TypeScript(import_ = "PlayerUser", importPath = "./PlayerUser")
    private PlayerUser publicData;

    private Boolean enabled; // wenn null -> enabled

    // Globale Rollen für den Region-Server (nicht pro einzelne Region)
    @Field("roles") // liest alte Property 'roles' weiterhin ein
    @TypeScript(import_ = "SectorRoles", importPath = "./SectorRoles")
    private Set<SectorRoles> sectorRoles; // null oder leer -> keine Rollen
    @TypeScript(import_ = "RegionRoles", importPath = "./RegionRoles")
    private Map<String, RegionRoles> regionRoles; // regionId -> role

    // Charakter-Limits pro Region: regionId -> maxCount
    private Map<String,Integer> characterLimits;

    // User Settings per ClientType: clientType (as String) -> Settings
    @TypeScript(import_ = "Settings", importPath = "../../configs")
    private Map<String, Settings> userSettings;

    public RUser() { this.enabled = true; }

    public boolean isEnabled() { return enabled == null || enabled; }
    public Boolean getEnabledRaw() { return enabled; }
    public void enable() { this.enabled = true; }
    public void disable() { this.enabled = false; }

    // ServerRoles API
    public Set<SectorRoles> getSectorRoles() { return sectorRoles == null ? Collections.emptySet() : Collections.unmodifiableSet(sectorRoles); }
    public void setSectorRoles(Set<SectorRoles> roles) { this.sectorRoles = (roles == null || roles.isEmpty()) ? null : new HashSet<>(new LinkedHashSet<>(roles)); }
    public boolean addSectorRole(SectorRoles role) {
        if (role == null) return false; if (sectorRoles == null) sectorRoles = new HashSet<>(); if (sectorRoles.contains(role)) return false; sectorRoles.add(role); return true;
    }
    public boolean removeSectorRole(SectorRoles role) {
        if (role == null || sectorRoles == null) return false; return sectorRoles.remove(role);
    }
    public boolean hasSectorRole(SectorRoles role) { return role != null && sectorRoles != null && sectorRoles.contains(role); }

    public String getSectorRolesRaw() { return sectorRoles == null ? "" : sectorRoles.stream().map(Enum::name).collect(Collectors.joining(",")); }
    public void setSectorRolesRaw(String raw) {
        if (raw == null || raw.isBlank()) { sectorRoles = null; return; }
        Set<SectorRoles> list = new HashSet<>();
        for (String part : raw.split(",")) {
            String p = part.trim(); if (p.isEmpty()) continue;
            try { list.add(SectorRoles.valueOf(p)); } catch (IllegalArgumentException ignored) { }
        }
        setSectorRoles(list);
    }

    // RegionRoles API
    public Map<String, RegionRoles> getRegionRoles() {
        return regionRoles == null ? Collections.emptyMap() : Collections.unmodifiableMap(regionRoles);
    }

    public void setRegionRoles(Map<String, RegionRoles> roles) {
        this.regionRoles = (roles == null || roles.isEmpty()) ? null : new java.util.HashMap<>(roles);
    }

    public RegionRoles getRegionRole(String regionId) {
        if (regionId == null) return null;
        if (regionRoles == null) {
            // Fallback auf DEFAULT_REGION_ROLE nur wenn NULL, nicht bei Empty
            try {
                return RegionRoles.valueOf(DEFAULT_REGION_ROLE);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        RegionRoles role = regionRoles.get(regionId);
        if (role == null) {
            // Fallback auf DEFAULT_REGION_ROLE
            try {
                return RegionRoles.valueOf(DEFAULT_REGION_ROLE);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return role;
    }

    public boolean setRegionRole(String regionId, RegionRoles role) {
        if (regionId == null) return false;
        if (role == null) {
            if (regionRoles == null) return false;
            return regionRoles.remove(regionId) != null;
        }
        if (regionRoles == null) regionRoles = new java.util.HashMap<>();
        RegionRoles oldRole = regionRoles.put(regionId, role);
        return !role.equals(oldRole);
    }

    public boolean hasRegionRole(String regionId, RegionRoles role) {
        if (regionId == null || role == null) return false;
        if (regionRoles == null) {
            // Bei NULL prüfen wir gegen DEFAULT_REGION_ROLE
            try {
                return role.equals(RegionRoles.valueOf(DEFAULT_REGION_ROLE));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        RegionRoles actualRole = regionRoles.get(regionId);
        if (actualRole != null) {
            // Explizit gesetzte Rolle
            return role.equals(actualRole);
        } else {
            // Keine explizite Rolle für diese Region - prüfen gegen DEFAULT_REGION_ROLE
            try {
                return role.equals(RegionRoles.valueOf(DEFAULT_REGION_ROLE));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    public boolean removeRegionRole(String regionId) {
        if (regionId == null || regionRoles == null) return false;
        boolean removed = regionRoles.remove(regionId) != null;
        if (removed && regionRoles.isEmpty()) {
            regionRoles = null; // Set to null when empty to maintain consistency
        }
        return removed;
    }

    public List<String> getRegionIdsWithRole(RegionRoles role) {
        if (role == null || regionRoles == null) return Collections.emptyList();
        return regionRoles.entrySet().stream()
                .filter(entry -> role.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // Charakter-Limits API
    public java.util.Map<String,Integer> getCharacterLimits() { return characterLimits == null ? java.util.Map.of() : java.util.Map.copyOf(characterLimits); }
    public void setCharacterLimits(java.util.Map<String,Integer> limits) { this.characterLimits = (limits == null || limits.isEmpty()) ? null : new java.util.HashMap<>(limits); }
    public Integer getCharacterLimitForRegion(String regionId) { if (characterLimits == null) return null; return characterLimits.get(regionId); }
    public void setCharacterLimitForRegion(String regionId, Integer limit) { if (characterLimits == null) characterLimits = new java.util.HashMap<>(); if (limit == null) characterLimits.remove(regionId); else characterLimits.put(regionId, limit); }

    // UserSettings API
    public Map<String, Settings> getUserSettings() { return userSettings == null ? Collections.emptyMap() : Collections.unmodifiableMap(userSettings); }
    public void setUserSettings(Map<String, Settings> settings) { this.userSettings = (settings == null || settings.isEmpty()) ? null : new java.util.HashMap<>(settings); }
    public Settings getSettingsForClientType(String clientType) { if (clientType == null || userSettings == null) return null; return userSettings.get(clientType); }
    public void setSettingsForClientType(String clientType, Settings settings) {
        if (clientType == null) return;
        if (settings == null) {
            if (userSettings != null) userSettings.remove(clientType);
            if (userSettings != null && userSettings.isEmpty()) userSettings = null;
        } else {
            if (userSettings == null) userSettings = new java.util.HashMap<>();
            userSettings.put(clientType, settings);
        }
    }
    public boolean hasSettingsForClientType(String clientType) { return clientType != null && userSettings != null && userSettings.containsKey(clientType); }

    public boolean isSectorAdmin() {
        return hasSectorRole(SectorRoles.ADMIN);
    }

    public boolean isRegionAdmin(WorldId worldId) {
        if (worldId.isCollection()) return false;
        return hasRegionRole(worldId.getRegionId(), RegionRoles.ADMIN);
    }

}
