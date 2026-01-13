package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String name;
    private String description;
    private String regionId;
    private boolean enabled;
    private boolean publicFlag;
}
