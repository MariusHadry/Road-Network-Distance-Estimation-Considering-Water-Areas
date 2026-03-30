package de.uniwuerzburg.distanceestimation.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

public class GeoLocation extends Coordinate {
    private final double lat;
    private final double lon;

    private boolean initialized_hash = false;
    private int precalculatedHash;
    private boolean initialized_equals = false;
    private long latBits;
    private long lonBits;

    @JsonCreator
    public GeoLocation(double lat, double lon) {
        super(lon, lat);
        this.lat = lat;
        this.lon = lon;
    }

    // JsonIgnores ensure that these fields are not part of the response object!
    @JsonIgnore
    @Override
    public double getOrdinate(int ordinateIndex) {
        return super.getOrdinate(ordinateIndex);
    }

    @JsonIgnore
    public Coordinate getCoordinate() {
        return this;
    }

    @JsonIgnore
    @Override
    public double getM() {
        return super.getM();
    }

    @JsonIgnore
    @Override
    public double getZ() {
        return super.getZ();
    }

    @JsonIgnore
    @Override
    public double getX() {
        return super.getX();
    }

    @JsonIgnore
    @Override
    public double getY() {
        return super.getY();
    }

    @JsonIgnore
    @Override
    public boolean isValid() {
        return super.isValid();
    }

    public GeoLocation(Point point) {
        this(point.getCoordinate());
    }

    public GeoLocation(Coordinate coordinate) {
        this(coordinate.y, coordinate.x);
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    private void initializeEquals(GeoLocation location) {
        if (!location.initialized_equals){
            location.latBits = Double.doubleToLongBits(location.lat);
            location.lonBits = Double.doubleToLongBits(location.lon);
            location.initialized_equals = true;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GeoLocation otherWaterArea)) return false;

        initializeEquals(this);
        initializeEquals(otherWaterArea);
        return this.latBits == otherWaterArea.latBits && this.lonBits == otherWaterArea.lonBits;
    }

    @Override
    public int hashCode() {
        if (!initialized_hash){
            initializeEquals(this);
            this.precalculatedHash =  31 * Long.hashCode(this.latBits) + Long.hashCode(this.lonBits);
            initialized_hash = true;
        }

        return this.precalculatedHash;
    }

    @Override
    public String toString() {
        return "GeoLocation[" +
                "lat=" + lat + ", " +
                "lon=" + lon + ']';
    }

    public static GeoLocation fromString(String str) throws IllegalArgumentException {
        if (str == null || !str.startsWith("GeoLocation[") || !str.endsWith("]")) {
            throw new IllegalArgumentException("Invalid format: " + str);
        }

        try {
            String content = str.substring("GeoLocation[".length(), str.length() - 1);
            String[] parts = content.split(", ");
            double lat = Double.parseDouble(parts[0].split("=")[1]);
            double lon = Double.parseDouble(parts[1].split("=")[1]);
            return new GeoLocation(lat, lon);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid format: " + str, e);
        }
    }
}
