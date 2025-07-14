package de.uniwuerzburg.distanceestimation.models.valhalla;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;

public record ValhallaLocation(double lat, double lon) {
    public ValhallaLocation(GeoLocation location){
        this(location.getLat(), location.getLon());
    }
}
