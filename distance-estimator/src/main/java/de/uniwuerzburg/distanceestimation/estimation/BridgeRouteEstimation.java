package de.uniwuerzburg.distanceestimation.estimation;

import com.github.davidmoten.rtree.RTree;
import de.uniwuerzburg.distanceestimation.models.*;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.preprocessing.BridgeRoutePreprocessing;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import java.util.*;

public class BridgeRouteEstimation extends DirectLineEstimation {
    private final boolean recalculated;
    private final boolean splitWaterAreas;
    protected final Map<WaterArea, Set<Bridge>> waterAreasWithBridgesMap;
    private List<Bridge> lastBridgesUsed;

    private BridgeRouteEstimation(Map<WaterArea, Set<Bridge>> waterAreasWithBridgesMap,
                                 Map<WaterArea, Geometry> simpleWaterAreasMap,
                                 RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree,
                                 AirlineDistance metric, boolean recalculated, boolean splitWaterAreas) {
        super(simpleWaterAreasMap, new ArrayList<>(waterAreasWithBridgesMap.keySet()), waterAreaTree, metric);
        this.waterAreasWithBridgesMap = waterAreasWithBridgesMap;
        this.recalculated = recalculated;
        this.splitWaterAreas = splitWaterAreas;
    }

    public BridgeRouteEstimation(BridgeRoutePreprocessing bridgeRoutePreprocessing, AirlineDistance metric, boolean recalculated) {
        this(bridgeRoutePreprocessing.getSplitWaterAreasWithBridges(),
                bridgeRoutePreprocessing.getSimpleSplitWaterAreasMap(),
                bridgeRoutePreprocessing.getSplitWaterAreasTree(),
                metric,recalculated, true);
    }

