package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.models.DirectLine;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import org.locationtech.jts.geom.LineString;

import java.util.Objects;

public abstract class AirlineDistance implements DistanceEstimation {
    public static final int EARTH_RADIUS = 6371;

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        DirectLine line = new DirectLine(start, dest);
        return line.getLine();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }
}
