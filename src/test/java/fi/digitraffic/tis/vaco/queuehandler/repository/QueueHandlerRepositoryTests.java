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
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
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
            () -> assertThat(Streams.collect(result.validations(), ValidationInput::name), equalTo(List.of(validation.name()))),
            () -> assertThat(Streams.collect(result.conversions(), ConversionInput::name), equalTo(List.of(conversion.name())))
        );
    }

    private List<ImmutableTask> validationTasks = Streams.mapIndexed(
        ValidationService.ALL_SUBTASKS,
        (i, p) -> ImmutableTask.of(null, p, 100 + i))
        .toList();
    private List<ImmutableTask> conversionTasks = Streams.mapIndexed(
        ConversionService.ALL_SUBTASKS,
        (i, p) -> ImmutableTask.of(null, p, 200 + i))
        .toList();

    @Test
    void entryWithoutValidationsAndConversionsGetsGeneratedValidationTasks() {
        ImmutableEntry result = repository.create(entry);

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(validationTasks));
    }

    @Test
    void entryWithConversionsGetsGeneratedValidationAndConversionTasks() {
        ImmutableEntry result = repository.create(entry.withConversions(conversion));

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(Streams.concat(validationTasks, conversionTasks).toList()));
    }

    @NotNull
    private ImmutableTask withoutGeneratedValues(Task p) {
        return ImmutableTask.copyOf(p).withId(null).withEntryId(null).withCreated(null);
    }
}
