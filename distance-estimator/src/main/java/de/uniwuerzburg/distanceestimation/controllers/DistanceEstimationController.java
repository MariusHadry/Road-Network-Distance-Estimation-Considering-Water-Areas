package de.uniwuerzburg.distanceestimation.controllers;

import de.uniwuerzburg.distanceestimation.controllers.models.*;
import de.uniwuerzburg.distanceestimation.estimation.ApproachType;
import de.uniwuerzburg.distanceestimation.estimation.DistanceEstimation;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.services.DistanceEstimationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/estimation")
public class DistanceEstimationController {
    private final DistanceEstimationService distanceEstimationService;

    @Autowired
    public DistanceEstimationController(DistanceEstimationService distanceEstimationService) {
        this.distanceEstimationService = distanceEstimationService;
    }

    @GetMapping("/path")
    public @ResponseBody PathResponse path(@RequestParam double startLat, @RequestParam double startLon,
                                           @RequestParam double destLat, @RequestParam double destLon,
                                           @RequestParam ApproachType approachType) {

        var result = distanceEstimationService.estimateDistance(approachType, new GeoLocation(startLat, startLon), new GeoLocation(destLat, destLon), true);
        return new PathResponse(result.path() == null ? null : Arrays.stream(result.path().getCoordinates()).map(GeoLocation::new).toList(),
                result.timeNs(), result.result() == null ? result.resultCircuity() : result.result());
    }

    @GetMapping("/crossesWater")
    public @ResponseBody CrossesWaterResponse crossesWater(@RequestParam double startLat, @RequestParam double startLon,
                                           @RequestParam double destLat, @RequestParam double destLon) {
        boolean crossesWater = distanceEstimationService.crossesWater(new GeoLocation(startLat, startLon), new GeoLocation(destLat, destLon));
        boolean crossesRiver = distanceEstimationService.crossesRiver(new GeoLocation(startLat, startLon), new GeoLocation(destLat, destLon));
        return new CrossesWaterResponse(crossesWater, crossesRiver);
    }

    @GetMapping("/distance")
    public @ResponseBody DistanceResponse distance(@RequestParam double startLat, @RequestParam double startLon,
                                                   @RequestParam double destLat, @RequestParam double destLon,
                                                   @RequestParam ApproachType approachType) {
        var result = distanceEstimationService.estimateDistance(approachType, new GeoLocation(startLat, startLon), new GeoLocation(destLat, destLon), false);
        return new DistanceResponse(result.timeNs(), result.result() == null ? result.resultCircuity() : result.result());
    }

    @GetMapping("/overheadMeasurement")
    public @ResponseBody OverheadResponse overheadMeasurement(@RequestParam double startLat, @RequestParam double startLon,
                                                   @RequestParam double destLat, @RequestParam double destLon,
                                                   @RequestParam ApproachType approachType) {
        var tmp = distanceEstimationService.getDistanceEstimationByType(approachType);
        return new OverheadResponse(startLat, startLon, destLat, destLon, approachType);
    }

    @PostMapping("/cluster")
    public @ResponseBody ClusterResponse cluster(@RequestBody ClusterRequest request) {
        final long start = System.nanoTime();
        var result = distanceEstimationService.cluster(request.approachType(), request.k(), request.locations());
        final long end = System.nanoTime();
        return new ClusterResponse(result, request.k(), request.approachType(), end - start);
    }
}
