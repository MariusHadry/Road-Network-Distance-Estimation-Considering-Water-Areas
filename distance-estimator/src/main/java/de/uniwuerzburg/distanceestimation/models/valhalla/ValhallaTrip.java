package de.uniwuerzburg.distanceestimation.models.valhalla;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;


public record ValhallaTrip(ValhallaSummary summary, GeoLocation[] locations, ValhallaLeg[] legs) {}
