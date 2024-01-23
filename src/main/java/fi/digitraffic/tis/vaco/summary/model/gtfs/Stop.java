package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.opencsv.bean.CsvBindByName;

public class Stop {

    @CsvBindByName(column = "stop_id")
    String stopId;

    @CsvBindByName(column = "location_type")
    String locationType;

    public Stop() {
    }

    public Stop(String stopId, String locationType) {
        this.stopId = stopId;
        this.locationType = locationType;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }
}
