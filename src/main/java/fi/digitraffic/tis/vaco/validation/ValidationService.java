package fi.digitraffic.tis.vaco.validation;

import fi.digitraffic.tis.vaco.messaging.model.ImmutableJobDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

    public ImmutableJobDescription validate(ImmutableJobDescription jobDescription) {
        // TODO: validation process goes here :)
        LOGGER.info("Validate {}", jobDescription);
        return jobDescription;
    }
}
