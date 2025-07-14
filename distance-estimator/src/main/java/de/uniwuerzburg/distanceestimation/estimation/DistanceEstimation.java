package de.uniwuerzburg.distanceestimation.estimation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import org.locationtech.jts.geom.LineString;

public interface DistanceEstimation {
    float CIRCUITY_FACTOR_GERMANY = 1.32f;

    @JsonIgnore
        // This method should be used when measuring accuracy and time
    DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest);

    @JsonIgnore
        // This method should not be compared and is just for visualizing the data
    LineString getPath(GeoLocation start, GeoLocation dest);

    ApproachType getApproachType();

    @JsonIgnore
    DistanceEstimation copyApproach();
}
