package de.uniwuerzburg.distanceestimation;

import de.uniwuerzburg.distanceestimation.estimation.BridgeRouteEstimation;
import de.uniwuerzburg.distanceestimation.estimation.EuclideanDistance;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.preprocessing.BridgeRoutePreprocessing;
import de.uniwuerzburg.distanceestimation.util.DurationTimer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


public class BridgeRouteEstimationExample {
    private static final BridgeRoutePreprocessing bridgeRoutePreprocessing = new BridgeRoutePreprocessing();
    private static final boolean useSplitWaterAreas = true;

    public static void main(String[] args) {
        Factory.FACTORY.createPoint();
        DurationTimer timer = new DurationTimer(true);
        double avg = 0.0;
        ArrayList<Double> averages = new ArrayList<>();
        bridgeRoutePreprocessing.preprocessing();
        timer.stop();
        System.out.println("Preprocessing took: " + timer.getDurationMilliseconds() + " ms");

//        getWaterAreaSizeAndGeoJsonSimple();
//        getWaterAreaSizeAndGeoJsonSplit();

//        compareSplitUnsplit(new GeoLocation(50.514056,10.879033), new GeoLocation(50.510368,10.853044));
//        compareSplitUnsplit(new GeoLocation(49.887094,9.814757), new GeoLocation(49.911161,9.810646));
//        System.exit(0);

        // throw away first results
        ArrayList<Long> measuredTimes = getTimes(new GeoLocation(49.985769, 9.121876), new GeoLocation(49.781181,9.973124));

        // slow query 1
        measuredTimes = getTimes(new GeoLocation(50.000219,10.01155), new GeoLocation(50.174785,9.64002));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("[Slow] Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // slow query 2
        measuredTimes = getTimes(new GeoLocation(50.111591, 9.725067), new GeoLocation(50.124969, 9.760176));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("[Slow] Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // Aschaffenburg -> Würzburg
        measuredTimes = getTimes(new GeoLocation(49.985769, 9.121876), new GeoLocation(49.781181,9.973124));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // fast
        measuredTimes = getTimes(new GeoLocation(50.072676,9.178807), new GeoLocation(50.071494,9.204624));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("[Fast] Average Time for Calculation: " + avg + "ns");
        System.out.println("---");


        // alte Mainbrücke test
        measuredTimes = getTimes(new GeoLocation(49.793848, 9.918994), new GeoLocation(49.793056, 9.936595));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // Aschaffenburg -> Würzburg
        measuredTimes = getTimes(new GeoLocation(49.985769, 9.121876), new GeoLocation(49.781181,9.973124));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // additional edge check
        measuredTimes = getTimes(new GeoLocation(50.451058,10.281904), new GeoLocation(50.428449,10.302903));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // Talsperre Ratscher
        measuredTimes = getTimes(new GeoLocation(50.484644, 10.777801), new GeoLocation(50.494334, 10.797822));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // ferry check
        measuredTimes = getTimes(new GeoLocation(50.119847, 9.794878), new GeoLocation(50.108348, 9.772247));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // do not cross
        measuredTimes = getTimes(new GeoLocation(49.959984, 9.593918), new GeoLocation(49.892227, 9.591732));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // no water area test
        measuredTimes = getTimes(new GeoLocation(50.011555, 9.652666), new GeoLocation(49.992718, 9.681179));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // lake test
        measuredTimes = getTimes(new GeoLocation(50.160122, 10.363829), new GeoLocation(50.122980, 10.384127));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("Average Time for Calculation: " + avg + "ns");
        System.out.println("---");

        System.out.println("Overall average: " + averages.stream().mapToDouble(d -> d).average().orElse(-1.0));
        System.out.println("Standard Deviation: " + calculateStandardDeviation(averages));

        System.out.println("done!");
    }

    private static ArrayList<Long> getTimes(GeoLocation start, GeoLocation dest) {
        return getTimes(start, dest, 10_000);
    }

    private static void compareSplitUnsplit(GeoLocation start, GeoLocation dest) {
        BridgeRouteEstimation breNoSplit = BridgeRouteEstimation.buildApproach(bridgeRoutePreprocessing, new EuclideanDistance(),false, false);
        DistanceEstimate deNoSplit = breNoSplit.estimateDistance(start, dest);
        BridgeRouteEstimation breSplit = BridgeRouteEstimation.buildApproach(bridgeRoutePreprocessing, new EuclideanDistance(),false, true);
        DistanceEstimate deSplit = breSplit.estimateDistance(start, dest);

        System.out.println("\t estimated distance (Split):    " + deSplit.getMeters());
        System.out.println("\t estimated distance (No Split): " + deNoSplit.getMeters());
    }

    private static ArrayList<Long> getTimes(GeoLocation start, GeoLocation dest, int repetitions) {
        BridgeRouteEstimation bre = BridgeRouteEstimation.buildApproach(bridgeRoutePreprocessing, new EuclideanDistance(),false, useSplitWaterAreas);
        ArrayList<Long> measuredTimes = new ArrayList<>();

        DistanceEstimate de = null;
        for (int i = 0; i < repetitions; i++) {
            DurationTimer timerEstimation = new DurationTimer(true);
            de = bre.estimateDistance(start, dest);
            timerEstimation.stop();
            measuredTimes.add(timerEstimation.getDuration());
        }

        System.out.println("\t estimated distance: " + de.getMeters());
        return measuredTimes;
    }

    private static void getWaterAreaSizeAndGeoJsonSimple(){
        System.out.println("Analysis of Simple Water Areas");
        ArrayList<Double> waterAreaSizes = new ArrayList<>();
        ArrayList<Integer> waterAreaNumPoints = new ArrayList<>();

        for (Map.Entry<WaterArea, Set<Bridge>> entry: bridgeRoutePreprocessing.getWaterAreasWithBridgesMap().entrySet()) {
//            System.out.println("\t\tarea = " + entry.getValue().getArea());
//            System.out.println("area conv = " + Math.toRadians(entry.getValue().getArea()) * 6371000 * 100);

            waterAreaSizes.add(entry.getKey().getGeom().getArea());
            waterAreaNumPoints.add(entry.getKey().getGeom().getNumPoints());

//            GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
//            geoJsonWriter.setEncodeCRS(false);
//            System.out.println(geoJsonWriter.write(entry.getValue()));
//            System.out.println("--");
        }
        System.out.println("\tNum water areas = " + waterAreaSizes.size());
        System.out.println("\tAvg. water area size = " + waterAreaSizes.stream().mapToDouble(d -> d).average().orElse(-1.0));
        System.out.println("\tAvg. water area points = " + waterAreaNumPoints.stream().mapToDouble(d -> d).average().orElse(-1.0));
        System.out.println("....");
    }

    private static void getWaterAreaSizeAndGeoJsonSplit(){
        System.out.println("Analysis of Split Water Areas");
        ArrayList<Double> waterAreaSizes = new ArrayList<>();
        ArrayList<Integer> waterAreaNumPoints = new ArrayList<>();

        for (Map.Entry<WaterArea, Set<Bridge>> entry: bridgeRoutePreprocessing.getSplitWaterAreasWithBridges().entrySet()) {
//            System.out.println("\t\tarea = " + entry.getValue().getArea());
            waterAreaSizes.add(entry.getKey().getGeom().getArea());
            waterAreaNumPoints.add(entry.getKey().getGeom().getNumPoints());
        }
        System.out.println("\tNum water areas = " + waterAreaSizes.size());
        System.out.println("\tAvg. water area size = " + waterAreaSizes.stream().mapToDouble(d -> d).average().orElse(-1.0));
        System.out.println("\tAvg. water area points = " + waterAreaNumPoints.stream().mapToDouble(d -> d).average().orElse(-1.0));
        System.out.println("....");
    }

    public static double calculateStandardDeviation(ArrayList<Double> array) {
        // get the mean of array
        double mean = array.stream().mapToDouble(d -> d).average().orElse(-1.0);

        // calculate the standard deviation
        double standardDeviation = 0.0;
        for (double num : array) {
            standardDeviation += Math.pow(num - mean, 2);
        }

        return Math.sqrt(standardDeviation / array.size());
    }
}
