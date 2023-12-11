package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.utilities.dto.Link;
import fi.digitraffic.tis.utilities.dto.Resource;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import fi.digitraffic.tis.vaco.errorhandling.Error;
import fi.digitraffic.tis.vaco.packages.PackagesController;
import fi.digitraffic.tis.vaco.packages.model.Package;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ui.model.ImmutableNotice;
import fi.digitraffic.tis.vaco.ui.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.ui.model.ItemCounter;
import fi.digitraffic.tis.vaco.ui.model.Notice;
import fi.digitraffic.tis.vaco.ui.model.ValidationReport;
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

    private final EntryStateRepository entryStateRepository;
    private final VacoProperties vacoProperties;

    public EntryStateService(EntryStateRepository entryStateRepository, VacoProperties vacoProperties) {
        this.entryStateRepository = Objects.requireNonNull(entryStateRepository);
        this.vacoProperties = Objects.requireNonNull(vacoProperties);
    }

    public ValidationReport getValidationReport(Task task, Ruleset rule, List<Package> taskPackages, Entry entry) {
        List<ItemCounter> counters = entryStateRepository.findValidationRuleCounters(task.id());

        List<Notice> notices = entryStateRepository.findValidationRuleNotices(task.id());
        if (notices.isEmpty()) {
            return null;
        }
        List<Notice> noticesWithInstances = new ArrayList<>();
        notices.forEach(notice -> {
            List<Error> noticeInstances = entryStateRepository.findNoticeInstances(task.id(), notice.code());
            Notice noticeWithInstances = ImmutableNotice.copyOf(notice).withInstances(noticeInstances);
            noticesWithInstances.add(noticeWithInstances);
        });

        return ImmutableValidationReport.builder()
            .ruleName(task.name())
            .ruleDescription(rule.description())
            .counters(counters)
            .packages(Streams.map(taskPackages, p -> asPackageResource(p, task, entry)).toList())
            .notices(noticesWithInstances).build();
    }

    private Resource<Package> asPackageResource(Package taskPackage, Task task, Entry entry) {
        Map<String, Map<String, Link>> links = new HashMap<>();
        links.put("refs", Map.of("self", Link.to(
            vacoProperties.baseUrl(),
            RequestMethod.GET,
            fromMethodCall(on(PackagesController.class).fetchPackage(entry.publicId(), task.name(), taskPackage.name(), null)))));
        return new Resource<>(taskPackage, null, links);
    }
}
