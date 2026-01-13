package de.mhus.nimbus.shared.security;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class SignServiceTest {

    @Test
    void signAndValidate_ecc_success() throws Exception {
        KeyService keyService = Mockito.mock(KeyService.class);
        SignService signService = new SignService(keyService);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair pair = kpg.generateKeyPair();
        PrivateKey priv = pair.getPrivate();
        PublicKey pub = pair.getPublic();
        Mockito.when(keyService.getPrivateKey(KeyType.UNIVERSE, "owner:id")).thenReturn(Optional.of(priv));
        Mockito.when(keyService.getPublicKey(KeyType.UNIVERSE, "owner:id")).thenReturn(Optional.of(pub));
        String sig = signService.sign("DATA", "owner:id");
        assertNotNull(sig);
        assertTrue(signService.validate("DATA", sig));
        assertFalse(signService.validate("DATA2", sig));
    }

    @Test
    void sign_missingKey_throws() {
        SignService signService = new SignService(Mockito.mock(KeyService.class));
        assertThrows(SignService.SignatureException.class, () -> signService.sign("X", "nix:id"));
    }
}

