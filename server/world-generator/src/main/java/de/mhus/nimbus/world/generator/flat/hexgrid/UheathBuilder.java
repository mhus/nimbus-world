package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UheathBuilder implements CompositionBuilder {
    @Override
    public String getType() {
        return "heath";
    }

    @Override
    public void build(BuilderContext context) {
        log.info("Building heath scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement heath generation
    }
}
