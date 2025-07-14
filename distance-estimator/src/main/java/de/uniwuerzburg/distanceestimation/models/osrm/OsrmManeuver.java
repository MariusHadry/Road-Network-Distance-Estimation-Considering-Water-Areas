package de.uniwuerzburg.distanceestimation.models.osrm;

public record OsrmManeuver(int bearing_after, int bearing_before, double[] location, String type) {
}
