package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.opencsv.bean.CsvBindByName;

public class Trip {

    @CsvBindByName(column = "trip_id")
    String tripId;

    @CsvBindByName(column = "trip_headsign")
    String tripHeadsign;

    @CsvBindByName(column = "block_id")
    String blockId;

    @CsvBindByName(column = "wheelchair_accessible")
    String wheelchairAccessible;

    @CsvBindByName(column = "bikes_allowed")
    String bikesAllowed;

    // These are just for the record what agency.txt can contain (but now not needed)
    //@CsvBindByName(column = "shape_id")
    //String shapeId;
    //@CsvBindByName(column = "route_id")
    //String routeId;
    // @CsvBindByName(column = "service_id")
    // String serviceId;
    //@CsvBindByName(column = "trip_short_name")
    //String tripShortName;
    //@CsvBindByName(column = "direction_id")
    // String directionId;

    public Trip() {
    }

    public Trip(String tripId, String tripHeadsign, String blockId, String wheelchairAccessible, String bikesAllowed) {
        this.tripId = tripId;
        this.tripHeadsign = tripHeadsign;
        this.blockId = blockId;
        this.wheelchairAccessible = wheelchairAccessible;
        this.bikesAllowed = bikesAllowed;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getTripHeadsign() {
        return tripHeadsign;
    }

    public void setTripHeadsign(String tripHeadsign) {
        this.tripHeadsign = tripHeadsign;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
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
