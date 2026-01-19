package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UcityBuilder implements CompositionBuilder {
    @Override
    public String getType() {
        return "city";
    }

    @Override
    public void build(BuilderContext context) {
        log.info("Building city scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement city generation
    }
}
