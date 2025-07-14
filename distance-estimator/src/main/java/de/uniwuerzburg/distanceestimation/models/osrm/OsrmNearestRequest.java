package de.uniwuerzburg.distanceestimation.models.osrm;

public class OsrmNearestRequest {
    public OsrmLocation start;
    public int n_closest = 1;

    public OsrmNearestRequest(OsrmLocation start) {
        this.start = start;
    }

    public OsrmNearestRequest(OsrmLocation start, int n_closest) {
        this.start = start;
        this.n_closest = n_closest;
    }
}
