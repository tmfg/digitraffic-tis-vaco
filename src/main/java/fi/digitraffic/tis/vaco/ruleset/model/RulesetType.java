package fi.digitraffic.tis.vaco.ruleset.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum RulesetType implements PersistableEnum {
    VALIDATION_SYNTAX(Name.VALIDATION_SYNTAX),
    VALIDATION_LOGIC(Name.VALIDATION_LOGIC),
    CONVERSION_SYNTAX(Name.CONVERSION_SYNTAX),
    CONVERSION_LOGIC(Name.CONVERSION_LOGIC),
    INTERNAL(Name.INTERNAL);

    private final String fieldName;

    RulesetType(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static RulesetType forField(String field) {
        return switch (field) {
            case Name.VALIDATION_SYNTAX -> VALIDATION_SYNTAX;
            case Name.VALIDATION_LOGIC -> VALIDATION_LOGIC;
            case Name.CONVERSION_SYNTAX -> CONVERSION_SYNTAX;
            case Name.CONVERSION_LOGIC -> CONVERSION_LOGIC;
            case Name.INTERNAL -> INTERNAL;
            default ->
                throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }

    public static final class Name {

        public static final String VALIDATION_SYNTAX = "validation_syntax";

        public static final String VALIDATION_LOGIC = "validation_logic";

        public static final String CONVERSION_SYNTAX = "conversion_syntax";

        public static final String CONVERSION_LOGIC = "conversion_logic";

        public static final String INTERNAL = "internal";

        private Name() {}
    }
}
