package de.mhus.nimbus.shared.security;

import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;

public interface SymmetricKeyProvider {
    Optional<SecretKey> getSecretKey(KeyType type, KeyId id);

    List<SecretKey> getSecretKeysForOwner(KeyType type, String owner);
}
