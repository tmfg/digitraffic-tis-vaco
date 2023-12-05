package fi.digitraffic.tis.vaco.errorhandling;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.ruleset.RulesetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ErrorHandlerService {
    private final ErrorHandlerRepository errorHandlerRepository;
    private final RulesetRepository rulesetRepository;

    public ErrorHandlerService(ErrorHandlerRepository errorHandlerRepository, RulesetRepository rulesetRepository) {
        this.errorHandlerRepository = errorHandlerRepository;
        this.rulesetRepository = rulesetRepository;
    }

    public void reportError(Error error) {
        errorHandlerRepository.create(error);
    }

    public boolean reportErrors(List<Error> errors) {
        return errorHandlerRepository.createErrors(Streams.map(errors, e -> {
            ImmutableError resolve = ImmutableError.copyOf(e);
            if (e.rulesetId() == null) {
                resolve = resolve.withRulesetId(rulesetRepository.findByName(resolve.source()).orElseThrow().id());
            }
            return (Error) resolve;
        }).toList());
    }

    public boolean hasErrors(Entry entry) {
        return errorHandlerRepository.hasErrors(entry);
    }
}
