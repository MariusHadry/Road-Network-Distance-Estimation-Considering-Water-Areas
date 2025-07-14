package de.uniwuerzburg.distanceestimation.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.uniwuerzburg.distanceestimation.estimation.DistanceEstimation;
import org.locationtech.jts.geom.LineString;

public record EstimationResult(@JsonIgnore DistanceEstimation metric,
                               @JsonIgnore GeoLocation start, @JsonIgnore GeoLocation destination,
                               DistanceEstimate result,
                               DistanceEstimate resultCircuity, long timeNs, @JsonIgnore LineString path,
                               boolean failed, String errorMsg) {}
