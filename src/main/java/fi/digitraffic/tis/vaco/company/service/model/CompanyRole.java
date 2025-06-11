package fi.digitraffic.tis.vaco.company.service.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum CompanyRole implements PersistableEnum {
    OPERATOR(Name.OPERATOR),
    AUTHORITY(Name.AUTHORITY);

    private final String fieldName;

    CompanyRole(String fieldName) {
        this.fieldName = fieldName;
    }

    public static CompanyRole forField(String field) {
        return switch (field) {
            case Name.OPERATOR -> OPERATOR;
            case Name.AUTHORITY -> AUTHORITY;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to CompanyRole! Implementation missing?");
        };
    }

    @Override
    @JsonValue
    public String fieldName() {
        return fieldName;
    }

    public static final class Name {

        public static final String OPERATOR = "operator";

        public static final String AUTHORITY = "authority";

        private Name() {}
    }
}
