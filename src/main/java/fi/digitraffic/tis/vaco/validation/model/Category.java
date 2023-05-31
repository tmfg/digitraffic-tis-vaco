package fi.digitraffic.tis.vaco.validation.model;

public enum Category implements PersistableEnum {
    GENERIC("generic"), SPECIFIC("specific");

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
            case "generic" -> GENERIC;
            case "specific" -> SPECIFIC;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }
}
