package de.uniwuerzburg.distanceestimation.preprocessing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.harium.storage.kdtree.KDTree;
import com.harium.storage.kdtree.exception.KeyDuplicateException;
import de.uniwuerzburg.distanceestimation.estimation.DistanceEstimation;
import de.uniwuerzburg.distanceestimation.estimation.GreatCircleDistance;
import de.uniwuerzburg.distanceestimation.estimation.OsrmEstimation;
import de.uniwuerzburg.distanceestimation.estimation.clients.OsrmClient;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmLocation;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmRouteRequest;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmRouteResponse;
import de.uniwuerzburg.distanceestimation.util.Debug;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OverheadGraphPreprocessing {
    private final int KD_TREE_DIMENSION = 2;
    private final String PREPROCESSING_PATH;
    private final int N_RANDOM_POINTS;

    private KDTree<GeoLocation> locationKDTree;
    private HashMap<GeoLocation, HashMap<GeoLocation, Double>> circuityLookupMap;
    private HashMap<GeoLocation, Double> circuityMinimumLookupMap;

    public OverheadGraphPreprocessing(int n_random_points) {
        this.locationKDTree = new KDTree<>(KD_TREE_DIMENSION);
        this.circuityLookupMap = new HashMap<>();
        this.circuityMinimumLookupMap = new HashMap<>();
        this.N_RANDOM_POINTS = n_random_points;
        this.PREPROCESSING_PATH = "preprocessing_files" + File.separator + "ohg_" + N_RANDOM_POINTS;
    }

    public void preprocessing() {
        if (loadPreprocessingFromDisk()) {
            Debug.message("loaded overhead graph preprocessing from disk");
            return;
        }

        int duplicateCounter = 0;

        DatabaseAccess dba = new DatabaseAccess();
        // 1. generate random Locations
        Set<GeoLocation> randomLocations = dba.getRandomIntersectionLocation(this.N_RANDOM_POINTS);
        saveLocationsToDisk(randomLocations);

        // 2. Snap random Locations and insert them into kdTree
        List<GeoLocation> snappedLocations = new ArrayList<>();
        OsrmClient osrmClient = new OsrmClient();
        for (GeoLocation location : randomLocations) {
            GeoLocation responseLocation = getSnappedLocation(osrmClient, location);

            if (responseLocation == null) {
                continue;
            }

            if (locationKDTree.isEmpty()) {
                snappedLocations.add(responseLocation);
                locationKDTree.insert(new double[]{responseLocation.getLat(), responseLocation.getLon()}, responseLocation);
            } else {
                GeoLocation nearestExistingLocation = locationKDTree.nearest(new double[]{responseLocation.getLat(), responseLocation.getLon()});
                // make sure to not insert the same location twice!
                if (!nearestExistingLocation.equals(location)) {
                    snappedLocations.add(responseLocation);
                    try {
                        locationKDTree.insert(new double[]{responseLocation.getLat(), responseLocation.getLon()}, responseLocation);
                    } catch (KeyDuplicateException e) {
                        duplicateCounter++;
                    }
                }
            }
        }

        Debug.message("There were **" + duplicateCounter + "** duplicate(s), when inserting random location into " +
                "the KD-Tree! The Duplicates are ignored in the following steps.");

        // 4. calculate distances **and** circuity factors between all random locations using osrm and great circle distance
        OsrmEstimation osrmEstimation = new OsrmEstimation(osrmClient);
        GreatCircleDistance greatCircleDistance = new GreatCircleDistance();

        int number_of_all_iterations = snappedLocations.size() * snappedLocations.size();
        int current_iteration = 0;

        for (int i = 0; i < snappedLocations.size(); i++) {
            for (int j = 0; j < snappedLocations.size(); j++) {
                current_iteration += 1;

                if (current_iteration % 1000 == 0) {
                    System.out.println(current_iteration + "/" + number_of_all_iterations);
                }

                if (i == j) continue;

                GeoLocation start = snappedLocations.get(i);
                GeoLocation dest = snappedLocations.get(j);

                if (!isInLookupMap(start, dest)) {
                    // calculate distances
                    double distOsrm = osrmEstimation.estimateDistance(start, dest).getMeters();
                    double distGreatCircle = greatCircleDistance.estimateDistance(start, dest).getMeters();

                    if (Double.isFinite(distOsrm) && Double.isFinite(distGreatCircle)) {
                        insertIntoCircuityLookupMap(start, dest, distOsrm / distGreatCircle);
                    } else {
                        Debug.message("some value was not finite: ");
                        Debug.message("\t- start = " + start);
                        Debug.message("\t- dest = " + dest);
                        Debug.message("\t- distOsrm = " + distOsrm);
                        Debug.message("\t- distGreatCircle = " + distGreatCircle);
                    }

                }
            }
        }

        // 5. fill average lookup map to save some time later
        fillCircuityMinimumLookupMap();

        // save preprocessing for later usage
        savePreprocessingToDisk();
    }

    private GeoLocation getSnappedLocation(OsrmClient osrmClient, GeoLocation location){
        OsrmLocation referenceLocation = new OsrmLocation(new GeoLocation(49.793094, 9.936605));
        OsrmRouteRequest request = new OsrmRouteRequest(new OsrmLocation(location), referenceLocation);

        OsrmRouteResponse response = null;
        response = osrmClient.route(request);
        if (response != null) {
            double[] snappedLocation = response.waypoints()[0].location();  // returned as lon, lat
            return new GeoLocation(snappedLocation[1], snappedLocation[0]);
        }

        return null;
    }

    private boolean isInLookupMap(GeoLocation start, GeoLocation dest) {
        // ensure same order of start dest
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        // only add entries if they did not exist
        if (circuityLookupMap.containsKey(start) && circuityLookupMap.get(start).containsKey(dest)) {
            return true;
        }

        return false;
    }

    private void fillCircuityMinimumLookupMap() {
        for (GeoLocation geoLocation : circuityLookupMap.keySet()) {
            OptionalDouble optionalCircuityFactor = circuityLookupMap.get(geoLocation).values().stream().
                    mapToDouble(Double::doubleValue).min();
            if (optionalCircuityFactor.isEmpty()) System.err.println("Error when getting the average!");

            double circuityFactor = optionalCircuityFactor.isPresent() && Double.isFinite(optionalCircuityFactor.getAsDouble()) ?
                    optionalCircuityFactor.getAsDouble() : DistanceEstimation.CIRCUITY_FACTOR_GERMANY;

            circuityMinimumLookupMap.put(geoLocation, circuityFactor);
        }
    }

    private void insertIntoCircuityLookupMap(GeoLocation start, GeoLocation dest, Double circuity) {
        // ensure same order of start dest
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        // only add entries if they did not exist
        if (!circuityLookupMap.containsKey(start)) {
            circuityLookupMap.put(start, new HashMap<>());
        }

        if (!circuityLookupMap.get(start).containsKey(dest)) {
            circuityLookupMap.get(start).put(dest, circuity);
        }

        // also insert the reverse direction, but with the same value!
        if (!circuityLookupMap.containsKey(dest)) {
            circuityLookupMap.put(dest, new HashMap<>());
        }

        if (!circuityLookupMap.get(dest).containsKey(start)) {
            circuityLookupMap.get(dest).put(start, circuity);
        }
    }

    public void savePreprocessingToDisk() {
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().enableComplexMapKeySerialization().create();

        try {
            File directory = new File(PREPROCESSING_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // convert map so it is String -> String -> Double
            Map<String, Map<String, Double>> mapToWrite = new HashMap<>();
            for (GeoLocation startKey : this.circuityLookupMap.keySet()) {
                for (GeoLocation destKey : this.circuityLookupMap.get(startKey).keySet()) {
                    String start = startKey.toString();
                    String dest = destKey.toString();

                    if (!mapToWrite.containsKey(start)) {
                        mapToWrite.put(start, new HashMap<>());
                    }
                    mapToWrite.get(start).put(dest, this.circuityLookupMap.get(startKey).get(destKey));
                }
            }

            Type type = new TypeToken<Map<String, Map<String, Double>>>(){}.getType();
            Writer writer = new FileWriter(PREPROCESSING_PATH + File.separator + "ohg_circuityLookupMap_" + this.N_RANDOM_POINTS + ".json", StandardCharsets.UTF_8);
            gson.toJson(mapToWrite, type, writer);
            writer.flush();
            writer.close();


            // convert map so it is String -> Double
            Map<String, Double> mapToWrite2 = new HashMap<>();
            for (GeoLocation key : this.circuityMinimumLookupMap.keySet()) {
                String loc = key.toString();
                mapToWrite2.put(loc, this.circuityMinimumLookupMap.get(key));
            }

            type = new TypeToken<Map<String, Double>>(){}.getType();
            writer = new FileWriter(PREPROCESSING_PATH + File.separator + "ohg_circuityMinimumLookupMap_" + this.N_RANDOM_POINTS + ".json", StandardCharsets.UTF_8);
            gson.toJson(mapToWrite2, type, writer);
            writer.flush();
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return true, if loading was successful, false else
     */
    public boolean loadPreprocessingFromDisk() {
        File circuityLookupFile = new File(PREPROCESSING_PATH + File.separator + "ohg_circuityLookupMap_" + this.N_RANDOM_POINTS + ".json");
        File circuityAverageLookupFile = new File(PREPROCESSING_PATH + File.separator + "ohg_circuityMinimumLookupMap_" + this.N_RANDOM_POINTS + ".json");

        if (!circuityLookupFile.exists() || circuityLookupFile.isDirectory() ||
                !circuityAverageLookupFile.exists() || circuityAverageLookupFile.isDirectory()) {
            return false;
        }

        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().enableComplexMapKeySerialization().create();
        try {
            // load circuity lookup map
            Type type = new TypeToken<Map<String, Map<String, Double>>>(){}.getType();
            BufferedReader bufferedReader = new BufferedReader(new FileReader(circuityLookupFile));
            Map<String, Map<String, Double>> loadedCircuityLookupMap = gson.fromJson(bufferedReader, type);
            this.circuityMinimumLookupMap = new HashMap<>();

            for (String startKey : loadedCircuityLookupMap.keySet()) {
                for (String destKey : loadedCircuityLookupMap.get(startKey).keySet()) {
                    GeoLocation start = GeoLocation.fromString(startKey);
                    GeoLocation dest = GeoLocation.fromString(destKey);

                    if (!circuityLookupMap.containsKey(start)) {
                        circuityLookupMap.put(start, new HashMap<>());
                    }
                    circuityLookupMap.get(start).put(dest, loadedCircuityLookupMap.get(startKey).get(destKey));
                }
            }

            // load average circuity lookup map and KDTree creation
            type = new TypeToken<Map<String, Double>>(){}.getType();
            bufferedReader = new BufferedReader(new FileReader(circuityAverageLookupFile));
            Map<String, Double> loadedCircuityAverageLookupMap = gson.fromJson(bufferedReader, type);
            this.circuityMinimumLookupMap = new HashMap<>();
            this.locationKDTree = new KDTree<>(KD_TREE_DIMENSION);

            for (String key : loadedCircuityAverageLookupMap.keySet()) {
                GeoLocation loc = GeoLocation.fromString(key);
                this.circuityMinimumLookupMap.put(loc, loadedCircuityAverageLookupMap.get(key));
                this.locationKDTree.insert(new double[]{loc.getLat(), loc.getLon()}, loc);
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void saveLocationsToDisk(Set<GeoLocation> randomLocations) {
        ArrayList<GeoLocation> locationsList = new ArrayList<>();
        double[][] coordinates = new double[randomLocations.size()][2];
        int pos = 0;

        for (GeoLocation geoLocation : randomLocations) {
            coordinates[pos++] = new double[]{geoLocation.getLon(), geoLocation.getLat()};
            locationsList.add(geoLocation);
        }

        DatabaseAccess.GeoJsonMultiPoint geoJson = new DatabaseAccess.GeoJsonMultiPoint("MultiPoint", coordinates);

        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().enableComplexMapKeySerialization().create();
        try {
            File directory = new File(PREPROCESSING_PATH);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            Writer writer = new FileWriter(PREPROCESSING_PATH + File.separator + "randomLocations_" + randomLocations.size() + ".geojson");
            gson.toJson(geoJson, writer);
            writer.flush();
            writer.close();

            writer = new FileWriter(PREPROCESSING_PATH + File.separator + "randomLocations_" + randomLocations.size() + ".json");
            gson.toJson(locationsList, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public KDTree<GeoLocation> getLocationKDTree() {
        return locationKDTree;
    }

    public HashMap<GeoLocation, Double> getCircuityMinimumLookupMap() {
        return circuityMinimumLookupMap;
    }

    public HashMap<GeoLocation, HashMap<GeoLocation, Double>> getCircuityLookupMap() {
        return circuityLookupMap;
    }
}
