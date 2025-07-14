package de.uniwuerzburg.distanceestimation.models.valhalla;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;

public record ValhallaSummary(@JsonDeserialize(using = ValhallaDistanceDeserializer.class) DistanceEstimate length) {
}
