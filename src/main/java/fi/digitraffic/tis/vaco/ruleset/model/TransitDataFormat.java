package fi.digitraffic.tis.vaco.ruleset.model;

import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum TransitDataFormat implements PersistableEnum {
    GBFS("gbfs"),
    GTFS("gtfs"),
    GTFS_RT("gtfs-rt"),
    NETEX("netex"),
    SIRI("siri"),;

    private final String fieldName;

    TransitDataFormat(String fieldName) {
        this.fieldName = fieldName;
    }

    public static TransitDataFormat forField(String field) {
        for (TransitDataFormat format : values()) {
            if (format.fieldName().equals(field)) {
                return format;
            }
        }
        throw new InvalidMappingException("Could not map field value '" + field + "' to TransitDataFormat! Implementation missing?");
    }

    @Override
    public String fieldName() {
        return fieldName;
    }
}
