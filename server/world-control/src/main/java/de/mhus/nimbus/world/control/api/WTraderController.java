package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.shared.user.WorldRoles;
import de.mhus.nimbus.world.shared.access.RequireWorldRole;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.world.TraderType;
import de.mhus.nimbus.world.shared.world.WTrader;
import de.mhus.nimbus.world.shared.world.WTraderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/world/{worldId}/traders")
@RequiredArgsConstructor
@RequireWorldRole(WorldRoles.EDITOR)
@Slf4j
public class WTraderController extends BaseEditorController {

    private final WTraderService traderService;

    public record TraderRequest(
            String entityId,
            String traderType,
            List<String> categories,
            Double personalityModifier,
            Long silverAmount,
            String chestId,
            String poolChestId,
            List<String> questItems,
            Integer maxDisplayItems,
            Double goldExchangeRate,
            List<String> trainableSkills,
            Integer maxSkillPoints,
            Double costPerSkillPoint,
            List<String> repairTypes,
            Double repairCostPerPoint,
            Integer poolSyncIntervalSeconds
    ) {}

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String worldId) {
        List<WTrader> traders = traderService.findByWorldId(worldId);
        return ResponseEntity.ok(traders);
    }

    @GetMapping("/{entityId}")
    public ResponseEntity<?> get(
            @PathVariable String worldId,
            @PathVariable String entityId) {
        return traderService.findByWorldIdAndEntityId(worldId, entityId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound("Trader not found: " + entityId));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String worldId,
            @RequestBody TraderRequest request) {

        if (Strings.isBlank(request.entityId())) return bad("entityId is required");
        if (Strings.isBlank(request.chestId())) return bad("chestId is required");

        var existing = traderService.findByWorldIdAndEntityId(worldId, request.entityId());
        if (existing.isPresent()) return bad("Trader already exists for entity: " + request.entityId());

        WTrader trader = buildTrader(worldId, request, null);
        trader = traderService.save(trader);
        log.info("Trader created: worldId={}, entityId={}", worldId, request.entityId());
        return ResponseEntity.ok(trader);
    }

    @PutMapping("/{entityId}")
    public ResponseEntity<?> update(
            @PathVariable String worldId,
            @PathVariable String entityId,
            @RequestBody TraderRequest request) {

        var existing = traderService.findByWorldIdAndEntityId(worldId, entityId);
        if (existing.isEmpty()) return notFound("Trader not found: " + entityId);

        WTrader trader = buildTrader(worldId, request, existing.get());
        trader = traderService.save(trader);
        log.info("Trader updated: worldId={}, entityId={}", worldId, entityId);
        return ResponseEntity.ok(trader);
    }

    @DeleteMapping("/{entityId}")
    public ResponseEntity<?> delete(
            @PathVariable String worldId,
            @PathVariable String entityId) {
        if (!traderService.delete(worldId, entityId)) {
            return notFound("Trader not found: " + entityId);
        }
        log.info("Trader deleted: worldId={}, entityId={}", worldId, entityId);
        return ResponseEntity.ok().build();
    }

    private WTrader buildTrader(String worldId, TraderRequest req, WTrader existing) {
        WTrader trader = existing != null ? existing : WTrader.builder()
                .worldId(worldId)
                .entityId(req.entityId())
                .build();

        if (!Strings.isBlank(req.traderType())) {
            trader.setTraderType(TraderType.valueOf(req.traderType().toUpperCase().trim()));
        }
        if (req.categories() != null) trader.setCategories(req.categories());
        if (req.personalityModifier() != null) trader.setPersonalityModifier(req.personalityModifier());
        if (req.silverAmount() != null) trader.setSilverAmount(req.silverAmount());
        if (req.chestId() != null) trader.setChestId(req.chestId());
        if (req.poolChestId() != null) trader.setPoolChestId(req.poolChestId());
        if (req.questItems() != null) trader.setQuestItems(req.questItems());
        if (req.maxDisplayItems() != null) trader.setMaxDisplayItems(req.maxDisplayItems());
        if (req.goldExchangeRate() != null) trader.setGoldExchangeRate(req.goldExchangeRate());
        if (req.trainableSkills() != null) trader.setTrainableSkills(req.trainableSkills());
        if (req.maxSkillPoints() != null) trader.setMaxSkillPoints(req.maxSkillPoints());
        if (req.costPerSkillPoint() != null) trader.setCostPerSkillPoint(req.costPerSkillPoint());
        if (req.repairTypes() != null) trader.setRepairTypes(req.repairTypes());
        if (req.repairCostPerPoint() != null) trader.setRepairCostPerPoint(req.repairCostPerPoint());
        if (req.poolSyncIntervalSeconds() != null) trader.setPoolSyncIntervalSeconds(req.poolSyncIntervalSeconds());

        return trader;
    }
}
