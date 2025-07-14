package de.uniwuerzburg.distanceestimation.models.osrm;

public record OsrmRoutes(OsrmLeg[] legs, String weightName, double weight, double duration, double distance) {
}
