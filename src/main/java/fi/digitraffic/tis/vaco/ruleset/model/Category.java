package fi.digitraffic.tis.vaco.ruleset.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum Category implements PersistableEnum {
    GENERIC(Name.GENERIC),
    SPECIFIC(Name.SPECIFIC);

    private final String fieldName;

    Category(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static Category forField(String field) {
        return switch (field) {
            case Name.GENERIC ->  GENERIC;
            case Name.SPECIFIC -> SPECIFIC;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }

    public static final class Name {

        public static final String GENERIC = "generic";

        public static final String SPECIFIC = "specific";

        private Name() {}
    }
}
