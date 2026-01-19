package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UdessertBuilder implements CompositionBuilder {
    @Override
    public String getType() {
        return "dessert";
    }

    @Override
    public void build(BuilderContext context) {
        log.info("Building dessert scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement dessert generation
    }
}
