package fi.digitraffic.tis.spikes.jacksonsubtype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

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
        // name gets dropped, but mapping works as expected
        assertThat(reloaded, equalTo(ImmutableContent.of(ImmutableSubtypeA.builder().subtypeValueA("aaa").build())));
    }

}
