package fi.digitraffic.tis.vaco.ruleset.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum TransitDataFormat implements PersistableEnum {
    GTFS("gtfs"),
    GTFS_RT("gtfs-rt"),
    NETEX("netex"),
    SIRI("siri");

    private final String fieldName;

    TransitDataFormat(String fieldName) {
        this.fieldName = fieldName;
    }

    public static TransitDataFormat forField(String field) {
        return switch (field) {
            case "gtfs" -> GTFS;
            case "gtfs-rt" -> GTFS_RT;
            case "netex" -> NETEX;
            case "siri" -> SIRI;
            default -> throw new InvalidMappingException("Could not map field value '" + field + "' to Category! Implementation missing?");
        };
    }

    @Override
    public String fieldName() {
        return fieldName;
    }
}
