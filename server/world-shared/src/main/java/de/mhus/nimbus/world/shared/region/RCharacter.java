package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.RegionItemInfo; // geändert
import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.PlayerCharacter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "r_characters")
@ActualSchemaVersion("1.0.0")
@CompoundIndex(def = "{userId:1, regionId:1, name:1}", unique = true)
@Data
@Builder
@AllArgsConstructor
@GenerateTypeScript("entities")
public class RCharacter {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;      // eindeutiger Name pro userId

    @CreatedDate
    private Instant createdAt;
    private Instant modifiedAt;

    @TypeScript(import_ = "PlayerInfo", importPath = "../../types/PlayerInfo")
    private PlayerInfo publicData;
    @TypeScript(import_ = "PlayerBackpack", importPath = "../../configs/EngineConfiguration")
    private PlayerBackpack backpack;

    // Skills (Skill-Name -> Level)
    private Map<String, Integer> skills;

    // Skill points available for distribution
    private int skillPoints;

    // Accumulated experience towards next skill point
    private long skillExperience;

    // Constitution: wear/durability per category (1.0 = perfect, 0.0 = broken)
    // Keys: e.g. "weapon", "armor", "magic", "tool"
    private Map<String, Double> constitution;

    private Map<String, String> attributes; // neu: Attribute

    @Indexed
    private String regionId; // neu: Region-Zuordnung

    public RCharacter() { }
    public RCharacter(String userId, String regionId, String name) {
        this.userId = userId;
        this.regionId = regionId;
        this.name = name;
    }

    public Map<String, Integer> getSkills() { if (skills == null) skills = new HashMap<>(); return skills; }
    public Map<String, Double> getConstitution() { if (constitution == null) constitution = new HashMap<>(); return constitution; }

    /**
     * Get constitution value for a category. Returns 1.0 if not set.
     */
    public double getConstitutionValue(String category) {
        return getConstitution().getOrDefault(category, 1.0);
    }

    public void setSkill(String skill, int level) {
        if (level < 0) level = 0;
        getSkills().put(skill, level);
    }
    public int incrementSkill(String skill, int delta) {
        int current = getSkills().getOrDefault(skill, 0);
        int next = Math.max(0, current + delta);
        getSkills().put(skill, next);
        return next;
    }

    /**
     * Initialize timestamps.
     */
    public void touchCreate() {
        createdAt = Instant.now();
        touchUpdate();
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        if (publicData != null) {
            publicData.setPlayerId(getUserId() + ":" + getName());
        }
        modifiedAt = Instant.now();
    }

}
