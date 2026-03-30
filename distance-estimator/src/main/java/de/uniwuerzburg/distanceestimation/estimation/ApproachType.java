package de.uniwuerzburg.distanceestimation.estimation;

public enum ApproachType {
    AIRLINE, HAVERSINE,
    BRIDGE_REC, BRIDGE_NO_REC, BRIDGE_SPLIT_REC, BRIDGE_SPLIT_NO_REC,
    HYBRID_BRIDGE_SPLIT_HAVERSINE, HYBRID_BRIDGE_SPLIT_OHG,
    WATER_GRAPH, WATER_GRAPH_CIRCUITY,
    HYBRID_WATER_GRAPH_HAVERSINE, HYBRID_WATER_GRAPH_OHG,
    OVERHEAD_GRAPH_128, OVERHEAD_GRAPH_256, OVERHEAD_GRAPH_512, OVERHEAD_GRAPH_1024,
    OSRM;

    public boolean isOwnApproach() {
        switch (this) {
            case BRIDGE_REC, BRIDGE_NO_REC, BRIDGE_SPLIT_REC, BRIDGE_SPLIT_NO_REC, WATER_GRAPH, WATER_GRAPH_CIRCUITY, HYBRID_BRIDGE_SPLIT_HAVERSINE, HYBRID_BRIDGE_SPLIT_OHG -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isSimpleMetric() {
        switch (this) {
            case AIRLINE, HAVERSINE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
