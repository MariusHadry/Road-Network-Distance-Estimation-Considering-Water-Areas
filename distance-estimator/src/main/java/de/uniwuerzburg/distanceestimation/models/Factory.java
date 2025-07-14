package de.uniwuerzburg.distanceestimation.models;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

public class Factory {
    public static final GeometryFactory FACTORY = new GeometryFactory();

    public static Point coordinateToPoint(Coordinate coordinate) {
        return Factory.FACTORY.createPoint(coordinate);
    }
}
