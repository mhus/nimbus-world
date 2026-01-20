package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public class UmountainsBuilder extends HexGridBuilder {

    @Override
    public void build(BuilderContext context) {
        log.info("Building mountains scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement mountains generation
    }
}
