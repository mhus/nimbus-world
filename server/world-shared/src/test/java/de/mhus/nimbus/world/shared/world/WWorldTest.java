package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WWorldTest {

    @Test
    void builderDefaults() {
        WWorld w = WWorld.builder().worldId("terra").build();
        assertNotNull(w.getOwner());
        assertTrue(w.getOwner().isEmpty());
        assertFalse(w.isPublicFlag());
        assertFalse(w.isInstanceable());
    }

}
