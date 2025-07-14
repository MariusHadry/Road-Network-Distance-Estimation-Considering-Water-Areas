package de.uniwuerzburg.distanceestimation.models.osrm;

public record OsrmLeg(OsrmStep[] steps, String summary, double weight, double duration, double distance) {
}
