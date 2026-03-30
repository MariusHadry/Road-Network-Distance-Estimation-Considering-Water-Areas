package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.models.DirectLine;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;
import org.locationtech.jts.geom.Coordinate;

public class GreatCircleDistance extends AirlineDistance implements AStarAdmissibleHeuristic<GeoLocation> {

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        return DistanceEstimate.byM(getDistanceMeters(start, dest));
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

    public static double getDistanceMeters(DirectLine directLine) {
        Coordinate c1 = directLine.getLine().getCoordinateN(0);
        Coordinate c2 = directLine.getLine().getCoordinateN(1);
        return getDistanceMeters(c1, c2);
    }

    public static double getDistanceMeters(Coordinate c1, Coordinate c2) {
        double lat1 = c1.y;
        double lon1 = c1.x;
        double lat2 = c2.y;
        double lon2 = c2.x;

        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) + Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000 * c;
    }
}
