package de.uniwuerzburg.distanceestimation.controllers.models;

import de.uniwuerzburg.distanceestimation.estimation.ApproachType;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;

import java.util.List;

public record ClusterRequest(List<GeoLocation> locations, ApproachType approachType, int k) {
}
