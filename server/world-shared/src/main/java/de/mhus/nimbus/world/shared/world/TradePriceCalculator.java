package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.world.shared.region.RCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Calculates trade prices based on the formula:
 * <pre>
 * price = basePrice * (1 + personalityModifier + reputationModifier + individualModifier) * balanceModifier
 * </pre>
 *
 * <ul>
 *   <li>personalityModifier: fixed on the WTrader</li>
 *   <li>reputationModifier: derived from RCharacter.reputation for the trader's faction</li>
 *   <li>individualModifier: per-character modifier (e.g., quest reward, special relationship)</li>
 *   <li>balanceModifier: global balance factor from WAnything (collection="trade-config")</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradePriceCalculator {

    private static final String TRADE_CONFIG_COLLECTION = "trade-config";
    private static final String BALANCE_MODIFIER_KEY = "balance-modifier";
    private static final double DEFAULT_BALANCE_MODIFIER = 1.0;
    private static final double DEFAULT_SELL_FACTOR = 0.5;
    private static final String REPUTATION_KEY_PREFIX = "trade_";

    private static final String INDIVIDUAL_MODIFIER_TYPE = "trade-individual";

    private final WAnythingService anythingService;
    private final WProgressService progressService;

    /**
     * Calculate the buy price (player buys from trader).
     * Trader markup is applied positively.
     */
    public long calculateBuyPrice(WItem item, WTrader trader, RCharacter character, String worldId) {
        double base = getBasePrice(item);
        if (base <= 0) return 0;

        double modifier = 1.0
                + trader.getPersonalityModifier()
                + getReputationModifier(trader, character)
                + getIndividualModifier(trader, character, worldId);

        double balance = getBalanceModifier(worldId);
        double price = base * modifier * balance;

        return Math.max(1, Math.round(price));
    }

    /**
     * Calculate the sell price (player sells to trader).
     * Sell price is a fraction of the buy price (default 50%).
     */
    public long calculateSellPrice(WItem item, WTrader trader, RCharacter character, String worldId) {
        double base = getBasePrice(item);
        if (base <= 0) return 0;

        double modifier = 1.0
                - trader.getPersonalityModifier()
                + getReputationModifier(trader, character)
                + getIndividualModifier(trader, character, worldId);

        double balance = getBalanceModifier(worldId);
        double price = base * modifier * balance * DEFAULT_SELL_FACTOR;

        return Math.max(1, Math.round(price));
    }

    /**
     * Calculate silver received for gold exchange.
     */
    public long calculateGoldToSilver(long goldAmount, WTrader trader) {
        return Math.round(goldAmount * trader.getGoldExchangeRate());
    }

    // ===== Internal helpers =====

    private double getBasePrice(WItem item) {
        if (item.getBasePrice() != null) {
            return item.getBasePrice();
        }
        // Fallback: calculate from components if available
        double material = item.getMaterialPrice() != null ? item.getMaterialPrice() : 0;
        double crafting = item.getCraftingCost() != null ? item.getCraftingCost() : 0;
        double usage = item.getUsageBonus() != null ? item.getUsageBonus() : 0;
        double rarity = item.getRarityBonus() != null ? item.getRarityBonus() : 0;
        double calculated = material + crafting + usage + rarity;
        return calculated > 0 ? calculated : 1;
    }

    /**
     * Derive reputation modifier from the character's reputation map.
     * Looks for a faction key matching the trader (e.g., "trade_smiths").
     * Reputation is scaled: each point = 0.01 modifier change.
     */
    private double getReputationModifier(WTrader trader, RCharacter character) {
        if (character == null || character.getReputation() == null) return 0;

        // Try trader entity ID as faction key
        String factionKey = REPUTATION_KEY_PREFIX + trader.getEntityId();
        Integer rep = character.getReputation().get(factionKey);
        if (rep != null) {
            return rep * -0.01; // Positive reputation = cheaper prices (negative modifier)
        }

        return 0;
    }

    /**
     * Get individual modifier for a specific character-trader relationship.
     * Stored in WProgress (type="trade-individual", playerId=userId, quest=traderEntityId).
     * progressData contains {"modifier": double}.
     * Can be set by dialog effects, quest rewards, etc.
     */
    private double getIndividualModifier(WTrader trader, RCharacter character, String worldId) {
        if (character == null || character.getName() == null) return 0;
        try {
            var progressOpt = progressService.findByWorldIdAndPlayerIdAndTypeAndQuest(
                    worldId, character.getName(), INDIVIDUAL_MODIFIER_TYPE, trader.getEntityId());
            if (progressOpt.isPresent() && progressOpt.get().getProgressData() != null) {
                Object mod = progressOpt.get().getProgressData().get("modifier");
                if (mod instanceof Number number) {
                    return number.doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not load individual modifier for trader={}: {}", trader.getEntityId(), e.getMessage());
        }
        return 0;
    }

    /**
     * Load the global balance modifier from WAnything.
     * Falls back to 1.0 if not configured.
     */
    private double getBalanceModifier(String worldId) {
        try {
            Optional<WAnything> config = anythingService.findByWorldIdAndCollectionAndName(
                    worldId, TRADE_CONFIG_COLLECTION, BALANCE_MODIFIER_KEY);
            if (config.isPresent() && config.get().getData() != null) {
                Object data = config.get().getData();
                if (data instanceof Number number) {
                    return number.doubleValue();
                }
                if (data instanceof Map<?, ?> map) {
                    Object value = map.get("value");
                    if (value instanceof Number number) {
                        return number.doubleValue();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load balance modifier for worldId={}: {}", worldId, e.getMessage());
        }
        return DEFAULT_BALANCE_MODIFIER;
    }
}
