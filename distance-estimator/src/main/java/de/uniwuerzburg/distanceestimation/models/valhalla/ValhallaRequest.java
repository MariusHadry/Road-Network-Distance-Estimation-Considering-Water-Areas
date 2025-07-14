package de.uniwuerzburg.distanceestimation.models.valhalla;

import java.util.List;

public class ValhallaRequest {
    final List<ValhallaLocation> locations;

    public ValhallaRequest(List<ValhallaLocation> locations) {
        this.locations = locations;
    }

    public List<ValhallaLocation> getLocations() {
        return locations;
    }

    public Units getUnits() {
        return Units.km;
    }

    public CostingModel getCosting() {
        return CostingModel.pedestrian;
    }

    public enum CostingModel {
        auto, pedestrian
    }

    public enum Units {
        km, mi
    }
}
