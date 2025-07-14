package de.uniwuerzburg.distanceestimation.models.mapInfo;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import java.util.Objects;

public class WaterArea{
    private final Geometry geometry;
    private final String name;
    private final Envelope envelope;

    public WaterArea(String name, Geometry geometry) {
        this.geometry = geometry;
        this.name = name;
        this.envelope = geometry.getEnvelopeInternal();
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

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof WaterArea waterArea)) return false;

        return geometry.equals(waterArea.geometry) && Objects.equals(name, waterArea.name);
    }

    @Override
    public int hashCode() {
        int result = geometry.hashCode();
        result = 31 * result + Objects.hashCode(name);
        return result;
    }
}
