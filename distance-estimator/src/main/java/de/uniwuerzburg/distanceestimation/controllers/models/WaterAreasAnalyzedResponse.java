package de.uniwuerzburg.distanceestimation.controllers.models;

import de.uniwuerzburg.distanceestimation.estimation.ApproachType;

public record WaterAreasAnalyzedResponse (double startLat, double startLon, double destLat, double destLon,
                                          int wgWaterAreasAnalyzed, int wgWaterIntersected,
                                          int breWaterAreasAnalyzed, int breWaterIntersected,
                                          int bresWaterAreasAnalyzed, int bresWaterIntersected) {
}
