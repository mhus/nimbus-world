package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.access.AccessFilterBase;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.session.SessionCommandService;
import de.mhus.nimbus.world.shared.session.SessionCommandTarget;
import de.mhus.nimbus.world.shared.team.WTeam;
import de.mhus.nimbus.world.shared.team.WTeamService;
import de.mhus.nimbus.world.shared.world.WLease;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for the player-interact widget.
 * Allows players to interact with other players: send emojis, team invites,
 * trade offers, and block/unblock.
 */
@RestController
@RequestMapping("/control/player/interact")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Interact Widget", description = "Player-to-player interaction widget")
public class PlayerInteractWidgetController extends BaseEditorController {

    private static final long EMOJI_COOLDOWN_MS = 10_000; // 10 seconds

    private final WLeaseService leaseService;
    private final RCharacterService characterService;
    private final WTeamService teamService;
    private final SessionCommandService sessionCommandService;

    // --- DTOs ---

    public record InteractWidgetData(
            String targetEntityId,
            String targetName,
            String targetPortrait,
            boolean isBlocked,
            boolean hasTeam,
            String myTeamId,
            List<TeamInviteInfo> teamInvitations,
            List<TradeOfferInfo> tradeOffers
    ) {}

    public record TeamInviteInfo(String teamId, String title) {}
    public record TradeOfferInfo(String leaseId, String fromName) {}

    public record EmojiRequest(String emoji) {}
    public record TradeOfferRequest() {}

    // In-memory cooldown tracking (per-pod, sufficient for spam prevention)
    private final Map<String, Long> emojiCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Load widget data: target player info, team invitations, trade offers, block status.
     */
    @GetMapping
    @Operation(summary = "Get player interact widget data")
    public ResponseEntity<?> getWidgetData(
            HttpServletRequest request,
            @RequestParam String progressId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        // Validate lease
        var leaseOpt = leaseService.validate(progressId, worldId, playerName, "player-interact");
        if (leaseOpt.isEmpty()) return notFound("Lease not found or access denied");

        WLease lease = leaseOpt.get();
        String targetEntityId = (String) lease.getLeaseData().get("targetEntityId");
        if (Strings.isBlank(targetEntityId)) return bad("No target player in lease");

        // Resolve target player info
        String targetName = resolveCharacterName(targetEntityId);
        String targetPortrait = null; // Portrait comes from entity rendering, not stored on RCharacter

        // Find my character to check block status
        var regionId = WorldId.unchecked(worldId).getRegionId();
        Optional<RCharacter> myCharOpt = characterService.findByRegionAndName(regionId, characterId);
        if (myCharOpt.isEmpty()) return bad("Character not found");

        RCharacter myChar = myCharOpt.get();
        boolean isBlocked = myChar.isPlayerBlocked(targetEntityId);

        // Team info
        String mainInstanceId = WorldId.unchecked(worldId).getFullMainInstance().getId();
        Optional<WTeam> myTeamOpt = teamService.findActiveTeamForPlayer(worldId, mainInstanceId, playerName);
        boolean hasTeam = myTeamOpt.isPresent();
        String myTeamId = myTeamOpt.map(WTeam::getTeamId).orElse(null);

        // Check if target's team has invited me
        List<TeamInviteInfo> teamInvitations = teamService.findAllTeamsOfPlayer(playerName).stream()
                .filter(t -> t.getInvitation() != null && t.getInvitation().contains(playerName))
                .filter(t -> !t.getMembers().contains(playerName))
                // Only show invitations from teams that have the target as member
                .filter(t -> t.getMembers().contains(targetEntityId))
                .map(t -> new TeamInviteInfo(t.getTeamId(), t.getTitle()))
                .toList();

        // Check for trade offers from target player
        List<WLease> tradeLeases = leaseService.findByWorldIdAndPlayerIdAndType(worldId, playerName, "trade-offer");
        List<TradeOfferInfo> tradeOffers = tradeLeases.stream()
                .filter(l -> targetEntityId.equals(l.getResourceId()))
                .map(l -> new TradeOfferInfo(l.getLeaseId(), resolveCharacterName(targetEntityId)))
                .toList();

        return ResponseEntity.ok(new InteractWidgetData(
                targetEntityId, targetName, targetPortrait,
                isBlocked, hasTeam, myTeamId,
                teamInvitations, tradeOffers
        ));
    }

