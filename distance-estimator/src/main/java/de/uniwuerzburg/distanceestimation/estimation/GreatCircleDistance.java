package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

public class GreatCircleDistance extends AirlineDistance implements AStarAdmissibleHeuristic<GeoLocation> {

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        // not required here, but done for consistency reasons!
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        double dLat = Math.toRadians(dest.getLat() - start.getLat());
        double dLon = Math.toRadians(dest.getLon() - start.getLon());
        double startLatR = Math.toRadians(start.getLat());
        double destLatR = Math.toRadians(dest.getLat());
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) *
                Math.cos(startLatR) * Math.cos(destLatR);
        double c = 2 * Math.asin(Math.sqrt(a));
        return DistanceEstimate.byKm(EARTH_RADIUS * c);
    }

    @Override
    public ApproachType getApproachType() {
        return ApproachType.HAVERSINE;
    }

    @Override
    public DistanceEstimation copyApproach() {
        return new GreatCircleDistance();
    }

    @Override
    public double getCostEstimate(GeoLocation o, GeoLocation v1) {
        return estimateDistance(o, v1).getMeters();
    }

    @Override
    public boolean isConsistent(Graph graph) {
        return true;
    }
}
