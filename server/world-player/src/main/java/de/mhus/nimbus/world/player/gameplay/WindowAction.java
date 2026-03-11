package de.mhus.nimbus.world.player.gameplay;

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
        return "n:audio/actions/window_open.ogg";
    }

    @Override
    protected String getDefaultSoundClose() {
        return "n:audio/actions/window_close.ogg";
    }
}
