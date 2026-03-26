package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for development agent login.
 * Creates an agent access token without session binding.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateTypeScript("dto")
public class DevAgentLoginRequest {

    @NotBlank(message = "worldId is required")
    private String worldId;

    @NotBlank(message = "userId is required")
    private String userId;
}
