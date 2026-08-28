package de.mhus.nimbus.world.player.gameplay.adventure;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;

/**
 * GameplayAction for collecting rewards from blocks and entities.
 *
 * Server parameters (from block metadata / serverInfo):
 * - action=collect
 * - collectReward=probability:itemId:quantity[,...] (required), e.g. 100:wood:1,10:_gold_:5
 * - collectCooldown=&lt;seconds&gt; regrow time of the collected element (default: 0 = unlimited)
 * - collectStatus=&lt;status&gt; block status while the element is exhausted (default: empty)
 *
 * Cooldown handling for blocks: the collected block switches to the collect status and is
 * blocked for everybody in this world instance until world-life resets it. Additionally a
 * short per-player cooldown prevents click spamming.
 *
 * Entities have no block status, they keep the per-player cooldown of collectCooldown.
 */
@Slf4j
public class CollectAction implements GameplayAction {

    /** Block status used while a collected element is exhausted */
    private static final String DEFAULT_COLLECT_STATUS = "empty";

    /** Per-player cooldown between two collect actions, prevents click spamming */
    private static final long PLAYER_COLLECT_COOLDOWN_MS = 1000;

    private final AdventureGameplay adventure;
    private final Random random = new Random();

    public CollectAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) return false;
        return collect(session, serverInfo, new int[]{x, y, z});
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) return false;
        if (entity == null || entity.getServer() == null) return false;
        return collect(session, entity.getServer(), null);
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Collect only via interact on blocks/entities
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        // Collect only via interact on blocks/entities
        return false;
    }

    /**
     * Collect the configured rewards.
     *
     * @param position block world coordinates, or null for entities (no per-element cooldown)
     */
    private boolean collect(PlayerSession session, Map<String, String> serverInfo, int[] position) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        long now = System.currentTimeMillis();
        if (data.getNextCollectAllowed() > now) {
            log.debug("Collect on cooldown for player {} ({}ms remaining)",
                    session.getEntityId(), data.getNextCollectAllowed() - now);
            return false;
        }

        String rewardStr = serverInfo.get("collectReward");
        if (rewardStr == null || rewardStr.isBlank()) {
            log.debug("No collectReward defined in server info");
            return false;
        }

        int cooldownSeconds = 0;
        String cooldownStr = serverInfo.get("collectCooldown");
        if (cooldownStr != null && !cooldownStr.isBlank()) {
            try {
                cooldownSeconds = Integer.parseInt(cooldownStr.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid collectCooldown value: {}", cooldownStr);
            }
        }

        // Resolve the per-element cooldown target (blocks only)
        String collectStatus = resolveCollectStatus(serverInfo);
        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        String chunkKey = null;
        String blockKey = null;
        if (position != null && cooldownSeconds > 0 && worldId != null) {
            var world = adventure.getWorldService().getByWorldId(worldId).orElse(null);
            if (world == null) {
                log.warn("World not found: {}", worldId);
                return false;
            }
            chunkKey = world.getChunkKey(position[0], position[2]);
            blockKey = position[0] + "," + position[1] + "," + position[2];

            // Claim the element atomically, so two players never collect the same block twice
            if (!adventure.getProgressService().claimBlockStatus(worldId, chunkKey, blockKey, collectStatus)) {
                // Element is in cooldown, throttle further attempts of this player
                data.setNextCollectAllowed(now + PLAYER_COLLECT_COOLDOWN_MS);
                adventure.getClientService().sendNotification(session, 3, "",
                        "Hier gibt es nichts mehr zu ernten", null);
                log.debug("Collect blocked, element exhausted: worldId={}, chunk={}, block={}",
                        worldId, chunkKey, blockKey);
                return true;
            }
        }

        String[] rewards = rewardStr.split(",");
        for (String reward : rewards) {
            String trimmed = reward.trim();
            if (trimmed.isEmpty()) continue;

            String[] parts = trimmed.split(":");
            if (parts.length != 3) {
                log.warn("Invalid collectReward entry '{}', expected probability:itemId:quantity", trimmed);
                continue;
            }

            try {
                int probability = Integer.parseInt(parts[0].trim());
                String itemId = parts[1].trim();
                int quantity = Integer.parseInt(parts[2].trim());

                if (random.nextInt(100) < probability) {
                    if (handleSyntheticReward(session, data, itemId, quantity)) {
                        log.info("Player {} collected synthetic {} x{} (probability {}%)",
                                session.getEntityId(), itemId, quantity, probability);
                    } else {
                        boolean added = adventure.getGameplayService().putIntoBackpack(session, itemId, quantity);
                        if (added) {
                            sendCollectNotification(session, itemId, quantity);
                            log.info("Player {} collected {} x{} (probability {}%)",
                                    session.getEntityId(), itemId, quantity, probability);
                        } else {
                            log.debug("Player {} backpack full, could not add {} x{}",
                                    session.getEntityId(), itemId, quantity);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid number in collectReward entry '{}': {}", trimmed, e.getMessage());
            }
        }

        if (chunkKey != null) {
            // The element is already marked as exhausted, world-life resets it when expired
            adventure.getProgressService().setBlockCooldown(worldId, chunkKey, blockKey,
                    now + cooldownSeconds * 1000L);
            data.setNextCollectAllowed(now + PLAYER_COLLECT_COOLDOWN_MS);
            log.debug("Element exhausted for {}s: worldId={}, chunk={}, block={}, status={}",
                    cooldownSeconds, worldId, chunkKey, blockKey, collectStatus);
        } else {
            // Entities have no block status, keep the cooldown on the player
            data.setNextCollectAllowed(now + Math.max(cooldownSeconds * 1000L, PLAYER_COLLECT_COOLDOWN_MS));
        }
        return true;
    }

    /**
     * Block status of an exhausted element, may be overridden per block via collectStatus.
     */
    private String resolveCollectStatus(Map<String, String> serverInfo) {
        String status = serverInfo.get("collectStatus");
        return status != null && !status.isBlank() ? status.trim() : DEFAULT_COLLECT_STATUS;
    }

    private boolean handleSyntheticReward(PlayerSession session, AdventureData data, String itemId, int quantity) {
        String docId = data.getCachedCharacterDocId();
        if (docId == null) return false;

        switch (itemId) {
            case "_gold_" -> {
                String entityId = session.getEntityId();
                PlayerId playerId = entityId != null ? PlayerId.of(entityId).orElse(null) : null;
                if (playerId == null) return false;
                var userOpt = adventure.getUserService().getByUsername(playerId.getUserId());
                if (userOpt.isEmpty()) return false;
                adventure.getUserService().changeGold(userOpt.get().getId(), quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Gold", "n:textures/currencies/gold-coin.png");
                return true;
            }
            case "_silver_" -> {
                adventure.getCharacterService().changeSilver(docId, quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Silver", "n:textures/currencies/silver-coin.png");
                return true;
            }
            case "_exp_" -> {
                adventure.getCharacterService().addSkillExperience(docId, quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Exp", "n:textures/actions/exp.png");
                return true;
            }
            case "_skill_" -> {
                adventure.getCharacterService().addSkillPoints(docId, quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Skill", "n:textures/actions/skill.png");
                return true;
            }
            default -> {
                // Spell word learning: _spell_word_<wordName> e.g. _spell_word_fire
                if (itemId.startsWith("_spell_word_")) {
                    String wordName = itemId.substring("_spell_word_".length());
                    boolean learned = adventure.getCharacterService().learnSpellWord(docId, wordName);
                    if (learned) {
                        adventure.getClientService().sendNotification(session, 3, "",
                                "Neues Zauberwort: " + wordName, "r:textures/items/enchanted_book.png");
                    } else {
                        adventure.getClientService().sendNotification(session, 3, "",
                                "Zauberwort bereits bekannt: " + wordName, null);
                    }
                    return true;
                }
                return false;
            }
        }
    }

    private void sendCollectNotification(PlayerSession session, String itemId, int quantity) {
        try {
            // Try cached items first, then fall back to DB
            WItem item = null;
            if (session.getGameplayData() instanceof AdventureData data && data.getCachedItems() != null) {
                item = data.getCachedItems().get(itemId);
            }
            if (item == null) {
                item = adventure.getItemService().findByItemId(session.getWorldId(), itemId).orElse(null);
            }
            String title = item != null && item.getPublicData() != null ? item.getPublicData().getTitle() : itemId;
            String texture = item != null && item.getPublicData() != null ? item.getPublicData().getTexture() : null;
            adventure.getClientService().sendNotification(session, 3, "", "+ " + quantity + " " + title, texture);
        } catch (Exception e) {
            log.warn("Failed to send collect notification for {}: {}", itemId, e.getMessage());
        }
    }
}
