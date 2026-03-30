package de.uniwuerzburg.distanceestimation.models.mapInfo;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.Arrays;
import java.util.Objects;

public class WaterArea{
    private final Geometry geometry;
    private final int geometryHash;
    private final String name;
    private final Envelope envelope;
    private final PreparedGeometry preparedGeometry;
    private final int instance_id;

    // used to construct the instance_id
    private static int LATEST_INSTANCE_ID = 0;

    public WaterArea(String name, Geometry geometry) {
        this.geometry = geometry.copy();
        this.name = name;
        this.envelope = geometry.getEnvelopeInternal();

        // normalize geometry to ignore vertex order
        Geometry tmpGeometry = this.geometry.copy();
        tmpGeometry.normalize();

        // build a composite hash using invariants
        int h = Objects.hash(
                tmpGeometry.getEnvelopeInternal(),
                tmpGeometry.getArea(),
                tmpGeometry.getLength(),
                tmpGeometry.getCentroid().getCoordinate()
        );

        // For higher precision, hash the coordinate sequence itself and add it to the existing hash
         h = 41 * h + Arrays.deepHashCode(tmpGeometry.getCoordinates());

        this.geometryHash = h;
        this.preparedGeometry = PreparedGeometryFactory.prepare(this.geometry);

        instance_id = WaterArea.LATEST_INSTANCE_ID++;
    }

    public int getInstanceId() {
        return instance_id;
    }

    public Geometry getGeom() {
        return geometry;
    }

    public String getName() {
        return name;
    }

    public Envelope getEnvelope() {
        return envelope;
    }

    public PreparedGeometry getPreparedGeometry() {
        return preparedGeometry;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof WaterArea otherWaterArea)) return false;

        // only use the instance id. This works, because we only construct water areas once during preprocessing and
        // there should be no duplicates!
        return this.instance_id == otherWaterArea.instance_id;
    }

    @Override
    public int hashCode() {
        return geometryHash + 71 * Objects.hashCode(name);
    }
}
