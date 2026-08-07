package de.mhus.nimbus.world.shared.access;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingString;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Precedence of the dev-login access key: explicit configuration (property/env, then setting) wins
 * over the confidential key file, so the key can be injected into a container or test harness where
 * that file does not exist and the filesystem should not be written to.
 * <p>
 * Every case here configures a key or disables dev-login, so the file path is never taken — that is
 * the point of the test: no test may create {@code confidential/dev-login-key.txt}.
 */
class AccessSettingsDevLoginKeyTest {

    private static final String FROM_SETTING = "key-from-settings-0000";
    private static final String FROM_PROPERTY = "key-from-property-0000";

    private AccessSettings settings(boolean devLoginEnabled, String property, String settingValue) {
        SSettingsService settingsService = mock(SSettingsService.class);
        when(settingsService.getInteger(anyString(), anyInt())).thenReturn(null);
        when(settingsService.getBoolean(anyString(), anyBoolean())).thenReturn(null);

        SettingString keySetting = mock(SettingString.class);
        when(keySetting.get()).thenReturn(settingValue);
        when(settingsService.getString(eq("access.devLoginAccessKey"), anyString())).thenReturn(keySetting);

        AccessSettings accessSettings = new AccessSettings(settingsService);
        ReflectionTestUtils.setField(accessSettings, "devLoginEnvEnabled", devLoginEnabled);
        ReflectionTestUtils.setField(accessSettings, "devLoginKeyProperty", property);
        ReflectionTestUtils.invokeMethod(accessSettings, "init");
        return accessSettings;
    }

    @Test
    void settingIsUsedWhenNoPropertyIsSet() {
        assertThat(settings(true, "", FROM_SETTING).getDevLoginAccessKey()).isEqualTo(FROM_SETTING);
    }

    @Test
    void propertyWinsOverSetting() {
        assertThat(settings(true, FROM_PROPERTY, FROM_SETTING).getDevLoginAccessKey())
                .isEqualTo(FROM_PROPERTY);
    }

    @Test
    void keyIsTrimmed() {
        assertThat(settings(true, "  " + FROM_PROPERTY + "  ", "").getDevLoginAccessKey())
                .isEqualTo(FROM_PROPERTY);
    }

    /**
     * The production case: dev-login off means no key at all, whatever is configured — and the
     * filesystem is never touched (a read-only container FS must not fail bean init).
     */
    @Test
    void noKeyWhenDevLoginIsDisabled() {
        assertThat(settings(false, FROM_PROPERTY, FROM_SETTING).getDevLoginAccessKey()).isNull();
    }
}
