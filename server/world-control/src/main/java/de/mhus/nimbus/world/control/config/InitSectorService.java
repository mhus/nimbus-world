package de.mhus.nimbus.world.control.config;

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

/**
 * This will setup region keys if needed.
 * TODO should be a Sector key. Region keys are not needed anymore.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class InitSectorService {

    private final KeyService keyService;
    private final RegionSettings regionProperties;

    @PostConstruct
    public void init() {
        checkSectorServerJwtToken();
    }

    private void checkSectorServerJwtToken() {
        var intent = KeyIntent.of(regionProperties.getSectorServerId(), KeyIntent.SECTOR_SERVER_JWT_TOKEN);
        if (keyService.getLatestPrivateKey(KeyType.SECTOR, intent).isEmpty()) {
            var keys = keyService.createECCKeys();
            keyService.storeKeyPair(
                    KeyType.SECTOR,
                    KeyId.newOf(intent),
                    keys
            );
            log.info("Created missing JWT token key for sector server'{}'", regionProperties.getSectorServerId());
            ConfidentialUtil.save("regionServerPublicKey.txt", keys.getPublic());
        }
    }
}
