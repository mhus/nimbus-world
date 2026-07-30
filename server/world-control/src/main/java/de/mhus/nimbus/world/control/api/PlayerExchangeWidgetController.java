package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.generated.types.ItemRef;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.sector.RUserService;
import de.mhus.nimbus.world.shared.session.SessionCommandService;
import de.mhus.nimbus.world.shared.session.SessionCommandTarget;
import de.mhus.nimbus.world.shared.world.WChest;
import de.mhus.nimbus.world.shared.world.WChestService;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WLease;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import de.mhus.nimbus.world.shared.util.ForbiddenWordFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

/**
 * REST Controller for the player exchange widget.
 * Manages P2P item/currency exchange between two players using their transfer chests.
 *
 * Each player has their own WLease(type="player-exchange") storing:
 * - selectedItems: list of itemIds the player wants from the partner's transfer chest
 * - silverOffer / goldOffer: currency the player offers
 * - message: short text message
 * - accepted: whether the player has accepted the current state
 */
@RestController
@RequestMapping("/control/player/exchange")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Exchange Widget", description = "P2P item and currency exchange")
public class PlayerExchangeWidgetController extends BaseEditorController {

    private final WLeaseService leaseService;
    private final WChestService chestService;
    private final WItemService wItemService;
    private final RCharacterService characterService;
    private final RUserService userService;
    private final SessionCommandService sessionCommandService;
    private final ForbiddenWordFilter forbiddenWordFilter;
    private final WorldRedisLockService redisLockService;

    // --- DTOs ---

    public record SelectedItem(String itemId, int amount) {}
    public record UpdateRequest(List<SelectedItem> selectedItems, int silverOffer, int goldOffer, String message) {}

    /**
     * Load exchange data: both transfer chests (enriched with item details), currency balances, offer state.
     */
    @GetMapping
    @Operation(summary = "Get exchange widget data")
    public ResponseEntity<?> getExchangeData(
            HttpServletRequest request,
            @RequestParam String progressId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var myLeaseOpt = leaseService.validate(progressId, worldId, playerName, "player-exchange");
        if (myLeaseOpt.isEmpty()) return notFound("Exchange lease not found or access denied");

        WLease myLease = myLeaseOpt.get();
        String partnerEntityId = myLease.getResourceId();
        String partnerLeaseId = (String) myLease.getLeaseData().get("partnerLeaseId");

        if (Strings.isBlank(partnerEntityId) || Strings.isBlank(partnerLeaseId)) {
            return bad("Invalid exchange lease data");
        }

        var partnerLeaseOpt = leaseService.findByLeaseId(partnerLeaseId);
        if (partnerLeaseOpt.isEmpty()) {
            return bad("Partner exchange lease not found — exchange may have been cancelled");
        }
        WLease partnerLease = partnerLeaseOpt.get();

        // Load transfer chests
        var myPlayerId = PlayerId.of(playerName).orElse(null);
        var partnerPlayerId = PlayerId.of(partnerEntityId).orElse(null);
        if (myPlayerId == null || partnerPlayerId == null) return bad("Invalid player ID");

        var parsedWorldId = WorldId.of(worldId).orElse(null);
        if (parsedWorldId == null) return bad("Invalid worldId");

        WChest myChest = chestService.getOrCreateUserTransferChest(worldId, myPlayerId);
        WChest partnerChest = chestService.getOrCreateUserTransferChest(worldId, partnerPlayerId);

        // Currency balances
        var regionId = parsedWorldId.getRegionId();
        var myCharOpt = characterService.findByRegionAndName(regionId, characterId);
        long mySilver = myCharOpt.map(RCharacter::getSilver).orElse(0L);
        var myUser = userService.getByUsername(userId);
        long myGold = myUser.map(u -> u.getGold()).orElse(0L);

        // Build enriched response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("myLeaseId", myLease.getLeaseId());
        result.put("partnerLeaseId", partnerLeaseId);
        result.put("partnerName", resolveCharacterName(partnerEntityId));

        // Enriched items (with name, texture, description, itemType)
        result.put("myTransferItems", enrichItems(parsedWorldId, myChest.getItems()));
        result.put("partnerTransferItems", enrichItems(parsedWorldId, partnerChest.getItems()));

        // My currency & offer
        result.put("mySilver", mySilver);
        result.put("myGold", myGold);
        result.put("mySilverOffer", toInt(myLease.getLeaseData().get("silverOffer")));
        result.put("myGoldOffer", toInt(myLease.getLeaseData().get("goldOffer")));
        result.put("mySelectedItems", toSelectedItemList(myLease.getLeaseData().get("selectedItems")));
        result.put("myMessage", toString(myLease.getLeaseData().get("message")));
        result.put("myAccepted", toBool(myLease.getLeaseData().get("accepted")));

        // Partner offer
        result.put("partnerSilverOffer", toInt(partnerLease.getLeaseData().get("silverOffer")));
        result.put("partnerGoldOffer", toInt(partnerLease.getLeaseData().get("goldOffer")));
        result.put("partnerSelectedItems", toSelectedItemList(partnerLease.getLeaseData().get("selectedItems")));
        result.put("partnerMessage", toString(partnerLease.getLeaseData().get("message")));
        result.put("partnerAccepted", toBool(partnerLease.getLeaseData().get("accepted")));

        return ResponseEntity.ok(result);
    }

