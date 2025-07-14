package de.uniwuerzburg.distanceestimation.controllers.models;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimateSerializer;
import io.swagger.v3.oas.annotations.media.Schema;


public record DistanceResponse(long durationNanos,
                               @JsonSerialize(using = DistanceEstimateSerializer.class) @Schema(implementation = Double.class) DistanceEstimate distanceMeters) {
    public DistanceResponse(long durationNanos, DistanceEstimate distanceMeters) {
        this.durationNanos = durationNanos;
        this.distanceMeters = distanceMeters;
    }

    @Override
    public DistanceEstimate distanceMeters() {
        return distanceMeters;
    }
}
