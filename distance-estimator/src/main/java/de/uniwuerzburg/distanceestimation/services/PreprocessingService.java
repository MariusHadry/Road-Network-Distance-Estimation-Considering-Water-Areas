package de.uniwuerzburg.distanceestimation.services;

import de.uniwuerzburg.distanceestimation.estimation.EuclideanDistance;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.preprocessing.BridgeRoutePreprocessing;
import de.uniwuerzburg.distanceestimation.preprocessing.OverheadGraphPreprocessing;
import de.uniwuerzburg.distanceestimation.preprocessing.WaterGraphPreprocessing;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class PreprocessingService {
    private final WaterGraphPreprocessing waterGraphPreprocessing = new WaterGraphPreprocessing(false);
    private final BridgeRoutePreprocessing bridgeRoutePreprocessing = new BridgeRoutePreprocessing();
    private final Map<Integer, OverheadGraphPreprocessing> overheadGraphPreprocessingMap = new HashMap<>();

    @PostConstruct
    void initialize() {
        // ensure that Factory is initialized
        Factory.FACTORY.getSRID();
        bridgeRoutePreprocessing.preprocessing();
        waterGraphPreprocessing.preprocessing(new EuclideanDistance());

        Stream.of(1024, 512, 256, 128).forEach(n -> {
            OverheadGraphPreprocessing result = new OverheadGraphPreprocessing(n);
            result.preprocessing();
            overheadGraphPreprocessingMap.put(n, result);
        });
    }

    public WaterGraphPreprocessing getWaterGraphPreprocessing() {
        return waterGraphPreprocessing;
    }

    public BridgeRoutePreprocessing getBridgeRoutePreprocessing() {
        return bridgeRoutePreprocessing;
    }

    public OverheadGraphPreprocessing getOverheadGraphPreprocessing(int n_points) {
        return overheadGraphPreprocessingMap.get(n_points);
    }
}
