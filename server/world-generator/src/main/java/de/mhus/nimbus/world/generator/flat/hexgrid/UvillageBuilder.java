package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UvillageBuilder implements CompositionBuilder {
    @Override
    public String getType() {
        return "village";
    }

    @Override
    public void build(BuilderContext context) {
        log.info("Building village scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement village generation
    }
}
