package de.uniwuerzburg.distanceestimation.models.osrm;

public record OsrmRouteResponse(long durationNanos, String code, OsrmRoutes[] routes, OsrmWaypointRoute[] waypoints) {
}


