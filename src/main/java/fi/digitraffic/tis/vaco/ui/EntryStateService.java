package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.model.FindingRecord;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.db.repositories.SummaryRepository;
import fi.digitraffic.tis.vaco.findings.model.FindingSeverity;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ruleset.model.Type;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.ui.model.AggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ImmutableAggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ImmutableItemCounter;
import fi.digitraffic.tis.vaco.ui.model.ImmutableTaskReport;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import fi.digitraffic.tis.vaco.ui.model.TaskReport;
import fi.digitraffic.tis.vaco.ui.model.summary.ImmutableCard;
import fi.digitraffic.tis.vaco.ui.model.summary.ImmutableLabelValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.fromMethodCall;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@Service
public class EntryStateService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final FindingRepository findingRepository;

    private final RecordMapper recordMapper;

    private final SummaryRepository summaryRepository;

    private final VacoProperties vacoProperties;

    public EntryStateService(FindingRepository findingRepository,
                             SummaryRepository summaryRepository,
                             VacoProperties vacoProperties,
                             RecordMapper recordMapper) {
        this.findingRepository = Objects.requireNonNull(findingRepository);
        this.summaryRepository = Objects.requireNonNull(summaryRepository);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
        this.recordMapper = Objects.requireNonNull(recordMapper);
    }

    public TaskReport getTaskReport(Task task, Entry entry, Map<String, Ruleset> rulesets) {
        List<FindingRecord> allFindings = findingRepository.findFindingsByTaskId(task.id());

        Map<String, Long> findingCountersBySeverity = allFindings.stream()
            .collect(Collectors.groupingBy(f -> f.severity().toUpperCase(), Collectors.counting()));
        // Order of severities matters for UI:
        List<String> countersBySeverityKeys = new ArrayList<>(findingCountersBySeverity.keySet());
        countersBySeverityKeys.sort(new SortStringsBySeverity());
        List<ItemCounter> counters = new ArrayList<>();
        counters.add(ImmutableItemCounter.builder()
            .name("ALL")
            .total(allFindings.size())
            .build());
        countersBySeverityKeys.forEach(severity ->
            counters.add(ImmutableItemCounter.builder()
                .name(severity)
                .total(findingCountersBySeverity.get(severity))
                .build())
        );

        Map<String, List<FindingRecord>> findingsByMessage = allFindings.stream()
            .collect(Collectors.groupingBy(f -> f.message().toLowerCase()));
        List<AggregatedFinding> aggregatedWithFindings = new ArrayList<>();
        findingsByMessage.forEach((code, findings) -> aggregatedWithFindings.add(ImmutableAggregatedFinding.builder()
                .code(code)
                .severity(findings.getFirst().severity())
                .total(findings.size())
                .findings(Streams.collect(findings, recordMapper::toFinding))
            .build()));
        // aggregatedWithFindings should also be ordered from highest to lowest severity:
        aggregatedWithFindings.sort(new SortFindingsBySeverity());

        List<Package> taskPackages = entry.packages() != null
            ? Streams.filter(entry.packages(), p -> p.task().id().equals(task.id())).toList()
            : List.of();
        Optional<Ruleset> rule = Optional.ofNullable(rulesets.get(task.name()));

        return ImmutableTaskReport.builder()
            .name(task.name())
            .description(rule.map(Ruleset::description).orElse(null))
            .type(rule.map(Ruleset::type).orElse(Type.INTERNAL))
            .findingCounters(counters)
            .packages(Streams.map(taskPackages, p -> asPackageResource(p, task, entry)).toList())
            .findings(aggregatedWithFindings).build();
    }

    private Resource<Package> asPackageResource(Package taskPackage, Task task, Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", Link.to(
            vacoProperties.baseUrl(),
            RequestMethod.GET,
            fromMethodCall(on(UiController.class).fetchPackage(entry.publicId(), task.name(), taskPackage.name(), null)))));
        return new Resource<>(taskPackage, null, links);
    }

    public List<Summary> getTaskSummaries(Entry entry) {
        return summaryRepository.findTaskSummaryByEntry(entry);
    }

    public static List<ImmutableCard> getAgencyCardUiContent(List<Map<String, String>> agencies) {
        return Streams.map(agencies,
            agency -> ImmutableCard.builder()
                .title(agency.get("agency_name"))
                .content(getAgencyCard(agency))
                .build()).toList();
    }

    private static List<ImmutableLabelValuePair> getAgencyCard(Map<String, String> agency) {
        return List.of(
            ImmutableLabelValuePair.of("website", agency.get("agency_url")),
            ImmutableLabelValuePair.of("email", agency.get("agency_email")),
            ImmutableLabelValuePair.of("phone", agency.get("agency_phone")));
    }

    public static List<ImmutableLabelValuePair> getFeedInfoUiContent(Map<String, String> feedInfo) {
        return List.of(ImmutableLabelValuePair.of("publisherName", feedInfo.get("feed_publisher_name")),
            ImmutableLabelValuePair.of("publisherUrl", feedInfo.get("feed_publisher_url")),
            ImmutableLabelValuePair.of("feedLanguage", feedInfo.get("feed_lang")),
            ImmutableLabelValuePair.of("feedStartsDate", feedInfo.get("feed_start_date")),  // TODO: Unmatching plural in label name
            ImmutableLabelValuePair.of("feedEndDate", feedInfo.get("feed_end_date")));
    }

    private static int severityToInt(String severity) {
        return switch (severity) {
            case FindingSeverity.CRITICAL -> 1;
            case FindingSeverity.ERROR -> 2;
            case FindingSeverity.WARNING -> 3;
            case FindingSeverity.INFO -> 4;
            default -> 5;
        };
    }

    static class SortStringsBySeverity implements Comparator<String> {
        public int compare(String s1, String s2) {
            int severity1 = severityToInt(s1);
            int severity2 = severityToInt(s2);
            return severity1 - severity2;
        }
    }

    static class SortFindingsBySeverity implements Comparator<AggregatedFinding> {
        public int compare(AggregatedFinding f1, AggregatedFinding f2) {
            int severity1 = severityToInt(f1.severity());
            int severity2 = severityToInt(f2.severity());
            return severity1 - severity2;
        }
    }
}
