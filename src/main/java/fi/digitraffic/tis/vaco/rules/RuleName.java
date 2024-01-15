package fi.digitraffic.tis.vaco.rules;

import java.util.List;
import java.util.Set;

public final class RuleName {
    private RuleName() {}
    public static final String GTFS_CANONICAL_4_0_0 = "gtfs.canonical.v4_0_0";
    public static final String GTFS_CANONICAL_4_1_0 = "gtfs.canonical.v4_1_0";
    public static final String NETEX_ENTUR_1_0_1 = "netex.entur.v1_0_1";
    public static final String NETEX2GTFS_ENTUR_2_0_6 = "netex2gtfs.entur.v2_0_6";
    public static final String GTFS2NETEX_FINTRAFFIC_1_0_0 = "gtfs2netex.fintraffic.v1_0_0";

    public static final List<String> ALL_EXTERNAL_VALIDATION_RULES = List.of(
        GTFS_CANONICAL_4_0_0,
        GTFS_CANONICAL_4_1_0,
        NETEX_ENTUR_1_0_1);

    public static final Set<String> VALIDATION_RULES = Set.of(
        RuleName.GTFS_CANONICAL_4_0_0,
        RuleName.GTFS_CANONICAL_4_1_0,
        RuleName.NETEX_ENTUR_1_0_1
    );

    public static Set<String> CONVERSION_RULES = Set.of(
        RuleName.GTFS2NETEX_FINTRAFFIC_1_0_0,
        RuleName.NETEX2GTFS_ENTUR_2_0_6
    );
}