    /**
     * Send an emoji to the target player.
     */
    @PostMapping("/emoji")
    @Operation(summary = "Send emoji to target player")
    public ResponseEntity<?> sendEmoji(
            HttpServletRequest request,
            @RequestParam String progressId,
            @RequestBody EmojiRequest body) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        if (Strings.isBlank(body.emoji())) return bad("Emoji is required");

        // Validate lease
        var leaseOpt = leaseService.validate(progressId, worldId, playerName, "player-interact");
        if (leaseOpt.isEmpty()) return notFound("Lease not found");

        String targetEntityId = (String) leaseOpt.get().getLeaseData().get("targetEntityId");

        // Check block — target may have blocked me (neutral error)
        if (isBlockedByTarget(targetEntityId, playerName, worldId)) {
            return ResponseEntity.ok(Map.of("sent", true)); // neutral response
        }

        // Cooldown check
        String cooldownKey = playerName + ">" + targetEntityId;
        Long lastSent = emojiCooldowns.get(cooldownKey);
        if (lastSent != null && System.currentTimeMillis() - lastSent < EMOJI_COOLDOWN_MS) {
            return bad("Please wait before sending another emoji");
        }
        emojiCooldowns.put(cooldownKey, System.currentTimeMillis());

        // Send notification to target player
        sessionCommandService.sendNotification(
                SessionCommandTarget.PLAYER, targetEntityId,
                1, characterId, body.emoji()
        );

        log.debug("Player {} sent emoji '{}' to {}", playerName, body.emoji(), targetEntityId);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    /**
     * Block a player.
     */
    @PostMapping("/block")
    @Operation(summary = "Block a player")
    public ResponseEntity<?> blockPlayer(
            HttpServletRequest request,
            @RequestParam String progressId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var leaseOpt = leaseService.validate(progressId, worldId, playerName, "player-interact");
        if (leaseOpt.isEmpty()) return notFound("Lease not found");

        String targetEntityId = (String) leaseOpt.get().getLeaseData().get("targetEntityId");

        var regionId = WorldId.unchecked(worldId).getRegionId();
        Optional<RCharacter> myCharOpt = characterService.findByRegionAndName(regionId, characterId);
        if (myCharOpt.isEmpty()) return bad("Character not found");

        characterService.blockPlayer(myCharOpt.get().getId(), targetEntityId);
        log.info("Player {} blocked {}", playerName, targetEntityId);
        return ResponseEntity.ok(Map.of("blocked", true));
    }

    /**
     * Unblock a player.
     */
    @PostMapping("/unblock")
    @Operation(summary = "Unblock a player")
    public ResponseEntity<?> unblockPlayer(
            HttpServletRequest request,
            @RequestParam String progressId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var leaseOpt = leaseService.validate(progressId, worldId, playerName, "player-interact");
        if (leaseOpt.isEmpty()) return notFound("Lease not found");

        String targetEntityId = (String) leaseOpt.get().getLeaseData().get("targetEntityId");

        var regionId = WorldId.unchecked(worldId).getRegionId();
        Optional<RCharacter> myCharOpt = characterService.findByRegionAndName(regionId, characterId);
        if (myCharOpt.isEmpty()) return bad("Character not found");

        characterService.unblockPlayer(myCharOpt.get().getId(), targetEntityId);
        log.info("Player {} unblocked {}", playerName, targetEntityId);
        return ResponseEntity.ok(Map.of("blocked", false));
    }

    /**
     * Offer a trade to the target player.
     */
    @PostMapping("/offer-trade")
    @Operation(summary = "Offer trade to target player")
    public ResponseEntity<?> offerTrade(
            HttpServletRequest request,
            @RequestParam String progressId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var leaseOpt = leaseService.validate(progressId, worldId, playerName, "player-interact");
        if (leaseOpt.isEmpty()) return notFound("Lease not found");

        String targetEntityId = (String) leaseOpt.get().getLeaseData().get("targetEntityId");

        // Check block
        if (isBlockedByTarget(targetEntityId, playerName, worldId)) {
            return ResponseEntity.ok(Map.of("offered", true)); // neutral response
        }

        // Create trade offer lease for target player
        Map<String, Object> leaseData = new HashMap<>();
        leaseData.put("fromName", characterId);
        leaseData.put("fromEntityId", playerName);

        leaseService.acquire(
                worldId,
                targetEntityId, // target is the playerId on the lease
                "trade-offer",
                playerName,     // resourceId = who sent the offer
                null,
                leaseData
        );

        // Notify target player
        sessionCommandService.sendNotification(
                SessionCommandTarget.PLAYER, targetEntityId,
                1, characterId, "Trade offer received"
        );

        log.info("Player {} offered trade to {}", playerName, targetEntityId);
        return ResponseEntity.ok(Map.of("offered", true));
    }

