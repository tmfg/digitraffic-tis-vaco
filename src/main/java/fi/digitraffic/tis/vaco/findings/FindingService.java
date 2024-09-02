package fi.digitraffic.tis.vaco.findings;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.mapper.RecordMapper;
import fi.digitraffic.tis.vaco.db.repositories.FindingRepository;
import fi.digitraffic.tis.vaco.findings.model.Finding;
import fi.digitraffic.tis.vaco.findings.model.ImmutableFinding;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.db.repositories.RulesetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FindingService {

    private final FindingRepository findingRepository;

    private final RecordMapper recordMapper;

    private final RulesetRepository rulesetRepository;

    public FindingService(FindingRepository findingRepository,
                          RulesetRepository rulesetRepository,
                          RecordMapper recordMapper) {
        this.findingRepository = Objects.requireNonNull(findingRepository);
        this.rulesetRepository = Objects.requireNonNull(rulesetRepository);
        this.recordMapper = Objects.requireNonNull(recordMapper);
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

    public Map<String, Long> summarizeFindingsSeverities(Task task) {
        return findingRepository.getSeverityCounts(task);
    }

    public List<Finding> findFindingsByName(Task task, String findingName) {
        return Streams.collect(findingRepository.findFindingsByName(task.id(), findingName), recordMapper::toFinding);
    }
}
