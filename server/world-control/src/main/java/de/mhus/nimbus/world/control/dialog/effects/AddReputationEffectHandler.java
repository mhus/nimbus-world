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
public class AddReputationEffectHandler implements DialogEffectHandler {

    private final RCharacterService characterService;

    @Override
    public String getEffectType() { return "addReputation"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) return;
        int delta = effect.delta() != null ? effect.delta() : 0;
        characterService.changeReputation(ctx.getCharacter().getId(), effect.faction(), delta);
        log.debug("Changed reputation {} by {} for character {}", effect.faction(), delta, ctx.getCharacter().getId());
    }
}
