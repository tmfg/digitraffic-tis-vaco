package fi.digitraffic.tis.vaco.company.model;


import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

// TODO: migrate to db package?
public enum HierarchyType implements PersistableEnum {
    AUTHORITY_PROVIDER("authority-provider"),
    WEBHOOK_LISTENER("webhook-listener");

    private final String fieldName;

    HierarchyType(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    public static HierarchyType forField(String field) {
        return switch (field) {
            case "authority-provider" -> AUTHORITY_PROVIDER;
            case "webhook-listener" -> WEBHOOK_LISTENER;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to PartnershipType! Implementation missing?");
        };
    }
}
