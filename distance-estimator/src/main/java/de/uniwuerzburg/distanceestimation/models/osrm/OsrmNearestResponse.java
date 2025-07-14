package de.uniwuerzburg.distanceestimation.models.osrm;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;

public record OsrmNearestResponse(String code, OsrmWaypointNearest[] waypoints) {

    public double getClosestDistance() {
        return waypoints[0].distance();
    }

    public GeoLocation getClosestLocation() {
        return waypoints[0].getAsGeoLocation();
    }

}