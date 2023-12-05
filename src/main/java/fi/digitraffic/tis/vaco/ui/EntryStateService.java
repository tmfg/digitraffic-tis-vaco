package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.vaco.errorhandling.ImmutableError;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.ruleset.model.Ruleset;
import fi.digitraffic.tis.vaco.ui.model.ImmutableItemCounter;
import fi.digitraffic.tis.vaco.ui.model.ImmutableNotice;
import fi.digitraffic.tis.vaco.ui.model.ImmutableValidationReport;
import fi.digitraffic.tis.vaco.ui.model.ValidationReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EntryStateService {

    private final EntryStateRepository entryStateRepository;

    public EntryStateService(EntryStateRepository entryStateRepository) {
        this.entryStateRepository = entryStateRepository;
    }

    public ValidationReport getValidationReport(Task task, Ruleset rule) {
        List<ImmutableItemCounter> counters = entryStateRepository.findValidationRuleCounters(task.id());

        List<ImmutableNotice> notices = entryStateRepository.findValidationRuleNotices(task.id());
        if (notices.isEmpty()) {
            return null;
        }
        List<ImmutableNotice> noticesWithInstances = new ArrayList<>();
        notices.forEach(notice -> {
            List<ImmutableError> noticeInstances = entryStateRepository.findNoticeInstances(task.id(), notice.code());
            ImmutableNotice noticeWithInstances = notice.withInstances(noticeInstances);
            noticesWithInstances.add(noticeWithInstances);
        });

        return ImmutableValidationReport.builder()
            .ruleName(task.name())
            .ruleDescription(rule.description())
            .counters(counters)
            .notices(noticesWithInstances).build();
    }
}
