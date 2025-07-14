package de.uniwuerzburg.distanceestimation.models;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

public class Coordinates {
    public final static float UNTERFRANKEN_MIN_LAT_Y = 49.767074f;
    public final static float UNTERFRANKEN_MIN_LON_X = 9.093933f;
    public final static float UNTERFRANKEN_MAX_LAT_Y = 50.122339f;
    public final static float UNTERFRANKEN_MAX_LON_X = 10.566101f;

    public final static float GERMANY_MIN_LAT_Y = 47.472663f;
    public final static float GERMANY_MIN_LON_X = 6.262207f;
    public final static float GERMANY_MAX_LAT_Y = 54.835500f;
    public final static float GERMANY_MAX_LON_X = 14.854889f;

    public final static float AB_WUE_MIN_LAT_Y = 49.692508f;
    public final static float AB_WUE_MIN_LON_X = 9.087067f;
    public final static float AB_WUE_MAX_LAT_Y = 50.073006f;
    public final static float AB_WUE_MAX_LON_X = 10.053864f;

    public static Polygon getEstimatedBorderGermany(){
        Coordinate p1 = new Coordinate(7.614899, 47.582547);
        Coordinate p2 = new Coordinate(13.006439, 47.472663);
        Coordinate p3 = new Coordinate(13.408813, 48.549342);
        Coordinate p4 = new Coordinate(11.870728, 50.310392);
        Coordinate p5 = new Coordinate(14.854889, 51.241286);
        Coordinate p6 = new Coordinate(13.320923, 54.450880);
        Coordinate p7 = new Coordinate(10.903931, 53.946388);
        Coordinate p8 = new Coordinate(9.915161, 54.749991);
        Coordinate p9 = new Coordinate(8.709412, 54.835500);
        Coordinate p10 = new Coordinate(9.019775, 53.803895);
        Coordinate p11 = new Coordinate(7.987061, 53.680442);
        Coordinate p12 = new Coordinate(6.262207, 50.909961);
        Coordinate p13 = new Coordinate(6.668701, 49.375220);
        Coordinate p14 = new Coordinate(8.297424, 48.985625);
        Coordinate[] germanyCoords = {p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p1};
        return Factory.FACTORY.createPolygon(germanyCoords);
    }

    public static Polygon getSquareGermany(){
        Coordinate p1 = new Coordinate(GERMANY_MIN_LON_X, GERMANY_MIN_LAT_Y);
        Coordinate p2 = new Coordinate(GERMANY_MAX_LON_X, GERMANY_MIN_LAT_Y);
        Coordinate p3 = new Coordinate(GERMANY_MAX_LON_X, GERMANY_MAX_LAT_Y);
        Coordinate p4 = new Coordinate(GERMANY_MIN_LON_X, GERMANY_MAX_LAT_Y);
        Coordinate[] germanySquare = {p1, p2, p3, p4, p1};
        return Factory.FACTORY.createPolygon(germanySquare);
    }

    public static Polygon getSquareUnterfranken(){
        Coordinate p1 = new Coordinate(UNTERFRANKEN_MIN_LON_X, UNTERFRANKEN_MIN_LAT_Y);
        Coordinate p2 = new Coordinate(UNTERFRANKEN_MAX_LON_X, UNTERFRANKEN_MIN_LAT_Y);
        Coordinate p3 = new Coordinate(UNTERFRANKEN_MAX_LON_X, UNTERFRANKEN_MAX_LAT_Y);
        Coordinate p4 = new Coordinate(UNTERFRANKEN_MIN_LON_X, UNTERFRANKEN_MAX_LAT_Y);
        Coordinate[] germanySquare = {p1, p2, p3, p4, p1};
        return Factory.FACTORY.createPolygon(germanySquare);
    }



}
