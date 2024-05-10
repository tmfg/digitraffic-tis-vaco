package fi.digitraffic.tis.vaco.ruleset.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum Type implements PersistableEnum {
    VALIDATION_SYNTAX("validation_syntax"),
    VALIDATION_LOGIC("validation_logic"),
    CONVERSION_SYNTAX("conversion_syntax"),
    CONVERSION_LOGIC("conversion_logic"),
    INTERNAL("internal");

    private final String fieldName;

    Type(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static Type forField(String field) {
        return switch (field) {
            case "validation_syntax" -> VALIDATION_SYNTAX;
            case "validation_logic" -> VALIDATION_LOGIC;
            case "conversion_syntax" -> CONVERSION_SYNTAX;
            case "conversion_logic" -> CONVERSION_LOGIC;
            case "internal" -> INTERNAL;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }
}
