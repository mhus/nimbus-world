package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SetProgressEffectHandler implements DialogEffectHandler {

    private final WProgressService progressService;

    @Override
    public String getEffectType() { return "setProgress"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        if (ctx.getDialogProgress() == null) return;
        progressService.setProgressDataValue(
                ctx.getDialogProgress().getProgressId(), effect.key(), effect.value());
        log.debug("Set dialog progress {} = {}", effect.key(), effect.value());
    }
}
