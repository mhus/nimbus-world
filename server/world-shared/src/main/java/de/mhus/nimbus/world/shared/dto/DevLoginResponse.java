package de.mhus.nimbus.world.shared.dto;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for development login operations.
 * Contains access token and URLs for session/agent access.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateTypeScript("dto")
public class DevLoginResponse {

    /**
     * JWT access token for authentication.
     */
    private String accessToken;

    /**
     * List of cookie URLs for multi-domain cookie setup.
     * Currently hardcoded, will be made dynamic later.
     */
    private List<String> accessUrls;

    /**
     * Jump URL to redirect user after login.
     * Currently hardcoded, will be made dynamic later.
     */
    private String jumpUrl;

    /**
     * Session ID (only for session login, null for agent).
     */
    private String sessionId;

    /**
     * Player ID (only for session login, null for agent).
     */
    private String playerId;
}
