package de.uniwuerzburg.distanceestimation.estimation;

import com.harium.storage.kdtree.KDTree;
import de.uniwuerzburg.distanceestimation.models.DirectLine;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import org.locationtech.jts.geom.LineString;

import java.util.HashMap;


public class OverheadGraphEstimation extends DirectLineEstimation {

    private KDTree<GeoLocation> locationKDTree;
    private HashMap<GeoLocation, HashMap<GeoLocation, Double>> circuityLookupMap;
    private HashMap<GeoLocation, Double> circuityAverageLookupMap;
    private final int N_RANDOM_POINTS;

    public OverheadGraphEstimation(KDTree<GeoLocation> locationKDTree,
                                   HashMap<GeoLocation, HashMap<GeoLocation, Double>> circuityLookupMap,
                                   HashMap<GeoLocation, Double> circuityAverageLookupMap,
                                   int nPoints) {
        super(null, null, null, new GreatCircleDistance());
        this.locationKDTree = locationKDTree;
        this.circuityLookupMap = circuityLookupMap;
        this.circuityAverageLookupMap = circuityAverageLookupMap;
        this.N_RANDOM_POINTS = nPoints;
    }

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        // ensure same order of start dest
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        GeoLocation closestPointStart = this.locationKDTree.nearest(new double[]{start.getLat(), start.getLon()});
        GeoLocation closestPointDest = this.locationKDTree.nearest(new double[]{dest.getLat(), dest.getLon()});

        double circuityFactor;
        if (closestPointStart.equals(closestPointDest)) {
            circuityFactor = circuityAverageLookupMap.get(closestPointDest);
        } else {
            circuityFactor = circuityLookupMap.get(closestPointStart).get(closestPointDest);
        }

        double airlineDistance = metric.estimateDistance(start, dest).getMeters();

        return DistanceEstimate.byM(airlineDistance * circuityFactor);
    }

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        // the path is simply a straight line multiplied with the according circuity factor!
        DirectLine line = new DirectLine(start, dest);
        return line.getLine();
    }

    @Override
    public ApproachType getApproachType() {
        if (N_RANDOM_POINTS == 1024) {
            return ApproachType.OVERHEAD_GRAPH_1024;
        } else if (N_RANDOM_POINTS == 512) {
            return ApproachType.OVERHEAD_GRAPH_512;
        } else if (N_RANDOM_POINTS == 256) {
            return ApproachType.OVERHEAD_GRAPH_256;
        } else if (N_RANDOM_POINTS == 128) {
            return ApproachType.OVERHEAD_GRAPH_128;
        }
        return null;
    }

    @Override
    public DistanceEstimation copyApproach() {
        return new OverheadGraphEstimation(locationKDTree, circuityLookupMap, circuityAverageLookupMap, N_RANDOM_POINTS);
    }
}
