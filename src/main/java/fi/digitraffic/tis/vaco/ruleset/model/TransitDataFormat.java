package fi.digitraffic.tis.vaco.ruleset.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum TransitDataFormat implements PersistableEnum {
    GBFS("gbfs", false),
    GTFS("gtfs", false),
    GTFS_RT("gtfs-rt", true),
    NETEX("netex", false),
    SIRI_ET("siri-et", true),
    SIRI_SX("siri-sx", true),
    SIRI_VM("siri-vm",true);

    private final String fieldName;
    private final boolean realtime;

    TransitDataFormat(String fieldName, boolean realtime) {
        this.fieldName = fieldName;
        this.realtime = realtime;
    }

    public static TransitDataFormat forField(String field) {
        for (TransitDataFormat format : values()) {
            if (format.fieldName().equals(field)) {
                return format;
            }
        }
        throw new InvalidMappingException("Could not map field value '" + field + "' to TransitDataFormat! Implementation missing?");
    }

    public static TransitDataFormat forField(TransitDataFormat field) {
        for (TransitDataFormat format : values()) {
            if (format.equals(field)) {
                return format;
            }
        }
        throw new InvalidMappingException("Could not map field value '" + field + "' to TransitDataFormat! Implementation missing?");
    }


    @Override
    @JsonValue
    public String fieldName() {
        return fieldName;
    }

    public boolean isRealtime() {
        return realtime;
    }
}
