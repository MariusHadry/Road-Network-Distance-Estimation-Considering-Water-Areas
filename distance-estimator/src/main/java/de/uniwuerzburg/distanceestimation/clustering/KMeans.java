package de.uniwuerzburg.distanceestimation.clustering;

import de.uniwuerzburg.distanceestimation.estimation.DistanceEstimation;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;

import java.util.*;

public class KMeans {
    public static List<Cluster> fit(List<GeoLocation> locations, int k, int maxIterations, DistanceEstimation estimation) {

        List<GeoLocation> centroids = randomCentroids(locations, k, true);
        Map<GeoLocation, List<GeoLocation>> clusters = new HashMap<>();
        Map<GeoLocation, List<GeoLocation>> lastState = new HashMap<>();

        for (int i = 0; i < maxIterations; i++) {
            for (GeoLocation location : locations) {
                GeoLocation centroid = nearestCentroid(location, centroids, estimation);
                assignToCluster(clusters, location, centroid);
            }

            // terminate if there are no more changes. So neither the assignment to clusters nor the centroids are allowed to change!
            boolean shouldTerminate = clusters.equals(lastState);
            lastState = clusters;
            if (shouldTerminate) {
                break;
            }

            centroids = relocateCentroids(clusters);
            clusters = new HashMap<>();
        }

        return lastState.entrySet().stream().map(e -> new Cluster(e.getKey(), e.getValue())).toList();
    }

    private static List<GeoLocation> randomCentroids(List<GeoLocation> locations, int k, boolean deterministic) {
        double minLon = Double.MAX_VALUE;
        double maxLon = Double.MIN_VALUE;
        double minLat = Double.MAX_VALUE;
        double maxLat = Double.MIN_VALUE;
        List<GeoLocation> centroids = new ArrayList<>();

        for(GeoLocation l : locations) {
            minLon = Math.min(l.getLon(), minLon);
            maxLon = Math.max(l.getLon(), maxLon);
            minLat = Math.min(l.getLat(), minLat);
            maxLat = Math.max(l.getLat(), maxLat);
        }

        Random rand = new Random();
        if (deterministic){
            rand = new Random(42);
        }

        for (int i = 0; i < k; i++) {
            double randomLon = minLon + rand.nextDouble() * (maxLon - minLon);
            double randomLat = minLat + rand.nextDouble() * (maxLat - minLat);
            centroids.add(new GeoLocation(randomLat, randomLon));
        }

        return centroids;
    }

    private static GeoLocation nearestCentroid(GeoLocation location, List<GeoLocation> centroids, DistanceEstimation estimation) {
        double minimumDistance = Double.MAX_VALUE;
        GeoLocation nearest = null;

        for (GeoLocation centroid : centroids) {
            final var currentDistance = estimation.estimateDistance(location, centroid).getMeters();

            if (currentDistance < minimumDistance) {
                minimumDistance = currentDistance;
                nearest = centroid;
            }
        }

        return nearest;
    }

    private static void assignToCluster(Map<GeoLocation, List<GeoLocation>> clusters,
                                        GeoLocation location,
                                        GeoLocation centroid) {
        clusters.compute(centroid, (key, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }

            list.add(location);
            return list;
        });
    }

    private static List<GeoLocation> relocateCentroids(Map<GeoLocation, List<GeoLocation>> clusters) {
        return clusters.values().stream().map(KMeans::average).toList();
    }

    private static GeoLocation average(List<GeoLocation> locations) {
        return locations.stream().reduce((l1, l2) -> new GeoLocation((l1.getLat() + l2.getLat()) / 2, (l1.getLon() + l2.getLon()) / 2)).orElseThrow();
    }
}
