package fi.digitraffic.tis.vaco.errorhandling;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ErrorHandlerService {
    private final ErrorHandlerRepository errorHandlerRepository;

    public ErrorHandlerService(ErrorHandlerRepository errorHandlerRepository) {
        this.errorHandlerRepository = errorHandlerRepository;
    }

    public void reportError(Error error) {
        errorHandlerRepository.create(error);
    }

    public boolean reportErrors(List<Error> errors) {
        return errorHandlerRepository.createErrors(errors);
    }
}
