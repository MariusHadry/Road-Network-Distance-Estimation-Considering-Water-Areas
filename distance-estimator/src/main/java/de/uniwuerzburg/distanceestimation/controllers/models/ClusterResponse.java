package de.uniwuerzburg.distanceestimation.controllers.models;

import de.uniwuerzburg.distanceestimation.clustering.Cluster;
import de.uniwuerzburg.distanceestimation.estimation.ApproachType;

import java.util.List;

public record ClusterResponse(List<Cluster> clusters, int k, ApproachType approachType, long durationNanos) { }
