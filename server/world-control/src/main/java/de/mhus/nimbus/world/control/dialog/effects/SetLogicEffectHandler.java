package de.mhus.nimbus.world.control.dialog.effects;

import de.mhus.nimbus.world.control.dialog.DialogContext;
import de.mhus.nimbus.world.control.dialog.DialogDtos.Effect;
import de.mhus.nimbus.world.control.dialog.DialogEffectHandler;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SetLogicEffectHandler implements DialogEffectHandler {

    private static final String LOGIC_PLAYER_ID = "logic";
    private static final String LOGIC_FLAG_TYPE = "logic-flag";
    private static final String LOGIC_PACKAGE = "dialog";

    private final WProgressService progressService;

    @Override
    public String getEffectType() { return "setLogic"; }

    @Override
    public void execute(Effect effect, DialogContext ctx) {
        String flag = effect.flag();
        Object value = effect.value() != null ? effect.value() : true;

        var logicProgress = progressService
                .findByWorldIdAndPlayerIdAndTypeAndQuest(ctx.getWorldId(), LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null)
                .orElse(null);

        if (logicProgress == null) {
            Map<String, Object> state = new HashMap<>();
            Map<String, Object> pkg = new HashMap<>();
            pkg.put(flag, value);
            state.put(LOGIC_PACKAGE, pkg);
            progressService.save(ctx.getWorldId(), LOGIC_PLAYER_ID, LOGIC_FLAG_TYPE, null, state);
        } else {
            progressService.setProgressDataValue(logicProgress.getProgressId(),
                    LOGIC_PACKAGE + "." + flag, value);
        }

        log.debug("Set logic flag dialog.{} = {} in world {}", flag, value, ctx.getWorldId());
    }
}
