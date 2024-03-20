package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.api.model.Link;
import fi.digitraffic.tis.vaco.api.model.Resource;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.findings.FindingRepository;
import fi.digitraffic.tis.vaco.findings.FindingSeverity;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.summary.SummaryRepository;
import fi.digitraffic.tis.vaco.summary.model.Summary;
import fi.digitraffic.tis.vaco.summary.model.gtfs.Agency;
import fi.digitraffic.tis.vaco.summary.model.gtfs.FeedInfo;
import fi.digitraffic.tis.vaco.ui.model.AggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ImmutableAggregatedFinding;
import fi.digitraffic.tis.vaco.ui.model.ImmutableItemCounter;
import fi.digitraffic.tis.vaco.ui.model.ImmutableRuleReport;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import fi.digitraffic.tis.vaco.ui.model.RuleReport;
import fi.digitraffic.tis.vaco.ui.model.summary.ImmutableCard;
import fi.digitraffic.tis.vaco.ui.model.summary.ImmutableLabelValuePair;
import fi.digitraffic.tis.vaco.ui.model.summary.LabelValuePair;
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
import java.util.stream.Collectors;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.fromMethodCall;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@Service
public class EntryStateService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FindingRepository findingRepository;
    private final SummaryRepository summaryRepository;
    private final VacoProperties vacoProperties;

    public EntryStateService(FindingRepository findingRepository, SummaryRepository summaryRepository, VacoProperties vacoProperties) {
        this.findingRepository = Objects.requireNonNull(findingRepository);
        this.summaryRepository = Objects.requireNonNull(summaryRepository);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    public RuleReport getRuleReport(Task task, Entry entry, Map<String, Ruleset> rulesets) {
        List<Finding> allFindings = findingRepository.findFindingsByTaskId(task.id());

        Map<String, Long> findingCountersBySeverity =
            allFindings.stream().collect(Collectors.groupingBy(f -> f.severity().toUpperCase(),
                Collectors.counting()));
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

        Map<String, List<Finding>> findingsByMessage =
            allFindings.stream().collect(Collectors.groupingBy(f -> f.message().toLowerCase()));
        List<AggregatedFinding> aggregatedWithFindings = new ArrayList<>();
        findingsByMessage.forEach((code, findings) -> aggregatedWithFindings.add(ImmutableAggregatedFinding.builder()
                .code(code)
                .severity(findings.get(0).severity())
                .total(findings.size())
                .findings(findings)
            .build()));
        // aggregatedWithFindings should also be ordered from highest to lowest severity:
        aggregatedWithFindings.sort(new SortFindingsBySeverity());

        List<Package> taskPackages = entry.packages() != null
            ? Streams.filter(entry.packages(), p -> p.taskId().equals(task.id())).toList()
            : List.of();
        Ruleset rule = rulesets.get(task.name());

        return ImmutableRuleReport.builder()
            .ruleName(task.name())
            .ruleDescription(rule.description())
            .ruleType(rule.type())
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

    public static List<ImmutableCard> getAgencyCardUiContent(List<Agency> agencies) {
        return Streams.map(agencies,
            agency -> ImmutableCard.builder()
                .title(agency.getAgencyName())
                .content(getAgencyCard(agency))
                .build()).toList();
    }

    private static List<LabelValuePair> getAgencyCard(Agency agency) {
        List<LabelValuePair> agencyCardContent = new ArrayList<>();
        agencyCardContent.add(ImmutableLabelValuePair.builder()
            .label("website")
            .value(agency.getAgencyUrl())
            .build());
        agencyCardContent.add(ImmutableLabelValuePair.builder()
            .label("email")
            .value(agency.getAgencyEmail())
            .build());
        agencyCardContent.add(ImmutableLabelValuePair.builder()
            .label("phone")
            .value(agency.getAgencyPhone())
            .build());
        return agencyCardContent;
    }

    public static List<LabelValuePair> getFeedInfoUiContent(FeedInfo feedInfo) {
        List<LabelValuePair> feedInfoContent = new ArrayList<>();
        feedInfoContent.add(ImmutableLabelValuePair.builder()
            .label("publisherName")
            .value(feedInfo.getFeedPublisherName())
            .build());
        feedInfoContent.add(ImmutableLabelValuePair.builder()
            .label("publisherUrl")
            .value(feedInfo.getFeedPublisherUrl())
            .build());
        feedInfoContent.add(ImmutableLabelValuePair.builder()
            .label("feedLanguage")
            .value(feedInfo.getFeedLang())
            .build());
        feedInfoContent.add(ImmutableLabelValuePair.builder()
            .label("feedStartsDate")
            .value(feedInfo.getFeedStartDate())
            .build());
        feedInfoContent.add(ImmutableLabelValuePair.builder()
            .label("feedEndDate")
            .value(feedInfo.getFeedEndDate())
            .build());
        return feedInfoContent;
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
