package de.uniwuerzburg.distanceestimation.estimation;

import com.github.davidmoten.rtree.RTree;
import de.uniwuerzburg.distanceestimation.models.DirectLine;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.preprocessing.BridgeRoutePreprocessing;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.apache.commons.lang3.tuple.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.*;
import java.util.stream.Collectors;

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

        var res = calculateRecursive(start, dest, DistanceEstimate.zero, new HashSet<>(),
                recalculated, 1, null, null);
        Debug.message("---");
        return res;
    }

    private List<Pair<LineString, WaterArea>> removeDoubleCrossings(List<Pair<LineString, WaterArea>> intersections) {
        /**
         * Removes intersections if the same water area is intersected multiple times and no actual crossing of the
         * water area is required.
         *
         * @param intersections A list of Pair objects containing the intersection geometry and the associated WaterArea, sorted by distance.
         * @return A filtered list containing only valid crossings.
         */

        if (intersections == null || intersections.isEmpty()) return new ArrayList<>();

        List<Pair<LineString, WaterArea>> result = new ArrayList<>();
        int n = intersections.size();
        int i = 0;

        while (i < n) {
            int j = i;
            long currentId = intersections.get(i).getRight().getInstanceId();

            // Find the boundary of the current group of identical IDs
            while (j < n && intersections.get(j).getRight().getInstanceId() == currentId) {
                j++;
            }

            // If odd, keep one -> we need to cross the river once
            // If even, keep none -> we do not need to cross the river
            int groupSize = j - i;
            if (groupSize % 2 != 0) {
                result.add(intersections.get(i));
            }

            i = j; // Move pointer to the start of the next ID group
        }

        return result;
    }

    private DistanceEstimate calculateRecursive(GeoLocation start, GeoLocation dest, DistanceEstimate savedDistance,
                                                Set<Bridge> previousBridges, boolean recalculated, int step,
                                                //These parameters are only used in not-recalculated mode, else they get overwritten each time
                                                List<Pair<LineString, WaterArea>> intersections,
                                                List<Pair<LineString, WaterArea>> intersectionsSortedByDistanceList) {
//        Debug.message("---Recursion Step " + step + "---");

        /* This approach has two modes, recalculated and not-recalculated:
        Recalculated: The Intersections are recalculated in each step based on a line from the last found bridge to the dest
        Not-Recalculated: The intersections are always from initial start to dest and nearest/skipped ones gets removed
         */
        if (recalculated || intersections == null) {
            DirectLine directLine = new DirectLine(start, dest);
            intersections = getIntersections(directLine, true);
            intersectionsSortedByDistanceList = sortIntersectionsByDistance(directLine, intersections);
            intersectionsSortedByDistanceList = removeDoubleCrossings(intersectionsSortedByDistanceList);
        }

        // no Water Areas remaining
        if (intersectionsSortedByDistanceList.isEmpty()) {
            Debug.message("No Intersections with Water Areas remaining.");
            return calculateDistanceWithMetric(start, dest, savedDistance);
        }

        if (Debug.DEBUG){
            GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
            geoJsonWriter.setEncodeCRS(false);

            List<Geometry> lst = new ArrayList<>();

            for (Pair<LineString, WaterArea> pair : intersectionsSortedByDistanceList) {
                lst.add(pair.getRight().getGeom());
            }

            String polyFeatures = lst.stream()
                    .map(geom -> {
                        String jsonGeom = geoJsonWriter.write(geom);
                        return "{ \"type\": \"Feature\", \"properties\": {}, \"geometry\": " + jsonGeom + " }";
                    })
                    .collect(Collectors.joining(", "));

            DirectLine directLine = new DirectLine(start, dest);
            String lineFeature = "{ \"type\": \"Feature\", \"properties\": { \"name\": \"Direct Line\" }, \"geometry\": "
                    + geoJsonWriter.write(directLine.getLine()) + " }";

            String all_waterAreas = "{ \"type\": \"FeatureCollection\", \"features\": [" + polyFeatures + ", " + lineFeature + " ] }";

            System.out.println(all_waterAreas);
        }

        // Find nearest Bridge of nearest Intersection
        int nextIndexWhenNotRecalculating = 0;
        Bridge nearestBridge = null;
        boolean bridgeFound = false;
        for (Pair<LineString,WaterArea> entry : intersectionsSortedByDistanceList) {
            LineString i = entry.getLeft();
            WaterArea w = entry.getRight();
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
//                Debug.message("Intersection: " + i.getCoordinate().y + " " + i.getCoordinate().x + " with Water Area " + w.getName());
                break;
            }
        }

        if (nearestBridge == null || !bridgeFound) {
            return calculateDistanceWithMetric(start, dest, savedDistance);
        }
        // In not-recalculated mode, remove all skipped and the used intersection from
        if (!recalculated) {
            intersectionsSortedByDistanceList = intersectionsSortedByDistanceList.subList(nextIndexWhenNotRecalculating,
                    intersectionsSortedByDistanceList.size());
        }

        // Calculate Distance to Bridge with Metric
        var anyBridgePoint = new GeoLocation(nearestBridge.geom().getCoordinate());    //Any Point should be okay
        savedDistance = calculateDistanceWithMetric(start, anyBridgePoint, savedDistance);

        lastBridgesUsed.add(nearestBridge);
        previousBridges.add(nearestBridge);

        // Recursive call with Bridge as new start
        return calculateRecursive(anyBridgePoint, dest, savedDistance, previousBridges,
                recalculated, step + 1, intersections, intersectionsSortedByDistanceList);
    }

    public boolean crossesWater(GeoLocation start, GeoLocation dest){
        DirectLine directLine = new DirectLine(start, dest);
        List<Pair<LineString, WaterArea>> intersectionWaterAreasMap = getIntersections(directLine, true);

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
}
