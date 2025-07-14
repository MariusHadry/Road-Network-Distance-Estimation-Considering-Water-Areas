package de.uniwuerzburg.distanceestimation.models.osrm;


public class OsrmRouteRequest {
    public OsrmLocation start;
    public OsrmLocation dest;

    public OsrmRouteRequest(OsrmLocation start, OsrmLocation dest) {
        this.start = start;
        this.dest = dest;
    }
}
