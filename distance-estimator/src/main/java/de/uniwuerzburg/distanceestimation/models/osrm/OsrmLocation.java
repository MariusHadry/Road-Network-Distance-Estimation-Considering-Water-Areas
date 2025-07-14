package de.uniwuerzburg.distanceestimation.models.osrm;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;

public class OsrmLocation {
    private final GeoLocation location;

    public OsrmLocation(double lat, double lon) {
        this.location = new GeoLocation(lat, lon);
    }

    public OsrmLocation(GeoLocation location) {
        this.location = location;
    }

    public String getLocationString(){
        return location.getLon() + "," + location.getLat();
    }
}
