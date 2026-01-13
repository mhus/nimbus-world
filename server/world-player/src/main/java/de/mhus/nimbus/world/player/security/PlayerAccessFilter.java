package de.mhus.nimbus.world.player.security;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Access filter for world-player service.
 * Extends AccessFilterBase to validate sessionToken cookies and attach
 * session information to requests.
 *
 * Enforces authentication for all endpoints except:
 * - /player/aaa/authorize (cookie setting endpoint)
 *
 * IMPORTANT: Only allows SESSION tokens (agent=false).
 * Agent tokens are not allowed in world-player.
 */
@Component
@Slf4j
public class PlayerAccessFilter extends AccessFilterBase {

    private final AccessSettings accessProperties;

    public PlayerAccessFilter(JwtService jwtService, WSessionService sessionService, AccessSettings accessProperties, RegionSettings regionProperties) {
        super(jwtService, sessionService, regionProperties);
        this.accessProperties = accessProperties;
    }

    @Override
    protected boolean shouldRequireAuthentication(String requestUri, String method) {
        // Allow access to cookie setting endpoint
        if (requestUri.startsWith("/player/aaa/authorize")) {
            return false;
        }

        // All other endpoints require authentication
        return true;
    }

    @Override
    protected boolean isClaimsAcceptable(SessionTokenClaims claims) {
        // Player service only accepts SESSION tokens (agent=false)
        // Agent tokens are not allowed
        if (claims.agent()) {
            log.warn("Agent token not allowed in player service - userId={}, worldId={}",
                    claims.userId(), claims.worldId());
            return false;
        }
        return true;
    }

    @Override
    protected String getLoginUrl() {
        return accessProperties.getLoginUrl();
    }
}
