package de.mhus.nimbus.world.shared.region;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.RegionItemInfo; // geändert
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
public class RCharacter {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;      // eindeutiger Name pro userId
    private String display;   // Anzeige-Name

    @CreatedDate
    private Instant createdAt;

    private PlayerInfo publicData;
    private PlayerBackpack backpack;

    // Skills (Skill-Name -> Level)
    private Map<String, Integer> skills;

    @Indexed
    private String regionId; // neu: Region-Zuordnung

    public RCharacter() { }
    public RCharacter(String userId, String regionId, String name, String display) {
        this.userId = userId;
        this.regionId = regionId;
        this.name = name;
        this.display = display;
    }

    public Map<String, Integer> getSkills() { if (skills == null) skills = new HashMap<>(); return skills; }

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
}
