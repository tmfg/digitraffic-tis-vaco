package fi.digitraffic.tis.vaco;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * General exception handler based on <a href="https://reflectoring.io/spring-boot-exception-handling/">Complete Guide to Exception Handling in Spring Boot</>
 */
@ControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map((e) -> "Field '%s' %s (was %s)".formatted(e.getField(), e.getDefaultMessage(), e.getRejectedValue()))
                .toList();
        return new ResponseEntity<>(createBody(errors), HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> createBody(List<String> errors) {
        return Map.of(
                "errors", errors,
                "timestamp", LocalDateTime.now());
    }
}