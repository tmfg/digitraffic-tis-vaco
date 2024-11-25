package fi.digitraffic.tis.vaco.entries.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

import java.util.Set;

public enum Status implements PersistableEnum {
    CANCELLED(Name.CANCELLED),
    ERRORS(Name.ERRORS),
    FAILED(Name.FAILED),
    PROCESSING(Name.PROCESSING),
    RECEIVED(Name.RECEIVED),
    SUCCESS(Name.SUCCESS),
    WARNINGS(Name.WARNINGS);

    private final String fieldName;

    Status(String fieldName) {
        this.fieldName = fieldName;
    }

    public static Status forField(String field) {
        return switch (field) {
            case Name.CANCELLED -> CANCELLED;
            case Name.ERRORS -> ERRORS;
            case Name.FAILED -> FAILED;
            case Name.PROCESSING -> PROCESSING;
            case Name.RECEIVED -> RECEIVED;
            case Name.SUCCESS -> SUCCESS;
            case Name.WARNINGS -> WARNINGS;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Status! Implementation missing?");
        };
    }

    @Override
    @JsonValue
    public String fieldName() {
        return fieldName;
    }

    public static boolean isNotCompleted(Status status) {
        return Set.of(Status.RECEIVED, Status.PROCESSING).contains(status);
    }


    public static final class Name {

        public static final String CANCELLED = "cancelled";

        public static final String ERRORS = "errors";

        public static final String FAILED = "failed";

        public static final String PROCESSING = "processing";

        public static final String RECEIVED = "received";

        public static final String SUCCESS = "success";

        public static final String WARNINGS = "warnings";

        private Name() {}
    }
}
