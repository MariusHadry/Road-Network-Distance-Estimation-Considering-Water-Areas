package de.uniwuerzburg.distanceestimation.estimation;

import com.github.davidmoten.rtree.RTree;
import de.uniwuerzburg.distanceestimation.models.*;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Edge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.preprocessing.WaterGraphPreprocessing;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BidirectionalDijkstraShortestPath;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class WaterGraphEstimation extends DirectLineEstimation {

    private final Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> waterGraphs;
    private GraphPath<GeoLocation, Edge> lastGraphPath;
    private final Map<WaterArea, Set<LineString>> waterGraphEdges;
    private final boolean circuity;
    private final Map<WaterArea, Set<GeoLocation>> bridgesMap;

    public WaterGraphEstimation(Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> waterGraphs,
                                Set<WaterArea> splitSimpleWaterAreas, Map<WaterArea, Set<LineString>> waterGraphEdges,
                                AirlineDistance metric, boolean circuity, Map<WaterArea, Set<GeoLocation>> bridgesMap,
                                RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree) {
        super(null, new ArrayList<>(splitSimpleWaterAreas), waterAreaTree, metric);
        this.circuity = circuity;
        this.waterGraphs = waterGraphs;
        this.waterGraphEdges = waterGraphEdges;
        this.bridgesMap = bridgesMap;
    }

    public WaterGraphEstimation(WaterGraphPreprocessing waterGraphPreprocessing, AirlineDistance metric, boolean circuity) {
        super(null, new ArrayList<>(waterGraphPreprocessing.getSimpleSplitWaterAreas()),
                waterGraphPreprocessing.getWaterAreaTree(), metric);
        this.circuity = circuity;
        this.waterGraphs = waterGraphPreprocessing.getWaterGraphs();
        this.waterGraphEdges = waterGraphPreprocessing.getWaterGraphEdges();
        this.bridgesMap = waterGraphPreprocessing.getNewBridges();
    }

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        Debug.startDebugTimer();

        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        DirectLine directLine = new DirectLine(start, dest);
        Map<LineString, WaterArea> intersectionWaterAreasMap = getIntersections(directLine, false);
        Debug.stopDebugTimer("Get all intersections of Start-Dest-Line with Water Areas");
        Debug.startDebugTimer();
        List<Map.Entry<LineString, Double>> intersectionsSortedByDistanceList = sortIntersectionsByDistance(directLine, intersectionWaterAreasMap);
        Debug.stopDebugTimer("Get Distance of Start to Intersections in sorted List");

        if (intersectionsSortedByDistanceList.isEmpty()) {
            Debug.message("No Intersections with Water Areas. Calculating distance.");
            lastGraphPath = null;
            return calculateDistanceWithMetricNoSaved(start, dest);
        }

        // initialize graph for calculating the distance in the end
        SimpleWeightedGraph<GeoLocation, Edge> combined = new SimpleWeightedGraph<>(Edge.class);
        GeoLocation lastEnd = null;
        combined.addVertex(start);
        combined.addVertex(dest);
        Set<WaterArea> alreadyProcessed = new HashSet<>();

        Debug.startDebugTimer();
        WaterArea lastWater = null;
        boolean hasMultipleIntersections = false;
        for (int i = 0; i < intersectionsSortedByDistanceList.size(); i++) {
            LineString intersection = intersectionsSortedByDistanceList.get(i).getKey();
            GeoLocation intersectionStart = new GeoLocation(intersection.getStartPoint().getCoordinate());
            GeoLocation intersectionEnd = new GeoLocation(intersection.getEndPoint().getCoordinate());
            WaterArea w = intersectionWaterAreasMap.get(intersection);


            if (Debug.DEBUG){
                // get waterarea as geojson
                GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
                geoJsonWriter.setEncodeCRS(false);
                Debug.message("Current Waterarea as GeoJSON: " + geoJsonWriter.write(w.getGeom()));
            }

            // add all edges of current water area to the combined graph
            SimpleWeightedGraph<GeoLocation, Edge> graph = waterGraphs.get(w);
            if (!alreadyProcessed.contains(w)) {
                for (Edge e : graph.edgeSet()) {
                    var source = graph.getEdgeSource(e);
                    var target = graph.getEdgeTarget(e);
                    addEdgeWithWeight(combined, source, target, DistanceEstimate.byM(graph.getEdgeWeight(e)));
                }
                alreadyProcessed.add(w);
            }

            // make sure that lastEnd is initialized correctly, which essentially serves as the new starting point
            if (i == 0) {
                lastEnd = start;
            }

            // returns [vertexNearestToStart, vertexNearestToEnd, vertexNearestToLastEnd]
            GeoLocation[] closestVertices = findClosestVertices(w, intersectionStart, intersectionEnd, lastEnd);

            // find nearest vertices of found intersection edges AND to the new starting point
            GeoLocation vertexNearestToStart = closestVertices[0];
            GeoLocation vertexNearestToEnd = closestVertices[1];
            GeoLocation vertexNearestToLastEnd = closestVertices[2];

            // handle nearest vertex (of water area) to start
            if (vertexNearestToStart.equals(vertexNearestToEnd)) {
                Debug.message("Graph vertices of " + w.getName() + " " + intersection + " are equal");
            }
            // add edge between new start and water area intersection
            if (!combined.containsEdge(lastEnd, vertexNearestToStart) && !lastEnd.equals(vertexNearestToStart)) {
                addEdgeWithWeight(combined, lastEnd, vertexNearestToStart);
            }

            // add edge between new start and closest water area point
            if (!combined.containsEdge(lastEnd, vertexNearestToLastEnd) && !lastEnd.equals(vertexNearestToLastEnd)) {
                addEdgeWithWeight(combined, lastEnd, vertexNearestToLastEnd);
            }

            // Add Bridge Edges
            if (lastWater != null && lastWater.equals(w)) {
                hasMultipleIntersections = true;
            } else {
                if (!hasMultipleIntersections) {
                    if (bridgesMap.containsKey(lastWater)) {
                        var bridges = bridgesMap.get(lastWater);
                        for (var b : bridges) {
                            addEdgeWithWeight(combined, b, vertexNearestToStart);
                        }
                    }
                }
                hasMultipleIntersections = false;
            }

            lastEnd = vertexNearestToEnd;
            lastWater = w;
            // add edge between new start and destination, because there are no more intersections with water areas
            if (i == intersectionsSortedByDistanceList.size() - 1) {
                addEdgeWithWeight(combined, lastEnd, dest);

                // Add Bridge Edges to Destination
                if (lastWater != null) {
                    if (bridgesMap.containsKey(lastWater)) {
                        var bridges = bridgesMap.get(lastWater);
                        for (var b : bridges) {
                            addEdgeWithWeight(combined, b, dest);
                        }
                    }
                }
            }
        }

        Debug.stopDebugTimer("Insert extra Edges between Graphs");
        Debug.startDebugTimer();
        lastGraphPath = BidirectionalDijkstraShortestPath.findPathBetween(combined, start, dest);
        Debug.stopDebugTimer("Find shortest Dijkstra path");

        // first benchmarks have shown that this actually takes more time.
        // Maybe A* requires graphs of a certain size to leverage its performance
//        Debug.startDebugTimer();
//        lastGraphPath = (new BidirectionalAStarShortestPath<GeoLocation, Edge>(combined, new GreatCircleDistance())).getPath(start, dest);
//        Debug.stopDebugTimer("Find shortest A* path");

        if (Debug.DEBUG){
            Debug.message("---");
            Debug.message("Constructed Water Graph");
            Debug.message(getGraphAsGeoJSON(combined));
            Debug.message("");
        }

        return DistanceEstimate.byM(lastGraphPath.getWeight());
    }


    private GeoLocation getNearestCoordinateOfEdge(LineString edge, Coordinate other) {
        Point otherPoint = Factory.coordinateToPoint(other);
        if (edge.getStartPoint().distance(otherPoint) > edge.getEndPoint().distance(otherPoint)) {
            return new GeoLocation(edge.getEndPoint().getCoordinate());
        } else {
            return new GeoLocation(edge.getStartPoint().getCoordinate());
        }
    }

    private GeoLocation[] findClosestVertices(WaterArea waterArea, GeoLocation intersectionStart,
                                              GeoLocation intersectionEnd, GeoLocation lastEnd) {
        AtomicReference<LineString> nearestToStart = new AtomicReference<>();
        AtomicReference<LineString> nearestToEnd = new AtomicReference<>();
        AtomicReference<LineString> nearestToLastEnd = new AtomicReference<>();

        final double[] minDistanceToStart = { Double.MAX_VALUE };
        final double[] minDistanceToEnd = { Double.MAX_VALUE };
        final double[] minDistanceToLastEnd = { Double.MAX_VALUE };
        Geometry intersectionStartGeometry = Factory.coordinateToPoint(intersectionStart);
        Geometry intersectionEndGeometry = Factory.coordinateToPoint(intersectionEnd);
        Geometry lastEndGeometry = Factory.coordinateToPoint(lastEnd);
        final Object[] lock = { new Object(), new Object(), new Object() };

        var stream = waterGraphEdges.get(waterArea).stream();

        // only make use of parallelization if water graph is big enough
        if (waterGraphEdges.size() > 3000) {
            stream = stream.parallel();
        }

        stream.forEach(edge -> {
            double distanceToStart = edge.distance(intersectionStartGeometry);
            double distanceToEnd = edge.distance(intersectionEndGeometry);
            double distanceToLastEnd = edge.distance(lastEndGeometry);

            // idea here is to only acquire the lock if there is an actual chance of a lower value. We need to check the
            // condition again because, in theory, the value could have been set in the meantime by another thread!
            if (distanceToStart < minDistanceToStart[0]) {
                synchronized (lock[0]){
                    if (distanceToStart < minDistanceToStart[0]) {
                        minDistanceToStart[0] = distanceToStart;
                        nearestToStart.set(edge);
                    }
                }
            }

            if (distanceToEnd < minDistanceToEnd[0]) {
                synchronized (lock[1]){
                    if (distanceToEnd < minDistanceToEnd[0]) {
                        minDistanceToEnd[0] = distanceToEnd;
                        nearestToEnd.set(edge);
                    }
                }
            }

            if (distanceToLastEnd < minDistanceToLastEnd[0]) {
                synchronized (lock[2]){
                    if (distanceToLastEnd < minDistanceToLastEnd[0]) {
                        minDistanceToLastEnd[0] = distanceToLastEnd;
                        nearestToLastEnd.set(edge);
                    }
                }
            }
        });

        GeoLocation vertexNearestToStart = new GeoLocation(getNearestCoordinateOfEdge(nearestToStart.get(), intersectionStart));
        GeoLocation vertexNearestToEnd = new GeoLocation(getNearestCoordinateOfEdge(nearestToEnd.get(), intersectionEnd));
        GeoLocation vertexNearestToLastEnd = new GeoLocation(getNearestCoordinateOfEdge(nearestToLastEnd.get(), lastEnd));

        return new GeoLocation[]{vertexNearestToStart, vertexNearestToEnd, vertexNearestToLastEnd};
    }


    public boolean crossesWater(GeoLocation start, GeoLocation dest){
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        DirectLine directLine = new DirectLine(start, dest);
        Map<LineString, WaterArea> intersectionWaterAreasMap = getIntersections(directLine, false);

        return !intersectionWaterAreasMap.isEmpty();
    }

    private void addEdgeWithWeight(SimpleWeightedGraph<GeoLocation, Edge> combined, GeoLocation a, GeoLocation b) {
        var weight = calculateDistanceWithMetricNoSaved(a, b);
        addEdgeWithWeight(combined, a, b, weight);
    }

    private void addEdgeWithWeight(SimpleWeightedGraph<GeoLocation, Edge> combined, GeoLocation a, GeoLocation b, DistanceEstimate weight) {
        combined.addVertex(a);
        combined.addVertex(b);
        if (!a.equals(b) && !combined.containsEdge(a, b) && !combined.containsEdge(b, a)) {
            try {
                combined.addEdge(a, b);
            } catch (Exception e) {
                Debug.message(a + " " + b);
                Debug.message("");
            }

            combined.setEdgeWeight(a, b, weight.getMeters());
        }
    }

    private DistanceEstimate calculateDistanceWithMetricNoSaved(GeoLocation start, GeoLocation dest) {
        lastDistanceCircuity = DistanceEstimate.zero;
        // sets lastDistanceCircuity accordingly
        var distance = super.calculateDistanceWithMetric(start, dest, DistanceEstimate.zero);
        if (circuity) {
            distance = lastDistanceCircuity;
        }
        return distance;
    }

    public static String getGraphAsGeoJSON(SimpleWeightedGraph<GeoLocation, Edge> graph) {
        List<LineString> lines = new ArrayList<>();
        if (graph == null) {
            return "";
        }
        for (Edge edge : graph.edgeSet()) {
            Coordinate[] arr = new Coordinate[2];
            arr[0] = graph.getEdgeSource(edge);
            arr[1] = graph.getEdgeTarget(edge);
            lines.add(Factory.FACTORY.createLineString(arr));
        }
        MultiLineString multiLine = Factory.FACTORY.createMultiLineString(lines.toArray(new LineString[0]));
        GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
        geoJsonWriter.setEncodeCRS(false);
        return geoJsonWriter.write(multiLine);
    }

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        if (lastGraphPath == null) {
            return Factory.FACTORY.createLineString();
        }
        return Factory.FACTORY.createLineString(lastGraphPath.getVertexList().toArray(new Coordinate[0]));
    }

    @Override
    public ApproachType getApproachType() {
        if (circuity) {
            return ApproachType.WATER_GRAPH_CIRCUITY;
        } else {
            return ApproachType.WATER_GRAPH;
        }

    }

    @Override
    public DistanceEstimation copyApproach() {
        return new WaterGraphEstimation(waterGraphs, new HashSet<>(waterAreas), waterGraphEdges, metric, circuity,
                bridgesMap, waterAreaTree);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        WaterGraphEstimation that = (WaterGraphEstimation) o;
        return circuity == that.circuity && Objects.equals(waterGraphs, that.waterGraphs) && Objects.equals(waterGraphEdges, that.waterGraphEdges) && Objects.equals(bridgesMap, that.bridgesMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), waterGraphs, waterGraphEdges, circuity, bridgesMap);
    }
}
