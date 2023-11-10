package fi.digitraffic.tis.vaco.queuehandler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.conversion.ConversionService;
import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.ruleset.RulesetService;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.validation.RulesetSubmissionService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class QueueHandlerRepositoryTests extends SpringBootIntegrationTestBase {

    @Autowired
    private QueueHandlerRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RulesetService rulesetService;

    @Autowired
    private OrganizationService organizationService;

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

        // NOTE: These priorities were originally set when current priority ordering didn't exist, which is why they're
        //       misordered. Move along, nothing to see here.
        validationTasks = entryId -> List.of(
            ImmutableTask.of(entryId, DownloadRule.DOWNLOAD_SUBTASK, 100),
            ImmutableTask.of(entryId, RulesetSubmissionService.VALIDATE_TASK, 200)
        );
        conversionTasks = entryId -> Streams.mapIndexed(
                ConversionService.ALL_SUBTASKS,
                (i, p) -> ImmutableTask.of(entryId, p, 200 + i))
            .toList();
        conversionRuleTasks = entryId -> List.of(ImmutableTask.of(entryId, conversion.name(), 101));
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
    void entryWithoutValidationsAndConversionsGetsNoTasks() {
        Entry result = repository.create(entry);

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(List.of()));
    }

    @Test
    void entryWithConversionsGetsGeneratedValidationAndConversionTasks() {
        // matching Ruleset must exist for the task to be generated
        Optional<Organization> org = organizationService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID);
        assertThat(org.isPresent(), equalTo(true));
        Ruleset bananasRule = rulesetService.createRuleset(
            ImmutableRuleset.of(
                    org.get().id(),
                    conversion.name(),
                    "This test data is bananas!",
                    Category.GENERIC,
                    Type.CONVERSION_SYNTAX,
                    TransitDataFormat.forField(entry.format()))
                .withDependencies(DownloadRule.DOWNLOAD_SUBTASK, ConversionService.CONVERT_TASK));

        Entry result = repository.create(entry.withConversions(conversion));

        assertThat(
            Streams.map(result.tasks(), this::withoutGeneratedValues).toList(),
            equalTo(List.of(
                ImmutableTask.of(result.id(), DownloadRule.DOWNLOAD_SUBTASK, 100),
                ImmutableTask.of(result.id(), ConversionService.CONVERT_TASK, 200),
                ImmutableTask.of(result.id(), "bananas", 201)
            )));
    }

    @NotNull
    private ImmutableTask withoutGeneratedValues(Task p) {
        return ImmutableTask.copyOf(p).withId(null).withCreated(null);
    }
}
