package de.uniwuerzburg.distanceestimation.estimation;

public enum ApproachType {
    AIRLINE, HAVERSINE,
    BRIDGE_REC, BRIDGE_NO_REC, BRIDGE_SPLIT_REC, BRIDGE_SPLIT_NO_REC,
    BRIDGE_LINE,
    WATER_GRAPH, WATER_GRAPH_CIRCUITY,
    OVERHEAD_GRAPH_128, OVERHEAD_GRAPH_256, OVERHEAD_GRAPH_512, OVERHEAD_GRAPH_1024,
    OSRM;

    public boolean isDirectRouteWithBridges() {
        switch (this) {
            case BRIDGE_REC, BRIDGE_NO_REC, BRIDGE_SPLIT_REC, BRIDGE_SPLIT_NO_REC -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isOwnApproach() {
        switch (this) {
            case BRIDGE_REC, BRIDGE_NO_REC, BRIDGE_SPLIT_REC, BRIDGE_SPLIT_NO_REC, WATER_GRAPH, WATER_GRAPH_CIRCUITY -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isWaterGraph(){
        switch (this){
            case WATER_GRAPH, WATER_GRAPH_CIRCUITY -> {
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

    public boolean isOverheadGraph(){
        switch (this){
            case OVERHEAD_GRAPH_128, OVERHEAD_GRAPH_256, OVERHEAD_GRAPH_512 -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
