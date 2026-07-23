package com.opc.platform.ai.security;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmSecretCipherTest {

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutLeakingPlaintext() throws Exception {
        String encodedKey = generateKey();
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(encodedKey);
        String secret = "sk-sensitive-provider-key";

        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);

        assertTrue(first.startsWith("v1:"));
        assertFalse(first.contains(secret));
        assertFalse(first.equals(second));
        assertEquals(secret, cipher.decrypt(first));
        assertEquals(secret, cipher.decrypt(second));
    }

    @Test
    void rejectsMissingMasterKeyWithoutRepeatingSecret() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher("");
        String secret = "sk-must-not-appear";

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cipher.encrypt(secret)
        );

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertFalse(exception.getMessage().contains(secret));
    }

    private String generateKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
