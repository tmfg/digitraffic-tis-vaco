package fi.digitraffic.tis.vaco.queuehandler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.validation.ValidationService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class QueueHandlerRepositoryTests extends SpringBootIntegrationTestBase {

    @Autowired
    private QueueHandlerRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private ImmutableEntry entry;
    private JsonNode metadata;
    private ImmutableValidationInput validation;
    private ImmutableConversionInput conversion;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        entry = ImmutableEntry.of("gtfs", "www.example.fi", TestConstants.FINTRAFFIC_BUSINESS_ID);
        metadata = objectMapper.readTree("{\"metadata\":true}");
        validation = ImmutableValidationInput.of("ananas");
        conversion = ImmutableConversionInput.of("bananas");

    }

    @Test
    void createsCompleteEntries() {
        ImmutableEntry result = repository.create(entry.withEtag("etag")
            .withMetadata(metadata)
            .withConversions(ImmutableConversionInput.of("bananas"))
            .withValidations(validation));

        assertAll(
            () -> assertThat(result.format(), equalTo("gtfs")),
            () -> assertThat(result.etag(), equalTo("etag")),
            () -> assertThat(result.metadata(), equalTo(metadata)),
            () -> assertThat(result.url(), equalTo("www.example.fi")),
            () -> assertThat(result.businessId(), equalTo(TestConstants.FINTRAFFIC_BUSINESS_ID)),
            () -> assertThat(result.validations(), equalTo(List.of(validation.withId(1200000L)))),
            () -> assertThat(result.conversions(), equalTo(List.of(conversion.withId(1300000L))))
        );
    }

    private List<ImmutableTask> validationPhases = Streams.mapIndexed(
        ValidationService.ALL_SUBTASKS,
        (i, p) -> ImmutableTask.of(null, p, (int) (100 + i)))
        .toList();
    private List<ImmutableTask> conversionPhases = Streams.mapIndexed(
        ConversionService.ALL_SUBTASKS,
        (i, p) -> ImmutableTask.of(null, p, (int) (200 + i)))
        .toList();

    @Test
    void entryWithoutValidationsAndConversionsGetsGeneratedValidationPhases() {
        ImmutableEntry result = repository.create(entry);

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(validationPhases));
    }

    @Test
    void entryWithConversionsGetsGeneratedValidationAndConversionPhases() {
        ImmutableEntry result = repository.create(entry.withConversions(conversion));

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(Streams.concat(validationPhases, conversionPhases).toList()));
    }

    @NotNull
    private ImmutableTask withoutGeneratedValues(Task p) {
        return ImmutableTask.copyOf(p).withId(null).withEntryId(null).withCreated(null);
    }
}
