package de.uniwuerzburg.distanceestimation.preprocessing;

import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Rectangle;
import de.uniwuerzburg.distanceestimation.models.*;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Street;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BridgeRoutePreprocessing {
    private Set<WaterArea> waterAreas;
    private final Map<WaterArea, Set<Bridge>> waterAreasWithBridgesMap;
    private final Map<WaterArea, Geometry> simpleWaterAreasMap;
    private RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree;

    // for split water areas
    private final Set<WaterArea> splitWaterAreas;
    private final ConcurrentMap<WaterArea, Set<Bridge>> splitWaterAreasWithBridges;
    private final Map<WaterArea, Geometry> simpleSplitWaterAreasMap;
    private RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> splitWaterAreasTree;

    // variables used during preprocessing
    private final Map<LinearRing, Set<GeoLocation>> tempShortcutsTaken;

    public BridgeRoutePreprocessing() {
        this.waterAreasWithBridgesMap = new HashMap<>();
        this.waterAreas = new HashSet<>();
        this.simpleWaterAreasMap = new HashMap<>();

        this.tempShortcutsTaken = new HashMap<>();
        this.splitWaterAreas = new HashSet<>();
        this.splitWaterAreasWithBridges = new ConcurrentHashMap<>();
        this.simpleSplitWaterAreasMap = new HashMap<>();
    }

    public void preprocessing() {
        Debug.message("Preprocessing for Bridge Route Estimation approach...");
        DatabaseAccess dataManager = new DatabaseAccess();
        Set<Street> bridgeCandidatesStreet = dataManager.getBridgeCandidatesFromDB();
        Set<WaterArea> waterAreaComplex = dataManager.getWaterAreasFromDBBridgeRoute();

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

        //Create Water Area Bridges Map
        for (Bridge b : bridgeCandidates) {
            for (WaterArea w : waterAreas) {
                if (b.geom().intersects(w.getGeom())) {
                    if (!waterAreasWithBridgesMap.containsKey(w)) {
                        waterAreasWithBridgesMap.put(w, new HashSet<>());
                    }
                    Set<Bridge> bridgeSet = waterAreasWithBridgesMap.get(w);
                    bridgeSet.add(b);
                    waterAreasWithBridgesMap.put(w, bridgeSet);
                }
            }
        }

        // Merge Bridges with intersecting GeoLocations
        for (WaterArea w : waterAreasWithBridgesMap.keySet()) {
            Set<Bridge> allBridges = waterAreasWithBridgesMap.get(w);
            BridgeMerger merger = new BridgeMerger(allBridges, w);
            Set<Bridge> remainingBridges = merger.merge();
            waterAreasWithBridgesMap.put(w, remainingBridges);
        }

        // Create simplified Water Areas
        for (WaterArea w : waterAreas) {
            Geometry simple = TopologyPreservingSimplifier.simplify(w.getGeom(), CommonPreprocessing.SIMPLIFIER_TOLERANCE);
            simpleWaterAreasMap.put(w, simple);
        }

        // insert water areas into r-tree. Only use water areas with bridges!
        waterAreaTree = RTree.create();
        for (WaterArea w : waterAreasWithBridgesMap.keySet()) {
            Rectangle rectangle = com.github.davidmoten.rtree.geometry.Geometries.rectangleGeographic(
                    w.getEnvelope().getMinX(),
                    w.getEnvelope().getMinY(),
                    w.getEnvelope().getMaxX(),
                    w.getEnvelope().getMaxY());
            waterAreaTree = waterAreaTree.add(w, rectangle);
        }

        constructSplitWaterAreas();
    }

    public void constructSplitWaterAreas() {
        HashMap<WaterArea, Set<WaterArea>> splitWaterAreasMap = new HashMap<>();

        for (WaterArea w : waterAreasWithBridgesMap.keySet()) {
            Set<WaterArea> newlyAddedWaterAreasSet = new HashSet<>();
            splitWaterAreasMap.put(w, newlyAddedWaterAreasSet);

            // only split water areas if they are reasonably large
            if (w.getGeom().getNumPoints() < 40){
                // we still need to add the water area so it is considered in the split approach
                splitWaterAreas.add(w);
                continue;
            }

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

                Polygon[] polygons = new Polygon[1];
                polygons[0] = p;
                String name = w.getName();
                WaterArea newWater = new WaterArea(name, Factory.FACTORY.createMultiPolygon(polygons));
                splitWaterAreas.add(newWater);
                Set<WaterArea> tmp = splitWaterAreasMap.get(w);
                tmp.add(newWater);
                splitWaterAreasMap.put(w, tmp);

                // Save new nearest to bridge Nodes
                if (!tempShortcutsTaken.get(ring).isEmpty()) {
                    Set<GeoLocation> newNearestGeoLocationToBridgesSet = new HashSet<>();
                    for (GeoLocation b : tempShortcutsTaken.get(ring)) {
                        double minDistance = Double.MAX_VALUE;
                        GeoLocation nearest = null;
                        for (Coordinate c : p.getExteriorRing().getCoordinates()) {
                            double distance = b.distance(c);
                            if (distance < minDistance) {
                                minDistance = distance;
                                nearest = new GeoLocation(c);
                            }
                        }
                        newNearestGeoLocationToBridgesSet.add(nearest);
                    }
                }
            }
        }

        DatabaseAccess dataManager = new DatabaseAccess();
        Set<Street> bridgeCandidatesStreet = dataManager.getBridgeCandidatesFromDB();
        Set<Bridge> bridgeCandidates = streetSetToBridgeSet(bridgeCandidatesStreet);

        // Create Water Area Bridges Map
        bridgeCandidates.stream().parallel().forEach(b -> {
            for (WaterArea w : splitWaterAreas) {
                // after splitting some bridges might no longer intersect the water geometry. Hence, we use a distance threshold
                if (CommonPreprocessing.getGeometryDistanceMeters(b.geom(), w.getGeom()) < 20) {
                    synchronized (splitWaterAreasWithBridges) {
                        if (!splitWaterAreasWithBridges.containsKey(w)) {
                            splitWaterAreasWithBridges.put(w, ConcurrentHashMap.newKeySet());
                        }
                        Set<Bridge> bridgeSet = splitWaterAreasWithBridges.get(w);
                        bridgeSet.add(b);
                        splitWaterAreasWithBridges.put(w, bridgeSet);
                    }
                }
            }
        });



        for (WaterArea parentWater : splitWaterAreasMap.keySet()) {
            for(WaterArea childWater : splitWaterAreasMap.get(parentWater)) {
                Set<Bridge> bridges = waterAreasWithBridgesMap.get(parentWater);
                for (Bridge b : bridges) {
                    // add bridges to newly split water area if they are within a distance of 1000 meters
                    if (CommonPreprocessing.getGeometryDistanceMeters(b.geom(), childWater.getGeom()) < 1_000) {
                        if (!splitWaterAreasWithBridges.containsKey(childWater)) {
                            splitWaterAreasWithBridges.put(childWater, ConcurrentHashMap.newKeySet());
                        }
                        Set<Bridge> bridgeSet = splitWaterAreasWithBridges.get(childWater);
                        bridgeSet.add(b);
                        splitWaterAreasWithBridges.put(childWater, bridgeSet);
                    }
                }
            }
        }

        // Merge Bridges with intersecting GeoLocations
        for (WaterArea w : splitWaterAreasWithBridges.keySet()) {
            Set<Bridge> allBridges = splitWaterAreasWithBridges.get(w);
            BridgeMerger merger = new BridgeMerger(allBridges, w);
            Set<Bridge> remainingBridges = merger.merge();
            splitWaterAreasWithBridges.put(w, remainingBridges);
        }

        // Create simplified Water Areas and create R-Tree
        splitWaterAreasTree = RTree.create();
        for (WaterArea w : splitWaterAreasWithBridges.keySet()) {
            Geometry simple = TopologyPreservingSimplifier.simplify(w.getGeom(), CommonPreprocessing.SIMPLIFIER_TOLERANCE);
            simpleSplitWaterAreasMap.put(w, simple);

            Rectangle rectangle = com.github.davidmoten.rtree.geometry.Geometries.rectangleGeographic(
                    w.getEnvelope().getMinX(), w.getEnvelope().getMinY(),
                    w.getEnvelope().getMaxX(), w.getEnvelope().getMaxY());
            splitWaterAreasTree = splitWaterAreasTree.add(w, rectangle);
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

    public RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> getWaterAreaTree() {
        return waterAreaTree;
    }

    public Map<WaterArea, Set<Bridge>> getWaterAreasWithBridgesMap() {
        return waterAreasWithBridgesMap;
    }

    public Map<WaterArea, Geometry> getSimpleWaterAreasMap() {
        return simpleWaterAreasMap;
    }

    public Set<WaterArea> getWaterAreas() {
        return waterAreas;
    }

    public Set<WaterArea> getSplitWaterAreas() {
        return splitWaterAreas;
    }

    public Map<WaterArea, Set<Bridge>> getSplitWaterAreasWithBridges() {
        return splitWaterAreasWithBridges;
    }

    public RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> getSplitWaterAreasTree() {
        return splitWaterAreasTree;
    }

    public Map<WaterArea, Geometry> getSimpleSplitWaterAreasMap() {
        return simpleSplitWaterAreasMap;
    }
}
