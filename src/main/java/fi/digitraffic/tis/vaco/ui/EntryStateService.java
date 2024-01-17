package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.findings.Finding;
import fi.digitraffic.tis.vaco.packages.PackagesController;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.fromMethodCall;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

@Service
public class EntryStateService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final EntryStateRepository entryStateRepository;
    private final SummaryRepository summaryRepository;
    private final VacoProperties vacoProperties;

    public EntryStateService(EntryStateRepository entryStateRepository, SummaryRepository summaryRepository, VacoProperties vacoProperties) {
        this.entryStateRepository = Objects.requireNonNull(entryStateRepository);
        this.summaryRepository = Objects.requireNonNull(summaryRepository);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    public RuleReport getRuleReport(Task task, Entry entry, Map<String, Ruleset> rulesets) {
        List<ItemCounter> counters = entryStateRepository.findFindingCounters(task.id());

        List<AggregatedFinding> aggregated = entryStateRepository.findAggregatedFindings(task.id());
        List<AggregatedFinding> aggregatedWithFindings = new ArrayList<>();
        aggregated.forEach(aggregatedFinding -> {
            List<Finding> findings = entryStateRepository.findNoticeFindings(task.id(), aggregatedFinding.code());
            AggregatedFinding aggregatedWithFindingInstances = ImmutableAggregatedFinding.copyOf(aggregatedFinding).withFindings(findings);
            aggregatedWithFindings.add(aggregatedWithFindingInstances);
        });

        List<Package> taskPackages = Streams.filter(entry.packages(), p -> p.taskId().equals(task.id())).toList();
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
            fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), task.name(), taskPackage.name(), null)))));
        return new Resource<>(taskPackage, null, links);
    }

    public List<Summary> getTaskSummaries(Entry entry) {
        return summaryRepository.findTaskSummaryByEntryId(entry.id());
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
}
