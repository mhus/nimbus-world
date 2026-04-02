package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TakeItemEffectHandler implements DialogEffectHandler {

    private final RCharacterService characterService;

    @Override
    public String getEffectType() { return "takeItem"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) {
            log.warn("Cannot take item: no character in context");
            return;
        }
        int count = effect.count() != null ? effect.count() : 1;
        boolean success = characterService.removeBackpackItem(ctx.getCharacter().getId(), effect.item(), count);
        if (success) {
            log.debug("Took {} x{} from character {}", effect.item(), count, ctx.getCharacter().getId());
        } else {
            log.warn("Failed to take item {} from character {}", effect.item(), ctx.getCharacter().getId());
        }
    }
}
