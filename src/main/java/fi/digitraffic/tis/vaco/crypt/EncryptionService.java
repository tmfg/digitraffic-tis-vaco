package fi.digitraffic.tis.vaco.crypt;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.vaco.InvalidMappingException;
import fi.digitraffic.tis.vaco.VacoException;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.credentials.model.AuthenticationDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import java.util.Objects;
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
    private final VacoProperties vacoProperties;
    private final KmsAsyncClient kmsAsyncClient;


    public EncryptionService(VacoProperties vacoProperties,
                             ObjectMapper objectMapper, KmsAsyncClient kmsAsyncClient) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.kmsAsyncClient = Objects.requireNonNull(kmsAsyncClient);
    }

    public byte[] encryptBlob(AuthenticationDetails details) {

        SdkBytes myBytes = null;
        try {
            myBytes = SdkBytes.fromByteArray(objectMapper.writeValueAsBytes(details));
        } catch (JacksonException e) {
            throw new InvalidMappingException("Authentication details cannot converted to json", e);
        }
        EncryptRequest encryptRequest = EncryptRequest.builder()
                .keyId(vacoProperties.encryptionKeys().feedCredentials())
                .plaintext(myBytes)
                .build();

            try {
                EncryptResponse response = kmsAsyncClient
                    .encrypt(encryptRequest)
                    .toCompletableFuture()
                    .get();

                return response.ciphertextBlob().asByteArray();
            } catch (InterruptedException | ExecutionException e) {
                throw new VacoException("Error during encryption", e);
            }
    }

    public byte[] decryptBlob(byte[] encryptedData) {

        SdkBytes bytes = SdkBytes.fromByteArray(encryptedData);
        DecryptRequest decryptRequest = DecryptRequest.builder()
            .ciphertextBlob(bytes)
            .keyId(vacoProperties.encryptionKeys().feedCredentials())
            .build();

        try {
            DecryptResponse decryptResponse = kmsAsyncClient
                .decrypt(decryptRequest)
                .toCompletableFuture()
                .join();

            return decryptResponse.plaintext().asByteArray();

        } catch (CompletionException e) {
            throw new VacoException("Error occurred during decryption: " + e.getCause().getMessage(), e.getCause());
        }
    }

}