    public static BridgeRouteEstimation buildApproach(BridgeRoutePreprocessing bridgeRoutePreprocessing, AirlineDistance metric, boolean recalculated, boolean splitWaterAreas) {
        if (splitWaterAreas) {
            return new BridgeRouteEstimation(bridgeRoutePreprocessing.getSplitWaterAreasWithBridges(),
                    bridgeRoutePreprocessing.getSimpleSplitWaterAreasMap(),
                    bridgeRoutePreprocessing.getSplitWaterAreasTree(),
                    metric,recalculated, true);
        }
        else {
            return new BridgeRouteEstimation(bridgeRoutePreprocessing.getWaterAreasWithBridgesMap(),
                    bridgeRoutePreprocessing.getSimpleWaterAreasMap(),
                    bridgeRoutePreprocessing.getWaterAreaTree(),
                    metric,recalculated, false);
        }
    }

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        lastBridgesUsed = new ArrayList<>();
        lastDistanceCircuity = DistanceEstimate.zero;

        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        var res = calculateRecursive(start, dest, DistanceEstimate.zero, new HashSet<>(),
                recalculated, 1, null, null);
        Debug.message("---");
        return res;
    }

    private DistanceEstimate calculateRecursive(GeoLocation start, GeoLocation dest, DistanceEstimate savedDistance,
                                                Set<Bridge> previousBridges, boolean recalculated, int step,
                                                //These parameters are only used in not-recalculated mode, else they get overwritten each time
                                                Map<LineString, WaterArea> intersectionWaterAreasMap,
                                                List<Map.Entry<LineString, Double>> intersectionsSortedByDistanceList) {
        Debug.message("---Recursion Step " + step + "---");

        /* This approach has two modes, recalculated and not-recalculated:
        Recalculated: The Intersections are recalculated in each step based on a line from the last found bridge to the dest
        Not-Recalculated: The intersections are always from initial start to dest and nearest/skipped ones gets removed
         */
        if (recalculated || intersectionWaterAreasMap == null) {
            DirectLine directLine = new DirectLine(start, dest);
            Debug.startDebugTimer();
            intersectionWaterAreasMap = getIntersections(directLine, true);
            Debug.stopDebugTimer("Get all intersections of Start-Dest-Line with Water Areas");
            Debug.startDebugTimer();
            intersectionsSortedByDistanceList = sortIntersectionsByDistance(directLine, intersectionWaterAreasMap, step);
            Debug.stopDebugTimer("Get Distance of Start to Intersections in sorted List");
        }

        // no Water Areas remaining
        if (intersectionsSortedByDistanceList.isEmpty()) {
            Debug.message("No Intersections with Water Areas remaining.");
            return calculateDistanceWithMetric(start, dest, savedDistance);
        }


        // Find nearest Bridge of nearest Intersection
        Debug.startDebugTimer();
        int nextIndexWhenNotRecalculating = 0;
        Bridge nearestBridge = null;
        boolean bridgeFound = false;
        for (Map.Entry<LineString, Double> entry : intersectionsSortedByDistanceList) {
            LineString i = entry.getKey();
            WaterArea w = intersectionWaterAreasMap.get(i);
            Set<Bridge> bridges = waterAreasWithBridgesMap.get(w);
            double minDistance = Double.MAX_VALUE;
            for (Bridge b : bridges) {
                double distance = i.distance(b.geom());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestBridge = b;
                }
            }
            nextIndexWhenNotRecalculating++;
            // should not lead to previous bridges -> infinity loop
            if (nearestBridge == null || !previousBridges.contains(nearestBridge)) {
                bridgeFound = true;
                Debug.message("Intersection: " + i.getCoordinate().y + " " + i.getCoordinate().x +
                        " with Water Area " + w.getName());
                break;
            }
        }
        Debug.stopDebugTimer("Find nearest Bridge of nearest (not skipped) Intersection");

        if (nearestBridge == null || !bridgeFound) {
            return calculateDistanceWithMetric(start, dest, savedDistance);
        }
        // In not-recalculated mode, remove all skipped and the used intersection from
        if (!recalculated) {
            intersectionsSortedByDistanceList = intersectionsSortedByDistanceList.subList(nextIndexWhenNotRecalculating,
                    intersectionsSortedByDistanceList.size());
        }

        // Calculate Distance to Bridge with Metric
        Debug.startDebugTimer();
        var anyBridgePoint = new GeoLocation(nearestBridge.geom().getCoordinate());    //Any Point should be okay
        savedDistance = calculateDistanceWithMetric(start, anyBridgePoint, savedDistance);

        lastBridgesUsed.add(nearestBridge);
        previousBridges.add(nearestBridge);

        Debug.stopDebugTimer("Calculate Distance to Bridge with Metric");

        // Recursive call with Bridge as new start
        return calculateRecursive(anyBridgePoint, dest, savedDistance, previousBridges,
                recalculated, step + 1, intersectionWaterAreasMap, intersectionsSortedByDistanceList);
    }

    public boolean crossesWater(GeoLocation start, GeoLocation dest){
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        DirectLine directLine = new DirectLine(start, dest);
        Map<LineString, WaterArea> intersectionWaterAreasMap = getIntersections(directLine, true);

        return !intersectionWaterAreasMap.isEmpty();
    }

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        estimateDistance(start, dest);
        Coordinate[] coordinates = new Coordinate[lastBridgesUsed.size() + 2];
        coordinates[0] = start;
        for (int i = 0; i < lastBridgesUsed.size(); i++) {
            coordinates[i + 1] = lastBridgesUsed.get(i).geom().getCoordinate();
        }
        coordinates[lastBridgesUsed.size() + 1] = dest;
        return Factory.FACTORY.createLineString(coordinates);
    }

    @Override
    public ApproachType getApproachType() {
        if (recalculated && !splitWaterAreas) {
            return ApproachType.BRIDGE_REC;
        }
        else if (!recalculated && !splitWaterAreas) {
            return ApproachType.BRIDGE_NO_REC;
        }
        else if (recalculated && splitWaterAreas) {
            return ApproachType.BRIDGE_SPLIT_REC;
        }
        else {
            return ApproachType.BRIDGE_SPLIT_NO_REC;
        }
    }

    @Override
    public DistanceEstimation copyApproach() {
        return new BridgeRouteEstimation(waterAreasWithBridgesMap, simpleWaterAreasMap, waterAreaTree, metric,
                recalculated, splitWaterAreas);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BridgeRouteEstimation that = (BridgeRouteEstimation) o;
        return recalculated == that.recalculated && Objects.equals(waterAreasWithBridgesMap, that.waterAreasWithBridgesMap) && Objects.equals(lastBridgesUsed, that.lastBridgesUsed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), recalculated, waterAreasWithBridgesMap, lastBridgesUsed);
    }

    public List<Bridge> getLastBridgesUsed() {
        return lastBridgesUsed;
    }
}