    /**
     * Update my exchange offer: selected items from partner's chest, currency offer, message.
     * Resets both players' accepted state.
     */
    @PostMapping("/update")
    @Operation(summary = "Update exchange offer")
    public ResponseEntity<?> updateOffer(
            HttpServletRequest request,
            @RequestParam String progressId,
            @RequestBody UpdateRequest body) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var myLeaseOpt = leaseService.validate(progressId, worldId, playerName, "player-exchange");
        if (myLeaseOpt.isEmpty()) return notFound("Exchange lease not found");

        WLease myLease = myLeaseOpt.get();
        String partnerLeaseId = (String) myLease.getLeaseData().get("partnerLeaseId");
        String partnerEntityId = myLease.getResourceId();

        if (body.silverOffer() < 0 || body.goldOffer() < 0) return bad("Offers must be non-negative");

        String msg = forbiddenWordFilter.filter(body.message() != null ? body.message() : "");
        if (msg.length() > 140) return bad("Message must be 140 characters or less");

        // Update my offer
        leaseService.setLeaseDataValues(myLease.getLeaseId(), Map.of(
                "selectedItems", body.selectedItems() != null ? body.selectedItems() : List.of(),
                "silverOffer", body.silverOffer(),
                "goldOffer", body.goldOffer(),
                "message", msg,
                "accepted", false
        ));

        // Reset partner's accepted
        if (partnerLeaseId != null) {
            leaseService.setLeaseDataValue(partnerLeaseId, "accepted", false);
        }

        // Notify partner
        sessionCommandService.sendNotification(
                SessionCommandTarget.PLAYER, partnerEntityId,
                1, characterId, "Exchange offer updated"
        );

