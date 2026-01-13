package de.mhus.nimbus.shared.security;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;

public interface SymmetricKeyProvider {
    Optional<SecretKey> getSecretKey(KeyType type, KeyId id);
    List<SecretKey> getSecretKeysForOwner(KeyType type, String owner);
}

