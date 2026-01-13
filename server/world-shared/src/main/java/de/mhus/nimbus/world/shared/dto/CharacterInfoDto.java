package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified character information DTO for list operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateTypeScript("dto")
public class CharacterInfoDto {

    private String id;
    private String name;
    private String display;
    private String userId;
    private String regionId;
}
