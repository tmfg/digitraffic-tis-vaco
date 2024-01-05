package fi.digitraffic.tis.vaco.rules.model.gtfs.summary;

import com.opencsv.bean.CsvBindByName;

public class Shape extends CsvBean {

    @CsvBindByName(column = "shape_id")
    String shapeId;

    @CsvBindByName(column = "shape_pt_lat")
    String shapePtLat;

    @CsvBindByName(column = "shape_pt_lon")
    String shapePtLon;

    @CsvBindByName(column = "shape_pt_sequence")
    String shapePtSequence;

    @CsvBindByName(column = "shape_dist_traveled")
    String shapeDistTraveled;

    public Shape() {
    }

    public Shape(String shapeId, String shapePtLat, String shapePtLon, String shapePtSequence, String shapeDistTraveled) {
        this.shapeId = shapeId;
        this.shapePtLat = shapePtLat;
        this.shapePtLon = shapePtLon;
        this.shapePtSequence = shapePtSequence;
        this.shapeDistTraveled = shapeDistTraveled;
    }

    public String getShapeId() {
        return shapeId;
    }

    public void setShapeId(String shapeId) {
        this.shapeId = shapeId;
    }

    public String getShapePtLat() {
        return shapePtLat;
    }

    public void setShapePtLat(String shapePtLat) {
        this.shapePtLat = shapePtLat;
    }

    public String getShapePtLon() {
        return shapePtLon;
    }

    public void setShapePtLon(String shapePtLon) {
        this.shapePtLon = shapePtLon;
    }

    public String getShapePtSequence() {
        return shapePtSequence;
    }

    public void setShapePtSequence(String shapePtSequence) {
        this.shapePtSequence = shapePtSequence;
    }

    public String getShapeDistTraveled() {
        return shapeDistTraveled;
    }

    public void setShapeDistTraveled(String shapeDistTraveled) {
        this.shapeDistTraveled = shapeDistTraveled;
    }
}
