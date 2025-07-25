package de.uniwuerzburg.distanceestimation.estimation.clients;

// Source: https://github.com/scoutant/polyline-decoder/blob/master/src/main/java/org/scoutant/polyline/PolylineDecoder.java

import de.uniwuerzburg.distanceestimation.models.GeoLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Port to Java of Mark McClures Javascript PolylineEncoder :
 * http://facstaff.unca.edu/mcmcclur/GoogleMaps/EncodePolyline/decode.js
 */
public class PolylineDecoder {
    private static final double DEFAULT_PRECISION = 1E6;

    public static List<GeoLocation> decode(String encoded) {
        return decode(encoded, DEFAULT_PRECISION);
    }

    /**
     * Precision should be something like 1E5 or 1E6. For OSRM routes found precision was 1E6, not the original default
     * 1E5.
     *
     * @param encoded
     * @param precision
     * @return
     */
    public static List<GeoLocation> decode(String encoded, double precision) {
        List<GeoLocation> track = new ArrayList<>();
        int index = 0;
        int lat = 0, lng = 0;

        while (index < encoded.length()) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            GeoLocation p = new GeoLocation((double) lat / precision, (double) lng / precision);
            track.add(p);
        }
        return track;
    }

}