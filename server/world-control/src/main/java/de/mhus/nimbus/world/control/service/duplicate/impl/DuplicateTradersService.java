package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WTrader;
import de.mhus.nimbus.world.shared.world.WTraderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateTradersService implements DuplicateToWorld {

    private final WTraderRepository traderRepository;

    @Override
    public String name() {
        return "traders";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating traders from world {} to {}", sourceWorldId, targetWorldId);

        List<WTrader> sourceTraders = traderRepository.findByWorldId(sourceWorldId);
        log.info("Found {} traders in source world {}", sourceTraders.size(), sourceWorldId);

        int duplicatedCount = 0;

        for (WTrader source : sourceTraders) {
            WTrader target = WTrader.builder()
                    .worldId(targetWorldId)
                    .entityId(source.getEntityId())
                    .traderType(source.getTraderType())
                    .categories(source.getCategories() != null ? new ArrayList<>(source.getCategories()) : new ArrayList<>())
                    .personalityModifier(source.getPersonalityModifier())
                    .silverAmount(source.getSilverAmount())
                    .chestId(source.getChestId())
                    .poolChestId(source.getPoolChestId())
                    .questItems(source.getQuestItems() != null ? new ArrayList<>(source.getQuestItems()) : new ArrayList<>())
                    .maxDisplayItems(source.getMaxDisplayItems())
                    .goldExchangeRate(source.getGoldExchangeRate())
                    .trainableSkills(source.getTrainableSkills() != null ? new ArrayList<>(source.getTrainableSkills()) : new ArrayList<>())
                    .maxSkillPoints(source.getMaxSkillPoints())
                    .costPerSkillPoint(source.getCostPerSkillPoint())
                    .repairTypes(source.getRepairTypes() != null ? new ArrayList<>(source.getRepairTypes()) : new ArrayList<>())
                    .repairCostPerPoint(source.getRepairCostPerPoint())
                    .poolSyncIntervalSeconds(source.getPoolSyncIntervalSeconds())
                    .enabled(source.isEnabled())
                    .build();

            target.touchCreate();
            traderRepository.save(target);
            duplicatedCount++;
        }

        log.info("Duplicated {} traders from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
    }
}
