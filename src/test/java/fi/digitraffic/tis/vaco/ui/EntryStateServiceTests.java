package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.FindingRecord;
import fi.digitraffic.tis.vaco.db.model.ImmutableFindingRecord;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.db.repositories.SummaryRepository;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.packages.model.ImmutablePackage;
import fi.digitraffic.tis.vaco.process.model.ImmutableTask;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.RulesetType;
import fi.digitraffic.tis.vaco.ui.model.AggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import fi.digitraffic.tis.vaco.ui.model.TaskReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EntryStateServiceTests {

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private RecordMapper recordMapper;

    private EntryStateService entryStateService;

    @BeforeEach
    void setUp() {
        VacoProperties vacoProperties = TestObjects.vacoProperties();
        entryStateService = new EntryStateService(findingRepository, summaryRepository, vacoProperties, recordMapper);
    }

    @Test
    void createsOrderedCountersAndGroupsFindingsByCaseInsensitiveMessage() {
        Task task = ImmutableTask.of("test-rule", 10).withId(42L);
        FindingRecord firstDuplicate = findingRecord(1, task.id(), "Duplicate_Code", FindingSeverity.CRITICAL);
        FindingRecord secondDuplicate = findingRecord(2, task.id(), "duplicate_code", FindingSeverity.CRITICAL);
        FindingRecord error = findingRecord(3, task.id(), "other_code", FindingSeverity.ERROR);
        given(findingRepository.findFindingsByTaskId(task.id())).willReturn(List.of(error, firstDuplicate, secondDuplicate));

        Finding mappedFirstDuplicate = TestObjects.aFinding(1, task.id())
            .source(task.name())
            .message("Duplicate_Code")
            .severity(FindingSeverity.CRITICAL)
            .build();
        Finding mappedSecondDuplicate = TestObjects.aFinding(1, task.id())
            .source(task.name())
            .message("duplicate_code")
            .severity(FindingSeverity.CRITICAL)
            .build();
        Finding mappedError = TestObjects.aFinding(1, task.id())
            .source(task.name())
            .message("other_code")
            .severity(FindingSeverity.ERROR)
            .build();
        given(recordMapper.toFinding(firstDuplicate)).willReturn(mappedFirstDuplicate);
        given(recordMapper.toFinding(secondDuplicate)).willReturn(mappedSecondDuplicate);
        given(recordMapper.toFinding(error)).willReturn(mappedError);

        Ruleset rule = mock(Ruleset.class);
        given(rule.description()).willReturn("Test rule description");
        given(rule.type()).willReturn(RulesetType.VALIDATION_SYNTAX);

        Entry entry = TestObjects.anEntry()
            .packages(List.of(
                ImmutablePackage.of(task, "included-package", "/included.zip"),
                ImmutablePackage.of(ImmutableTask.of("another-rule", 20).withId(43L), "other-package", "/other.zip")))
            .build();
        TaskReport report = entryStateService.getTaskReport(task, entry, Map.of(task.name(), rule));

        assertEquals(task.name(), report.name());
        assertEquals("Test rule description", report.description());
        assertEquals(RulesetType.VALIDATION_SYNTAX, report.type());
        assertEquals(List.of("ALL", FindingSeverity.CRITICAL, FindingSeverity.ERROR),
            report.findingCounters().stream().map(ItemCounter::name).toList());
        assertEquals(List.of(3L, 2L, 1L), report.findingCounters().stream().map(ItemCounter::total).toList());
        assertEquals(2, report.findings().size());

        AggregatedFinding duplicate = report.findings().getFirst();
        assertEquals("duplicate_code", duplicate.code());
        assertEquals(FindingSeverity.CRITICAL, duplicate.severity());
        assertEquals(2, duplicate.total());
        assertEquals(List.of(mappedFirstDuplicate, mappedSecondDuplicate), duplicate.findings());

        AggregatedFinding errorFinding = report.findings().get(1);
        assertEquals("other_code", errorFinding.code());
        assertEquals(FindingSeverity.ERROR, errorFinding.severity());
        assertEquals(1, errorFinding.total());

        assertEquals(1, report.packages().size());
        assertEquals("included-package", report.packages().getFirst().data().name());
        assertEquals(RequestMethod.GET, report.packages().getFirst().links().get("refs").get("self").method());
        verify(findingRepository).findFindingsByTaskId(task.id());
    }

    @Test
    void returnsEmptyReportWithInternalTypeWhenThereAreNoFindingsOrMatchingRuleset() {
        Task task = ImmutableTask.of("unregistered-rule", 10).withId(99L);
        Entry entry = TestObjects.anEntry().build();
        given(findingRepository.findFindingsByTaskId(task.id())).willReturn(List.of());

        TaskReport report = entryStateService.getTaskReport(task, entry, Map.of());

        assertNull(report.description());
        assertEquals(RulesetType.INTERNAL, report.type());
        assertEquals(1, report.findingCounters().size());
        assertEquals("ALL", report.findingCounters().getFirst().name());
        assertEquals(0L, report.findingCounters().getFirst().total());
        assertEquals(List.of(), report.findings());
        assertEquals(List.of(), report.packages());
    }

    private static FindingRecord findingRecord(long id, long taskId, String message, String severity) {
        return ImmutableFindingRecord.builder()
            .id(id)
            .publicId("finding-" + id)
            .taskId(taskId)
            .message(message)
            .severity(severity)
            .source("test-rule")
            .build();
    }
}
