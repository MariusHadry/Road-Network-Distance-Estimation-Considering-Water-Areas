package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.preprocessing.BridgeRoutePreprocessing;
import org.locationtech.jts.geom.LineString;


public class HybridBridgeRouteEstimation extends DirectLineEstimation {
    private final int CUTOFF_DISTANCE = 15_000;
    private final DistanceEstimation CUTOFF_MEASURE = new GreatCircleDistance();

    private final BridgeRoutePreprocessing bridgeRoutePreprocessing;
    private final BridgeRouteEstimation bridgeRouteEstimation;

    private final DistanceEstimation fallbackEstimator;
    private final double circuityBridgeRoute;
    private final double circuityFallbackEstimator;
    private final ApproachType approachType;

    public HybridBridgeRouteEstimation(BridgeRoutePreprocessing bridgeRoutePreprocessing, DistanceEstimation fallbackEstimator,
                                       double circuityBridgeRoute, double circuityFallbackEstimator,
                                       ApproachType approachType) {
        super(null, null, null, new EuclideanDistance() );
        this.bridgeRouteEstimation = BridgeRouteEstimation.buildApproach(bridgeRoutePreprocessing,
                new EuclideanDistance(), false, true);

        this.bridgeRoutePreprocessing = bridgeRoutePreprocessing;
        this.fallbackEstimator = fallbackEstimator;
        this.circuityBridgeRoute = circuityBridgeRoute;
        this.circuityFallbackEstimator = circuityFallbackEstimator;
        this.approachType = approachType;
    }

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        DistanceEstimate dist = CUTOFF_MEASURE.estimateDistance(start, dest);

        if (dist.getMeters() > CUTOFF_DISTANCE) {
            return fallbackEstimator.estimateDistance(start, dest).multiply(circuityFallbackEstimator);
        }

        return this.bridgeRouteEstimation.estimateDistance(start, dest).multiply(circuityBridgeRoute);
    }

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        DistanceEstimate dist = CUTOFF_MEASURE.estimateDistance(start, dest);

        if (dist.getMeters() > CUTOFF_DISTANCE) {
            return fallbackEstimator.getPath(start, dest);
        }

        return this.bridgeRouteEstimation.getPath(start, dest);
    }

    @Override
    public ApproachType getApproachType() {
        return this.approachType;
    }

    @Override
    public DistanceEstimation copyApproach() {
        return new HybridBridgeRouteEstimation(this.bridgeRoutePreprocessing, this.fallbackEstimator,
                this.circuityBridgeRoute, this.circuityFallbackEstimator, this.approachType);
    }
}
