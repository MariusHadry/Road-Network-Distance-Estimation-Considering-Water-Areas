package de.uniwuerzburg.distanceestimation.models.mapInfo;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import org.locationtech.jts.geom.LineString;

import java.io.Serializable;

public record Bridge(String name, LineString geom) implements Serializable {
    public GeoLocation getStart() {
        return new GeoLocation(geom.getStartPoint());
    }

    public GeoLocation getEnd() {
        return new GeoLocation(geom.getEndPoint());
    }

    @Override
    public String toString() {
        return name + ", " + geom.getCoordinate().y + " " + geom.getCoordinate().x;
    }
}
