package de.mhus.nimbus.world.control.security;

import de.mhus.nimbus.shared.security.JwtService;
import de.mhus.nimbus.shared.service.MetricService;
import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.access.AccessSettings;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import de.mhus.nimbus.world.shared.session.WSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the dev-only Bearer authentication of {@link ControlAccessFilter}. */
class ControlAccessFilterTest {

    private final AccessSettings settings = mock(AccessSettings.class);

    private ControlAccessFilter filter(boolean devLoginEnvEnabled) {
        return new ControlAccessFilter(
                mock(JwtService.class), mock(WSessionService.class), settings,
                mock(RegionSettings.class), mock(SSettingsService.class), mock(MetricService.class),
                devLoginEnvEnabled);
    }

    private HttpServletRequest request(String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        when(req.getRequestURI()).thenReturn("/control/worlds/x/assets/y");
        return req;
    }

    @Test
    void acceptsCorrectKeyWhenDevLoginEnabled() {
        when(settings.isDevLoginEnabled()).thenReturn(true);
        when(settings.getDevLoginAccessKey()).thenReturn("SECRET-KEY");
        HttpServletRequest req = request("Bearer SECRET-KEY");

        assertThat(filter(true).validateDevLoginBearer(req)).isTrue();
        verify(req).setAttribute(AccessFilterBase.ATTR_IS_AUTHENTICATED, true);
        verify(req).setAttribute(AccessFilterBase.ATTR_DEV_FULL_ACCESS, true);
        verify(req).setAttribute(AccessFilterBase.ATTR_USER_ID, "dev");
    }

    @Test
    void rejectsWrongKey() {
        when(settings.isDevLoginEnabled()).thenReturn(true);
        when(settings.getDevLoginAccessKey()).thenReturn("SECRET-KEY");

        assertThat(filter(true).validateDevLoginBearer(request("Bearer WRONG-KEY"))).isFalse();
    }

    @Test
    void rejectsWhenDevLoginDisabledByEnvFlag() {
        // nimbus.devlogin.enabled=false (default) -> off regardless of key / DB toggle
        assertThat(filter(false).validateDevLoginBearer(request("Bearer SECRET-KEY"))).isFalse();
    }

    @Test
    void rejectsWhenDevLoginDisabledByDbToggle() {
        when(settings.isDevLoginEnabled()).thenReturn(false);
        assertThat(filter(true).validateDevLoginBearer(request("Bearer SECRET-KEY"))).isFalse();
    }

    @Test
    void rejectsWithoutBearerHeader() {
        when(settings.isDevLoginEnabled()).thenReturn(true);
        when(settings.getDevLoginAccessKey()).thenReturn("SECRET-KEY");

        assertThat(filter(true).validateDevLoginBearer(request(null))).isFalse();
        assertThat(filter(true).validateDevLoginBearer(request("SECRET-KEY"))).isFalse(); // no "Bearer " prefix
    }

    @Test
    void rejectsWhenNoKeyConfigured() {
        when(settings.isDevLoginEnabled()).thenReturn(true);
        when(settings.getDevLoginAccessKey()).thenReturn("");

        assertThat(filter(true).validateDevLoginBearer(request("Bearer "))).isFalse();
    }
}
