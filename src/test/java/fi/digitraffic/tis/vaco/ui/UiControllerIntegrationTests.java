package fi.digitraffic.tis.vaco.ui;

import tools.jackson.databind.JsonNode;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.api.model.queue.CreateEntryRequest;
import fi.digitraffic.tis.vaco.crypt.EncryptionService;
import fi.digitraffic.tis.vaco.ui.model.ImmutableMagicToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiControllerIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private EncryptionService encryptionService;

    @Test
    void canFetchEntryStateWithPublicId() throws Exception {
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Joh Rnado";
        JwtAuthenticationToken johRnado = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(johRnado);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").stringValue();

        MvcResult fetchResponse = apiCall(get("/ui/entries/" + entryPublicId + "/state"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode fetchResult = apiResponse(fetchResponse);

        assertAll("Entry fields are fetched properly",
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("name").stringValue(), equalTo(request.name())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("url").stringValue(), equalTo(request.url())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("etag").stringValue(), equalTo(request.etag())),
            () -> assertThat(fetchResult.get("data").get("entry").get("data").get("format").stringValue(), equalTo(request.format())));
    }

    @Test
    void returnsError404OnNonExistingId() throws Exception {
        apiCall(get("/ui/entries/smth/state"))
            .andExpect(status().isNotFound())
            .andReturn();
    }

    // A corrupted magic token must degrade to a 404 like a denied/absent magic link, not crash
    // the request with an uncaught decrypt() exception (500).
    @Test
    void fetchEntryStateDoesNotReturn500WhenMagicTokenDecryptFails() throws Exception {
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Joh Rnado";
        JwtAuthenticationToken johRnado = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(johRnado);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").stringValue();

        String corruptedMagicToken = corruptMagicToken(entryPublicId);

        // requester is otherwise unauthenticated -- only the magic-link path is under test
        SecurityContextHolder.clearContext();

        apiCall(get("/ui/entries/" + entryPublicId + "/state?magic=" + corruptedMagicToken))
            .andExpect(status().isNotFound())
            .andReturn();
    }

    // A `magic` value that isn't even valid base64 must also degrade to 404, not 500.
    @Test
    void fetchEntryStateDoesNotReturn500WhenMagicTokenIsGarbage() throws Exception {
        CreateEntryRequest request = TestObjects.aValidationEntryRequest().build();
        String oid = "Joh Rnado";
        JwtAuthenticationToken johRnado = TestObjects.jwtAuthenticationToken(oid);
        SecurityContextHolder.getContext().setAuthentication(johRnado);
        injectAuthOverrides(oid, asFintrafficIdGroup(companyHierarchyService.findByBusinessId(request.businessId()).get()));

        MvcResult response = apiCall(post("/queue").content(toJson(request)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode createResult = apiResponse(response);
        String entryPublicId = createResult.get("data").get("publicId").stringValue();

        SecurityContextHolder.clearContext();

        apiCall(get("/ui/entries/" + entryPublicId + "/state?magic=not-a-valid-token-at-all!!!"))
            .andExpect(status().isNotFound())
            .andReturn();
    }

    /**
     * Encrypts a magic token, then flips a byte in its raw ciphertext (not the IV) so decrypting
     * it fails the GCM authentication tag check.
     */
    private String corruptMagicToken(String entryPublicId) {
        String token = encryptionService.encrypt(ImmutableMagicToken.of(entryPublicId));

        var decoder = Base64.getUrlDecoder();
        var encoder = Base64.getUrlEncoder();

        String combined = new String(decoder.decode(token), StandardCharsets.UTF_8);
        String[] split = combined.split("\\.");
        byte[] cypherText = decoder.decode(split[0]);
        String iv64 = split[1];

        cypherText[cypherText.length / 2] ^= (byte) 0xFF;

        String corruptedCombined = new String(encoder.encode(cypherText), StandardCharsets.UTF_8) + "." + iv64;
        return new String(encoder.encode(corruptedCombined.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}
