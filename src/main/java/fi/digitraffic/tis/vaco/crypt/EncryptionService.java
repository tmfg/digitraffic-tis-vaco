package fi.digitraffic.tis.vaco.crypt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Simple wrapper for symmetric encryption and decryption of strings Should be considered "secure enough", that is, for
 * semi-private data, not Personal Data.
 */
@Service
public class EncryptionService {

    private final ObjectMapper objectMapper;
    private final SecretKey secretKey;

    private final Cipher cipher;

    public EncryptionService(VacoProperties vacoProperties,
                             ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        byte[] keyBytes = vacoProperties.magicLink().key().getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        try {
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (GeneralSecurityException e) {
            throw new VacoException("Failed to initialize cipher", e);
        }
    }

    public <T> String encrypt(T original) {
        try {
            byte[] iv = new byte[16]; // Initialization Vector
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv); // Generate a random IV
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encryptedData = cipher.doFinal(objectMapper.writeValueAsString(original).getBytes());
            var encoder = Base64.getUrlEncoder();
            var encrypt64 = encoder.encode(encryptedData);
            var iv64 = encoder.encode(iv);
            var combined = new String(encrypt64) + "." + new String(iv64);
            return new String(encoder.encode(combined.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new VacoException("Failed to initialize cipher for encryption", e);
        } catch (JsonProcessingException e) {
            throw new VacoException("Failed to serialize given object as JSON to be used as payload", e);
        }
    }

    public <T> T decrypt(String cypher, Class<T> clz) {
        try {
            var decoder = Base64.getUrlDecoder();
            var split = new String(decoder.decode(cypher), StandardCharsets.UTF_8).split("\\.");
            var cypherText = decoder.decode(split[0]);
            var iv = decoder.decode(split[1]);
            var paraSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, paraSpec);
            byte[] decryptedData = cipher.doFinal(cypherText);
            return objectMapper.readValue(decryptedData, clz);
        } catch (GeneralSecurityException e) {
            throw new VacoException("Failed to initialize cipher for encryption", e);
        } catch (IOException e) {
            throw new VacoException("Failed to deserialize decrypted result", e);
        }
    }
}
