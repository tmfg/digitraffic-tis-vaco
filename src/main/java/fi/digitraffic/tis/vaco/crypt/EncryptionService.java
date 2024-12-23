package fi.digitraffic.tis.vaco.crypt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Simple wrapper for symmetric encryption and decryption of strings Should be considered "secure enough", that is, for
 * semi-private data, not Personal Data.
 */
@Service
public class EncryptionService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
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

    @Value("${vaco.encryptionKeys.credentials}")
    private String keyId;

    private static KmsAsyncClient kmsAsyncClient;

    public byte[] encryptBlob(AuthenticationDetails details) {

            SdkBytes myBytes = SdkBytes.fromUtf8String(details.toString());
            EncryptRequest encryptRequest = EncryptRequest.builder()
                .keyId(keyId)
                .plaintext(myBytes)
                .build();

            try {
                EncryptResponse response = getAsyncClient()
                    .encrypt(encryptRequest)
                    .toCompletableFuture()
                    .get();

                return response.ciphertextBlob().asByteArray();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error during encryption", e);
            }
    }

    public byte[] decryptBlob(byte[] encryptedData) {

        SdkBytes bytes = SdkBytes.fromByteArray(encryptedData);
        logger.debug("Decrypting data: {}", Base64.getEncoder().encodeToString(encryptedData));
        DecryptRequest decryptRequest = DecryptRequest.builder()
            .ciphertextBlob(bytes)
            .keyId(keyId)
            .build();

        try {
            DecryptResponse decryptResponse = getAsyncClient()
                .decrypt(decryptRequest)
                .toCompletableFuture()
                .join();

            return decryptResponse.plaintext().asByteArray();

        } catch (CompletionException e) {
            throw new RuntimeException("Error occurred during decryption: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private static KmsAsyncClient getAsyncClient() {
        if (kmsAsyncClient == null) {
            SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .maxConcurrency(100)
                .connectionTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build();

            ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMinutes(2))
                .apiCallAttemptTimeout(Duration.ofSeconds(90))
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(3)
                    .build())
                .build();

            kmsAsyncClient = KmsAsyncClient.builder()
                .httpClient(httpClient)
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"))
                )
                .region(Region.of("eu-north-1"))
                .build();
        }
        return kmsAsyncClient;
    }

}
