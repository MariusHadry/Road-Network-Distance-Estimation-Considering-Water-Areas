package de.uniwuerzburg.distanceestimation.models.osrm;

public record OsrmWaypointRoute(String hint, double distance, String name, double[] location){
}
