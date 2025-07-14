package de.uniwuerzburg.distanceestimation.estimation;

import de.uniwuerzburg.distanceestimation.estimation.clients.OsrmClient;
import de.uniwuerzburg.distanceestimation.estimation.clients.PolylineDecoder;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.DistanceEstimate;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmLocation;
import de.uniwuerzburg.distanceestimation.models.osrm.OsrmRouteRequest;
import org.locationtech.jts.geom.LineString;

import java.util.Arrays;


public class OsrmEstimation implements DistanceEstimation {
    final OsrmClient client;

    public OsrmEstimation(OsrmClient client) {
        this.client = client;
    }

    @Override
    public DistanceEstimate estimateDistance(GeoLocation start, GeoLocation dest) {
        if (start.equals(dest)) return DistanceEstimate.zero;
        
        if (start.compareTo(dest) < 0) {
            var tmp = start;
            start = dest;
            dest = tmp;
        }

        double distanceMeters = client.route(new OsrmRouteRequest(new OsrmLocation(start), new OsrmLocation(dest))).routes()[0].distance();
        return DistanceEstimate.byM(distanceMeters);
    }

    @Override
    public LineString getPath(GeoLocation start, GeoLocation dest) {
        GeoLocation[] locations = Arrays.stream(client.route(new OsrmRouteRequest(new OsrmLocation(start), new OsrmLocation(dest))).routes()[0].legs()[0].steps()).flatMap(step -> PolylineDecoder.decode(step.geometry(), 1e5).stream()).toArray(GeoLocation[]::new);
        return Factory.FACTORY.createLineString(locations);
    }

    @Override
    public ApproachType getApproachType() {
        return ApproachType.OSRM;
    }

    @Override
    public DistanceEstimation copyApproach() {
        return this;
    }
}