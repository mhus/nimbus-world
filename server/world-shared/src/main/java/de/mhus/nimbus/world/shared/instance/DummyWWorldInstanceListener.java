package de.mhus.nimbus.world.shared.instance;

import de.mhus.nimbus.world.shared.world.WWorldInstanceListener;
import de.mhus.nimbus.world.shared.world.WorldInstanceEvent;
import org.springframework.stereotype.Service;

@Service
public class DummyWWorldInstanceListener implements WWorldInstanceListener {
    @Override
    public void worldInstanceCreated(WorldInstanceEvent event) {

    }

    @Override
    public void worldInstanceDeleted(WorldInstanceEvent event) {

    }
}