        log.info("Player {} updated exchange: {} items selected, silver={}, gold={}",
                playerName, body.selectedItems() != null ? body.selectedItems().size() : 0,
                body.silverOffer(), body.goldOffer());
        return ResponseEntity.ok(Map.of("updated", true));
    }

    /**
     * Accept the current exchange state. Saves own offer values and sets accepted=true
     * without resetting partner's accepted. If both accepted, execute the transfer.
     */
    @PostMapping("/accept")
    @Operation(summary = "Accept exchange")
    public ResponseEntity<?> acceptExchange(
            HttpServletRequest request,
            @RequestParam String progressId,
            @RequestBody(required = false) UpdateRequest body) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var myLeaseOpt = leaseService.validate(progressId, worldId, playerName, "player-exchange");
        if (myLeaseOpt.isEmpty()) return notFound("Exchange lease not found");

        WLease myLease = myLeaseOpt.get();
        String partnerLeaseId = (String) myLease.getLeaseData().get("partnerLeaseId");
        String partnerEntityId = myLease.getResourceId();

        // Save own offer values + set accepted=true (do NOT reset partner's accepted)
        Map<String, Object> updates = new HashMap<>();
        if (body != null) {
            String msg = forbiddenWordFilter.filter(body.message() != null ? body.message() : "");
            if (msg.length() > 140) return bad("Message must be 140 characters or less");
            updates.put("selectedItems", body.selectedItems() != null ? body.selectedItems() : List.of());
            updates.put("silverOffer", Math.max(0, body.silverOffer()));
            updates.put("goldOffer", Math.max(0, body.goldOffer()));
            updates.put("message", msg);
        }
        updates.put("accepted", true);
        leaseService.setLeaseDataValues(myLease.getLeaseId(), updates);

        var partnerLeaseOpt = leaseService.findByLeaseId(partnerLeaseId);
        if (partnerLeaseOpt.isEmpty()) {
            return bad("Partner exchange lease not found — exchange may have been cancelled");
        }

        boolean partnerAccepted = toBool(partnerLeaseOpt.get().getLeaseData().get("accepted"));

        if (!partnerAccepted) {
            sessionCommandService.sendNotification(
                    SessionCommandTarget.PLAYER, partnerEntityId,
                    1, characterId, "Exchange accepted — waiting for you"
            );
            return ResponseEntity.ok(Map.of("accepted", true, "completed", false));
        }

        // Both accepted → claim exclusive completion of this exchange pair.
        // A per-pair NX lock guarantees that when both players accept nearly
        // simultaneously only ONE request runs executeTransfer, preventing a
        // double currency/item transfer (double-spend).
        String pairLockKey = "exchange:" + pairLockKey(myLease.getLeaseId(), partnerLeaseId);
        String lockToken = redisLockService.acquireGenericLock(pairLockKey, Duration.ofSeconds(30));
        if (lockToken == null) {
            // The partner's concurrent accept is already completing this exchange.
            return ResponseEntity.ok(Map.of("accepted", true, "completed", false));
        }
        try {
            // Re-read both leases inside the lock: if either is gone the exchange
            // was already completed (leases are released on completion).
            var myFresh = leaseService.findByLeaseId(myLease.getLeaseId());
            var partnerFresh = leaseService.findByLeaseId(partnerLeaseId);
            if (myFresh.isEmpty() || partnerFresh.isEmpty()) {
                return ResponseEntity.ok(Map.of("accepted", true, "completed", false));
            }
            if (!toBool(partnerFresh.get().getLeaseData().get("accepted"))) {
                return ResponseEntity.ok(Map.of("accepted", true, "completed", false));
            }

            var result = executeTransfer(worldId, myFresh.get(), partnerFresh.get());
            if (result != null) return result;

            leaseService.release(myLease.getLeaseId());
            leaseService.release(partnerLeaseId);

            sessionCommandService.sendNotification(
                    SessionCommandTarget.PLAYER, partnerEntityId,
                    1, characterId, "Exchange complete!"
            );
            sessionCommandService.sendNotification(
                    SessionCommandTarget.PLAYER, playerName,
                    0, "", "Exchange complete!"
            );

            log.info("Exchange completed between {} and {}", playerName, partnerEntityId);
            return ResponseEntity.ok(Map.of("accepted", true, "completed", true));
        } finally {
            redisLockService.releaseGenericLock(pairLockKey, lockToken);
        }
    }

    /**
     * Cancel the exchange. Deletes both leases.
     */
    @PostMapping("/cancel")
    @Operation(summary = "Cancel exchange")
    public ResponseEntity<?> cancelExchange(
            HttpServletRequest request,
            @RequestParam String progressId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var myLeaseOpt = leaseService.validate(progressId, worldId, playerName, "player-exchange");
        if (myLeaseOpt.isEmpty()) return notFound("Exchange lease not found");

        WLease myLease = myLeaseOpt.get();
        String partnerLeaseId = (String) myLease.getLeaseData().get("partnerLeaseId");
        String partnerEntityId = myLease.getResourceId();

        leaseService.release(myLease.getLeaseId());
        if (partnerLeaseId != null) leaseService.release(partnerLeaseId);

        sessionCommandService.sendNotification(
                SessionCommandTarget.PLAYER, partnerEntityId,
                1, characterId, "Exchange cancelled"
        );

        log.info("Player {} cancelled exchange with {}", playerName, partnerEntityId);
        return ResponseEntity.ok(Map.of("cancelled", true));
    }

    // --- Transfer execution ---

    /**
     * Execute the atomic item and currency transfer.
     * Each player's selectedItems are items they WANT from the partner's transfer chest.
     * Each player's silverOffer/goldOffer is currency they GIVE.
     */
    private ResponseEntity<?> executeTransfer(String worldId, WLease leaseA, WLease leaseB) {
        String playerA = leaseA.getPlayerId();
        String playerB = leaseB.getPlayerId();

        var playerIdA = PlayerId.of(playerA).orElse(null);
        var playerIdB = PlayerId.of(playerB).orElse(null);
        if (playerIdA == null || playerIdB == null) return bad("Invalid player IDs");

        // A wants these items (with amounts) from B's chest
        List<SelectedItem> wantedByA = toSelectedItemList(leaseA.getLeaseData().get("selectedItems"));
        // B wants these items (with amounts) from A's chest
        List<SelectedItem> wantedByB = toSelectedItemList(leaseB.getLeaseData().get("selectedItems"));

        int silverOfferA = toInt(leaseA.getLeaseData().get("silverOffer"));
        int silverOfferB = toInt(leaseB.getLeaseData().get("silverOffer"));
        int goldOfferA = toInt(leaseA.getLeaseData().get("goldOffer"));
        int goldOfferB = toInt(leaseB.getLeaseData().get("goldOffer"));

        WChest chestA = chestService.getOrCreateUserTransferChest(worldId, playerIdA);
        WChest chestB = chestService.getOrCreateUserTransferChest(worldId, playerIdB);

        // Validate: requested items must be in chest with sufficient amount
        Map<String, ItemRef> chestAItems = indexByItemId(chestA.getItems());
        Map<String, ItemRef> chestBItems = indexByItemId(chestB.getItems());

        for (SelectedItem sel : wantedByA) {
            ItemRef ref = chestBItems.get(sel.itemId());
            if (ref == null) return bad("Item " + sel.itemId() + " not in partner's chest");
            if (ref.getAmount() < sel.amount()) return bad("Not enough " + sel.itemId() + " in partner's chest");
        }
        for (SelectedItem sel : wantedByB) {
            ItemRef ref = chestAItems.get(sel.itemId());
            if (ref == null) return bad("Item " + sel.itemId() + " not in your chest");
            if (ref.getAmount() < sel.amount()) return bad("Not enough " + sel.itemId() + " in your chest");
        }

        var regionId = WorldId.unchecked(worldId).getRegionId();
        var charA = characterService.findByRegionAndName(regionId, playerIdA.getCharacterId());
        var charB = characterService.findByRegionAndName(regionId, playerIdB.getCharacterId());
        if (charA.isEmpty() || charB.isEmpty()) return bad("Character not found");

        // --- Phase 1: Take currency ---
        if (silverOfferA > 0) {
            if (!characterService.changeSilver(charA.get().getId(), -silverOfferA)) {
                return bad("Insufficient silver");
            }
        }
        if (silverOfferB > 0) {
            if (!characterService.changeSilver(charB.get().getId(), -silverOfferB)) {
                if (silverOfferA > 0) characterService.changeSilver(charA.get().getId(), silverOfferA);
                return bad("Partner has insufficient silver");
            }
        }

        var userA = userService.getByUsername(playerIdA.getUserId());
        var userB = userService.getByUsername(playerIdB.getUserId());
        if (goldOfferA > 0) {
            if (userA.isEmpty() || !userService.changeGold(userA.get().getId(), -goldOfferA)) {
                if (silverOfferA > 0) characterService.changeSilver(charA.get().getId(), silverOfferA);
                if (silverOfferB > 0) characterService.changeSilver(charB.get().getId(), silverOfferB);
                return bad("Insufficient gold");
            }
        }
        if (goldOfferB > 0) {
            if (userB.isEmpty() || !userService.changeGold(userB.get().getId(), -goldOfferB)) {
                if (goldOfferA > 0 && userA.isPresent()) userService.changeGold(userA.get().getId(), goldOfferA);
                if (silverOfferA > 0) characterService.changeSilver(charA.get().getId(), silverOfferA);
                if (silverOfferB > 0) characterService.changeSilver(charB.get().getId(), silverOfferB);
                return bad("Partner has insufficient gold");
            }
        }

        // --- Phase 2: Move items ---
        // Items B wanted from A's chest → reduce in A's chest, add to B's backpack
        for (SelectedItem sel : wantedByB) {
            ItemRef ref = chestAItems.get(sel.itemId());
            if (ref.getAmount() <= sel.amount()) {
                // Take all — remove entire item from chest
                chestService.removeItemAtomic(chestA.getId(), sel.itemId());
            } else {
                // Take partial — reduce amount in chest
                chestService.updateItemAmount(chestA.getId(), sel.itemId(), ref.getAmount() - sel.amount());
            }
            characterService.addBackpackItem(charB.get().getId(), sel.itemId(), sel.amount());
        }
        // Items A wanted from B's chest → reduce in B's chest, add to A's backpack
        for (SelectedItem sel : wantedByA) {
            ItemRef ref = chestBItems.get(sel.itemId());
            if (ref.getAmount() <= sel.amount()) {
                chestService.removeItemAtomic(chestB.getId(), sel.itemId());
            } else {
                chestService.updateItemAmount(chestB.getId(), sel.itemId(), ref.getAmount() - sel.amount());
            }
            characterService.addBackpackItem(charA.get().getId(), sel.itemId(), sel.amount());
        }

        // --- Phase 3: Distribute currency ---
        if (silverOfferA > 0) characterService.changeSilver(charB.get().getId(), silverOfferA);
        if (silverOfferB > 0) characterService.changeSilver(charA.get().getId(), silverOfferB);
        if (goldOfferA > 0 && userB.isPresent()) userService.changeGold(userB.get().getId(), goldOfferA);
        if (goldOfferB > 0 && userA.isPresent()) userService.changeGold(userA.get().getId(), goldOfferB);

        log.info("Transfer executed: A wants {} items from B, B wants {} items from A, silver A:{}/B:{}, gold A:{}/B:{}",
                wantedByA.size(), wantedByB.size(), silverOfferA, silverOfferB, goldOfferA, goldOfferB);
        return null;
    }

    // --- Helper methods ---

    private List<Map<String, Object>> enrichItems(WorldId parsedWorldId, List<ItemRef> items) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        if (items == null) return enriched;

        for (ItemRef ref : items) {
            String texture = ref.getTexture();
            String name = ref.getName();
            String itemType = null;
            String description = null;

            Optional<WItem> itemOpt = wItemService.findByItemId(parsedWorldId, ref.getItemId());
            if (itemOpt.isPresent()) {
                Item publicData = itemOpt.get().getPublicData();
                if (publicData != null) {
                    itemType = publicData.getItemType();
                    if (texture == null && !Strings.isBlank(publicData.getTexture())) {
                        texture = publicData.getTexture();
                    }
                    if (Strings.isBlank(name) && !Strings.isBlank(publicData.getName())) {
                        name = publicData.getName();
                    }
                    if (publicData.getDescription() != null) {
                        description = publicData.getDescription();
                    }
                }
            }

            if (Strings.isBlank(name)) name = ref.getItemId();

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("itemId", ref.getItemId());
            info.put("name", name);
            info.put("itemType", itemType);
            info.put("texture", texture);
            info.put("description", description);
            info.put("amount", ref.getAmount());
            enriched.add(info);
        }
        return enriched;
    }

    private Map<String, ItemRef> indexByItemId(List<ItemRef> items) {
        Map<String, ItemRef> map = new LinkedHashMap<>();
        if (items != null) {
            for (ItemRef ref : items) {
                map.put(ref.getItemId(), ref);
            }
        }
        return map;
    }

    private String resolveCharacterName(String entityId) {
        if (entityId == null) return null;
        int colonIdx = entityId.indexOf(':');
        if (colonIdx >= 0) return entityId.substring(colonIdx + 1);
        return entityId.startsWith("@") ? entityId.substring(1) : entityId;
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return 0; }
    }

    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return "true".equals(value.toString());
    }

    /**
     * Builds a stable lock key for an exchange pair independent of which of the
     * two players calls accept (the ids are sorted), so both concurrent accepts
     * contend for the same lock.
     */
    private static String pairLockKey(String leaseA, String leaseB) {
        return leaseA.compareTo(leaseB) <= 0 ? leaseA + ":" + leaseB : leaseB + ":" + leaseA;
    }

    private String toString(Object value) {
        if (value == null) return "";
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<SelectedItem> toSelectedItemList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<SelectedItem> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof SelectedItem si) {
                    result.add(si);
                } else if (item instanceof Map<?, ?> map) {
                    String itemId = map.get("itemId") != null ? map.get("itemId").toString() : null;
                    int amount = map.get("amount") instanceof Number n ? n.intValue() : 1;
                    if (itemId != null && amount > 0) {
                        result.add(new SelectedItem(itemId, amount));
                    }
                }
            }
            return result;
        }
        return List.of();
    }
}
