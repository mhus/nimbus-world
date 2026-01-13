package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.user.ActorRoles;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for development session login.
 * Creates a session-based access token for a player character.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateTypeScript("dto")
public class DevSessionLoginRequest {

    @NotBlank(message = "worldId is required")
    private String worldId;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "characterId is required")
    private String characterId;

    @NotNull(message = "actor role is required")
    @TypeScript(import_ = "ActorRoles", importPath = "./ActorRoles")
    private ActorRoles actor;

    /**
     * Entry point specification for where the player should spawn.
     * Formats:
     * - "last" - Load from last saved position (WPlayerSession)
     * - "grid:q,r" - Load from hex grid coordinates (e.g., "grid:0,0")
     * - "world" - Use world default spawn point
     * - null/blank - Defaults to "world"
     */
    private String entryPoint;
}
