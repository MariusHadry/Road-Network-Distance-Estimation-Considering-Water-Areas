package de.uniwuerzburg.distanceestimation.services;

import de.uniwuerzburg.distanceestimation.estimation.clients.OsrmClient;
import de.uniwuerzburg.distanceestimation.clustering.Cluster;
import de.uniwuerzburg.distanceestimation.clustering.KMeans;
import de.uniwuerzburg.distanceestimation.estimation.*;
import de.uniwuerzburg.distanceestimation.models.EstimationResult;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.preprocessing.OverheadGraphPreprocessing;
import de.uniwuerzburg.distanceestimation.util.*;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DistanceEstimationService {
    private final PreprocessingService preprocessingService;

    @Autowired
    public DistanceEstimationService(PreprocessingService preprocessingService) {
        this.preprocessingService = preprocessingService;
    }

    public DistanceEstimation getDistanceEstimationByType(ApproachType type) {
        return switch (type) {
            case AIRLINE -> new EuclideanDistance();
            case OSRM -> new OsrmEstimation(new OsrmClient());
            case HAVERSINE -> new GreatCircleDistance();
            case WATER_GRAPH_CIRCUITY ->
                    new WaterGraphEstimation(preprocessingService.getWaterGraphPreprocessing(),
                            new EuclideanDistance(), true);
            case WATER_GRAPH ->
                    new WaterGraphEstimation(preprocessingService.getWaterGraphPreprocessing(),
                            new EuclideanDistance(),false);
            case BRIDGE_NO_REC ->
                    BridgeRouteEstimation.buildApproach(preprocessingService.getBridgeRoutePreprocessing(),
                            new EuclideanDistance(),false, false);
            case BRIDGE_REC ->
                    BridgeRouteEstimation.buildApproach(preprocessingService.getBridgeRoutePreprocessing(),
                            new EuclideanDistance(),true, false);
            case BRIDGE_SPLIT_REC ->
                    BridgeRouteEstimation.buildApproach(preprocessingService.getBridgeRoutePreprocessing(),
                            new EuclideanDistance(),true, true);
            case BRIDGE_SPLIT_NO_REC ->
                    BridgeRouteEstimation.buildApproach(preprocessingService.getBridgeRoutePreprocessing(),
                            new EuclideanDistance(),false, true);
            case OVERHEAD_GRAPH_128 -> getOverheadGraphEstimationByNPoints(128);
            case OVERHEAD_GRAPH_256 -> getOverheadGraphEstimationByNPoints(256);
            case OVERHEAD_GRAPH_512 -> getOverheadGraphEstimationByNPoints(512);
            case OVERHEAD_GRAPH_1024 -> getOverheadGraphEstimationByNPoints(1024);
            default -> throw new IllegalArgumentException("Unsupported approach type: " + type);
        };
    }

    private OverheadGraphEstimation getOverheadGraphEstimationByNPoints(int nPoints) {
        OverheadGraphPreprocessing preprocessing = preprocessingService.getOverheadGraphPreprocessing(nPoints);

        return new OverheadGraphEstimation(preprocessing.getLocationKDTree(), preprocessing.getCircuityLookupMap(),
                preprocessing.getCircuityMinimumLookupMap(), nPoints);
    }

    public boolean crossesWater(GeoLocation start, GeoLocation destination){
        WaterGraphEstimation wge = new WaterGraphEstimation(preprocessingService.getWaterGraphPreprocessing(),
                new EuclideanDistance(),false);

        return wge.crossesWater(start, destination);
    }

    public boolean crossesRiver(GeoLocation start, GeoLocation destination){
        BridgeRouteEstimation bre = new BridgeRouteEstimation(preprocessingService.getBridgeRoutePreprocessing(),
                new EuclideanDistance(),false);

        return bre.crossesWater(start, destination);
    }

    public EstimationResult estimateDistance(ApproachType type, GeoLocation start, GeoLocation destination, boolean includePath) {
        DistanceEstimation metric = getDistanceEstimationByType(type);

        // Ignore this for average time, just for Debug, Path and initial Delay
        Debug.message("\n----- Calculate " + metric.getApproachType().toString() + "-----");
        DurationTimer timer = new DurationTimer(true);
        DistanceEstimate anyResult;
        try {
            anyResult = metric.estimateDistance(start, destination);
        } catch (Exception e) {

            return new EstimationResult(metric, start, destination, null, null, -1, null,
                    true, e.getMessage());
        }

        timer.stop();

        DistanceEstimate resultCircuity;
        if (metric.getApproachType() == ApproachType.WATER_GRAPH_CIRCUITY) {
            resultCircuity = anyResult;
        } else if (metric.getApproachType() == ApproachType.WATER_GRAPH) {
            resultCircuity = null;
        } else if (metric.getApproachType().isOwnApproach()) {
            DirectLineEstimation own = (DirectLineEstimation) metric;
            resultCircuity = own.getLastDistanceCircuity();
        } else if (metric.getApproachType().isSimpleMetric()) {
            resultCircuity = anyResult.multiply(DistanceEstimation.CIRCUITY_FACTOR_GERMANY);
        } else {
            resultCircuity = null;
        }

        Debug.message("Test concluded with result " + anyResult + " in " + timer.getDuration() + " ns, " +
                timer.getDuration() / 1000000 + " ms.");

        LineString path = includePath ? metric.getPath(start, destination) : null;

        if (metric.getApproachType() == ApproachType.WATER_GRAPH_CIRCUITY) {
            anyResult = null;
        }

        return new EstimationResult(metric, start, destination, anyResult, resultCircuity, timer.getDuration(), path,
                false, "");
    }

    public List<Cluster> cluster(ApproachType type, int k, List<GeoLocation> locations) {
        return KMeans.fit(locations, k, 10_000, getDistanceEstimationByType(type));
    }
}
