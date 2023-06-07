package fi.digitraffic.tis.vaco.tis.model;

import fi.digitraffic.tis.vaco.validation.model.InvalidMappingException;
import fi.digitraffic.tis.vaco.validation.model.PersistableEnum;

public enum CooperationType implements PersistableEnum {
    AUTHORITY_PROVIDER("authority-provider");

    private final String fieldName;

    CooperationType(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static CooperationType forField(String field) {
        return switch (field) {
            case "authority-provider" -> AUTHORITY_PROVIDER;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to CooperationType! Implementation missing?");
        };
    }
}
