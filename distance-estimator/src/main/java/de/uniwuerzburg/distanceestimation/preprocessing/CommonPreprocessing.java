package de.uniwuerzburg.distanceestimation.preprocessing;

import de.uniwuerzburg.distanceestimation.estimation.AirlineDistance;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.locationtech.jts.geom.*;
import org.locationtech.proj4j.*;

import java.util.*;

public class CommonPreprocessing {
    public static final double SIMPLIFIER_TOLERANCE = 0.001;

    protected static Set<LinearRing> getNewRings(LinearRing ring, Map<GeoLocation, Integer> shortcuts,
                                                 Map<LinearRing, Set<GeoLocation>> tempShortcutsTaken) {
        Set<GeoLocation> alreadyProcessed = new HashSet<>();
        Set<LinearRing> rings = new HashSet<>();
        Coordinate[] r = ring.getCoordinates();
        for (int startIndex = 0; startIndex < r.length; startIndex++) {
            GeoLocation start = new GeoLocation(r[startIndex]);
            if (!alreadyProcessed.contains(start)) {
                Set<GeoLocation> alreadyProcessedInThisRun = new HashSet<>();
                Set<GeoLocation> tempShortcutsTakenSet = new HashSet<>();
                List<GeoLocation> list = new ArrayList<>();
                int i = startIndex + 1;
                if (i >= r.length - 1) {
                    i = 0;
                }
                list.add(start);
                alreadyProcessedInThisRun.add(start);
                boolean lastWasBridge = false;
                while (!r[i].equals(start)) {
                    GeoLocation current = new GeoLocation(r[i]);
                    list.add(current);
                    alreadyProcessedInThisRun.add(current);
                    if (!lastWasBridge && shortcuts.containsKey(current) &&
                            !alreadyProcessedInThisRun.contains(new GeoLocation(r[shortcuts.get(current)]))) {
                        // Take Shortcut
                        lastWasBridge = true;
                        i = shortcuts.get(current);
                        tempShortcutsTakenSet.add(current);

                    } else {
                        lastWasBridge = false;
                        i = i + 1;
                    }
                    if (i == r.length - 1) {
                        i = 0;
                    }
                }
                list.add(new GeoLocation(r[startIndex]));
                GeoLocation[] arr = list.toArray(new GeoLocation[0]);
                LinearRing newRing = Factory.FACTORY.createLinearRing(arr);
                rings.add(newRing);
                tempShortcutsTaken.put(newRing, tempShortcutsTakenSet);
                alreadyProcessed.addAll(alreadyProcessedInThisRun);
            }
        }
        return rings;
    }

    protected static Map<GeoLocation, Integer> getShortcuts(WaterArea w, Set<LinearRing> outerRings,
                                                          Map<WaterArea, Set<Bridge>> waterAreasWithBridgesMap) {
        Map<GeoLocation, Integer> shortcuts = new HashMap<>();
        if (waterAreasWithBridgesMap.containsKey(w)) {
            for (Bridge b : waterAreasWithBridgesMap.get(w)) {
                GeoLocation nearestToStart = null, nearestToEnd = null;
                LinearRing startRing = null, endRing = null;
                int startIndex = 0, endIndex = 0;
                double distanceToStart = Double.MAX_VALUE, distanceToEnd = Double.MAX_VALUE;
                for (LinearRing r : outerRings) {
                    int i = 0;
                    for (var c : r.getCoordinates()) {
                        double distStart = c.distance(b.geom().getStartPoint().getCoordinate());
                        if (distStart < distanceToStart) {
                            nearestToStart = new GeoLocation(c);
                            startRing = r;
                            startIndex = i;
                            distanceToStart = distStart;
                        }
                        double distEnd = c.distance(b.geom().getEndPoint().getCoordinate());
                        if (distEnd < distanceToEnd) {
                            nearestToEnd = new GeoLocation(c);
                            endRing = r;
                            endIndex = i;
                            distanceToEnd = distEnd;
                        }
                        i = i + 1;
                    }
                }
                if (startRing == null || !startRing.equals(endRing)) {
                    Debug.message("Shortcut Error: Bridge End Points do not connect same polygon of water area: " + w.getName() + ", " + b.name() + ", " + b.geom().getCoordinate());
                    return new HashMap<>();
                }
                if (nearestToStart.equals(nearestToEnd)) {
                    continue;
                }
                shortcuts.put(nearestToStart, endIndex);
                shortcuts.put(nearestToEnd, startIndex);
            }
        }
        return shortcuts;
    }

    public static double getGeometryAreaSquareMeters(Geometry geometry) {
        // Define source and target coordinate reference systems
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem srcCrs = crsFactory.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs");
        CoordinateReferenceSystem targetCrs = crsFactory.createFromParameters("Mercator", "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs");

        // Create coordinate transformation
        CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
        CoordinateTransform transform = ctFactory.createTransform(srcCrs, targetCrs);

        // Transform coordinates
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] transformedCoords = new Coordinate[geometry.getCoordinates().length];

        for (int i = 0; i < geometry.getCoordinates().length; i++) {
            ProjCoordinate srcCoord = new ProjCoordinate(geometry.getCoordinates()[i].x, geometry.getCoordinates()[i].y);
            ProjCoordinate destCoord = new ProjCoordinate();
            transform.transform(srcCoord, destCoord);
            transformedCoords[i] = new Coordinate(destCoord.x, destCoord.y);
        }

        // Create transformed polygon
        Polygon projectedPolygon = geometryFactory.createPolygon(transformedCoords);

        // Compute area in square meters
        return projectedPolygon.getArea();
    }

    public static double getGeometryDistanceMeters(Geometry geometry1, Geometry geometry2) {
        List<Coordinate> poly1Points = getPolygonCoordinates(geometry1);
        List<Coordinate> poly2Points = getPolygonCoordinates(geometry2);

        double minDistance = Double.MAX_VALUE;
        for (Coordinate c1 : poly1Points) {
            for (Coordinate c2 : poly2Points) {
                double distance = haversineDistance(c1, c2);
                minDistance = Math.min(minDistance, distance);
            }
        }
        return minDistance;
    }

    private static List<Coordinate> getPolygonCoordinates(Geometry polygon) {
        return new ArrayList<>(Arrays.asList(polygon.getCoordinates()));
    }

    protected static double haversineDistance(Coordinate c1, Coordinate c2) {
        double lat1 = Math.toRadians(c1.y);
        double lon1 = Math.toRadians(c1.x);
        double lat2 = Math.toRadians(c2.y);
        double lon2 = Math.toRadians(c2.x);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return AirlineDistance.EARTH_RADIUS * c * 1_000; // Distance in meters
    }
}
