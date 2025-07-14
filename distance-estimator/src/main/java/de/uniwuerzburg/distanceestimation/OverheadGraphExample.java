package de.uniwuerzburg.distanceestimation;

import de.uniwuerzburg.distanceestimation.estimation.GreatCircleDistance;
import de.uniwuerzburg.distanceestimation.estimation.OsrmEstimation;
import de.uniwuerzburg.distanceestimation.estimation.OverheadGraphEstimation;
import de.uniwuerzburg.distanceestimation.estimation.clients.OsrmClient;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmLocation;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmNearestRequest;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmNearestResponse;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmRouteRequest;
import de.uniwuerzburg.distanceestimation.preprocessing.DatabaseAccess;
import de.uniwuerzburg.distanceestimation.preprocessing.OverheadGraphPreprocessing;
import de.uniwuerzburg.distanceestimation.util.DurationTimer;

import java.util.Set;

public class OverheadGraphExample {

    public static void main(String[] args) {
        testSnapping();
        queryExample();
        testRandomLocations();
        testNearestRequest();
        testRandomLocationsIntersections();
    }

    public static void testSnapping(){
        // there is no valid route because a ferry needs to be taken!
        OsrmRouteRequest r = new OsrmRouteRequest(new OsrmLocation(50.042498699,8.9804224),
                new OsrmLocation(49.793094,9.936605));

        OsrmClient client = new OsrmClient();
        var response = client.route(r);
        if (response != null) {
            System.out.println(response.toString());
        }
    }

    public static void queryExample(){
        DurationTimer timer = new DurationTimer();
        timer.start();
        int n_random_points = 16;
        OverheadGraphPreprocessing overheadGraphPreprocessing = new OverheadGraphPreprocessing(n_random_points);
        overheadGraphPreprocessing.preprocessing();
        timer.stop();
        System.out.println("Entire preprocessing took " + timer.getDurationSeconds() + "sec");

        OverheadGraphEstimation overheadGraphEstimation = new OverheadGraphEstimation(
                overheadGraphPreprocessing.getLocationKDTree(),
                overheadGraphPreprocessing.getCircuityLookupMap(),
                overheadGraphPreprocessing.getCircuityMinimumLookupMap(),
                n_random_points);
        GreatCircleDistance greatCircleDistance = new GreatCircleDistance();
        OsrmEstimation osrmEstimation = new OsrmEstimation(new OsrmClient());

        GeoLocation start = new GeoLocation(49.920917, 9.725350);
        GeoLocation dest = new GeoLocation(49.993722, 9.917718);
        System.out.println("overhead: " +  overheadGraphEstimation.estimateDistance(start, dest).getMeters());
        System.out.println("greatCircle: " +  greatCircleDistance.estimateDistance(start, dest).getMeters());
        System.out.println("OSRM: " +  osrmEstimation.estimateDistance(start, dest).getMeters());
    }

    public static boolean testRandomLocations(){
        DatabaseAccess dba = new DatabaseAccess();
        Set<GeoLocation> rand = dba.getRandomLocations(20);
        assert rand.size() == 20 : "Did not find 20 pints";
        return true;
    }

    public static boolean testRandomLocationsIntersections(){
        DatabaseAccess dba = new DatabaseAccess();
        Set<GeoLocation> rand = dba.getRandomIntersectionLocation(200);
        System.out.println("rand.size() = " + rand.size());
        System.out.println(rand);
        assert rand.size() == 20 : "Did not find 20 pints";
        return true;
    }

    public static boolean testNearestRequest(){
        OsrmClient client = new OsrmClient();
        OsrmNearestRequest request = new OsrmNearestRequest(new OsrmLocation(49.753437,9.885736));
        OsrmNearestResponse response = client.nearest(request);

        assert response.getClosestDistance() < 500 : "Distance seems wrong";
        assert !response.getClosestLocation().equals(new GeoLocation(49.749604,9.889363)) : "Location is off!";

        return true;
    }
}
