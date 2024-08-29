package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.db.model.ContextRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.summary.GtfsInputSummaryService;
import fi.digitraffic.tis.vaco.summary.model.RendererType;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.model.AggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import fi.digitraffic.tis.vaco.ui.model.TaskReport;
import fi.digitraffic.tis.vaco.ui.model.summary.Card;
import fi.digitraffic.tis.vaco.ui.model.summary.LabelValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntryStateServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    EntryStateService entryStateService;

    @Autowired
    private GtfsInputSummaryService gtfsInputSummaryService;

    @Autowired
    EntryRepository entryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Autowired
    RecordMapper recordMapper;

    private Path inputPath;

    private Task task;

    private EntryRecord entry;

    private Ruleset rule;

    @BeforeEach
    void setUp() throws URISyntaxException {
        ImmutableEntry entryToCreate = TestObjects.anEntry("gtfs").build();
        entry = entryRepository.create(Optional.empty(), entryToCreate).get();
        taskRepository.createTasks(entry, List.of(ImmutableTask.of(RuleName.GTFS_CANONICAL, 1)));
        task = taskRepository.findTask(entry.id(), RuleName.GTFS_CANONICAL).get();
        rule = recordMapper.toRuleset(rulesetRepository.findByName(RuleName.GTFS_CANONICAL).get());

        inputPath = Path.of(ClassLoader.getSystemResource("summary/211_gtfs.zip").toURI());

        Finding criticalFinding = TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.CRITICAL)
            .message("code1")
            .source(RuleName.GTFS_CANONICAL)
            .build();
        findingRepository.create(criticalFinding);
        findingRepository.create(criticalFinding);
        findingRepository.create(TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.CRITICAL)
            .message("code2")
            .source(RuleName.GTFS_CANONICAL)
            .build());

        Finding errorFinding = TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.ERROR)
            .message("code3")
            .source(RuleName.GTFS_CANONICAL)
            .raw("Yay".getBytes())
            .build();
        findingRepository.create(errorFinding);
        findingRepository.create(errorFinding);

        Finding warningFinding = TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.WARNING)
            .message("code4")
            .source(RuleName.GTFS_CANONICAL)
            .build();
        findingRepository.create(warningFinding);
        findingRepository.create(warningFinding);
        findingRepository.create(TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.WARNING)
            .message("code5")
            .source(RuleName.GTFS_CANONICAL)
            .build());

        Finding infoFinding = TestObjects.aFinding(entry.publicId(), rule.id(), task.id())
            .severity(FindingSeverity.INFO)
            .message("code6")
            .source(RuleName.GTFS_CANONICAL)
            .build();
        findingRepository.create(infoFinding);
        findingRepository.create(infoFinding);
    }

    @Test
    void testGetRuleReport() {
        Map<String, Ruleset> rulesetMap = new HashMap<>();
        rulesetMap.put(task.name(), rule);
        Optional<ContextRecord> context = Optional.empty(); // TODO: add actual context
        TaskReport ruleReport = entryStateService.getTaskReport(task, recordMapper.toEntryBuilder(entry, context).build(), rulesetMap);
        assertThat(ruleReport.name(), equalTo(rule.identifyingName()));
        assertThat(ruleReport.description(), equalTo(rule.description()));

        // Order matters
        ItemCounter allCounter = ruleReport.findingCounters().get(0);
        Assertions.assertNotNull(allCounter);
        assertThat(allCounter.total(), equalTo(10L));
        ItemCounter criticalCounter = ruleReport.findingCounters().get(1);
        Assertions.assertNotNull(criticalCounter);
        assertThat(criticalCounter.total(), equalTo(3L));
        ItemCounter errorCounter = ruleReport.findingCounters().get(2);
        Assertions.assertNotNull(errorCounter);
        assertThat(errorCounter.total(), equalTo(2L));
        ItemCounter warningCounter = ruleReport.findingCounters().get(3);
        Assertions.assertNotNull(warningCounter);
        assertThat(warningCounter.total(), equalTo(3L));
        ItemCounter infoCounter = ruleReport.findingCounters().get(4);
        Assertions.assertNotNull(infoCounter);
        assertThat(infoCounter.total(), equalTo(2L));

        assertThat(ruleReport.findings().size(), equalTo(6));
        AggregatedFinding code1 = ruleReport.findings().get(0);
        assertThat(code1.severity(), equalTo(FindingSeverity.CRITICAL));

        AggregatedFinding code2 = ruleReport.findings().get(1);
        assertThat(code2.severity(), equalTo(FindingSeverity.CRITICAL));

        AggregatedFinding code3 = ruleReport.findings().get(2);
        assertThat(code3.code(), equalTo("code3"));
        assertThat(code3.severity(), equalTo(FindingSeverity.ERROR));
        assertThat(code3.total(), equalTo(2));
        assertThat(code3.findings().size(), equalTo(2));
        assertNotNull(code3.findings().get(0).raw());

        AggregatedFinding code4 = ruleReport.findings().get(3);
        assertThat(code4.severity(), equalTo(FindingSeverity.WARNING));

        AggregatedFinding code5 = ruleReport.findings().get(4);
        assertThat(code5.severity(), equalTo(FindingSeverity.WARNING));

        AggregatedFinding code6 = ruleReport.findings().get(5);
        assertThat(code6.code(), equalTo("code6"));
        assertThat(code6.severity(), equalTo(FindingSeverity.INFO));
        assertThat(code6.total(), equalTo(2));
        assertThat(code6.findings().size(), equalTo(2));
    }

    @Test
    void testGettingSummaries() {
        assertDoesNotThrow(() -> gtfsInputSummaryService.generateGtfsInputSummaries(inputPath, task.id()));
        Optional<ContextRecord> context = Optional.empty(); // TODO: add actual context
        List<Summary> summaries = entryStateService.getTaskSummaries(recordMapper.toEntryBuilder(entry, context).build());
        assertThat(summaries.size(), equalTo(5));
        // Order matters
        Summary agencies = summaries.get(0);
        assertThat(agencies.name(), equalTo("agencies"));
        assertThat(agencies.rendererType(), equalTo(RendererType.CARD));
        assertThat(((List<?>) agencies.content()).size(), equalTo(6));
        Card agency = (Card) ((List<?>) agencies.content()).get(0);
        assertThat(agency.title(), equalTo("Oulaisten Liikenne Oy"));
        LabelValuePair website = (LabelValuePair) ((List<?>) agency.content()).get(0);
        assertThat(website.label(), equalTo("website"));
        assertThat(website.value(), equalTo("https://www.oulaistenliikenne.fi/"));
        LabelValuePair email = (LabelValuePair) ((List<?>) agency.content()).get(1);
        assertThat(email.label(), equalTo("email"));
        assertThat(email.value(), equalTo("info@oulaistenliikenne.fi"));

        Summary feedInfo = summaries.get(1);
        assertThat(feedInfo.name(), equalTo("feedInfo"));
        assertThat(feedInfo.rendererType(), equalTo(RendererType.TABULAR));
        assertThat(((List<?>) feedInfo.content()).size(), equalTo(5));
        LabelValuePair publisher = (LabelValuePair) ((List<?>) feedInfo.content()).get(0);
        assertThat(publisher.label(), equalTo("publisherName"));
        assertThat(publisher.value(), equalTo("Kajaani"));
        LabelValuePair publisherName = (LabelValuePair) ((List<?>) feedInfo.content()).get(0);
        assertThat(publisherName.label(), equalTo("publisherName"));
        assertThat(publisherName.value(), equalTo("Kajaani"));
        LabelValuePair publisherUrl = (LabelValuePair) ((List<?>) feedInfo.content()).get(1);
        assertThat(publisherUrl.label(), equalTo("publisherUrl"));
        assertThat(publisherUrl.value(), equalTo("http://kajaani.fi"));
        LabelValuePair feedLanguage = (LabelValuePair) ((List<?>) feedInfo.content()).get(2);
        assertThat(feedLanguage.label(), equalTo("feedLanguage"));
        assertThat(feedLanguage.value(), equalTo("fi"));
        LabelValuePair feedStartDate = (LabelValuePair) ((List<?>) feedInfo.content()).get(3);
        assertThat(feedStartDate.label(), equalTo("feedStartsDate"));
        assertThat(feedStartDate.value(), equalTo("20180808"));
        LabelValuePair feedEndDate = (LabelValuePair) ((List<?>) feedInfo.content()).get(4);
        assertThat(feedEndDate.label(), equalTo("feedEndDate"));
        assertThat(feedEndDate.value(), equalTo("20261231"));

        Summary files = summaries.get(2);
        assertThat(files.name(), equalTo("files"));
        assertThat(files.rendererType(), equalTo(RendererType.LIST));
        assertThat(((List<?>) files.content()).size(), equalTo(13));

        Summary counts = summaries.get(3);
        assertThat(counts.name(), equalTo("counts"));
        assertThat(counts.rendererType(), equalTo(RendererType.LIST));
        assertThat(((List<?>) counts.content()).size(), equalTo(6));

        Summary components = summaries.get(4);
        assertThat(components.name(), equalTo("components"));
        assertThat(components.rendererType(), equalTo(RendererType.LIST));
        assertThat(((List<?>) components.content()).size(), equalTo(10));
    }
}
