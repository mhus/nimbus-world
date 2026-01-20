package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
public class UswampBuilder extends HexGridBuilder {

    @Override
    public void build(BuilderContext context) {
        log.info("Building swamp scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement swamp generation
    }
}
