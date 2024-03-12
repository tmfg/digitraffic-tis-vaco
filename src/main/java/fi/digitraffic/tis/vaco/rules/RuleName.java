package fi.digitraffic.tis.vaco.rules;

import java.util.List;

public final class RuleName {
    private RuleName() {}
    public static final String GTFS_CANONICAL_4_0_0 = "gtfs.canonical";
    public static final String GTFS_CANONICAL = "gtfs.canonical";
    public static final String NETEX_ENTUR = "netex.entur";
    public static final String NETEX2GTFS_ENTUR = "netex2gtfs.entur";
    public static final String GTFS2NETEX_FINTRAFFIC = "gtfs2netex.fintraffic";

    public static final List<String> ALL_EXTERNAL_VALIDATION_RULES = List.of(
        GTFS_CANONICAL,
        NETEX_ENTUR);
}
