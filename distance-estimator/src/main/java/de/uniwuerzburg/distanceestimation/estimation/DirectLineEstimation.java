package de.uniwuerzburg.distanceestimation.estimation;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;
import de.uniwuerzburg.distanceestimation.models.DirectLine;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.util.Debug;
import de.uniwuerzburg.distanceestimation.util.DurationTimer;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.geom.util.PointExtracter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DirectLineEstimation implements DistanceEstimation {

    protected final Map<WaterArea, Geometry> simpleWaterAreasMap;
    protected final List<WaterArea> waterAreas;
    protected final AirlineDistance metric;
    protected final RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree;
    protected DistanceEstimate lastDistanceCircuity;

    public DirectLineEstimation(Map<WaterArea, Geometry> simpleWaterAreasMap, List<WaterArea> searchList,
                                RTree<WaterArea, com.github.davidmoten.rtree.geometry.Geometry> waterAreaTree,
                                AirlineDistance metric) {
        this.simpleWaterAreasMap = simpleWaterAreasMap;
        this.waterAreas = searchList;
        this.metric = metric;
        this.waterAreaTree = waterAreaTree;
        lastDistanceCircuity = DistanceEstimate.zero;
    }

    protected boolean doesIntersect(DirectLine directLine, WaterArea waterArea, boolean useSimpleAreaMap){
        LineString line = directLine.getLine();
        Geometry g;
        if (useSimpleAreaMap){
            g = simpleWaterAreasMap.get(waterArea);
        } else {
            g = waterArea.getGeom();
        }
        return line.intersects(g);
    }

    protected Map<LineString, WaterArea> getIntersections(DirectLine directLine, boolean useSimpleAreaMap) {
        LineString line = directLine.getLine();
        Map<LineString, WaterArea> intersectionsWaterAreas = new ConcurrentHashMap<>();

        List<WaterArea> interestingWaterAreas = waterAreas;
        if (waterAreaTree != null) {
            Envelope lineEnvelope = line.getEnvelopeInternal();
            Rectangle searchBounds = Geometries.rectangleGeographic(
                    lineEnvelope.getMinX(), lineEnvelope.getMinY(),
                    lineEnvelope.getMaxX(), lineEnvelope.getMaxY());
            interestingWaterAreas = waterAreaTree.search(searchBounds).toList().toBlocking().single()
                    .stream().map(Entry::value).toList();
        }

        interestingWaterAreas.stream().parallel().forEach(w -> {
            Geometry simple;
            if (useSimpleAreaMap) {
                simple = simpleWaterAreasMap.get(w);
            } else {
                simple = w.getGeom();
            }
            DurationTimer debug2 = new DurationTimer();
            if (Debug.DEBUG) {
                debug2.start();
            }

            // early return if there is no intersection
            boolean intersects = line.intersects(simple);
            if (!intersects) return;

            Geometry intersection = line.intersection(simple);

            if (Debug.DEBUG) {
                debug2.stop();
                long time = debug2.getDuration();
                Debug.message("simple.getNumPoints() = " + simple.getNumPoints());
                GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
                geoJsonWriter.setEncodeCRS(false);
                Debug.message(geoJsonWriter.write(simple));
                Debug.message("- time for calculating intersection " + w.getName() + " " + intersection.getCoordinate() +
                        ": " + time + " ns, " + time / 1000000 + " ms.");
            }

            // only use first and last intersection with water area
            List<LineString> lines = LineStringExtracter.getLines(intersection);
            intersectionsWaterAreas.put(lines.get(0), w);
            intersectionsWaterAreas.put(lines.get(lines.size() - 1), w);
        });
        return intersectionsWaterAreas;
    }

    protected Map<Point, WaterArea> getLineIntersections(DirectLine directLine) {
        LineString line = directLine.getLine();
        Map<Point, WaterArea> intersectionsWaterLines = new ConcurrentHashMap<>();

        List<WaterArea> interestingWaterLines = waterAreas;
        if (waterAreaTree != null) {
            Envelope lineEnvelope = line.getEnvelopeInternal();
            Rectangle searchBounds = Geometries.rectangleGeographic(
                    lineEnvelope.getMinX(), lineEnvelope.getMinY(),
                    lineEnvelope.getMaxX(), lineEnvelope.getMaxY());
            interestingWaterLines = waterAreaTree.search(searchBounds).toList().toBlocking().single()
                    .stream().map(Entry::value).toList();
        }

        interestingWaterLines.stream().parallel().forEach(w -> {
            Geometry simple;
            simple = w.getGeom();
            DurationTimer debug2 = new DurationTimer();
            if (Debug.DEBUG) {
                debug2.start();
            }

            // early return if there is no intersection
            boolean intersects = line.intersects(simple);
            if (!intersects) return;

            Geometry intersection = line.intersection(simple);
//            if (intersection.isEmpty()){
//                return;
//            }

            if (Debug.DEBUG) {
                debug2.stop();
                long time = debug2.getDuration();
                Debug.message("simple.getNumPoints() = " + simple.getNumPoints());
                GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
                geoJsonWriter.setEncodeCRS(false);
                Debug.message(geoJsonWriter.write(simple));
                Debug.message("- time for calculating intersection " + w.getName() + " " + intersection.getCoordinate() +
                        ": " + time + " ns, " + time / 1000000 + " ms.");
            }

            // only use first and last intersection with water area
            List<Point> points = PointExtracter.getPoints(intersection);
            if (points.size() % 2 != 0) {
                intersectionsWaterLines.put(points.get(0), w);
                if (points.size() > 1) {
                    intersectionsWaterLines.put(points.get(points.size() - 1), w);
                }
            }

            if (Debug.DEBUG && !points.isEmpty()){
                // get waterarea as geojson
                GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
                geoJsonWriter.setEncodeCRS(false);
                Debug.message("Water area that has been intersected: " + geoJsonWriter.write(w.getGeom()));
                Debug.message(points.size() + " intersections");
                for (Point p : points) {
                    Debug.message("\t - " + geoJsonWriter.write(p));
                }
            }

        });
        return intersectionsWaterLines;
    }

    protected List<Map.Entry<LineString, Double>> sortIntersectionsByDistance(
            DirectLine directLine, Map<LineString, WaterArea> intersectionsWaterAreasMap) {
        return sortIntersectionsByDistance(directLine, intersectionsWaterAreasMap, 1);
    }

    protected List<Map.Entry<LineString, Double>> sortIntersectionsByDistance(
            DirectLine directLine, Map<LineString, WaterArea> intersectionsWaterAreasMap, int step) {
        DurationTimer debugTimer = new DurationTimer();
        if (Debug.DEBUG) {
            debugTimer.start();
        }

        Map<LineString, Double> intersectionsWithDistance = new ConcurrentHashMap<>();
        Point start = Factory.coordinateToPoint(directLine.getStart());
        intersectionsWaterAreasMap.keySet().stream().parallel().forEach(i -> {
            double distance = start.distance(i.getEndPoint());
            if (step == 1 || distance > 0.005) {
                // Only with Bridge-recalc: If intersection is very near, it is likely the same as previous
                intersectionsWithDistance.put(i, distance);
            }
        });
        List<Map.Entry<LineString, Double>> intersectionsWithDistanceList = new ArrayList<>(intersectionsWithDistance.entrySet());
        intersectionsWithDistanceList.sort(Map.Entry.comparingByValue());

        if (Debug.DEBUG) {
            debugTimer.stop();
            long time = debugTimer.getDuration();
            Debug.message("- time for sorting intersections: " + time + " ns, " + time / 1000000 + " ms.");
        }

        return intersectionsWithDistanceList;
    }

    protected List<Map.Entry<Point, Double>> sortPointIntersectionsByDistance(
            DirectLine directLine, Map<Point, WaterArea> intersectionsWaterAreasMap, int step) {
        DurationTimer debugTimer = new DurationTimer();
        if (Debug.DEBUG) {
            debugTimer.start();
        }

        Map<Point, Double> intersectionsWithDistance = new ConcurrentHashMap<>();
        Point start = Factory.coordinateToPoint(directLine.getStart());
        intersectionsWaterAreasMap.keySet().stream().parallel().forEach(i -> {
            double distance = start.distance(i);
            if (step == 1 || distance > 0.005) {
                // Only with Bridge-recalc: If intersection is very near, it is likely the same as previous
                intersectionsWithDistance.put(i, distance);
            }
        });
        List<Map.Entry<Point, Double>> intersectionsWithDistanceList = new ArrayList<>(intersectionsWithDistance.entrySet());
        intersectionsWithDistanceList.sort(Map.Entry.comparingByValue());

        if (Debug.DEBUG) {
            debugTimer.stop();
            long time = debugTimer.getDuration();
            Debug.message("- time for sorting intersections: " + time + " ns, " + time / 1000000 + " ms.");
        }

        return intersectionsWithDistanceList;
    }

    protected DistanceEstimate calculateDistanceWithMetric(GeoLocation start, GeoLocation dest, DistanceEstimate savedDistance) {
        var distance = metric.estimateDistance(start, dest);
        lastDistanceCircuity = lastDistanceCircuity.add(distance.multiply(CIRCUITY_FACTOR_GERMANY));
        return savedDistance.add(distance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectLineEstimation that = (DirectLineEstimation) o;
        return Objects.equals(simpleWaterAreasMap, that.simpleWaterAreasMap) && Objects.equals(waterAreas, that.waterAreas) && Objects.equals(metric, that.metric);
    }

    @Override
    public int hashCode() {
        return Objects.hash(simpleWaterAreasMap, waterAreas, metric);
    }

    public DistanceEstimate getLastDistanceCircuity() {
        return lastDistanceCircuity;
    }
}
