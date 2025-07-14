package de.uniwuerzburg.distanceestimation.controllers.models;

import de.uniwuerzburg.distanceestimation.estimation.ApproachType;

public record OverheadResponse (double startLat, double startLon, double destLat, double destLon, ApproachType approachType) {
}
