package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;

public class EuclideanDistance extends AirlineDistance {

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        // not required here, but done for consistency reasons!
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        double deltaLonX = Math.toRadians(dest.getLon() - start.getLon());
        double deltaLatY = Math.toRadians(dest.getLat() - start.getLat());

        double x = deltaLonX * Math.cos((Math.toRadians(start.getLat()) + Math.toRadians(dest.getLat())) / 2);

        return DistanceEstimate.byKm(EARTH_RADIUS * Math.sqrt(x * x + deltaLatY * deltaLatY));
    }

    @Override
    public ApproachType getApproachType() {
        return ApproachType.AIRLINE;
    }

    @Override
    public DistanceEstimation copyApproach() {
        return new EuclideanDistance();
    }


}
