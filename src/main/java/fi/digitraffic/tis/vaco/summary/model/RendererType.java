package fi.digitraffic.tis.vaco.summary.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum RendererType implements PersistableEnum {
    CARD(Name.CARD),
    TABULAR(Name.TABULAR),
    LIST(Name.LIST),
    UNKNOWN(Name.UNKNOWN);

    private final String fieldName;

    RendererType(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static RendererType forField(String field) {
        return switch (field) {
            case Name.CARD -> CARD;
            case Name.TABULAR -> TABULAR;
            case Name.LIST -> LIST;
            case Name.UNKNOWN -> UNKNOWN;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }

    public static final class Name {

        public static final String CARD = "card";

        public static final String TABULAR = "tabular";

        public static final String LIST = "list";

        public static final String UNKNOWN = "unknown";

        private Name() {
        }
    }
}
