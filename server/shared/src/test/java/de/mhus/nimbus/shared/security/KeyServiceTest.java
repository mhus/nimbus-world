package de.mhus.nimbus.shared.security;

import de.mhus.nimbus.shared.persistence.SKey;
import de.mhus.nimbus.shared.persistence.SKeyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class KeyServiceTest {

    @Test
    void createECCKeys_generatesEcKeys() {
        SKeyRepository repo = Mockito.mock(SKeyRepository.class);
        KeyService service = new KeyService(repo);
        KeyPair pair = service.createECCKeys();
        assertNotNull(pair.getPrivate());
        assertNotNull(pair.getPublic());
        assertEquals("EC", pair.getPrivate().getAlgorithm());
        assertEquals("EC", pair.getPublic().getAlgorithm());
    }

    @Test
    void getLatestPrivateKey_filtersExpiredDisabled() {
        SKeyRepository repo = Mockito.mock(SKeyRepository.class);
        KeyService service = new KeyService(repo);
        SKey good = new SKey();
        good.setId("1");
        good.setType(KeyType.UNIVERSE); good.setKind(KeyKind.PRIVATE); good.setOwner("system"); good.setIntent("auth"); good.setKeyId("kid");
        good.setAlgorithm("EC");
        good.setKey(""); // invalid base64
        good.setCreatedAt(Instant.now()); good.setEnabled(true);
        good.setExpiresAt(Instant.now().plusSeconds(60));
        Mockito.when(repo.findTop1ByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(good));
        Optional<PrivateKey> opt = service.getLatestPrivateKey(KeyType.UNIVERSE, KeyIntent.of("system","auth"));
        assertTrue(opt.isEmpty()); // invalid base64 prevents parsing
    }

    @Test
    void parseKeyId_valid() {
        SKeyRepository repo = Mockito.mock(SKeyRepository.class);
        KeyService service = new KeyService(repo);
        Optional<KeyId> id = service.parseKeyId("owner;intent;uuid");
        assertTrue(id.isPresent());
        assertEquals("owner", id.get().owner());
        assertEquals("intent", id.get().intent());
        assertEquals("uuid", id.get().id());
    }

    @Test
    void parseKeyId_invalid() {
        KeyService service = new KeyService(Mockito.mock(SKeyRepository.class));
        assertTrue(service.parseKeyId(";x;x").isEmpty()); // missing owner
        assertTrue(service.parseKeyId("x;;x").isEmpty()); // missing intent
        assertTrue(service.parseKeyId("x;x;").isEmpty()); // missing id
        assertTrue(service.parseKeyId("no-colon").isEmpty());
        assertTrue(service.parseKeyId(null).isEmpty());
    }
}
