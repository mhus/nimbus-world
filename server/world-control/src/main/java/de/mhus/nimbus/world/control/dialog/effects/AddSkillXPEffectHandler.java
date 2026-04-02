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
public class AddSkillXPEffectHandler implements DialogEffectHandler {

    private final RCharacterService characterService;

    @Override
    public String getEffectType() { return "addSkillXP"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        if (ctx.getCharacter() == null) return;
        int amount = effect.amount() != null ? effect.amount() : 0;
        characterService.incrementSkillAtomic(ctx.getCharacter().getId(), effect.skill(), amount);
        log.debug("Added {} XP to skill {} for character {}", amount, effect.skill(), ctx.getCharacter().getId());
    }
}
