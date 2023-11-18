package fi.digitraffic.tis.vaco.company.model;


import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum PartnershipType implements PersistableEnum {
    AUTHORITY_PROVIDER("authority-provider");

    private final String fieldName;

    PartnershipType(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static PartnershipType forField(String field) {
        return switch (field) {
            case "authority-provider" -> AUTHORITY_PROVIDER;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to PartnershipType! Implementation missing?");
        };
    }
}
