package de.uniwuerzburg.distanceestimation.estimation;

import com.github.davidmoten.rtree.RTree;
import de.uniwuerzburg.distanceestimation.models.DirectLine;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Edge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.preprocessing.WaterGraphPreprocessing;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BidirectionalDijkstraShortestPath;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.GeometryItemDistance;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class WaterGraphEstimation extends DirectLineEstimation {

    private final Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> waterGraphs;
    private GraphPath<GeoLocation, Edge> lastGraphPath;
    private final Map<WaterArea, Set<LineString>> waterGraphEdges;
    private final Map<WaterArea, STRtree> spatialIndicesWaterGraphs;
    private final boolean circuity;
    private final Map<WaterArea, Set<GeoLocation>> bridgesMap;

    public WaterGraphEstimation(Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> waterGraphs,
                                Set<WaterArea> splitSimpleWaterAreas, Map<WaterArea, Set<LineString>> waterGraphEdges,
                                AirlineDistance metric, boolean circuity, Map<WaterArea, Set<GeoLocation>> bridgesMap,
                                RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree,
                                Map<WaterArea, STRtree> spatialIndicesWaterGraphs) {
        super(null, new ArrayList<>(splitSimpleWaterAreas), waterAreaTree, metric);
        this.circuity = circuity;
        this.waterGraphs = waterGraphs;
        this.waterGraphEdges = waterGraphEdges;
        this.bridgesMap = bridgesMap;
        this.spatialIndicesWaterGraphs = spatialIndicesWaterGraphs;
    }

    public WaterGraphEstimation(WaterGraphPreprocessing waterGraphPreprocessing, AirlineDistance metric, boolean circuity) {
        super(null, new ArrayList<>(waterGraphPreprocessing.getSimpleSplitWaterAreas()),
                waterGraphPreprocessing.getWaterAreaTree(), metric);
        this.circuity = circuity;
        this.waterGraphs = waterGraphPreprocessing.getWaterGraphs();
        this.waterGraphEdges = waterGraphPreprocessing.getWaterGraphEdges();
        this.bridgesMap = waterGraphPreprocessing.getNewBridges();
        this.spatialIndicesWaterGraphs = waterGraphPreprocessing.getSpatialIndicesWaterGraphs();
    }

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        Debug.startDebugTimer();

        DirectLine directLine = new DirectLine(start, dest);
        List<Pair<LineString, WaterArea>> intersections = getIntersections(directLine, false);
        Debug.stopDebugTimer("Get all intersections of Start-Dest-Line with Water Areas");
        Debug.startDebugTimer();
        List<Pair<LineString, WaterArea>> intersectionsSortedByDistanceList = sortIntersectionsByDistance(directLine, intersections);
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
            Pair<LineString, WaterArea> entry = intersectionsSortedByDistanceList.get(i);
            LineString intersection = entry.getLeft();
            GeoLocation intersectionStart = new GeoLocation(intersection.getStartPoint().getCoordinate());
            GeoLocation intersectionEnd = new GeoLocation(intersection.getEndPoint().getCoordinate());
            WaterArea w = entry.getRight();


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
        Coordinate start = edge.getCoordinateN(0);
        Coordinate end = edge.getCoordinateN(edge.getNumPoints() - 1);

        double dxStart = start.x - other.x;
        double dyStart = start.y - other.y;
        double distSqStart = dxStart * dxStart + dyStart * dyStart;

        double dxEnd = end.x - other.x;
        double dyEnd = end.y - other.y;
        double distSqEnd = dxEnd * dxEnd + dyEnd * dyEnd;

        return new GeoLocation(distSqStart > distSqEnd ? end : start);
    }

    private GeoLocation[] findClosestVertices(WaterArea waterArea, GeoLocation intersectionStart,
                                              GeoLocation intersectionEnd, GeoLocation lastEnd) {
        STRtree index = spatialIndicesWaterGraphs.get(waterArea);
        Geometry intersectionStartGeometry = Factory.coordinateToPoint(intersectionStart);
        Geometry intersectionEndGeometry = Factory.coordinateToPoint(intersectionEnd);
        Geometry lastEndGeometry = Factory.coordinateToPoint(lastEnd);

        // STRtree.nearestNeighbor uses a Branch-and-Bound algorithm to find the
        // nearest item in O(log N) time instead of O(N).
        LineString nearestToStart = (LineString) index.nearestNeighbour(
                intersectionStartGeometry.getEnvelopeInternal(), // Search using the point's envelope
                intersectionStartGeometry,
                new GeometryItemDistance() // JTS class to calculate distance between items
        );

        LineString nearestToEnd = (LineString) index.nearestNeighbour(
                intersectionEndGeometry.getEnvelopeInternal(),
                intersectionEndGeometry,
                new GeometryItemDistance()
        );

        LineString nearestToLastEnd = (LineString) index.nearestNeighbour(
                lastEndGeometry.getEnvelopeInternal(),
                lastEndGeometry,
                new GeometryItemDistance()
        );

        return new GeoLocation[]{
                new GeoLocation(getNearestCoordinateOfEdge(nearestToStart, intersectionStart)),
                new GeoLocation(getNearestCoordinateOfEdge(nearestToEnd, intersectionEnd)),
                new GeoLocation(getNearestCoordinateOfEdge(nearestToLastEnd, lastEnd))
        };
    }

    public boolean crossesWater(GeoLocation start, GeoLocation dest){
        DirectLine directLine = new DirectLine(start, dest);
        List<Pair<LineString, WaterArea>> intersections = getIntersections(directLine, false);

        return !intersections.isEmpty();
    }

    private void addEdgeWithWeight(SimpleWeightedGraph<GeoLocation, Edge> combined, GeoLocation a, GeoLocation b) {
        var weight = calculateDistanceWithMetricNoSaved(a, b);
        addEdgeWithWeight(combined, a, b, weight);
    }

    private void addEdgeWithWeight(SimpleWeightedGraph<GeoLocation, Edge> combined, GeoLocation a, GeoLocation b, DistanceEstimate weight) {
        // avoid self-loops
        if (a.equals(b)) return;

        combined.addVertex(a);
        combined.addVertex(b);

        // No need to check if edge is already in graph, as e is null if this is the case!
        Edge e = combined.addEdge(a, b);

        if (e != null) {
            combined.setEdgeWeight(e, weight.getMeters());
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
            return Factory.FACTORY.createLineString(new Coordinate[]{start, dest});
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
                bridgesMap, waterAreaTree, spatialIndicesWaterGraphs);
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
