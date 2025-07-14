package de.uniwuerzburg.distanceestimation.models;

import java.util.Objects;

public final class DistanceEstimate {
    private final double meters;

    private DistanceEstimate(double meters) {
        this.meters = meters;
    }

    public final static DistanceEstimate zero = new DistanceEstimate(0.0);

    public static DistanceEstimate byKm(double km) {
        return new DistanceEstimate(km * 1000);
    }

    public static DistanceEstimate byM(double m) {
        return new DistanceEstimate(m);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DistanceEstimate that = (DistanceEstimate) o;
        return Double.compare(meters, that.meters) == 0;
    }

    public DistanceEstimate add(DistanceEstimate other) {
        return new DistanceEstimate(meters + other.meters);
    }

    public DistanceEstimate multiply(double factor) {
        return new DistanceEstimate(meters * factor);
    }

    public double getMeters() {
        return meters;
    }

    @Override
    public int hashCode() {
        return Objects.hash(meters);
    }

    @Override
    public String toString() {
        return meters + "m";
    }

    public boolean isSmallerThan(DistanceEstimate other) {
        return meters < other.meters;
    }
}
