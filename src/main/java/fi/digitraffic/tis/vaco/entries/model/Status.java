package fi.digitraffic.tis.vaco.entries.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum Status implements PersistableEnum {
    ERRORS("errors"),
    FAILED("failed"),
    PROCESSING("processing"),
    RECEIVED("received"),
    SUCCESS("success"),
    WARNINGS("warnings");

    private final String fieldName;

    Status(String fieldName) {
        this.fieldName = fieldName;
    }

    public static Status forField(String field) {
        return switch (field) {
            case "errors" -> ERRORS;
            case "failed" -> FAILED;
            case "processing" -> PROCESSING;
            case "received" -> RECEIVED;
            case "success" -> SUCCESS;
            case "warnings" -> WARNINGS;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Status! Implementation missing?");
        };
    }

    @Override
    @JsonValue
    public String fieldName() {
        return fieldName;
    }
}
