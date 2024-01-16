package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.opencsv.bean.CsvBindByName;

public class Route {

    @CsvBindByName(column = "route_id")
    String routeId;

    @CsvBindByName(column = "agency_id")
    String agencyId;

    @CsvBindByName(column = "route_short_name")
    String routeShortName;

    @CsvBindByName(column = "route_long_name")
    String routeLongName;

    @CsvBindByName(column = "route_desc")
    String routeDesc;

    @CsvBindByName(column = "route_type")
    String routeType;

    @CsvBindByName(column = "route_url")
    String routeUrl;

    @CsvBindByName(column = "route_color")
    String routeColor;

    @CsvBindByName(column = "route_text_color")
    String routeTextColor;

    @CsvBindByName(column = "route_sort_order")
    String routeSortOrder;

    @CsvBindByName(column = "continuous_pickup")
    String continuousPickup;

    @CsvBindByName(column = "continuous_drop_off")
    String continuousDropOff;

    @CsvBindByName(column = "network_id")
    String networkId;

    public Route() {
    }

    public Route(String routeId, String agencyId, String routeShortName, String routeLongName, String routeDesc, String routeType, String routeUrl, String routeColor, String routeTextColor, String routeSortOrder, String continuousPickup, String continuousDropOff, String networkId) {
        this.routeId = routeId;
        this.agencyId = agencyId;
        this.routeShortName = routeShortName;
        this.routeLongName = routeLongName;
        this.routeDesc = routeDesc;
        this.routeType = routeType;
        this.routeUrl = routeUrl;
        this.routeColor = routeColor;
        this.routeTextColor = routeTextColor;
        this.routeSortOrder = routeSortOrder;
        this.continuousPickup = continuousPickup;
        this.continuousDropOff = continuousDropOff;
        this.networkId = networkId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
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

    public String getRouteDesc() {
        return routeDesc;
    }

    public void setRouteDesc(String routeDesc) {
        this.routeDesc = routeDesc;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
    }

    public String getRouteUrl() {
        return routeUrl;
    }

    public void setRouteUrl(String routeUrl) {
        this.routeUrl = routeUrl;
    }

    public String getRouteColor() {
        return routeColor;
    }

    public void setRouteColor(String routeColor) {
        this.routeColor = routeColor;
    }

    public String getRouteTextColor() {
        return routeTextColor;
    }

    public void setRouteTextColor(String routeTextColor) {
        this.routeTextColor = routeTextColor;
    }

    public String getRouteSortOrder() {
        return routeSortOrder;
    }

    public void setRouteSortOrder(String routeSortOrder) {
        this.routeSortOrder = routeSortOrder;
    }

    public String getContinuousPickup() {
        return continuousPickup;
    }

    public void setContinuousPickup(String continuousPickup) {
        this.continuousPickup = continuousPickup;
    }

    public String getContinuousDropOff() {
        return continuousDropOff;
    }

    public void setContinuousDropOff(String continuousDropOff) {
        this.continuousDropOff = continuousDropOff;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }
}
