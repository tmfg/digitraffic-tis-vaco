package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.opencsv.bean.CsvBindByName;

public class Route {

    @CsvBindByName(column = "route_id")
    String routeId;

    @CsvBindByName(column = "route_short_name")
    String routeShortName;

    @CsvBindByName(column = "route_long_name")
    String routeLongName;

    @CsvBindByName(column = "route_color")
    String routeColor;

    //@CsvBindByName(column = "route_type")
    //String routeType;
    //@CsvBindByName(column = "agency_id")
    //String agencyId;
    //@CsvBindByName(column = "route_desc")
    //String routeDesc;
    // @CsvBindByName(column = "route_url")
    // String routeUrl;
   // @CsvBindByName(column = "route_text_color")
   // String routeTextColor;
   // @CsvBindByName(column = "route_sort_order")
   // String routeSortOrder;
    //@CsvBindByName(column = "continuous_pickup")
   // String continuousPickup;
   // @CsvBindByName(column = "continuous_drop_off")
   // String continuousDropOff;

    @CsvBindByName(column = "network_id")
    String networkId;

    public Route() {
    }

    public Route(String routeId, String routeShortName, String routeLongName, String routeColor) {
        this.routeId = routeId;
        this.routeShortName = routeShortName;
        this.routeLongName = routeLongName;
        this.routeColor = routeColor;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getRouteShortName() {
        return routeShortName;
    }

    public void setRouteShortName(String routeShortName) {
        this.routeShortName = routeShortName;
    }

    public String getRouteLongName() {
        return routeLongName;
    }

    public void setRouteLongName(String routeLongName) {
        this.routeLongName = routeLongName;
    }

    public String getRouteColor() {
        return routeColor;
    }

    public void setRouteColor(String routeColor) {
        this.routeColor = routeColor;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }
}
