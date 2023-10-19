package fi.digitraffic.tis.spikes.jacksonsubtype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * This test is meant to show how to create variable yet typed configuration class for validations.
 */
class JacksonSubtypingWithImmutablesTests {

    @Test
    void subtyping() throws JsonProcessingException {
        ImmutableContent content = ImmutableContent.of(ImmutableSubtypeA.builder().name("a").subtypeValueA("aaa").build());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.valueToTree(content);
        Content reloaded = mapper.treeToValue(tree, Content.class);
    }

}
