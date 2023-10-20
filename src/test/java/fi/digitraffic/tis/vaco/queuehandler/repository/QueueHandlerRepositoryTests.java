package fi.digitraffic.tis.vaco.queuehandler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
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
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class QueueHandlerRepositoryTests extends SpringBootIntegrationTestBase {

    @Autowired
    private QueueHandlerRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private ImmutableEntry entry;
    private JsonNode metadata;
    private ImmutableValidationInput validation;
    private ImmutableConversionInput conversion;

    private Function<Long, List<ImmutableTask>> validationTasks;
    private Function<Long, List<ImmutableTask>> conversionTasks;
    private Function<Long, List<ImmutableTask>> conversionRuleTasks;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        entry = ImmutableEntry.of("gtfs", "www.example.fi", Constants.FINTRAFFIC_BUSINESS_ID);
        metadata = objectMapper.readTree("{\"metadata\":true}");
        validation = ImmutableValidationInput.of("ananas");
        conversion = ImmutableConversionInput.of("bananas");

        validationTasks = entryId -> Streams.mapIndexed(
                ValidationService.ALL_SUBTASKS,
                (i, p) -> ImmutableTask.of(entryId, p, 100 + i))
            .toList();
        conversionTasks = entryId -> Streams.mapIndexed(
                ConversionService.ALL_SUBTASKS,
                (i, p) -> ImmutableTask.of(entryId, p, 200 + i))
            .toList();
        conversionRuleTasks = entryId -> Streams.mapIndexed(
                List.of(conversion),
                (i, p) -> ImmutableTask.of(entryId, p.name(), 300 + i))
            .toList();
    }

    @Test
    void createsCompleteEntries() {
        Entry result = repository.create(entry.withEtag("etag")
            .withMetadata(metadata)
            .withConversions(ImmutableConversionInput.of("bananas"))
            .withValidations(validation));

        assertAll(
            () -> assertThat(result.format(), equalTo("gtfs")),
            () -> assertThat(result.etag(), equalTo("etag")),
            () -> assertThat(result.metadata(), equalTo(metadata)),
            () -> assertThat(result.url(), equalTo("www.example.fi")),
            () -> assertThat(result.businessId(), equalTo(Constants.FINTRAFFIC_BUSINESS_ID)),
            () -> assertThat(Streams.collect(result.validations(), ValidationInput::name), equalTo(List.of(validation.name()))),
            () -> assertThat(Streams.collect(result.conversions(), ConversionInput::name), equalTo(List.of(conversion.name())))
        );
    }

    @Test
    void entryWithoutValidationsAndConversionsGetsGeneratedValidationTasks() {
        Entry result = repository.create(entry);

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(validationTasks.apply(result.id())));
    }

    @Test
    void entryWithConversionsGetsGeneratedValidationAndConversionTasks() {
        Entry result = repository.create(entry.withConversions(conversion));

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(Streams.concat(validationTasks.apply(result.id()),
                    conversionTasks.apply(result.id()),
                    conversionRuleTasks.apply(result.id()))
                .toList()));
    }

    @NotNull
    private ImmutableTask withoutGeneratedValues(Task p) {
        return ImmutableTask.copyOf(p).withId(null).withCreated(null);
    }
}
