package de.mhus.nimbus.shared.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;

/**
 * Service für asymmetrische Signaturen (ECC bevorzugt, RSA unterstützt).
 * Format: keyIdBase64:algorithm:signatureBase64.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignService {

    private final KeyService keyService;
    private static final KeyType DEFAULT_KEY_TYPE = KeyType.UNIVERSE;

    private static final String DEFAULT_ECC_ALGORITHM = "SHA256withECDSA";
    private static final String DEFAULT_RSA_ALGORITHM = "SHA256withRSA";

    /**
     * Signs the given text using a symmetric key identified by keyId.
     * The signature format is "keyIdBase64:algorithm:signatureBase64".
     *
     * @param text the text to sign, never null
     * @param keyId the key identifier in format "owner:id", never null
     * @return the complete signature string containing Base64-encoded keyId, algorithm, and signature
     * @throws SignatureException if the key cannot be found or signing fails
     */
    public String sign(@NonNull String text, @NonNull String keyId) {
        Optional<PrivateKey> privateKeyOpt = keyService.getPrivateKey(DEFAULT_KEY_TYPE, keyId);
        if (privateKeyOpt.isEmpty()) {
            throw new SignatureException("No asymmetric private key (ECC/RSA) found for keyId: " + keyId);
        }
        PrivateKey privateKey = privateKeyOpt.get();
        String algorithm = deriveAsymmetricAlgorithm(privateKey);
        return signWithAsymmetric(text, keyId, privateKey, algorithm);
    }

    /**
     * Validates the given text against a signature string.
     * The signature string must be in the format "keyIdBase64:algorithm:signatureBase64".
     *
     * @param text the original text to validate, never null
     * @param signatureString the complete signature string, never null
     * @return true if the signature is valid, false otherwise
     */
    public boolean validate(@NonNull String text, @NonNull String signatureString) {
        try {
            SignatureParts parts = parseSignature(signatureString);
            String algorithm = parts.algorithm();
            if (isAsymmetricAlgorithm(algorithm)) {
                Optional<PublicKey> publicKeyOpt = keyService.getPublicKey(DEFAULT_KEY_TYPE, parts.keyId());
                if (publicKeyOpt.isEmpty()) {
                    log.warn("No public key found for keyId '{}'", parts.keyId());
                    return false;
                }
                return validateWithAsymmetric(text, parts, publicKeyOpt.get());
            } else {
                log.warn("Unsupported algorithm '{}' in signature (only asymmetric supported)", algorithm);
                return false;
            }
        } catch (Exception e) {
            log.error("Validation failed: {}", e.getMessage(), e);
            return false;
        }
    }

    // ----------------------------------------------------------------
    // Private helper methods

    private boolean isAsymmetricAlgorithm(String algorithm) {
        if (algorithm == null) return false;
        String u = algorithm.toUpperCase();
        return u.contains("ECDSA") || u.contains("RSA");
    }

    private String deriveAsymmetricAlgorithm(PrivateKey privateKey) {
        String alg = privateKey.getAlgorithm();
        if ("EC".equalsIgnoreCase(alg)) return DEFAULT_ECC_ALGORITHM;
        if ("RSA".equalsIgnoreCase(alg)) return DEFAULT_RSA_ALGORITHM;
        // Fallback: versuche RSA Default
        return DEFAULT_RSA_ALGORITHM;
    }

    private String signWithAsymmetric(String text, String keyId, PrivateKey privateKey, String algorithm) {
        try {
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
            String keyIdBase64 = Base64.getEncoder().encodeToString(keyId.getBytes(StandardCharsets.UTF_8));
            return keyIdBase64 + ";" + algorithm + ";" + signatureBase64;
        } catch (Exception e) {
            throw new SignatureException("Failed to sign text with asymmetric key: " + e.getMessage(), e);
        }
    }

    private boolean validateWithAsymmetric(@NonNull String text, SignatureParts parts, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance(parts.algorithm());
            sig.initVerify(publicKey);
            sig.update(text.getBytes(StandardCharsets.UTF_8));
            byte[] providedSignature = Base64.getDecoder().decode(parts.signatureBase64());
            return sig.verify(providedSignature);
        } catch (Exception e) {
            log.error("Asymmetric validation error: {}", e.getMessage(), e);
            return false;
        }
    }

    private SignatureParts parseSignature(String signatureString) {
        String[] parts = signatureString.split(";", 3);
        if (parts.length != 3) {
            throw new SignatureException("Invalid signature format. Expected 'keyIdBase64:algorithm:signatureBase64'");
        }
        // Decode the Base64-encoded keyId
        String keyIdBase64 = parts[0];
        String keyId = new String(Base64.getDecoder().decode(keyIdBase64), StandardCharsets.UTF_8);
        return new SignatureParts(keyId, parts[1], parts[2]);
    }

    // ----------------------------------------------------------------
    // Internal record to hold parsed signature components

    private record SignatureParts(String keyId, String algorithm, String signatureBase64) {}

    // ----------------------------------------------------------------
    // Custom exception

    public static class SignatureException extends RuntimeException {
        public SignatureException(String message) {
            super(message);
        }

        public SignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
