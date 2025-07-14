package de.uniwuerzburg.distanceestimation.preprocessing;

import com.google.gson.Gson;
import de.uniwuerzburg.distanceestimation.models.Factory;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.Street;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;

import java.sql.*;
import java.util.*;

public class DatabaseAccess {
    private static final String host = "jdbc:postgresql://127.0.0.1:5432/osm";
    private static final String password = "iWcuUThpbYdmoGRdDFw3UTww4MnU7unb";
    private static final String user = "admin";

    private final Connection conn;

    public DatabaseAccess() {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("ssl", "false");
        try {
            conn = DriverManager.getConnection(host, props);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<Street> getBridgeCandidatesFromDB() {
        HashSet<Street> result = new HashSet<>();
        try {
            PreparedStatement st = conn.prepareStatement(
            """
                    SELECT name, ST_AsEWKB(ST_Transform(way,4326)) AS wkb 
                    FROM planet_osm_line
                    WHERE (bridge is not null or man_made = 'bridge') and highway is not null and	
                            highway not in ('bridleway', 'cicleway', 'cycleway', 'footway', 'no', 'pedestrian', 'path', 'proposed', 'raceway', 'steps') and
		                    (access is null or access in ('yes', 'permissive'))
                """
            );
            var fetchResult = st.executeQuery();
            while (fetchResult.next()) {
                var geometry = new WKBReader().read(fetchResult.getBytes("wkb"));
                result.add(new Street(fetchResult.getString("name"), geometry instanceof MultiLineString ? (MultiLineString) geometry : new MultiLineString(new LineString[]{(LineString) geometry}, Factory.FACTORY)));
            }
            return result;
        } catch (SQLException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<WaterArea> getWaterAreasFromDBBridgeRoute() {
        HashSet<WaterArea> result = new HashSet<>();
        try {
            // Here we would use the lines instead of the polygons.
            // approx takes 120sec in preprocessing locally and results in ~199 (No Split) or 1461 (Split) Water areas
            PreparedStatement st = conn.prepareStatement(
                    """
                        SELECT name, ST_AsEWKB(ST_Buffer(ST_Transform(ST_LineExtend(way, 100, 100), 4326), 0.00005)) as wkb
                        FROM planet_osm_line
                        WHERE (route = 'waterway' OR waterway='river') and name is not null
                    """
            );
            var fetchResult = st.executeQuery();
            while (fetchResult.next()) {
                result.add(new WaterArea(fetchResult.getString("name"), new WKBReader().read(fetchResult.getBytes("wkb"))));
            }
            return result;
        } catch (SQLException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<WaterArea> getWaterAreasFromDBWaterGraph() {
        HashSet<WaterArea> result = new HashSet<>();
        try {
            PreparedStatement st = conn.prepareStatement(
                    """
                            SELECT name, ST_AsEWKB(ST_Transform((ST_DUMP(inner_geom)).geom, 4326)) as wkb
                            FROM (
                                -- Merge clustered water areas to polygons
                                SELECT STRING_AGG(DISTINCT name, ';' order by name asc) as name, ST_Union(geom) as inner_geom
                                FROM (
                                    -- Cluster water areas that are close to each other using DBSCAN
                                    SELECT name, geom, ST_ClusterDBSCAN(geom, eps => 0.0005, minpoints => 1) over () AS cid
                                    FROM (
                                        -- Select all water areas from planet_osm_polygon and planet_osm_line
                                        SELECT name, ST_Transform(way,4326) as geom
                                        FROM planet_osm_polygon
                                        WHERE water not in ('drain', 'ditch', 'absetzbecken', 'basin', 'canal', 'river', 'service;cattle', 'stream', 'wastewater', 'waste_water') and ST_Area(ST_Transform(way,4326),true) > 1000.0
                                        UNION ALL
                                        SELECT name, ST_Buffer(ST_Transform(way, 4326), 0.00005) as geom
                                        FROM planet_osm_line
                                        WHERE (route = 'waterway' OR waterway='river') and name is not null
                                    ) AS sub_1
                                ) AS sub_2
                                GROUP BY cid
                            ) sub_3
                        """
            );
            var fetchResult = st.executeQuery();
            while (fetchResult.next()) {
                result.add(new WaterArea(fetchResult.getString("name"), new WKBReader().read(fetchResult.getBytes("wkb"))));
            }
            return result;
        } catch (SQLException | ParseException e) {
            throw new RuntimeException(e);
        }
    }


    public Set<GeoLocation> getRandomIntersectionLocation(int n_intersections){
        try{
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(
                    """
                       CREATE TABLE IF NOT EXISTS road_intersections AS
                       WITH intersection_points AS (
                           -- Find all intersection points between road segments
                           SELECT ST_Intersection(a.way, b.way) AS intersection_point, a.osm_id AS street_a, b.osm_id AS street_b
                           FROM planet_osm_line a
                           JOIN planet_osm_line b ON a.way && b.way  -- Bounding box pre-filter
                               AND ST_Intersects(a.way, b.way)  -- Ensure geometries actually intersect
                               AND a.osm_id < b.osm_id  -- Avoid duplicate checks
                           WHERE a.highway IS NOT NULL AND b.highway IS NOT NULL -- Consider only roads
                       ),
                       grouped_intersections AS (
                           -- Group by intersection points and count distinct street IDs
                           SELECT intersection_point, COUNT(DISTINCT street_a) + COUNT(DISTINCT street_b) AS street_count
                           FROM intersection_points
                           GROUP BY intersection_point
                       )
                       -- Create the table
                       SELECT *
                       FROM grouped_intersections;
                       """
            );
            stmt.close();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        HashSet<GeoLocation> result = new HashSet<>();
        try {
            PreparedStatement st = conn.prepareStatement(
                    """
                            SELECT geojson
                            FROM (
                            	SELECT setseed(?), null geojson
                            	UNION ALL
                            	SELECT null, ST_AsGeoJSON(ST_Transform(intersection_point, 4326)) as geojson 
                            	FROM road_intersections
                            	WHERE ST_GeometryType(intersection_point) = 'ST_Point'
                            ) sub
                            ORDER BY random()
                            LIMIT ?
                        """
            );
            st.setDouble(1, 0.123 + n_intersections / 1_000_000.0); // set random seed
            st.setInt(2, n_intersections);
            var fetchResult = st.executeQuery();
            while (fetchResult.next()) {
                Gson g = new Gson();
                GeoJsonPoint geoJson = g.fromJson(fetchResult.getString("geojson"), GeoJsonPoint.class);
                GeoLocation newLocation = new GeoLocation(geoJson.coordinates[1], geoJson.coordinates[0]);
                result.add(newLocation);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Set<GeoLocation> getRandomLocations(int n_points){
        HashSet<GeoLocation> result = new HashSet<>();
        try {
            PreparedStatement st = conn.prepareStatement(
                """
                        SELECT ST_AsGeoJSON(ST_Transform(ST_GeneratePoints(sub.geom, ?, 123), 4326)) as geojson
                        FROM (
                            SELECT ST_MakeEnvelope(8.97465, 49.47898, 10.88051, 50.56779, 4326) AS geom
                        ) AS sub
                    """
            );
            st.setInt(1, n_points);
            var fetchResult = st.executeQuery();
            while (fetchResult.next()) {
                Gson g = new Gson();
                GeoJsonMultiPoint geoJson = g.fromJson(fetchResult.getString("geojson"), GeoJsonMultiPoint.class);
                List<GeoLocation> newLocations = Arrays.stream(geoJson.coordinates()).map(e -> new GeoLocation(e[1], e[0])).toList();
                result.addAll(newLocations);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    record GeoJsonMultiPoint (String type, double[][] coordinates){}

    record GeoJsonPoint (String type, double[] coordinates){}
}
