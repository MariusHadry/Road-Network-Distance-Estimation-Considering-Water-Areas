package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.preprocessing.WaterGraphPreprocessing;
import org.locationtech.jts.geom.LineString;

public class HybridWaterGraphEstimation extends DirectLineEstimation {

    private final int CUTOFF_DISTANCE = 15_000;
    private final DistanceEstimation CUTOFF_MEASURE = new GreatCircleDistance();

    private final WaterGraphPreprocessing waterGraphPreprocessing;
    private final WaterGraphEstimation waterGraphEstimation;

    private final DistanceEstimation fallbackEstimator;
    private final double circuityFallbackEstimator;
    private final ApproachType approachType;

    public HybridWaterGraphEstimation(WaterGraphPreprocessing waterGraphPreprocessing, DistanceEstimation fallbackEstimator,
                                      double circuityFallbackEstimator, ApproachType approachType) {
        super(null, null, null, new EuclideanDistance());
        this.waterGraphPreprocessing = waterGraphPreprocessing;
        this.fallbackEstimator = fallbackEstimator;
        this.circuityFallbackEstimator = circuityFallbackEstimator;
        this.approachType = approachType;
        this.waterGraphEstimation = new WaterGraphEstimation(this.waterGraphPreprocessing, new EuclideanDistance(), true);
    }


    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        DistanceEstimate dist = CUTOFF_MEASURE.estimateDistance(start, dest);

        if (dist.getMeters() > CUTOFF_DISTANCE) {
            return fallbackEstimator.estimateDistance(start, dest).multiply(circuityFallbackEstimator);
        }

        return this.waterGraphEstimation.estimateDistance(start, dest);
    }

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        DistanceEstimate dist = CUTOFF_MEASURE.estimateDistance(start, dest);

        if (dist.getMeters() > CUTOFF_DISTANCE) {
            return fallbackEstimator.getPath(start, dest);
        }

        return this.waterGraphEstimation.getPath(start, dest);
    }

    @Override
    public ApproachType getApproachType() {
        return this.approachType;
    }

    @Override
    public DistanceEstimation copyApproach() {
        return new HybridWaterGraphEstimation(this.waterGraphPreprocessing, this.fallbackEstimator,
                this.circuityFallbackEstimator, this.approachType);
    }
}
