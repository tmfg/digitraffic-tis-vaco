package fi.digitraffic.tis.vaco.errorhandling;

import org.springframework.stereotype.Service;

@Service
public class ErrorHandlerService {
    private final ErrorHandlerRepository errorHandlerRepository;

    public ErrorHandlerService(ErrorHandlerRepository errorHandlerRepository) {
        this.errorHandlerRepository = errorHandlerRepository;
    }

    public void reportError(Error error) {
        errorHandlerRepository.create(error);
    }
}
