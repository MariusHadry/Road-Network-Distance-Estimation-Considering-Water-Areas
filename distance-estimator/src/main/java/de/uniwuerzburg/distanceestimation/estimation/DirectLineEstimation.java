package de.uniwuerzburg.distanceestimation.estimation;

import org.apache.commons.lang3.tuple.Pair;
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
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.geom.util.PointExtracter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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


    private List<WaterArea> queryIndexSegmented(DirectLine directLine, int numSegments) {
        LineString line = directLine.getLine();
        Coordinate start = line.getCoordinateN(0);
        Coordinate end = line.getCoordinateN(1);
        Set<WaterArea> candidates = new HashSet<>();

        for (int i = 0; i < numSegments; i++) {
            double t1 = (double) i / numSegments;
            double t2 = (double) (i + 1) / numSegments;

            double x1 = start.x + (end.x - start.x) * t1;
            double y1 = start.y + (end.y - start.y) * t1;
            double x2 = start.x + (end.x - start.x) * t2;
            double y2 = start.y + (end.y - start.y) * t2;

            com.github.davidmoten.rtree.geometry.Line searchLine = Geometries.line(
                    Math.min(x1, x2), Math.min(y1, y2),
                    Math.max(x1, x2), Math.max(y1, y2)
            );

            // das toBlocking single ist wohl sehr ineffizient hier!
            List<WaterArea> segmentResult = waterAreaTree.search(searchLine)
                    .map(Entry::value).toList().toBlocking().single();
            candidates.addAll(segmentResult);
        }

        return new ArrayList<>(candidates);
    }


    protected List<Pair<LineString, WaterArea>> getIntersections(DirectLine directLine, boolean useSimpleAreaMap) {
        LineString line = directLine.getLine();
        Envelope lineEnvelope = line.getEnvelopeInternal();

        double area = lineEnvelope.getWidth() * lineEnvelope.getHeight();
        double lineLengthSq = lineEnvelope.getWidth() * lineEnvelope.getWidth() + lineEnvelope.getHeight() * lineEnvelope.getHeight();

        List<WaterArea> interestingWaterAreas = waterAreas;
        if (waterAreaTree != null) {
            if (GreatCircleDistance.getDistanceMeters(directLine) > 20_000 && area > lineLengthSq * 0.2) {
                interestingWaterAreas = queryIndexSegmented(directLine, (int) (GreatCircleDistance.getDistanceMeters(directLine) / 5000) + 1);
            } else {
                com.github.davidmoten.rtree.geometry.Line searchLine = Geometries.line(
                        lineEnvelope.getMinX(), lineEnvelope.getMinY(),
                        lineEnvelope.getMaxX(), lineEnvelope.getMaxY()
                );
                interestingWaterAreas = waterAreaTree.search(searchLine)
                        .map(Entry::value).toList().toBlocking().single();
            }
        }

        var stream = interestingWaterAreas.parallelStream();
        if (interestingWaterAreas.size() >= 5) {
            stream = interestingWaterAreas.parallelStream();
        }

        List<Pair<LineString, WaterArea>> intersections = stream
                .<Pair<LineString, WaterArea>>mapMulti((w, consumer) -> {
                    Geometry targetGeom = useSimpleAreaMap ? simpleWaterAreasMap.get(w) : w.getGeom();

                    // Bounding box check
                    if (!lineEnvelope.intersects(w.getGeom().getEnvelopeInternal())) return;

                    // Fast check with more accuracy than bounding box
                    if (!w.getPreparedGeometry().intersects(line)) return;

                    // actual intersection determination
                    Geometry intersection = line.intersection(targetGeom);

                    List<LineString> lines = LineStringExtracter.getLines(intersection);
                    if (!lines.isEmpty()) {
                        consumer.accept(Pair.of(lines.get(0), w));
                        consumer.accept(Pair.of(lines.get(lines.size() - 1), w));
                    }
                })
                .collect(Collectors.toList());

        return intersections;
    }

    public int getNumberOfAnalyzedWaterAreas(GeoLocation start, GeoLocation dest){
        DirectLine directLine = new DirectLine(start, dest);
        LineString line = directLine.getLine();

        List<WaterArea> interestingWaterAreas = waterAreas;
        if (waterAreaTree != null) {
            Envelope lineEnvelope = line.getEnvelopeInternal();
            Rectangle searchBounds = Geometries.rectangleGeographic(
                    lineEnvelope.getMinX(), lineEnvelope.getMinY(),
                    lineEnvelope.getMaxX(), lineEnvelope.getMaxY());
            interestingWaterAreas = waterAreaTree.search(searchBounds).toList().toBlocking().single()
                    .stream().map(Entry::value).toList();
        }

        return interestingWaterAreas.size();
    }

    public int getNumberOfIntersectedWaterAreas(GeoLocation start, GeoLocation dest){
        DirectLine directLine = new DirectLine(start, dest);
        List<Pair<LineString, WaterArea>> intersectionWaterAreasMap = getIntersections(directLine, false);
        Debug.stopDebugTimer("Get all intersections of Start-Dest-Line with Water Areas");
        Debug.startDebugTimer();
        List<Pair<LineString, WaterArea>> intersectionsSortedByDistanceList = sortIntersectionsByDistance(directLine, intersectionWaterAreasMap);
        return intersectionsSortedByDistanceList.size();
    }

    protected List<Pair<LineString, WaterArea>> sortIntersectionsByDistance(
            DirectLine directLine, List<Pair<LineString, WaterArea>> intersections) {
        DurationTimer debugTimer = new DurationTimer();
        if (Debug.DEBUG) {
            debugTimer.start();
        }

        Point start = Factory.coordinateToPoint(directLine.getStart());

        // 1) sort by distance to start
        intersections.sort(
                Comparator.comparingDouble(
                        p -> start.distance(p.getLeft().getEndPoint())
                )
        );

        // 2) collapse duplicates (old Map behavior)
        List<Pair<LineString, WaterArea>> filtered = new ArrayList<>();

        Pair<LineString, WaterArea> prev = null;
        for (Pair<LineString, WaterArea> curr : intersections) {
            if (prev == null ||
                    !curr.getLeft().equalsExact(prev.getLeft(), 1e-9)) {
                filtered.add(curr);
            }
            prev = curr;
        }

        if (Debug.DEBUG) {
            debugTimer.stop();
            long time = debugTimer.getDuration();
            Debug.message("- time for sorting intersections: " + time + " ns, " + time / 1000000 + " ms.");
        }

        return filtered;
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
