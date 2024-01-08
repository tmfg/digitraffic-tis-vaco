package fi.digitraffic.tis.vaco.rules.model.gtfs.summary;

import com.opencsv.bean.CsvBindByName;

public class Trip {

    @CsvBindByName(column = "trip_id")
    String tripId;

    @CsvBindByName(column = "route_id")
    String routeId;

    @CsvBindByName(column = "service_id")
    String serviceId;

    @CsvBindByName(column = "trip_headsign")
    String tripHeadsign;

    @CsvBindByName(column = "trip_short_name")
    String tripShortName;

    @CsvBindByName(column = "direction_id")
    String directionId;

    @CsvBindByName(column = "block_id")
    String blockId;

    @CsvBindByName(column = "short_id")
    String shapeId;

    @CsvBindByName(column = "wheelchair_accessible")
    String wheelchairAccessible;

    @CsvBindByName(column = "bikes_allowed")
    String bikesAllowed;

    public Trip() {
    }

    public Trip(String tripId, String routeId, String serviceId, String tripHeadsign, String tripShortName, String directionId, String blockId, String shapeId, String wheelchairAccessible, String bikesAllowed) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.serviceId = serviceId;
        this.tripHeadsign = tripHeadsign;
        this.tripShortName = tripShortName;
        this.directionId = directionId;
        this.blockId = blockId;
        this.shapeId = shapeId;
        this.wheelchairAccessible = wheelchairAccessible;
        this.bikesAllowed = bikesAllowed;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getTripHeadsign() {
        return tripHeadsign;
    }

    public void setTripHeadsign(String tripHeadsign) {
        this.tripHeadsign = tripHeadsign;
    }

    public String getTripShortName() {
        return tripShortName;
    }

    public void setTripShortName(String tripShortName) {
        this.tripShortName = tripShortName;
    }

    public String getDirectionId() {
        return directionId;
    }

    public void setDirectionId(String directionId) {
        this.directionId = directionId;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getShapeId() {
        return shapeId;
    }

    public void setShapeId(String shapeId) {
        this.shapeId = shapeId;
    }

    public String getWheelchairAccessible() {
        return wheelchairAccessible;
    }

    public void setWheelchairAccessible(String wheelchairAccessible) {
        this.wheelchairAccessible = wheelchairAccessible;
    }

    public String getBikesAllowed() {
        return bikesAllowed;
    }

    public void setBikesAllowed(String bikesAllowed) {
        this.bikesAllowed = bikesAllowed;
    }
}
