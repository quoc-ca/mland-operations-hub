package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts a customer's bank account number at rest (003.3 FR-040).
 *
 * <p>AES-GCM with a 256-bit key supplied as base64 in {@code WORKSHOP_REFUND_ACCOUNT_KEY}. Each value gets a
 * fresh 12-byte nonce, stored in front of the ciphertext, so the same account number never produces the same
 * stored text and nothing can be matched by comparing rows. GCM also authenticates, so a tampered row fails
 * to decrypt instead of returning a wrong number.
 *
 * <p>The key is read lazily: an installation that never refunds by bank never needs it, and the refund
 * routes say plainly what is missing rather than the application refusing to start.
 *
 * <p>Losing the key loses the account numbers. Nothing else in a refund request depends on them: the masked
 * form, the holder name and the bank are stored in clear, so a request stays readable and a customer can be
 * asked for the number again.
 */
public final class RefundAccountCipher {

    public static final String KEY_VARIABLE = "WORKSHOP_REFUND_ACCOUNT_KEY";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Overridden only by tests; null means the environment variable. */
    private static volatile String overrideKey;

    private RefundAccountCipher() {
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[nonce.length + cipherText.length];
            System.arraycopy(nonce, 0, stored, 0, nonce.length);
            System.arraycopy(cipherText, 0, stored, nonce.length, cipherText.length);
            return Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new GeneralSecurityFailure("The account number could not be encrypted", failure);
        }
    }

    public static String decrypt(String storedText) {
        if (storedText == null || storedText.isBlank()) {
            return null;
        }
        try {
            byte[] stored = Base64.getDecoder().decode(storedText);
            if (stored.length <= NONCE_BYTES) {
                throw new GeneralSecurityFailure("The stored account number is too short to be valid", null);
            }
            byte[] nonce = Arrays.copyOfRange(stored, 0, NONCE_BYTES);
            byte[] cipherText = Arrays.copyOfRange(stored, NONCE_BYTES, stored.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new GeneralSecurityFailure("The account number could not be read", failure);
        }
    }

    /** {@code ****1234}: all that is ever shown outside the approving Manager and the transferring actor. */
    public static String mask(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("\\s", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    static void useKeyForTests(String base64Key) {
        overrideKey = base64Key;
    }

    private static SecretKeySpec key() {
        String configured = overrideKey != null ? overrideKey : System.getenv(KEY_VARIABLE);
        if (configured == null || configured.isBlank()) {
            throw new GeneralSecurityFailure(KEY_VARIABLE + " is not configured, so bank account numbers "
                    + "cannot be stored or read", null);
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException invalid) {
            throw new GeneralSecurityFailure(KEY_VARIABLE + " is not valid base64", invalid);
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new GeneralSecurityFailure(KEY_VARIABLE + " must decode to 16, 24 or 32 bytes", null);
        }
        return new SecretKeySpec(raw, "AES");
    }

    /** Typed failure so a missing or wrong key is reported as configuration, never as a lost refund. */
    public static class GeneralSecurityFailure extends IllegalStateException {
        public GeneralSecurityFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
