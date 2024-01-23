package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.opencsv.bean.CsvBindByName;

public class Shape {

    @CsvBindByName(column = "shape_id")
    String shapeId;

    // These are just for the record what shapes can contain (but now not needed)
    //@CsvBindByName(column = "shape_pt_lat")
    //String shapePtLat;
    //@CsvBindByName(column = "shape_pt_lon")
    //String shapePtLon;
    //@CsvBindByName(column = "shape_pt_sequence")
    //String shapePtSequence;
    //@CsvBindByName(column = "shape_dist_traveled")
    //String shapeDistTraveled;

    public Shape() {
    }

    public Shape(String shapeId) {
        this.shapeId = shapeId;
    }

    public String getShapeId() {
        return shapeId;
    }

    public void setShapeId(String shapeId) {
        this.shapeId = shapeId;
    }
}
