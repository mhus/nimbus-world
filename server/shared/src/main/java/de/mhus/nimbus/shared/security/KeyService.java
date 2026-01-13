package de.mhus.nimbus.shared.security;

import de.mhus.nimbus.shared.persistence.SKey;
import de.mhus.nimbus.shared.persistence.SKeyRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Implementierung der Provider-Interfaces, die Schlüssel aus der SKey-Entity lädt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyService {

    private static final String KIND_PRIVATE = KeyKind.PRIVATE.name();
    private static final String KIND_PUBLIC = KeyKind.PUBLIC.name();
    private static final String KIND_SECRET = KeyKind.SECRET.name();

    private final SKeyRepository repository;

    public Optional<PublicKey> getPublicKey(KeyType type, KeyId id) {
        return repository
                .findByTypeAndKindAndOwnerAndKeyId(type.name(), KIND_PUBLIC, id.owner(), id.id())
                .flatMap(this::toPublicKey);
    }

    public List<PublicKey> getPublicKeysForIntent(KeyType type, KeyIntent intent) {
        return repository.findAllByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(type.name(), KIND_PUBLIC, intent.owner(), intent.intent())
                .stream()
                .filter(sKey -> sKey.isEnabled() && !sKey.isExpired())
                .map(key -> toPublicKey(key).orElse(null))
                .filter(key -> key != null)
                .toList();
    }

    public Optional<PrivateKey> getPrivateKey(KeyType type, KeyId id) {
        return repository
                .findByTypeAndKindAndOwnerAndKeyId(type.name(), KIND_PRIVATE, id.owner(), id.id())
                .flatMap(this::toPrivateKey);
    }

    public List<PrivateKey> getPrivateKeysForOwner(KeyType type, KeyIntent intent) {
        return repository.findAllByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(type.name(), KIND_PRIVATE, intent.owner(), intent.intent())
                .stream()
                .filter(sKey -> sKey.isEnabled() && !sKey.isExpired())
                .map(key -> toPrivateKey(key).orElse(null))
                .filter(key -> key != null)
                .toList();
    }

    private Optional<PrivateKey> toPrivateKey(SKey sKey) {
        try {
            byte[] encoded = Base64.getDecoder().decode(sKey.getKey());
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance(sKey.getAlgorithm());
            return Optional.of(kf.generatePrivate(spec));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<SecretKey> getSecretKey(KeyType type, KeyId id) {
        return repository
                .findByTypeAndKindAndOwnerAndKeyId(type.name(), KIND_SECRET, id.owner(), id.id())
                .flatMap(this::toSecretKey);
    }

    public List<SecretKey> getSecretKeysForOwner(KeyType type, KeyIntent intent) {
        // Bugfix: vorher KIND_PRIVATE, korrekt ist KIND_SECRET ("symmetric")
        return repository.findAllByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(type.name(), KIND_SECRET, intent.owner(), intent.intent())
                .stream()
                .filter(sKey -> sKey.isEnabled() && !sKey.isExpired())
                .map(key -> toSecretKey(key).orElse(null))
                .filter(key -> key != null)
                .toList();
    }

    private Optional<PublicKey> toPublicKey(SKey entity) {
        try {
            byte[] encoded = Base64.getDecoder().decode(entity.getKey());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance(entity.getAlgorithm());
            return Optional.of(kf.generatePublic(spec));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<SecretKey> toSecretKey(SKey entity) {
        try {
            byte[] encoded = Base64.getDecoder().decode(entity.getKey());
            return Optional.of(new SecretKeySpec(encoded, entity.getAlgorithm()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<PrivateKey> getLatestPrivateKey(KeyType keyType, KeyIntent intent) {
        return repository.findTop1ByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(keyType.name(), KIND_PRIVATE, intent.owner(), intent.intent())
                .stream()
                .filter(SKey::isEnabled)
                .filter(k -> !k.isExpired())
                .findFirst()
                .flatMap(this::toPrivateKey);
    }

    public Optional<PrivateKey> getLatestPublicKey(KeyType keyType, KeyIntent intent) {
        return repository.findTop1ByTypeAndKindAndOwnerAndIntentOrderByCreatedAtDesc(keyType.name(), KIND_PUBLIC, intent.owner(), intent.intent())
                .stream()
                .filter(SKey::isEnabled)
                .filter(k -> !k.isExpired())
                .findFirst()
                .flatMap(this::toPrivateKey);
    }

    public Optional<SecretKey> getLatestSecretKey(KeyType keyType, String owner) {
        return repository.findTop1ByTypeAndKindAndOwnerOrderByCreatedAtDesc(keyType.name(), KIND_SECRET, owner)
                .stream()
                .filter(SKey::isEnabled)
                .filter(k -> !k.isExpired())
                .findFirst()
                .flatMap(this::toSecretKey);
    }

    public Optional<SecretKey> getSecretKey(KeyType keyType, @NonNull String keyId) {
        return getSecretKey(keyType, parseKeyId(keyId).get());
    }

    public Optional<PublicKey> getPublicKey(KeyType keyType, @NonNull String keyId) {
        return getPublicKey(keyType, parseKeyId(keyId).get());
    }

    public Optional<PrivateKey> getPrivateKey(KeyType keyType, @NonNull String keyId) {
        return getPrivateKey(keyType, parseKeyId(keyId).get());
    }

    /**
     * Parses a string in the form "owner:id" into a KeyId.
     * Returns Optional.empty() if the input is null, blank, or malformed.
     */
    public Optional<KeyId> parseKeyId(String keyId) {
        if (keyId == null) return Optional.empty();
        String trimmed = keyId.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        String[] parts = trimmed.split(";", 3);
        if (parts.length != 3) return Optional.empty();
        String owner = parts[0].trim();
        String intent = parts[1].trim();
        String id = parts[2].trim();
        if (owner.isEmpty() || intent.isEmpty() || id.isEmpty()) return Optional.empty();
        try {
            return Optional.of(KeyId.of(owner, intent, id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public KeyPair createECCKeys() {
        // Erzeugt ein EC Schlüsselpaar mit Standard-Kurve secp256r1 (NIST P-256)
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("EC KeyPair Generierung fehlgeschlagen", e);
        }
    }

    public KeyId generateKeyId(KeyIntent intent) {
        return new KeyId(intent, java.util.UUID.randomUUID().toString());
    }

    public void storeKeyPair(KeyType keyType, KeyId keyId, KeyPair keyPair) {
        repository.save(SKey.ofPrivateKey(keyType, keyId, keyPair.getPrivate()));
        repository.save(SKey.ofPublicKey(keyType, keyId, keyPair.getPublic()));
    }

    public void deleteAllForIntent(KeyIntent intent) {
        repository.deleteAllByTypeAndKindAndOwnerAndIntent(KeyType.UNIVERSE.name(), KeyKind.PUBLIC.name(), intent.owner(), intent.intent());
        repository.deleteAllByTypeAndKindAndOwnerAndIntent(KeyType.UNIVERSE.name(), KeyKind.PRIVATE.name(), intent.owner(), intent.intent());
        repository.deleteAllByTypeAndKindAndOwnerAndIntent(KeyType.UNIVERSE.name(), KeyKind.SECRET.name(), intent.owner(), intent.intent());
    }

    public void deleteAllForIntent(KeyType keyType, KeyIntent intent) {
        repository.deleteAllByTypeAndKindAndOwnerAndIntent(keyType.name(), KeyKind.PUBLIC.name(), intent.owner(), intent.intent());
        repository.deleteAllByTypeAndKindAndOwnerAndIntent(keyType.name(), KeyKind.PRIVATE.name(), intent.owner(), intent.intent());
        repository.deleteAllByTypeAndKindAndOwnerAndIntent(keyType.name(), KeyKind.SECRET.name(), intent.owner(), intent.intent());
    }

    public void storePublicKey(KeyType type, FormattedKey formattedKey) {
        storePublicKey(type, formattedKey.getKeyId(), formattedKey.getKey());
    }

    public void storePublicKey(KeyType type, KeyId keyId, String publicKey) {
        if (type == null || publicKey == null || publicKey.isBlank()) {
            log.warn("storePublicKey: ungültige Parameter (type/name/publicKey)" );
            throw new IllegalArgumentException("Ungültige Parameter für storePublicKey");
        }
        String ownerStr = keyId.owner();
        String intentStr = keyId.intent();
        // Existenz prüfen
        if (repository.findByTypeAndKindAndOwnerAndKeyId(type.name(), KeyKind.PUBLIC.name(), ownerStr, keyId.id()).isPresent()) {
            log.warn("storePublicKey: Public Key existiert bereits type={} owner={} intent={} keyId={}", type, ownerStr, intentStr, keyId.id());
            throw new IllegalStateException("Public Key existiert bereits für " + keyId);
        }
        String trimmed = publicKey.trim();
        String base64;
        if (trimmed.contains("BEGIN PUBLIC KEY") && trimmed.contains("END PUBLIC KEY")) {
            StringBuilder sb = new StringBuilder();
            boolean inside = false;
            for (String line : trimmed.split("\\R")) {
                line = line.trim();
                if (line.startsWith("-----BEGIN") && line.contains("PUBLIC KEY")) { inside = true; continue; }
                if (line.startsWith("-----END") && line.contains("PUBLIC KEY")) { break; }
                if (inside && !line.isEmpty() && !line.startsWith("#")) sb.append(line);
            }
            base64 = sb.toString();
        } else {
            base64 = java.util.Arrays.stream(trimmed.split("\\R"))
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .reduce("", (a,b) -> a + b);
        }
        if (base64.isBlank()) {
            log.warn("storePublicKey: extrahierter Base64-Inhalt leer für owner='{}' intent='{}' keyId='{}'", ownerStr, intentStr, keyId.id());
            throw new IllegalArgumentException("Leerer Public Key Inhalt");
        }
        byte[] der;
        try { der = Base64.getDecoder().decode(base64); } catch (Exception e) {
            log.warn("storePublicKey: Base64 Decode Fehler für owner='{}' intent='{}' keyId='{}': {}", ownerStr, intentStr, keyId.id(), e.toString());
            throw new IllegalArgumentException("Public Key Base64 ungültig", e);
        }
        PublicKey pubKey;
        String algorithm;
        try {
            pubKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            algorithm = "EC";
        } catch (Exception ignore) {
            try {
                pubKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
                algorithm = "RSA";
            } catch (Exception ex) {
                log.warn("storePublicKey: Algorithmus nicht erkannt für owner='{}' intent='{}' keyId='{}': {}", ownerStr, intentStr, keyId.id(), ex.toString());
                throw new IllegalArgumentException("Public Key Algorithmus nicht erkannt", ex);
            }
        }
        try {
            repository.save(SKey.ofPublicKey(type, keyId, pubKey));
            log.info("storePublicKey: Public Key gespeichert type={} owner={} intent={} keyId={} alg={}", type, ownerStr, intentStr, keyId.id(), algorithm);
        } catch (Exception e) {
            log.error("storePublicKey: Fehler beim Speichern des Public Keys type={} owner={} intent={} keyId={}: {}", type, ownerStr, intentStr, keyId.id(), e.toString());
            throw new IllegalStateException("Speichern des Public Keys fehlgeschlagen", e);
        }
    }
}
