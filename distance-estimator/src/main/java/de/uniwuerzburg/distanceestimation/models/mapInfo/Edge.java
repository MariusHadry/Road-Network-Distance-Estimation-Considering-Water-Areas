package de.uniwuerzburg.distanceestimation.models.mapInfo;

import org.jgrapht.graph.DefaultWeightedEdge;

public class Edge extends DefaultWeightedEdge {
    @Override
    public String toString() {
        return String.valueOf(getWeight());
    }

    @Override
    public double getWeight() {
        return super.getWeight();
    }
}
