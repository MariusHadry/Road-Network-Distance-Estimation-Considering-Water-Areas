package de.uniwuerzburg.distanceestimation;

import de.uniwuerzburg.distanceestimation.estimation.EuclideanDistance;
import de.uniwuerzburg.distanceestimation.estimation.WaterGraphEstimation;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.preprocessing.WaterGraphPreprocessing;
import de.uniwuerzburg.distanceestimation.util.DurationTimer;

import java.util.ArrayList;


public class WaterGraphExample {
    private static final WaterGraphPreprocessing waterGraphPreprocessing = new WaterGraphPreprocessing(false);

    public static void main(String[] args) {
        Factory.FACTORY.createPoint();
        DurationTimer timer = new DurationTimer(true);
        double avg = 0.0;
        ArrayList<Double> averages = new ArrayList<>();
        waterGraphPreprocessing.preprocessing(new EuclideanDistance());
        timer.stop();
        System.out.println("Preprocessing took: " + timer.getDurationMilliseconds() + " ms");

        // throw away first results
        ArrayList<Long> measuredTimes = getTimes(new GeoLocation(50.01, 9.121876), new GeoLocation(49.781181,9.973124));


        // slow (does cross water area, but not river)
        System.out.println("[slow, crosses water]");
        measuredTimes = getTimes(new GeoLocation(50.361597,10.450895), new GeoLocation(50.35464,10.415236));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("\tAverage Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // slow 2 (does cross water area, but not river)
        System.out.println("[slow, crosses water]");
        measuredTimes = getTimes(new GeoLocation(50.238297,9.796348), new GeoLocation(50.250936,9.820205));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("\tAverage Time for Calculation: " + avg + "ns");
        System.out.println("---");

        // slow 3 (does not cross water area)
        System.out.println("[slow, no water]");
        measuredTimes = getTimes(new GeoLocation(49.683443,10.683768), new GeoLocation(49.698154,10.646869));
        avg = measuredTimes.stream().mapToDouble(d -> d).average().orElse(-1.0);
        averages.add(avg);
        System.out.println("\tAverage Time for Calculation: " + avg + "ns");
        System.out.println("---");


        System.out.println("############");
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

        System.out.println("done!");
    }

    private static ArrayList<Long> getTimes(GeoLocation start, GeoLocation dest, int repetitions) {
        WaterGraphEstimation wge = new WaterGraphEstimation(waterGraphPreprocessing.getWaterGraphs(),
                waterGraphPreprocessing.getSimpleSplitWaterAreas(), waterGraphPreprocessing.getWaterGraphEdges(),
                new EuclideanDistance(),true, waterGraphPreprocessing.getNewBridges(), waterGraphPreprocessing.getWaterAreaTree());

        ArrayList<Long> measuredTimes = new ArrayList<>();

        DistanceEstimate de = null;
        for (int i = 0; i < repetitions; i++) {
            DurationTimer timerEstimation = new DurationTimer(true);
            de = wge.estimateDistance(start, dest);
            timerEstimation.stop();
            measuredTimes.add(timerEstimation.getDuration());
        }

        System.out.println("\t estimated distance: " + de.getMeters());
        return measuredTimes;
    }

    private static ArrayList<Long> getTimes(GeoLocation start, GeoLocation dest) {
        return getTimes(start, dest, 10_000);
    }
}
