package de.uniwuerzburg.distanceestimation.models.mapInfo;

import org.locationtech.jts.geom.MultiLineString;

import java.util.Objects;

public final class Street {
    private final String name;
    private final MultiLineString geom;

    public Street(String name, MultiLineString geom) {
        this.name = name;
        this.geom = geom;
    }

    public MultiLineString getGeom() {
        return geom;
    }

    public String name() {
        return name;
    }

    public MultiLineString geom() {
        return geom;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Street) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.geom, that.geom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, geom);
    }

    @Override
    public String toString() {
        return "Street[" +
                "name=" + name + ", " +
                "geom=" + geom + ']';
    }

}
