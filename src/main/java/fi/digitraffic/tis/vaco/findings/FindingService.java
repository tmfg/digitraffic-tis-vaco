package fi.digitraffic.tis.vaco.findings;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FindingService {
    private final FindingRepository findingRepository;
    private final RulesetRepository rulesetRepository;

    public FindingService(FindingRepository findingRepository, RulesetRepository rulesetRepository) {
        this.findingRepository = findingRepository;
        this.rulesetRepository = rulesetRepository;
    }

    public void reportFinding(Finding finding) {
        findingRepository.create(finding);
    }

    public boolean reportFindings(List<Finding> findings) {
        return findingRepository.createFindings(Streams.map(findings, e -> {
            ImmutableFinding resolve = ImmutableFinding.copyOf(e);
            if (e.rulesetId() == null) {
                resolve = resolve.withRulesetId(rulesetRepository.findByName(resolve.source()).orElseThrow().id());
            }
            return (Finding) resolve;
        }).toList());
    }

    public boolean hasErrors(Entry entry) {
        return findingRepository.hasErrors(entry);
    }

    public Map<String, Long> summarizeFindingsSeverities(Entry entry, Task task) {
        return findingRepository.getSeverityCounts(entry, task);
    }
}