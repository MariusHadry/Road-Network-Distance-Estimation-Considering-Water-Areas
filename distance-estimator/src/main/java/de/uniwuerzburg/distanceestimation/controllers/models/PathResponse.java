package de.uniwuerzburg.distanceestimation.controllers.models;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimateSerializer;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PathResponse(List<GeoLocation> path, long durationNanos,
                           @JsonSerialize(using = DistanceEstimateSerializer.class) @Schema(implementation = Double.class) DistanceEstimate distanceMeters) {
    public PathResponse(List<GeoLocation> path, long durationNanos, DistanceEstimate distanceMeters) {
        this.path = path;
        this.durationNanos = durationNanos;
        this.distanceMeters = distanceMeters;
    }

    @Override
    public DistanceEstimate distanceMeters() {
        return distanceMeters;
    }
}
