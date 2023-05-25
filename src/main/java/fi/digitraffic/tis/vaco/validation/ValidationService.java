package fi.digitraffic.tis.vaco.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

    public void validate() {
        // TODO: validation process goes here :)
        LOGGER.info("Validate...");
    }
}
