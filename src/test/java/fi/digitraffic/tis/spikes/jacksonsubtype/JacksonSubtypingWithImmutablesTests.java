package fi.digitraffic.tis.spikes.jacksonsubtype;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This test is meant to show how to create variable yet typed configuration class for validations.
 */
class JacksonSubtypingWithImmutablesTests {

    @Test
    void subtyping() throws JacksonException {
        ImmutableContent content = ImmutableContent.of(ImmutableSubtypeA.builder().name("a").subtypeValueA("aaa").build());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.valueToTree(content);
        Content reloaded = mapper.treeToValue(tree, Content.class);
        // name gets dropped, but mapping works as expected
        assertThat(reloaded, equalTo(ImmutableContent.of(ImmutableSubtypeA.builder().subtypeValueA("aaa").build())));
    }

}
