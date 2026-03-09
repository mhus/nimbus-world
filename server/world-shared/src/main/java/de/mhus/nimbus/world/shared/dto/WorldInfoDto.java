package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.world.shared.world.WEpochMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Simplified world information DTO for list operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateTypeScript("dto")
public class WorldInfoDto {

    private String worldId;
    private String title;
    private String description;
    private String regionId;
    private boolean enabled;
    private boolean publicFlag;

    /**
     * Epoch definitions for this world.
     */
    @TypeScript(follow = true)
    @Builder.Default
    private List<WEpochMeta> epoches = List.of();
}
