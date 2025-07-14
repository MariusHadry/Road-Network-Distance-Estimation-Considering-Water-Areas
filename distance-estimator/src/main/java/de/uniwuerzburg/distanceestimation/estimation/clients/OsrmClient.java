package de.uniwuerzburg.distanceestimation.estimation.clients;

import de.uniwuerzburg.distanceestimation.models.osrm.OsrmNearestRequest;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmNearestResponse;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmRouteRequest;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmRouteResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public class OsrmClient {
    private final RestClient restClient;


    public OsrmClient() {
        this.restClient = RestClient.create("http://127.0.0.1:5000");
    }

    public OsrmRouteResponse route(OsrmRouteRequest request) {
        try {
            return restClient.get()
                    .uri(uribuilder -> uribuilder.path("/route/v1/driving/" + request.start.getLocationString() + ";" + request.dest.getLocationString())
                            .queryParam("steps", true)
                            .queryParam("alternatives", false)
                            .queryParam("overview", false)
                            .queryParam("exclude", "ferry")
                            .build())
                    .retrieve().body(OsrmRouteResponse.class);
        } catch (HttpClientErrorException e) {
            return null;
        }
    }

    public OsrmNearestResponse nearest(OsrmNearestRequest request) {
        return restClient.get()
                .uri(uribuilder -> uribuilder.path("/nearest/v1/driving/" + request.start.getLocationString())
                        .queryParam("number", request.n_closest)
                        .build())
                .retrieve().body(OsrmNearestResponse.class);
    }

}
