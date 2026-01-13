package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.user.WorldRoles;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO containing role information for a character in a world.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateTypeScript("dto")
public class WorldRoleDto {

    private String characterId;
    private String worldId;
    @TypeScript(type = "string[]")
    private List<WorldRoles> roles;
}
