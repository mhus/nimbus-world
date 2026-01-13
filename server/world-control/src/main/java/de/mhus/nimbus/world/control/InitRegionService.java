package de.mhus.nimbus.world.control;

import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.shared.security.KeyId;
import de.mhus.nimbus.shared.security.KeyIntent;
import de.mhus.nimbus.shared.security.KeyService;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.shared.utils.ConfidentialUtil;
import de.mhus.nimbus.world.shared.region.RegionSettings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InitRegionService {

    private final KeyService keyService;
    private final RRegionService regionService;
    private final RegionSettings regionProperties;

    @PostConstruct
    public void init() {
        checkRegionServerJwtToken();
        checkRegionJwtTokens();
    }

    private void checkRegionJwtTokens() {
        for (var regionId : regionService.listAllIds()) {
            regionService.getRegionNameById(regionId).ifPresent(
                    regionName -> checkRegionJwtToken(regionName)
            );
        }
    }

    private void checkRegionJwtToken(String regionName) {
        var intent = KeyIntent.of(regionName, KeyIntent.REGION_JWT_TOKEN);
        if (keyService.getLatestPrivateKey(KeyType.REGION, intent).isEmpty()) {
            var keys = keyService.createECCKeys();
            keyService.storeKeyPair(
                    KeyType.REGION,
                    KeyId.newOf(intent),
                    keys
            );
            log.info("Created missing JWT token key for region '{}'", regionName);
        }
    }

    private void checkRegionServerJwtToken() {
        var intent = KeyIntent.of(regionProperties.getSectorServerId(), KeyIntent.REGION_SERVER_JWT_TOKEN);
        if (keyService.getLatestPrivateKey(KeyType.REGION, intent).isEmpty()) {
            var keys = keyService.createECCKeys();
            keyService.storeKeyPair(
                    KeyType.REGION,
                    KeyId.newOf(intent),
                    keys
            );
            log.info("Created missing JWT token key for region server'{}'", regionProperties.getSectorServerId());
            ConfidentialUtil.save("regionServerPublicKey.txt", keys.getPublic());
        }
    }

}
