package fi.digitraffic.tis.vaco.company.model;


import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum PartnershipType implements PersistableEnum {
    AUTHORITY_PROVIDER(Name.AUTHORITY_PROVIDER);

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
            case Name.AUTHORITY_PROVIDER -> AUTHORITY_PROVIDER;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to PartnershipType! Implementation missing?");
        };
    }

    public static final class Name {

        public static final String AUTHORITY_PROVIDER = "authority-provider";

        private Name() {}
    }
}
