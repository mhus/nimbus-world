package de.mhus.nimbus.world.player.commands;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.commands.Command;
import de.mhus.nimbus.world.shared.commands.CommandContext;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Returns current vitals and combat stats from the live player session as JSON.
 * Called by world-control PlayerStatusController to display real-time status.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetStatusCommand implements Command {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "GetStatus";
    }

    @Override
    public CommandResult execute(CommandContext context, List<String> args) {
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CommandResult.error(-2, "Session ID required");
        }

        Optional<PlayerSession> sessionOpt = sessionManager.getBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return CommandResult.error(-4, "Session not found: " + sessionId);
        }

        PlayerSession session = sessionOpt.get();
        if (!(session.getGameplayData() instanceof AdventureData data)) {
            return CommandResult.error(-4, "No adventure data for session");
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();

            // Vitals
            List<Map<String, Object>> vitals = new ArrayList<>();
            if (data.getVitals() != null) {
                data.getVitals().values().stream()
                        .sorted(Comparator.comparingInt(VitalValue::getOrder))
                        .forEach(v -> {
                            Map<String, Object> vital = new LinkedHashMap<>();
                            vital.put("type", v.getType());
                            vital.put("displayName", v.getDisplayName());
                            vital.put("current", round2(v.getCurrent()));
                            vital.put("base", round2(v.getBase()));
                            vital.put("effectiveMax", round2(v.getEffectiveMax()));
                            vital.put("baseRegenRate", round2(v.getBaseRegenRate()));
                            vital.put("effectiveRegenRate", round2(v.getEffectiveRegenRate()));
                            vital.put("color", v.getColor());
                            vital.put("order", v.getOrder());
                            vital.put("options", v.getOptions());
                            vitals.add(vital);
                        });
            }
            result.put("vitals", vitals);

            // Combat stats
            List<Map<String, Object>> combatStats = new ArrayList<>();
            if (data.getCombatStats() != null) {
                for (CombatStat stat : data.getCombatStats().values()) {
                    Map<String, Object> cs = new LinkedHashMap<>();
                    cs.put("type", stat.getType());
                    cs.put("base", round2(stat.getBase()));
                    cs.put("effective", round2(stat.getEffective()));
                    cs.put("buffFlat", round2(stat.getBuffFlat()));
                    cs.put("buffPercent", round2(stat.getBuffPercent()));
                    combatStats.add(cs);
                }
            }
            result.put("combatStats", combatStats);

            String json = objectMapper.writeValueAsString(result);
            return CommandResult.success(json);

        } catch (Exception e) {
            log.error("GetStatusCommand failed: session={}", sessionId, e);
            return CommandResult.error(-5, "Internal error: " + e.getMessage());
        }
    }

    @Override
    public String getHelp() {
        return "Returns current vitals and combat stats from the live session";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
