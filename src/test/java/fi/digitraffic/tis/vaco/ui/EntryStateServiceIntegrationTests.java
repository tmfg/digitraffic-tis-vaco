package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.EntryRecord;
import fi.digitraffic.tis.vaco.db.repositories.EntryRepository;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import fi.digitraffic.tis.vaco.db.repositories.TaskRepository;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import fi.digitraffic.tis.vaco.rules.RuleName;
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

    private EntryRecord entryRecord;
    private Entry entry;

    private Ruleset rule;

    @BeforeEach
    void setUp() throws URISyntaxException {
        ImmutableEntry entryToCreate = TestObjects.anEntry("gtfs").build();
        entryRecord = entryRepository.create(Optional.empty(), entryToCreate).get();
        taskRepository.createTasks(entryRecord, List.of(ImmutableTask.of(RuleName.GTFS_CANONICAL, 1)));
        task = taskRepository.findTask(entryRecord.id(), RuleName.GTFS_CANONICAL).get();
        rule = recordMapper.toRuleset(rulesetRepository.findByName(RuleName.GTFS_CANONICAL).get());

        entry = recordMapper.toEntryBuilder(entryRecord, Optional.empty()).build();

        inputPath = Path.of(ClassLoader.getSystemResource("summary/211_gtfs.zip").toURI());

        Finding criticalFinding = TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.CRITICAL)
            .message("code1")
            .source(RuleName.GTFS_CANONICAL)
            .build();
        findingRepository.create(criticalFinding);
        findingRepository.create(criticalFinding);
        findingRepository.create(TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.CRITICAL)
            .message("code2")
            .source(RuleName.GTFS_CANONICAL)
            .build());

        Finding errorFinding = TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.ERROR)
            .message("code3")
            .source(RuleName.GTFS_CANONICAL)
            .raw("Yay".getBytes())
            .build();
        findingRepository.create(errorFinding);
        findingRepository.create(errorFinding);

        Finding warningFinding = TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.WARNING)
            .message("code4")
            .source(RuleName.GTFS_CANONICAL)
            .build();
        findingRepository.create(warningFinding);
        findingRepository.create(warningFinding);
        findingRepository.create(TestObjects.aFinding(rule.id(), task.id())
            .severity(FindingSeverity.WARNING)
            .message("code5")
            .source(RuleName.GTFS_CANONICAL)
            .build());

        Finding infoFinding = TestObjects.aFinding(rule.id(), task.id())
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
        TaskReport ruleReport = entryStateService.getTaskReport(task, entry, rulesetMap);
        assertThat(ruleReport.name(), equalTo(rule.identifyingName()));
        assertThat(ruleReport.description(), equalTo(rule.description()));

        // Order matters
        ItemCounter allCounter = ruleReport.findingCounters().getFirst();
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
        AggregatedFinding code1 = ruleReport.findings().getFirst();
        assertThat(code1.severity(), equalTo(FindingSeverity.CRITICAL));

        AggregatedFinding code2 = ruleReport.findings().get(1);
        assertThat(code2.severity(), equalTo(FindingSeverity.CRITICAL));

        AggregatedFinding code3 = ruleReport.findings().get(2);
        assertThat(code3.code(), equalTo("code3"));
        assertThat(code3.severity(), equalTo(FindingSeverity.ERROR));
        assertThat(code3.total(), equalTo(2));
        assertThat(code3.findings().size(), equalTo(2));
        assertNotNull(code3.findings().getFirst().raw());

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
        assertDoesNotThrow(() -> gtfsInputSummaryService.generateGtfsInputSummaries(entry, task, inputPath));
        List<Summary> summaries = entryStateService.getTaskSummaries(entry);
        assertThat(summaries.size(), equalTo(5));
        // Order matters // TODO: LinkedHashMap
        Summary agenciesSummary = summaries.getFirst();
        assertThat(agenciesSummary.name(), equalTo("agencies"));
        assertThat(agenciesSummary.rendererType(), equalTo(RendererType.CARD));

        List<Card> agencies = (List<Card>) agenciesSummary.content();
        assertThat(agencies.size(), equalTo(6));

        assertAgencyCard(agencies.get(0), "Oulaisten Liikenne Oy", "https://www.oulaistenliikenne.fi/", null, "info@oulaistenliikenne.fi");
        assertAgencyCard(agencies.get(1), "Vekka Group Oy", "http://www.vekkaliikenne.fi", null, "");
        assertAgencyCard(agencies.get(2), "KYMEN CHARTERLINE OY", "http://www.bussimatkatoimisto.fi/", null, "");
        assertAgencyCard(agencies.get(3), "Liikenne M. Heikura Oy", "http://www.google.fi", null, "");
        assertAgencyCard(agencies.get(4), "Kainuun Tilausliikenne P. Jääskeläinen Ky", "http://www.kainuuntilausliikenne.fi/", null, "");
        assertAgencyCard(agencies.get(5), "Tilausliikenne Kuvaja Oy", "http://www.tilausliikennekuvaja.fi", null, "");

        Summary feedInfosSummary = summaries.get(1);
        assertThat(feedInfosSummary.name(), equalTo("feedInfo"));
        assertThat(feedInfosSummary.rendererType(), equalTo(RendererType.TABULAR));

        List<LabelValuePair> feedInfos = (List<LabelValuePair>) feedInfosSummary.content();
        assertThat(feedInfos.size(), equalTo(5));

        assertFeedInfoCard(feedInfos, "Kajaani", "http://kajaani.fi", "fi", "20180808", "20261231");

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

    private static void assertAgencyCard(Card card, String title, String website, String phone, String email) {
        assertThat(card.title(), equalTo(title));
        List<LabelValuePair> content = (List<LabelValuePair>) card.content();
        assertLabelValuePair(content.get(0), "website", website);
        assertLabelValuePair(content.get(1), "email", email);
        assertLabelValuePair(content.get(2), "phone", phone);
    }

    private static void assertFeedInfoCard(List<LabelValuePair> feedInfos, String publisherName, String publisherUrl, String language, String startDate, String endDate) {
        assertLabelValuePair(feedInfos.get(0), "publisherName", publisherName);
        assertLabelValuePair(feedInfos.get(1), "publisherUrl", publisherUrl);
        assertLabelValuePair(feedInfos.get(2), "feedLanguage", language);
        assertLabelValuePair(feedInfos.get(3), "feedStartsDate", startDate);
        assertLabelValuePair(feedInfos.get(4), "feedEndDate", endDate);
    }

    private static void assertLabelValuePair(LabelValuePair pair, String expectedLabel, String expectedValue) {
        assertThat("Unexpected label for " + pair, pair.label(), equalTo(expectedLabel));
        assertThat("Unexpected value for " + pair, pair.value(), equalTo(expectedValue));
    }
}
