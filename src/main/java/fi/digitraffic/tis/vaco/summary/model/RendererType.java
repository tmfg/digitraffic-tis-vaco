package fi.digitraffic.tis.vaco.summary.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum RendererType implements PersistableEnum {
    CARD("card"),
    TABULAR("tabular"),
    LIST("list"),
    UNKNOWN("unknown");

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
            case "card" -> CARD;
            case "tabular" -> TABULAR;
            case "list" -> LIST;
            case "unknown" -> UNKNOWN;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }
}