    /**
     * Accept a trade offer (placeholder — actual P2P trade widget is a separate feature).
     */
    @PostMapping("/accept-trade")
    @Operation(summary = "Accept a trade offer")
    public ResponseEntity<?> acceptTrade(
            HttpServletRequest request,
            @RequestParam String progressId,
            @RequestParam String offerId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        // Validate the trade-offer lease
        var offerOpt = leaseService.validate(offerId, worldId, playerName, "trade-offer");
        if (offerOpt.isEmpty()) return notFound("Trade offer not found or expired");

        String fromEntityId = (String) offerOpt.get().getLeaseData().get("fromEntityId");

        // Release the offer lease
        leaseService.release(offerId);

        // Notify sender that trade was accepted
        if (fromEntityId != null) {
            sessionCommandService.sendNotification(
                    SessionCommandTarget.PLAYER, fromEntityId,
                    1, characterId, "Trade offer accepted"
            );
        }

        // TODO: Open P2P trade widget for both players (separate feature)
        log.info("Player {} accepted trade from {}", playerName, fromEntityId);
        return ResponseEntity.ok(Map.of("accepted", true));
    }

    /**
     * Decline a trade offer.
     */
    @PostMapping("/decline-trade")
    @Operation(summary = "Decline a trade offer")
    public ResponseEntity<?> declineTrade(
            HttpServletRequest request,
            @RequestParam String progressId,
            @RequestParam String offerId) {

        String userId = (String) request.getAttribute(AccessFilterBase.ATTR_USER_ID);
        String worldId = (String) request.getAttribute(AccessFilterBase.ATTR_WORLD_ID);
        String characterId = (String) request.getAttribute(AccessFilterBase.ATTR_CHARACTER_ID);
        if (userId == null || worldId == null || characterId == null) return bad("Not authenticated");

        String playerName = "@" + userId + ":" + characterId;

        var offerOpt = leaseService.validate(offerId, worldId, playerName, "trade-offer");
        if (offerOpt.isEmpty()) return notFound("Trade offer not found or expired");

        String fromEntityId = (String) offerOpt.get().getLeaseData().get("fromEntityId");

        leaseService.release(offerId);

        // Notify sender
        if (fromEntityId != null) {
            sessionCommandService.sendNotification(
                    SessionCommandTarget.PLAYER, fromEntityId,
                    1, characterId, "Trade offer declined"
            );
        }

        log.info("Player {} declined trade from {}", playerName, fromEntityId);
        return ResponseEntity.ok(Map.of("declined", true));
    }

    // --- Helper methods ---

    /**
     * Extract character name from entityId format "@userId:characterName".
     */
    private String resolveCharacterName(String entityId) {
        if (entityId == null) return null;
        int colonIdx = entityId.indexOf(':');
        if (colonIdx >= 0) return entityId.substring(colonIdx + 1);
        return entityId.startsWith("@") ? entityId.substring(1) : entityId;
    }

    /**
     * Check if the target player has blocked the requester.
     * Returns true if blocked (caller should return neutral response).
     */
    private boolean isBlockedByTarget(String targetEntityId, String requesterEntityId, String worldId) {
        String targetCharName = resolveCharacterName(targetEntityId);
        if (targetCharName == null) return false;

        var regionId = WorldId.unchecked(worldId).getRegionId();
        Optional<RCharacter> targetCharOpt = characterService.findByRegionAndName(regionId, targetCharName);
        if (targetCharOpt.isEmpty()) return false;

        return targetCharOpt.get().isPlayerBlocked(requesterEntityId);
    }
}
