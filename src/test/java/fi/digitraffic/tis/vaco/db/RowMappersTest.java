package fi.digitraffic.tis.vaco.db;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.RuleConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex.EnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex.ImmutableEnturNetexValidatorConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.EnturNetex2GtfsConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.ImmutableEnturNetex2GtfsConverterConfiguration;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RowMappersTest extends SpringBootIntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writeJson_returnsNullForNullInput() {
        assertThat(RowMappers.writeJson(objectMapper, null), is(nullValue()));
    }

    @Test
    void writeJson_excludesTypeDiscriminatorFromStoredJson() {
        EnturNetex2GtfsConverterConfiguration config = ImmutableEnturNetex2GtfsConverterConfiguration.builder()
            .codespace("FSR")
            .build();

        PGobject result = RowMappers.writeJson(objectMapper, config);

        assertThat(result, is(not(nullValue())));
        assertThat(result.getValue(), not(containsString("@type")));
    }

    @Test
    void writeJson_preservesFieldValues() {
        EnturNetex2GtfsConverterConfiguration config = ImmutableEnturNetex2GtfsConverterConfiguration.builder()
            .codespace("FSR")
            .stopsOnly(true)
            .build();

        PGobject result = RowMappers.writeJson(objectMapper, config);

        assertThat(result, is(not(nullValue())));
        assertThat(result.getValue(), containsString("\"codespace\""));
        assertThat(result.getValue(), containsString("\"FSR\""));
        assertThat(result.getValue(), containsString("\"stopsOnly\""));
        assertThat(result.getValue(), containsString("true"));
    }

    @Test
    void ruleConfiguration_roundtripsCorrectlyWithoutTypeDiscriminatorInStorage() {
        EnturNetex2GtfsConverterConfiguration original = ImmutableEnturNetex2GtfsConverterConfiguration.builder()
            .codespace("FSR")
            .stopsOnly(true)
            .build();

        // Simulate write: config is serialized to DB without @type
        PGobject stored = RowMappers.writeJson(objectMapper, original);
        assertThat(stored.getValue(), not(containsString("@type")));

        // Simulate read: inject @type (as RowMappers.readRuleConfiguration does) and deserialize
        ObjectNode nodeWithType = (ObjectNode) objectMapper.readTree(stored.getValue());
        nodeWithType.put("@type", RuleName.NETEX2GTFS_ENTUR);
        RuleConfiguration deserialized = objectMapper.treeToValue(nodeWithType, RuleConfiguration.class);

        assertThat(deserialized, instanceOf(EnturNetex2GtfsConverterConfiguration.class));
        EnturNetex2GtfsConverterConfiguration result = (EnturNetex2GtfsConverterConfiguration) deserialized;
        assertEquals("FSR", result.codespace());
        assertEquals(true, result.stopsOnly());
    }

    @Test
    void ruleConfiguration_serializesWithRuleNameTypeDiscriminator_notImmutableClassName() throws Exception {
        // Jackson 3 must use the registered rule name ("netex.entur") as the @type value,
        // not the Immutable class name ("ImmutableEnturNetexValidatorConfiguration"),
        // so that SQS roundtrips deserialize the config correctly.
        RuleConfiguration config = ImmutableEnturNetexValidatorConfiguration.builder()
            .codespace("FIN")
            .maximumErrors(100)
            .build();

        String json = objectMapper.writeValueAsString(config);

        assertThat(json, containsString("\"@type\":\"" + RuleName.NETEX_ENTUR + "\""));
        assertThat(json, not(containsString("Immutable")));

        RuleConfiguration deserialized = objectMapper.readValue(json, RuleConfiguration.class);
        assertThat(deserialized, instanceOf(EnturNetexValidatorConfiguration.class));
        assertEquals("FIN", ((EnturNetexValidatorConfiguration) deserialized).codespace());
    }
}
