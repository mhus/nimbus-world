package de.mhus.nimbus.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void createTokenWithPrivateKey_ecKey_success() throws Exception {
        KeyService keyService = Mockito.mock(KeyService.class); // not used directly
        JwtService jwtService = new JwtService(keyService);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair pair = kpg.generateKeyPair();
        String token = jwtService.createTokenWithPrivateKey(pair.getPrivate(), "subj", Map.of("a","b"), Instant.now().plusSeconds(60));
        assertNotNull(token);
        assertTrue(token.split("\\.").length >= 3, "JWT should have 3 parts");
    }

    @Test
    void validateTokenWithPublicKey_success() throws Exception {
        KeyService keyService = Mockito.mock(KeyService.class);
        JwtService jwtService = new JwtService(keyService);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair pair = kpg.generateKeyPair();
        PrivateKey priv = pair.getPrivate();
        PublicKey pub = pair.getPublic();
        String token = jwtService.createTokenWithPrivateKey(priv, "user1", Map.of("x","y"), Instant.now().plusSeconds(60));
        KeyIntent intent = KeyIntent.of("system","auth");
        Mockito.when(keyService.getPublicKeysForIntent(KeyType.UNIVERSE, intent)).thenReturn(java.util.List.of(pub));
        Optional<Jws<Claims>> claimsOpt = jwtService.validateTokenWithPublicKey(token, KeyType.UNIVERSE, intent);
        assertTrue(claimsOpt.isPresent());
        assertEquals("user1", claimsOpt.get().getPayload().getSubject());
    }

    @Test
    void validateTokenWithPublicKey_invalidSignature_returnsEmpty() throws Exception {
        KeyService keyService = Mockito.mock(KeyService.class);
        JwtService jwtService = new JwtService(keyService);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair signer = kpg.generateKeyPair();
        String token = jwtService.createTokenWithPrivateKey(signer.getPrivate(), "userX", null, Instant.now().plusSeconds(60));
        KeyPair other = kpg.generateKeyPair();
        KeyIntent intent = KeyIntent.of("system","auth");
        Mockito.when(keyService.getPublicKeysForIntent(KeyType.UNIVERSE, intent)).thenReturn(java.util.List.of(other.getPublic()));
        assertTrue(jwtService.validateTokenWithPublicKey(token, KeyType.UNIVERSE, intent).isEmpty());
    }
}
