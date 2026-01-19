package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.FlatPainter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HillsBuilder implements CompositionBuilder {
    @Override
    public String getType() {
        return "hills";
    }

    @Override
    public void build(BuilderContext context) {
        log.info("Building hills scenario for flat: {} (TODO: implement), neighbors: {}",
                context.getFlat().getFlatId(), context.getNeighborTypes());
        // TODO: Implement hills generation
    }
}
