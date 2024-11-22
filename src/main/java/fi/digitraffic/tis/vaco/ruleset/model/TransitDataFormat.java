package fi.digitraffic.tis.vaco.ruleset.model;

import com.fasterxml.jackson.annotation.JsonValue;
import fi.digitraffic.tis.utilities.model.PersistableEnum;
import fi.digitraffic.tis.vaco.InvalidMappingException;

public enum TransitDataFormat implements PersistableEnum {
    GBFS(Name.GBFS, false),
    GTFS(Name.GTFS, false),
    GTFS_RT(Name.GTFS_RT, true),
    NETEX(Name.NETEX, false),
    SIRI_ET(Name.SIRI_ET, true),
    SIRI_SX(Name.SIRI_SX, true),
    SIRI_VM(Name.SIRI_VM, true);

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

    @Override
    @JsonValue
    public String fieldName() {
        return fieldName;
    }

    public boolean isRealtime() {
        return realtime;
    }

    public static final class Name {

        public static final String GBFS = "gbfs";

        public static final String GTFS = "gtfs";

        public static final String GTFS_RT = "gtfs-rt";

        public static final String NETEX = "netex";

        public static final String SIRI_ET = "siri-et";

        public static final String SIRI_SX = "siri-sx";

        public static final String SIRI_VM = "siri-vm";

        private Name() {}
    }
}
