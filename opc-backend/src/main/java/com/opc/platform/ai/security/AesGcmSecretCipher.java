package com.opc.platform.ai.security;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmSecretCipher {

    private static final String VERSION = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec masterKey;

    public AesGcmSecretCipher(String encodedMasterKey) {
        this.masterKey = decodeKey(encodedMasterKey);
    }

    public boolean available() {
        return masterKey != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception exception) {
            throw unavailable("AI secret encryption failed");
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            String[] parts = encoded == null ? new String[0] : encoded.split(":", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported ciphertext format");
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("AI secret decryption failed");
        }
    }

    private SecretKeySpec decodeKey(String encodedMasterKey) {
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedMasterKey.trim());
            if (bytes.length != 32) {
                return null;
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void requireKey() {
        if (masterKey == null) {
            throw unavailable("AI settings master key is not configured");
        }
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
