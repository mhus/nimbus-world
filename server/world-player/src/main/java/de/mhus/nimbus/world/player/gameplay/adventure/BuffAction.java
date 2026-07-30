package de.mhus.nimbus.world.player.gameplay.adventure;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.ActiveEffect;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Buff action that applies instant vital changes and timed max-value buffs.
 *
 * <p>Server parameters:</p>
 * <ul>
 *   <li>thirst=N   - reduce thirst by up to N points</li>
 *   <li>hunger=N   - reduce hunger by up to N points</li>
 *   <li>health=N   - add up to N health points</li>
 *   <li>mana=N     - add up to N mana points</li>
 *   <li>stamina=N  - add up to N stamina points</li>
 *   <li>health_regen=N - add N health/sec regen (timed, requires duration)</li>
 *   <li>mana_regen=N   - add N mana/sec regen (timed, requires duration)</li>
 *   <li>stamina_regen=N - add N stamina/sec regen (timed, requires duration)</li>
 *   <li>max_health=N  - increase max health by N (timed, requires duration)</li>
 *   <li>max_mana=N    - increase max mana by N (timed, requires duration)</li>
 *   <li>max_stamina=N - increase max stamina by N (timed, requires duration)</li>
 *   <li>duration=S    - duration in seconds for timed buffs</li>
 *   <li>sound=...     - sound to play (default: 'n:audio/actions/eat.ogg')</li>
 *   <li>texture=...   - icon texture for timed effect display</li>
 *   <li>icon=...      - fallback for texture</li>
 * </ul>
 */
@Slf4j
public class BuffAction implements GameplayAction {

    private static final String DEFAULT_SOUND = "n:audio/actions/eat.ogg";

    private final AdventureGameplay adventure;

    public BuffAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) return false;
        return applyBuff(session, serverInfo, null);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) return false;
        if (entity == null || entity.getServer() == null) return false;
        return applyBuff(session, entity.getServer(), null);
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        Map<String, String> serverParams = item.getServer();
        if (serverParams == null) return false;

        // Resolve item texture for flashImage and effect fallback
        String itemTexture = item.getPublicData() != null ? item.getPublicData().getTexture() : null;

        boolean applied = applyBuff(session, serverParams, itemTexture);
        if (applied) {
            adventure.getGameplayService().reduceItem(session, item.getName(), 1);
        }
        return applied;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        return false;
    }

    private boolean applyBuff(PlayerSession session, Map<String, String> params, String itemTexture) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        double duration = parseDouble(params.get("duration"), 0);
        String source = "buff:" + params.getOrDefault("name", "unknown");

        // Resolve texture: server param "texture" > "icon" > item's own texture
        String texture = params.get("texture");
        if (texture == null || texture.isBlank()) {
            texture = params.get("icon");
        }
        if ((texture == null || texture.isBlank()) && itemTexture != null && !itemTexture.isBlank()) {
            texture = itemTexture;
        }

        boolean applied = false;

        // Instant vital changes
        applied |= applyInstantReduce(data, "thirst", params.get("thirst"));
        applied |= applyInstantReduce(data, "hunger", params.get("hunger"));
        applied |= applyInstantAdd(data, "health", params.get("health"));
        applied |= applyInstantAdd(data, "mana", params.get("mana"));
        applied |= applyInstantAdd(data, "stamina", params.get("stamina"));

        // Timed regen buffs
        applied |= applyTimedEffect(data, session, "health.regen", params.get("health_regen"), duration, source, texture);
        applied |= applyTimedEffect(data, session, "mana.regen", params.get("mana_regen"), duration, source, texture);
        applied |= applyTimedEffect(data, session, "stamina.regen", params.get("stamina_regen"), duration, source, texture);

        // Timed max value buffs
        applied |= applyTimedEffect(data, session, "health.max", params.get("max_health"), duration, source, texture);
        applied |= applyTimedEffect(data, session, "mana.max", params.get("max_mana"), duration, source, texture);
        applied |= applyTimedEffect(data, session, "stamina.max", params.get("max_stamina"), duration, source, texture);

        if (applied) {
            String sound = params.getOrDefault("sound", DEFAULT_SOUND);
            adventure.getClientService().sendCommand(session, "playSound", List.of(sound));

            // Flash the item texture on screen
            if (texture != null && !texture.isBlank()) {
                adventure.getClientService().sendCommand(session, "flashImage",
                        List.of(texture, "500", "0.5"));
            }

            log.info("Applied buff to player {}: {}", session.getEntityId(), describeParams(params));
        }

        return applied;
    }

    private boolean applyInstantReduce(AdventureData data, String vitalType, String valueStr) {
        if (valueStr == null || valueStr.isBlank()) return false;
        double amount = parseDouble(valueStr, 0);
        if (amount <= 0) return false;

        VitalValue vital = data.getVital(vitalType);
        if (vital == null) return false;

        vital.setCurrent(vital.getCurrent() - amount);
        vital.clamp();
        return true;
    }

    private boolean applyInstantAdd(AdventureData data, String vitalType, String valueStr) {
        if (valueStr == null || valueStr.isBlank()) return false;
        double amount = parseDouble(valueStr, 0);
        if (amount <= 0) return false;

        VitalValue vital = data.getVital(vitalType);
        if (vital == null) return false;

        vital.setCurrent(vital.getCurrent() + amount);
        vital.clamp();
        return true;
    }

    private boolean applyTimedEffect(AdventureData data, PlayerSession session,
                                      String stat, String valueStr, double duration,
                                      String source, String texture) {
        if (valueStr == null || valueStr.isBlank()) return false;
        double amount = parseDouble(valueStr, 0);
        if (amount <= 0 || duration <= 0) return false;

        ActiveEffect effect = ActiveEffect.builder()
                .source(source)
                .stat(stat)
                .value(amount)
                .duration(duration)
                .maxDuration(duration)
                .build();

        data.addEffect(effect);

        if (texture != null && !texture.isBlank()) {
            long durationMs = (long) (duration * 1000);
            adventure.getClientService().sendCommand(session, "effect",
                    List.of("add", texture, String.valueOf(durationMs)));
        }

        return true;
    }

    private String describeParams(Map<String, String> params) {
        var sb = new StringBuilder();
        for (String key : List.of("thirst", "hunger", "health", "mana", "stamina", "health_regen", "mana_regen", "stamina_regen", "max_health", "max_mana", "max_stamina", "duration")) {
            String val = params.get(key);
            if (val != null && !val.isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(key).append("=").append(val);
            }
        }
        return sb.toString();
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
