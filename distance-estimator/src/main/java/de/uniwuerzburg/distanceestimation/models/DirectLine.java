package de.uniwuerzburg.distanceestimation.models;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

public class DirectLine {

    private final GeoLocation start;
    private final GeoLocation dest;
    private LineString line;

    public DirectLine(GeoLocation start, GeoLocation dest) {
        this.start = start;
        this.dest = dest;
        calculateDirectLine();
    }

    private void calculateDirectLine() {
        line = Factory.FACTORY.createLineString(new Coordinate[]{start, dest});
    }

    public Coordinate getStart() {
        return start;
    }

    public Coordinate getDest() {
        return dest;
    }

    public LineString getLine() {
        return line;
    }
}
