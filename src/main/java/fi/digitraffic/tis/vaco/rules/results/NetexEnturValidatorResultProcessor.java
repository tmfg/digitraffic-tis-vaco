package fi.digitraffic.tis.vaco.rules.results;

import fi.digitraffic.tis.vaco.packages.PackagesService;
import fi.digitraffic.tis.vaco.process.model.Task;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.rules.RuleName;
import fi.digitraffic.tis.vaco.rules.model.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NetexEnturValidatorResultProcessor extends RuleResultProcessor implements ResultProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected NetexEnturValidatorResultProcessor(PackagesService packagesService) {
        super(packagesService);
    }

    @Override
    public boolean processResults(ResultMessage resultMessage, Entry entry, Task task) {
        logger.info("Processing result from {} for entry {}/task {}", RuleName.NETEX_ENTUR_1_0_1, entry.publicId(), task.name());
        createOutputPackages(resultMessage, entry, task);
        return true;
    }
}
