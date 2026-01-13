package de.mhus.nimbus.shared.persistence;

import de.mhus.nimbus.shared.security.KeyId;
import de.mhus.nimbus.shared.security.KeyKind;
import de.mhus.nimbus.shared.security.KeyType;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;

/**
 * Persisted key definition to be stored in MongoDB.
 */
@Document(collection = "s_keys")
@ActualSchemaVersion("1.0.0")
@Data
public class SKey implements Identifiable {

    @Id
    private String id;

    // Frei waehlbarer Kontexttyp (z. B. UNIVERSE/REGION/WORLD -> KeyType.name())
    private KeyType type;

    // Art des Schluessels: "public", "private", "secret" - for symmetric keys
    private KeyKind kind;

    // Algorithmus (z. B. RSA, EC, AES, HmacSHA256)
    private String algorithm; // if known

    // Besitzer (KeyId.owner()), e.g region id,world id
    private String owner;

    // Intent (KeyId.intent()), e.g "jwt-signing", "data-encryption"
    private String intent;

    // Name/Bezeichner (hier wird KeyId.id() gemappt)
    private String keyId;

    // Der Schluesselinhalt als Base64-kodierte Bytes
    private String key;

    @CreatedDate
    private Instant createdAt;

    private Instant expiresAt;

    private boolean enabled = true;

    public SKey() {}

    // Convenience-Konstruktor fuer generischen Eintrag inkl. Owner
    public SKey(KeyType type, KeyKind kind, KeyId id, String algorithm, String base64Key) {
        this.type = type;
        this.kind = kind;
        this.owner = id.owner();
        this.intent = id.intent();
        this.keyId = id.id();
        this.algorithm = algorithm;
        this.key = base64Key;
    }

    // Factory-Methoden
    public static SKey ofPublicKey(KeyType type, KeyId id, PublicKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return new SKey(type, KeyKind.PUBLIC, id, key.getAlgorithm(), base64);
    }

    public static SKey ofPrivateKey(KeyType type, KeyId id, PrivateKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return new SKey(type, KeyKind.PRIVATE, id, key.getAlgorithm(), base64);
    }

    public static SKey ofSecretKey(KeyType type, KeyId id, SecretKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return new SKey(type, KeyKind.SECRET, id, key.getAlgorithm(), base64);
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
