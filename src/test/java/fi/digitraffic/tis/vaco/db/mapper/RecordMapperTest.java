package fi.digitraffic.tis.vaco.db.mapper;

import fi.digitraffic.tis.vaco.db.model.ImmutableConversionInputRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableValidationInputRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.EnturNetex2GtfsConverterConfiguration;
import fi.digitraffic.tis.vaco.rules.model.netex2gtfs.ImmutableEnturNetex2GtfsConverterConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordMapperTest {

    private ObjectMapper objectMapper;
    private RecordMapper recordMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        recordMapper = new RecordMapper(objectMapper);
    }

    @Test
    void toConversionInput_returnsNullConfigWhenStoredConfigIsNull() {
        var inputRecord = ImmutableConversionInputRecord.builder()
            .id(1L)
            .name(RuleName.NETEX2GTFS_ENTUR)
            .build();

        ConversionInput result = recordMapper.toConversionInput(inputRecord);

        assertThat(result.config(), is(nullValue()));
    }

    @Test
    void toConversionInput_deserializesConfigCorrectly() {
        EnturNetex2GtfsConverterConfiguration original = ImmutableEnturNetex2GtfsConverterConfiguration.builder()
            .codespace("FSR")
            .stopsOnly(true)
            .build();
        // Simulate what the DB stores: JSON without @type
        ObjectNode storedNode = (ObjectNode) objectMapper.valueToTree(original);
        storedNode.remove("@type");

        var inputRecord = ImmutableConversionInputRecord.builder()
            .id(1L)
            .name(RuleName.NETEX2GTFS_ENTUR)
            .config(storedNode)
            .build();

        ConversionInput result = recordMapper.toConversionInput(inputRecord);

        assertThat(result.config(), instanceOf(EnturNetex2GtfsConverterConfiguration.class));
        EnturNetex2GtfsConverterConfiguration config = (EnturNetex2GtfsConverterConfiguration) result.config();
        assertEquals("FSR", config.codespace());
        assertEquals(true, config.stopsOnly());
    }

    @Test
    void toValidationInput_returnsNullConfigWhenStoredConfigIsNull() {
        var inputRecord = ImmutableValidationInputRecord.builder()
            .id(1L)
            .name(RuleName.GTFS_CANONICAL)
            .build();

        ValidationInput result = recordMapper.toValidationInput(inputRecord);

        assertThat(result.config(), is(nullValue()));
    }

    @Test
    void toValidationInput_deserializesConfigCorrectly() {
        EnturNetex2GtfsConverterConfiguration original = ImmutableEnturNetex2GtfsConverterConfiguration.builder()
            .codespace("FSR")
            .build();
        ObjectNode storedNode = (ObjectNode) objectMapper.valueToTree(original);
        storedNode.remove("@type");

        var inputRecord = ImmutableValidationInputRecord.builder()
            .id(1L)
            .name(RuleName.NETEX2GTFS_ENTUR)
            .config(storedNode)
            .build();

        ValidationInput result = recordMapper.toValidationInput(inputRecord);

        assertThat(result.config(), instanceOf(EnturNetex2GtfsConverterConfiguration.class));
        assertEquals("FSR", ((EnturNetex2GtfsConverterConfiguration) result.config()).codespace());
    }
}
