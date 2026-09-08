package de.mhus.nimbus.shared.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.Optional;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CipherServiceTest {

    @Test
    void encryptDecrypt_roundtrip_success() throws Exception {
        KeyService keyService = Mockito.mock(KeyService.class);
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128);
        SecretKey sk = kg.generateKey();
        Mockito.when(keyService.getSecretKey(KeyType.UNIVERSE, "owner:id")).thenReturn(Optional.of(sk));
        Mockito.when(keyService.getSecretKey(KeyType.UNIVERSE, "owner:id")).thenReturn(Optional.of(sk));
        CipherService cipherService = new CipherService(keyService);
        String cipher = cipherService.encryptAes("Hallo", KeyType.UNIVERSE, "owner:id");
        assertNotNull(cipher);
        String plain = cipherService.decryptAes(cipher, KeyType.UNIVERSE);
        assertEquals("Hallo", plain);
    }

    @Test
    void decrypt_invalidFormat_throws() {
        CipherService cipherService = new CipherService(Mockito.mock(KeyService.class));
        assertThrows(CipherService.CipherException.class, () -> cipherService.decryptAes("abc:def", KeyType.UNIVERSE));
    }
}
