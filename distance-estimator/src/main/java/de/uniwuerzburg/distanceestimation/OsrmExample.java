package de.uniwuerzburg.distanceestimation;

import de.uniwuerzburg.distanceestimation.controllers.models.PathResponse;
import de.uniwuerzburg.distanceestimation.estimation.ApproachType;
import de.uniwuerzburg.distanceestimation.estimation.OsrmEstimation;
import de.uniwuerzburg.distanceestimation.estimation.clients.OsrmClient;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.services.DistanceEstimationService;
import de.uniwuerzburg.distanceestimation.services.PreprocessingService;

import java.util.Arrays;

public class OsrmExample {

    public static void main(String[] args) {
        OsrmClient client = new OsrmClient();
        OsrmEstimation estimation = new OsrmEstimation(client);
        DistanceEstimate d = estimation.estimateDistance(new GeoLocation(49.793627,9.523249), new GeoLocation(49.785525,9.49753));
        System.out.println(d.getMeters());

        DistanceEstimationService des = new DistanceEstimationService(new PreprocessingService());
        var result = des.estimateDistance(ApproachType.OSRM, new GeoLocation(49.793627,9.523249), new GeoLocation(49.785525,9.49753), true);
        PathResponse p =  new PathResponse(result.path() == null ? null : Arrays.stream(result.path().getCoordinates()).map(GeoLocation::new).toList(),
                result.timeNs(), result.result() == null ? result.resultCircuity() : result.result());

        System.out.println(p.path());
    }
}
