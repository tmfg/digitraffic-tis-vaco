package fi.digitraffic.tis.vaco.notifications.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum SubscriptionType implements PersistableEnum {
    WEBHOOK(Name.WEBHOOK);

    private final String fieldName;

    SubscriptionType(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    @JsonValue
    public String fieldName() {
        return fieldName;
    }

    public static SubscriptionType forField(String field) {
        return switch (field) {
            case Name.WEBHOOK -> WEBHOOK;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to SubscriptionType! Implementation missing?");
        };
    }

    public static final class Name {

        public static final String WEBHOOK = "webhook";

        private Name() {}

    }
}
