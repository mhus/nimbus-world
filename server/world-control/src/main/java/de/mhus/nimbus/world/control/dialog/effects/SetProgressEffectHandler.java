package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SetProgressEffectHandler implements DialogEffectHandler {

    private final WLeaseService leaseService;

    @Override
    public String getEffectType() { return "setProgress"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        if (ctx.getDialogLease() == null) return;
        leaseService.setLeaseDataValue(
                ctx.getDialogLease().getLeaseId(), effect.key(), effect.value());
        log.debug("Set dialog lease data {} = {}", effect.key(), effect.value());
    }
}
