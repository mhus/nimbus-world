package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.world.player.service.GameplayUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * GameplayAction for opening/closing windows.
 * Extends DoorAction with different defaults:
 * - toggleType defaults to "single" (no neighbor block check)
 * - default sound is "n:audio/actions/window.ogg"
 *
 * Server parameters (from block metadata / serverInfo):
 * - action=window
 * - All other parameters are inherited from DoorAction (value, position, defaultDoorState, toggleType, toggleGroup, sound)
 */
@Slf4j
public class WindowAction extends DoorAction {

    public WindowAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    protected String getActionName() {
        return "window";
    }

    @Override
    protected String getDefaultToggleType() {
        return "single";
    }

    @Override
    protected String getDefaultSoundOpen() {
        return GameplayUtil.SOUND_WINDOW_OPEN;
    }

    @Override
    protected String getDefaultSoundClose() {
        return GameplayUtil.SOUND_WINDOW_CLOSE;
    }
}
