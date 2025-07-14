package de.uniwuerzburg.distanceestimation.preprocessing;

import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Rectangle;
import de.uniwuerzburg.distanceestimation.estimation.AirlineDistance;
import de.uniwuerzburg.distanceestimation.estimation.DistanceEstimation;
import de.uniwuerzburg.distanceestimation.estimation.EuclideanDistance;
import de.uniwuerzburg.distanceestimation.models.*;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Edge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Street;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaterGraphPreprocessing {
    private Set<WaterArea> waterAreas;
    private final Map<WaterArea, Set<Bridge>> waterAreasWithBridgesMap;

    private final Map<WaterArea, Geometry> simpleWaterAreasMap;
    private final Map<WaterArea, Geometry> simpleWaterAreasWithBridgesMap;
    private RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree;

    private final Set<WaterArea> simpleSplitWaterAreas;
    private final Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> waterGraphs;
    private final Map<WaterArea, Set<LineString>> waterGraphEdges;
    private final Map<WaterArea, Set<GeoLocation>> newBridges;

    private final Map<LinearRing, Set<GeoLocation>> tempShortcutsTaken;
    private final boolean circuityInGraph;
    private final boolean onlyImportantWaterAreas;

    public WaterGraphPreprocessing(boolean circuityInGraph) {
        this.waterAreasWithBridgesMap = new HashMap<>();
        this.waterAreas = new HashSet<>();
        this.simpleWaterAreasMap = new HashMap<>();
        this.simpleWaterAreasWithBridgesMap = new HashMap<>();
        this.waterGraphs = new HashMap<>();
        this.simpleSplitWaterAreas = new HashSet<>();
        this.waterGraphEdges = new HashMap<>();
        this.newBridges = new HashMap<>();
        this.tempShortcutsTaken = new HashMap<>();
        this.circuityInGraph = circuityInGraph;
        this.onlyImportantWaterAreas = true;
    }

    public void preprocessing() {
        preprocessingWaterGraphApproach();
        buildWaterGraphs(new EuclideanDistance());

        if (Debug.DEBUG) {
            analyzeWaterAreas();
        }
    }

    public void preprocessing(AirlineDistance distance) {
        preprocessingWaterGraphApproach();
        buildWaterGraphs(distance);

        if (Debug.DEBUG) {
            analyzeWaterAreas();
        }
    }

    public void preprocessingWaterGraphApproach() {
        Debug.message("Preprocessing for Water Graph approach...");
        DatabaseAccess dataManager = new DatabaseAccess();
        Set<Street> bridgeCandidatesStreet = dataManager.getBridgeCandidatesFromDB();
        Set<WaterArea> waterAreaComplex = dataManager.getWaterAreasFromDBWaterGraph();

        //Only use outer Polygons
        for (WaterArea w : waterAreaComplex) {
            Polygon[] polygons = new Polygon[w.getGeom().getNumGeometries()];
            for (int i = 0; i < w.getGeom().getNumGeometries(); i++) {
                Polygon p = (Polygon) w.getGeom().getGeometryN(i);
                polygons[i] = Factory.FACTORY.createPolygon(p.getExteriorRing());
            }
            waterAreas.add(new WaterArea(w.getName(),
                    Factory.FACTORY.createMultiPolygon(polygons)));
        }

        Set<Bridge> bridgeCandidates = streetSetToBridgeSet(bridgeCandidatesStreet);

        // Create Water Area Bridges Map
        bridgeCandidates.stream().parallel().forEach(b -> {
            for (WaterArea w : waterAreas) {
                if (b.geom().intersects(w.getGeom())) {
                    synchronized (waterAreasWithBridgesMap) {
                        if (!waterAreasWithBridgesMap.containsKey(w)) {
                            waterAreasWithBridgesMap.put(w, ConcurrentHashMap.newKeySet());
                        }
                        Set<Bridge> bridgeSet = waterAreasWithBridgesMap.get(w);
                        bridgeSet.add(b);
                        waterAreasWithBridgesMap.put(w, bridgeSet);
                    }
                }
            }
        });

        if (onlyImportantWaterAreas) {
            // only consider water areas if they are a river (are in waterAreasWithBridgesMap) or above a certain size
            Set<WaterArea> filteredWaterAreas = new HashSet<>();
            for (WaterArea w : waterAreas) {
                if (waterAreasWithBridgesMap.containsKey(w) || CommonPreprocessing.getGeometryAreaSquareMeters(w.getGeom()) > 20_000) {
                    filteredWaterAreas.add(w);
                }
            }
            waterAreas = filteredWaterAreas;
        }

        // Merge Bridges with intersecting locations
        for (WaterArea w : waterAreasWithBridgesMap.keySet()) {
            Set<Bridge> allBridges = waterAreasWithBridgesMap.get(w);
            BridgeMerger merger = new BridgeMerger(allBridges, w);
            Set<Bridge> remainingBridges = merger.merge();
            waterAreasWithBridgesMap.put(w, remainingBridges);
        }

        // Create simplified Water Areas for water areas with bridges
        for (WaterArea w : waterAreasWithBridgesMap.keySet()) {
            Geometry simple = TopologyPreservingSimplifier.simplify(w.getGeom(), CommonPreprocessing.SIMPLIFIER_TOLERANCE);
            simpleWaterAreasWithBridgesMap.put(w, simple);
        }

        // Create simplified Water Areas
        for (WaterArea w : waterAreas) {
            Geometry simple = TopologyPreservingSimplifier.simplify(w.getGeom(), CommonPreprocessing.SIMPLIFIER_TOLERANCE);
            simpleWaterAreasMap.put(w, simple);
        }
    }

    public Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> buildWaterGraphs(AirlineDistance simpleMetric) {
        for (WaterArea w : waterAreas) {
            // Get all outer Rings
            Set<LinearRing> outerRings = new HashSet<>();
            for (int i = 0; i < w.getGeom().getNumGeometries(); i++) {
                Polygon p = (Polygon) w.getGeom().getGeometryN(i);
                outerRings.add(p.getExteriorRing());
            }

            // Preprocessing Step 4
            // Get nearest Points in Rings to Bridges (skip if no bridges) and save these Shortcuts
            Map<GeoLocation, Integer> shortcuts = CommonPreprocessing.getShortcuts(w, outerRings, waterAreasWithBridgesMap);

            // Preprocessing Step 5
            // Get new LinearRings
            Set<LinearRing> newRings = new HashSet<>();
            for (LinearRing r : outerRings) {
                newRings.addAll(CommonPreprocessing.getNewRings(r, shortcuts, tempShortcutsTaken));
            }

            for (LinearRing ring : newRings) {
                Polygon p = Factory.FACTORY.createPolygon(ring);
                if (!IsValidOp.isValid(p)) {
                    //Fix invalid Polygons = crop ends
                    GeometryFixer fixer = new GeometryFixer(p);
                    Geometry result = fixer.getResult();
                    if (result instanceof MultiPolygon) {
                        Polygon biggest = null;
                        int biggestSize = Integer.MIN_VALUE;
                        for (int i = 0; i < result.getNumGeometries(); i++) {
                            Polygon pCandidate = (Polygon) result.getGeometryN(i);
                            int size = pCandidate.getExteriorRing().getCoordinates().length;
                            if (size > biggestSize) {
                                biggestSize = size;
                                biggest = pCandidate;
                            }
                        }
                        p = biggest;
                    } else {
                        p = (Polygon) result;
                    }
                }

                // Simplify Polygons (Preprocessing Step 6)
                Polygon simple = (Polygon) TopologyPreservingSimplifier.simplify(p, CommonPreprocessing.SIMPLIFIER_TOLERANCE);
                Polygon[] polygons = new Polygon[1];
                polygons[0] = simple;
                String name = w.getName();
                WaterArea newWater = new WaterArea(name, Factory.FACTORY.createMultiPolygon(polygons));
                simpleSplitWaterAreas.add(newWater);


                // Save new nearest to bridge Nodes
                if (!tempShortcutsTaken.get(ring).isEmpty()) {
                    Set<GeoLocation> newNearestGeoLocationToBridgesSet = new HashSet<>();
                    for (GeoLocation b : tempShortcutsTaken.get(ring)) {
                        double minDistance = Double.MAX_VALUE;
                        GeoLocation nearest = null;
                        for (Coordinate c : simple.getExteriorRing().getCoordinates()) {
                            double distance = b.distance(c);
                            if (distance < minDistance) {
                                minDistance = distance;
                                nearest = new GeoLocation(c);
                            }
                        }
                        newNearestGeoLocationToBridgesSet.add(nearest);
                    }
                    newBridges.put(newWater, newNearestGeoLocationToBridgesSet);
                }

                // Save Linestring Edges of Polygon for later
                Coordinate[] simpleMyLocations = simple.getExteriorRing().getCoordinates();
                Set<LineString> lines = new HashSet<>();
                for (int i = 0; i < simpleMyLocations.length - 1; i++) {
                    GeoLocation[] arr = new GeoLocation[2];
                    arr[0] = new GeoLocation(simpleMyLocations[i]);
                    arr[1] = new GeoLocation(simpleMyLocations[i + 1]);
                    LineString line = Factory.FACTORY.createLineString(arr);
                    lines.add(line);
                }
                waterGraphEdges.put(newWater, lines);
            }

        }

        // Create Graphs & R-Tree
        waterAreaTree = RTree.create();
        for (WaterArea w : simpleSplitWaterAreas) {
            SimpleWeightedGraph<GeoLocation, Edge> graph = new SimpleWeightedGraph<>(Edge.class);
            for (int i = 0; i < w.getGeom().getNumGeometries(); i++) {
                Polygon polygon = (Polygon) w.getGeom().getGeometryN(i);
                addAllFromGeometryToGraph(graph, polygon.getExteriorRing(), simpleMetric);
            }
            waterGraphs.put(w, graph);

            Rectangle rectangle = com.github.davidmoten.rtree.geometry.Geometries.rectangleGeographic(
                    w.getEnvelope().getMinX(),
                    w.getEnvelope().getMinY(),
                    w.getEnvelope().getMaxX(),
                    w.getEnvelope().getMaxY());
            waterAreaTree = waterAreaTree.add(w, rectangle);
        }

        return waterGraphs;
    }

    private void addAllFromGeometryToGraph(SimpleWeightedGraph<GeoLocation, Edge> graph,
                                           Geometry g, AirlineDistance simpleMetric) {
        GeoLocation last = null;
        for (var c : g.getCoordinates()) {
            graph.addVertex(new GeoLocation(c));
            if (last == null) {
                last = new GeoLocation(c);
                continue;
            }
            var distance = simpleMetric.estimateDistance(new GeoLocation(c.y, c.x),
                    new GeoLocation(last.y, last.x));

            Edge edge = graph.addEdge(new GeoLocation(c), last);
            if (edge != null) {
                // Null could occur if split merged bridges have same parts
                if (circuityInGraph){
                    distance = distance.multiply(DistanceEstimation.CIRCUITY_FACTOR_GERMANY);
                }

                graph.setEdgeWeight(edge, distance.getMeters());
            }
            last = new GeoLocation(c);
        }
    }

    private LineString multiLineToLine(MultiLineString l) {
        List<LineString> lines = LineStringExtracter.getLines(l);
        if (lines.size() > 1) {
            throw new IllegalArgumentException("MultiLineString can not be converted to LineString as it contains more than one");
        }
        return lines.get(0);

    }

    private Set<Bridge> streetSetToBridgeSet(Set<Street> bridgeCandidates) {
        Set<Bridge> bridges = new HashSet<>();
        for (Street b : bridgeCandidates) {
            bridges.add(new Bridge(b.name(), multiLineToLine(b.getGeom())));
        }
        return bridges;
    }

    public Set<WaterArea> getWaterAreas() {
        return waterAreas;
    }

    public Map<WaterArea, Set<Bridge>> getWaterAreasWithBridgesMap() {
        return waterAreasWithBridgesMap;
    }


    public Map<WaterArea, Geometry> getSimpleWaterAreasMap() {
        return simpleWaterAreasMap;
    }


    public Map<WaterArea, Geometry> getSimpleWaterAreasWithBridgesMap() {
        return simpleWaterAreasWithBridgesMap;
    }

    public Map<WaterArea, SimpleWeightedGraph<GeoLocation, Edge>> getWaterGraphs() {
        return waterGraphs;
    }

    public Set<WaterArea> getSimpleSplitWaterAreas() {
        return simpleSplitWaterAreas;
    }

    public Map<WaterArea, Set<LineString>> getWaterGraphEdges() {
        return waterGraphEdges;
    }

    public Map<WaterArea, Set<GeoLocation>> getNewBridges() {
        return newBridges;
    }

    public RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> getWaterAreaTree() {
        return waterAreaTree;
    }

    private void analyzeWaterAreas(){
        Debug.message("largest geometries: ");
        ArrayList<Double> waterAreaSizes = new ArrayList<>();

        for (WaterArea w : simpleSplitWaterAreas) {
            waterAreaSizes.add(CommonPreprocessing.getGeometryAreaSquareMeters(w.getGeom()));
        }

        Collections.sort(waterAreaSizes);
        Collections.reverse(waterAreaSizes);
        double threshold = waterAreaSizes.get(10);

        for (WaterArea w : simpleSplitWaterAreas) {
            if (CommonPreprocessing.getGeometryAreaSquareMeters(w.getGeom()) >= threshold){
                GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
                geoJsonWriter.setEncodeCRS(false);
                Debug.message(geoJsonWriter.write(w.getGeom()));
            }
        }

        Debug.message("----");
    }
}
