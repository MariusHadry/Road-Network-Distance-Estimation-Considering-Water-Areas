package de.uniwuerzburg.distanceestimation.clustering;

import de.uniwuerzburg.distanceestimation.models.GeoLocation;

import java.util.List;

public record Cluster(GeoLocation representation, List<GeoLocation> elements) {
}
