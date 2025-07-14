package de.uniwuerzburg.distanceestimation.models.osrm;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;

public record OsrmWaypointNearest (String hint, long[] nodes, double distance, String name, double[] location) {

    public GeoLocation getAsGeoLocation() {
        return new GeoLocation(location[1], location[0]);
    }
}