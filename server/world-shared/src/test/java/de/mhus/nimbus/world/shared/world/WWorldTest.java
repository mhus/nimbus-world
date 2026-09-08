package de.mhus.nimbus.world.shared.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WWorldTest {

    @Test
    void builderDefaults() {
        WWorld w = WWorld.builder().worldId("terra").build();
        assertNotNull(w.getOwner());
        assertTrue(w.getOwner().isEmpty());
        assertFalse(w.isPublicFlag());
        assertEquals(WorldInstanceType.NONE, w.getInstanceType());
        assertEquals(0, w.getMaxPlayersPerInstance());
    }
}
