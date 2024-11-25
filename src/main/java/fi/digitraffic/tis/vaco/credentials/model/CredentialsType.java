package fi.digitraffic.tis.vaco.credentials.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum CredentialsType implements PersistableEnum {
    HTTP_BASIC(Name.HTTP_BASIC);

    private final String name;

    CredentialsType(String name) {
        this.name = name;
    }

    @Override
    @JsonValue
    public String fieldName() {
        return name;
    }

    public static CredentialsType forField(String field) {
        return switch (field) {
            case Name.HTTP_BASIC -> HTTP_BASIC;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to SubscriptionType! Implementation missing?");
        };
    }

    public static final class Name {

        public static final String HTTP_BASIC = "HTTP Basic";

        private Name() {}
    }
}
