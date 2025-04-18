package fi.digitraffic.tis.vaco.entries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.model.RulesetRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.process.TaskService;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.ConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableConversionInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableValidationInput;
import fi.digitraffic.tis.vaco.queuehandler.model.ValidationInput;
import fi.digitraffic.tis.vaco.rules.internal.DownloadRule;
import fi.digitraffic.tis.vaco.ruleset.model.Category;
import fi.digitraffic.tis.vaco.ruleset.model.ImmutableRuleset;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ruleset.model.TransitDataFormat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class EntryServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private EntryService entryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private TaskService taskService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyHierarchyService companyHierarchyService;

    private ImmutableEntry entry;
    private JsonNode metadata;
    private ImmutableValidationInput validation;
    private ImmutableConversionInput conversion;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        entry = TestObjects.anEntry().build();
        metadata = objectMapper.readTree("{\"metadata\":true}");
        validation = ImmutableValidationInput.of("ananas");
        conversion = ImmutableConversionInput.of("bananas");
    }

    @Test
    void createsCompleteEntries() {

        Entry result = entryService.create(entry.withEtag("etag")
                .withMetadata(metadata)
                .withConversions(conversion)
                .withValidations(validation))
            .get();

        assertAll(
            () -> assertThat(result.format(), equalTo("gtfs")),
            () -> assertThat(result.etag(), equalTo("etag")),
            () -> assertThat(result.metadata(), equalTo(metadata)),
            () -> assertThat(result.url(), equalTo("https://example.fi")),
            () -> assertThat(result.businessId(), equalTo(Constants.FINTRAFFIC_BUSINESS_ID)),
            () -> assertThat(Streams.collect(result.validations(), ValidationInput::name), equalTo(List.of(validation.name()))),
            () -> assertThat(Streams.collect(result.conversions(), ConversionInput::name), equalTo(List.of(conversion.name())))
        );
    }

    @Test
    void entryWithoutValidationsAndConversionsGetsNoTasks() {
        Entry result = entryService.create(entry).get();

        assertThat(
            Streams.map(taskService.findTasks(result), this::withoutGeneratedValues).toList(),
            equalTo(List.of()));
    }

    @Test
    void entryWithConversionsGetsGeneratedValidationAndConversionTasks() {
        // matching Ruleset must exist for the task to be generated
        Optional<CompanyRecord> company = companyRepository.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID);
        assertThat(company.isPresent(), equalTo(true));
        Ruleset ruleset = ImmutableRuleset.of(
                conversion.name(),
                "This test data is bananas!",
                Category.GENERIC,
                RulesetType.CONVERSION_SYNTAX,
                TransitDataFormat.forField(entry.format()))
            .withBeforeDependencies(DownloadRule.PREPARE_DOWNLOAD_TASK);
        RulesetRecord bananasRule = rulesetRepository.createRuleset(company.get(), ruleset);

        // TODO: should be PersistentEntry for better assertions
        Entry result = entryService.create(entry.withConversions(conversion)).get();

        assertThat(
            Streams.map(taskService.findTasks(result), this::withoutGeneratedValues).toList(),
            equalTo(List.of(
                ImmutableTask.of(DownloadRule.PREPARE_DOWNLOAD_TASK, 100),
                ImmutableTask.of("bananas", 200)
            )));
    }

    @NotNull
    private ImmutableTask withoutGeneratedValues(Task p) {
        return ImmutableTask.copyOf(p).withId(null).withPublicId(null).withCreated(null);
    }
}
