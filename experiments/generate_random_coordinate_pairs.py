import json
import random

import math
from pathlib import Path

import psycopg2
from tqdm import tqdm

import util
from util import Location, OSRM, DistanceEstimation


database_connection = {
    'user': 'admin',
    'password': "",
    # 'host': "127.0.0.1",
    'host': "65.21.234.102",
    'port': "5432",
    'database': 'osm'
}


def get_random_points_clustering(k_points=50, n_biggest_districts=25, random_seed=42, print_query=False):
    connection = None
    cursor = None
    k_query = k_points

    random_coordinates = []

    try:
        connection = psycopg2.connect(user=database_connection['user'], password=database_connection['password'],
                                      host=database_connection['host'], port=database_connection['port'],
                                      database=database_connection['database'])
        cursor = connection.cursor()
        query = f"""SELECT name, ST_AsGeoJSON(ST_Transform(ST_GeneratePoints(way, {k_query}, {random_seed}), 4326))
                                        FROM planet_osm_polygon
                                        WHERE boundary = 'administrative' AND name in ('Frauenland', 'Heidingsfeld', 'Würzburg Altstadt', 'Zellerau', 'Sanderau')
                                        ORDER BY ST_Area(way) DESC
                                        LIMIT {n_biggest_districts}"""

        if print_query:
            print(query)

        cursor.execute(query)
        query_result = cursor.fetchall()

        for row in query_result:
            coordinates = json.loads(row[1])['coordinates']

            for i in range(0, len(coordinates)):
                c = Location(coordinates[i][1], coordinates[i][0])
                random_coordinates.append(c)
    except (Exception, psycopg2.Error) as error:
        print("Error while fetching data from PostgreSQL", error)

    finally:
        if connection:
            connection.close()
    if cursor:
        cursor.close()

    random.seed(123)
    random.shuffle(random_coordinates)
    ret_coordinates = []

    # check if points are close to roads!
    for c in random_coordinates:
        _location_snap = OSRM.get_snapped_location(c)
        ret_coordinates.append(Location(lat=_location_snap[1], lon=_location_snap[0]))

        if len(ret_coordinates) == k_points:
            break

    return ret_coordinates

def get_all_points_as_geoJSON(coordinates):
    coordinates_list = []

    for l1, l2 in coordinates:
        coordinates_list.append([l1.lon, l1.lat])
        coordinates_list.append([l2.lon, l2.lat])

    return {
        "type": "MultiPoint",
        "coordinates": coordinates_list
    }


def _generate_random_location_within_distance(base_location: Location, min_distance, max_distance):
    """
    Generate a random coordinate that is between min_distance and max_distance meters
    away from a given base coordinate.

    Parameters:
        base_location (Location): Base location for finding the randomly generated point
        min_distance (float): Minimum distance from the base point in meters.
        max_distance (float): Maximum distance from the base point in meters.

    Returns:
        Location: random location within range of base_location
    """
    # Convert degrees to radians
    base_lat_rad = math.radians(base_location.lat)
    base_lon_rad = math.radians(base_location.lon)

    # find random distance and bearing (direction)
    distance = random.uniform(min_distance, max_distance)
    bearing = random.uniform(0, 2 * math.pi)

    new_lat_rad = math.asin(math.sin(base_lat_rad) * math.cos(distance / util.EARTH_RADIUS) +
                            math.cos(base_lat_rad) * math.sin(distance / util.EARTH_RADIUS) * math.cos(bearing))

    new_lon_rad = math.atan2(math.sin(bearing) * math.sin(distance / util.EARTH_RADIUS) * math.cos(base_lat_rad),
                             math.cos(distance / util.EARTH_RADIUS) - math.sin(base_lat_rad) * math.sin(
                                 new_lat_rad)) + base_lon_rad

    # Convert radians back to degrees
    new_lat = math.degrees(new_lat_rad)
    new_lon = math.degrees(new_lon_rad)

    return Location(lat=new_lat, lon=new_lon)


def generate_random_location_pair_area_based(min_distance: [int|None] = None,
                                             max_distance: [int|None] = None,
                                             retry_counter: int = 0) -> tuple[Location, Location] | None:
    """
    Generate random coordinate pair within a bounding box. This is solely based on distance and checks if a water area
    is in between or not.

    Parameters:
        lower_left_location (Location|None): Location of the lower left point of the bounding box. Automatically use bounding box point of lower franconia if not specified.
        upper_right_location (Location|None): Location of the upper right point of the bounding box. Automatically use bounding box point of lower franconia if not specified.
        min_distance (int|None): Minimum distance between generated points in meters. Zero if None.
        max_distance (int|None): Minimum distance between generated points in meters. 5,000,000 if None.
        retry_counter (int): Keeps track of the recursive retries before choosing a distance that does not cross a water area. The threshold is at three retries.

    Returns:
        list: A (latitude, longitude) tuple. None is never returned due to the recursion!
    """
    connection = None
    cursor = None

    if min_distance is None:
        min_distance = 200
    if max_distance is None:
        max_distance = 50_000

    location_1 = None
    location_2 = None

    try:
        connection = psycopg2.connect(user=database_connection['user'], password=database_connection['password'],
                                      host=database_connection['host'], port=database_connection['port'],
                                      database=database_connection['database'])

        cursor = connection.cursor()
        query = f"""WITH random_start_raw AS (
                    -- 1. get random point in industrial area
                    SELECT (ST_Dump(ST_GeneratePoints(way, 1))).geom AS point_a_raw
                    FROM planet_osm_polygon 
                    WHERE landuse = 'industrial' 
                    ORDER BY random() LIMIT 100
                ),
                snapped_start AS (
                    -- 2. Snap point A to highway
                    SELECT 
                        ST_ClosestPoint(line.way, r.point_a_raw) AS point_a_3857
                    FROM random_start_raw r
                    CROSS JOIN LATERAL (
                        SELECT way FROM planet_osm_line
                        WHERE highway IS NOT NULL 
                          AND highway NOT IN ('motorway', 'motorway_link', 'trunk', 'trunk_link', 'steps', 'footway')
                        ORDER BY way <-> r.point_a_raw
                        LIMIT 1
                    ) line
                ),
                projected_target AS (
                    -- 3. find random point within distance
                    SELECT 
                        point_a_3857, 
                        (random() * ({max_distance} - {min_distance}) + {min_distance}) AS dist_m, 
                        radians(random() * 360) AS angle_rad
                    FROM snapped_start
                ),
                calculated_points_raw AS (
                    -- 4. project point b
                    SELECT 
                        point_a_3857, 
                        dist_m, 
                        ST_Transform(
                            ST_Project(
                                ST_Transform(point_a_3857, 4326)::geography, dist_m, angle_rad
                            )::geometry, 3857
                        ) AS point_b_raw
                    FROM projected_target
                ),
                valid_paths AS (
                    -- 5. snap point b and check landuse
                    SELECT 
                        cp.point_a_3857, 
                        ST_ClosestPoint(line.way, cp.point_b_raw) AS point_b_3857,
                        cp.dist_m,
                        osm.landuse AS b_landuse
                    FROM calculated_points_raw cp
                    CROSS JOIN LATERAL (
                        SELECT way FROM planet_osm_line
                        WHERE highway IS NOT NULL 
                          AND highway NOT IN ('motorway', 'motorway_link', 'trunk', 'trunk_link', 'steps', 'footway')
                        ORDER BY way <-> cp.point_b_raw
                        LIMIT 1
                    ) line
                    JOIN planet_osm_polygon osm 
                      ON ST_DWithin(osm.way, ST_ClosestPoint(line.way, cp.point_b_raw), 50)
                    WHERE osm.landuse = 'residential'
                )
                -- 6. final output
                SELECT 
                    ST_Y(ST_Transform(point_a_3857, 4326)) AS industrial_lat,
                    ST_X(ST_Transform(point_a_3857, 4326)) AS industrial_lon,
                    ST_Y(ST_Transform(point_b_3857, 4326)) AS b_lat,
                    ST_X(ST_Transform(point_b_3857, 4326)) AS b_lon
                    -- Debugging
                    -- ROUND(dist_m::numeric, 2) AS distance_meters,
                    -- (b_landuse = 'industrial') AS b_is_industrial,
                    -- (b_landuse = 'residential') AS b_is_residential,
                    -- ST_AsGeoJSON(ST_Transform(ST_MakeLine(point_a_3857, point_b_3857), 4326))::jsonb AS geojson_line
                FROM valid_paths 
                LIMIT 1;"""

        cursor.execute(query)
        query_result = cursor.fetchall()

        if len(query_result) > 0:
            _location_snap = OSRM.get_snapped_location(Location(lat=query_result[0][0],
                                                                lon=query_result[0][1]))
            location_1 = Location(lat=_location_snap[1], lon=_location_snap[0])

            _location_snap = OSRM.get_snapped_location(Location(lat=query_result[0][2],
                                                                lon=query_result[0][3]))
            location_2 = Location(lat=_location_snap[1], lon=_location_snap[0])

            # retry if one of the locations is on the highway
            if is_location_on_highway(location_1) or is_location_on_highway(location_2):
                generate_random_location_pair_area_based(min_distance=min_distance, max_distance=max_distance)

            osrm_dist: float = OSRM.get_distance_only(location_1, location_2)
            if not (min_distance < osrm_dist <= max_distance):
                location_1 = None
                location_2 = None

    except (Exception, psycopg2.Error) as error:
        print("Error while fetching data from PostgreSQL", error)

    finally:
        if connection:
            connection.close()
    if cursor:
        cursor.close()

    if location_1 and location_2:
        return location_1, location_2
    else:
        return generate_random_location_pair_area_based(min_distance=min_distance, max_distance=max_distance)


def is_location_on_highway(location: Location, radius: int = 20) -> bool:

    query = f""" WITH input_point AS (
                SELECT ST_Transform(ST_SetSRID(ST_Point({location.lon}, {location.lat}), 4326), 3857) AS geom
            ),
            nearest_road AS (
                SELECT 
                    highway, ST_Distance(way, (SELECT geom FROM input_point)) AS dist
                FROM planet_osm_line
                WHERE highway IS NOT NULL AND ST_DWithin(way, (SELECT geom FROM input_point), {radius})
                ORDER BY way <-> (SELECT geom FROM input_point) -- efficient nearest-neighbor Index Scan
                LIMIT 1
            )
            SELECT 
                COALESCE(nr.highway, 'no road found') AS road_type, -- this is only for debugging
                CASE 
                    WHEN nr.highway IN ('motorway', 'motorway_link', 'trunk', 'trunk_link') THEN true
                    ELSE false
                END AS is_accessible_start
            FROM (SELECT 1) AS dummy 
            LEFT JOIN nearest_road nr ON true;
    """

    connection = None
    cursor = None

    try:
        connection = psycopg2.connect(user=database_connection['user'], password=database_connection['password'],
                                      host=database_connection['host'], port=database_connection['port'],
                                      database=database_connection['database'])
        cursor = connection.cursor()
        cursor.execute(query)
        query_result = cursor.fetchall()

        return query_result[0][1]

    except (Exception, psycopg2.Error) as error:
        print("Error while checking if location is on highway", error)
    finally:
        if connection:
            connection.close()
    if cursor:
        cursor.close()

    return True

def generate_random_location_pair_distance_based(lower_left_location: [Location | None] = None,
                                                 upper_right_location: [Location | None] = None,
                                                 min_distance: [int|None] = None,
                                                 max_distance: [int|None] = None,
                                                 retry_counter: int = 0,
                                                 focus_on_water_areas: bool = True) -> tuple[Location, Location] | None:
    """
    Generate random coordinate pair within a bounding box. This is solely based on distance and checks if a water area
    is in between or not.

    Parameters:
        lower_left_location (Location|None): Location of the lower left point of the bounding box. Automatically use bounding box point of lower franconia if not specified.
        upper_right_location (Location|None): Location of the upper right point of the bounding box. Automatically use bounding box point of lower franconia if not specified.
        min_distance (int|None): Minimum distance between generated points in meters. Zero if None.
        max_distance (int|None): Minimum distance between generated points in meters. 5,000,000 if None.
        retry_counter (int): Keeps track of the recursive retries before choosing a distance that does not cross a water area. The threshold is at three retries.

    Returns:
        list: A (latitude, longitude) tuple. None is never returned due to the recursion!
    """

    if lower_left_location is None:
        lower_left_location = Location(lat=49.47898, lon=8.97465)
    if upper_right_location is None:
        upper_right_location = Location(lat=50.56779, lon=10.88051)
    min_distance = 0 if min_distance is None else min_distance
    max_distance = 5_000_000 if max_distance is None else max_distance

    min_lat = lower_left_location.lat
    min_lon = lower_left_location.lon
    max_lat = upper_right_location.lat
    max_lon = upper_right_location.lon

    _location_snap = OSRM.get_snapped_location(Location(lat=random.uniform(min_lat, max_lat),
                                                         lon=random.uniform(min_lon, max_lon)))
    random_location = Location(lat=_location_snap[1], lon=_location_snap[0])

    if is_location_on_highway(random_location):
        return generate_random_location_pair_distance_based(lower_left_location, upper_right_location, min_distance,
                                                            max_distance, retry_counter, focus_on_water_areas)


    found = False
    unsuccessful_counter: int = 0

    while not found:
        # We only need to lower the min_distance to ensure a faster process of finding random locations. Increasing
        # max_distance does not make sense as the distance cannot be shorter!
        second_location = _generate_random_location_within_distance(random_location, min_distance * 0.25, max_distance)
        found = second_location.in_bounding_box(lower_left_location, upper_right_location)

        if found:
            # snap location
            _location_snap = OSRM.get_snapped_location(second_location)
            if _location_snap is None:
                return generate_random_location_pair_distance_based(lower_left_location, upper_right_location, min_distance,
                                                                    max_distance, retry_counter, focus_on_water_areas)
            second_location: Location = Location(lat=_location_snap[1], lon=_location_snap[0])

            if is_location_on_highway(second_location):
                return generate_random_location_pair_distance_based(lower_left_location, upper_right_location, min_distance,
                                                                    max_distance, retry_counter, focus_on_water_areas)

            osrm_dist: float = OSRM.get_distance_only(random_location, second_location)

            # make sure that distance range is met
            if min_distance < osrm_dist <= max_distance:
                if focus_on_water_areas:
                    # check if crosses water, if not retry max 50 times
                    if retry_counter >= 50 or DistanceEstimation.get_crosses_water_area(random_location, second_location)['crossesWater']:
                        return random_location, second_location
                    else:
                        # retry
                        # simply using the other mechanism is not wanted here, because we want to choose both coordinates new.
                        # This is especially important for short distances where we might be unable to find a crossed water area!
                        retry_counter += 1
                        return generate_random_location_pair_distance_based(lower_left_location, upper_right_location, min_distance, max_distance, retry_counter, focus_on_water_areas)
                else:
                    return random_location, second_location
            else:
                found = False
                # try other coordinate if not within range
                unsuccessful_counter += 1
                if unsuccessful_counter == 50:
                    return generate_random_location_pair_distance_based(lower_left_location, upper_right_location, min_distance, max_distance, focus_on_water_areas)
    return None


def _store_range_based_lookup(lookup_dict, range_based=True, focus_on_water_areas=True, area_based=False):
    if range_based and focus_on_water_areas:
        lookup_file = Path("range_based_lookup.json")
    elif range_based and not focus_on_water_areas:
        lookup_file = Path("range_based_lookup_no_waterarea_focus.json")
    elif area_based:
        lookup_file = Path("area_based_lookup.json")

    with open(lookup_file, 'w') as f:
        json.dump(lookup_dict, f, default=lambda o: o.__dict__)

def _load_range_based_lookup(range_based=True, focus_on_water_areas=True, area_based=False):
    if range_based and focus_on_water_areas:
        lookup_file = Path("range_based_lookup.json")
    elif range_based and not focus_on_water_areas:
        lookup_file = Path("range_based_lookup_no_waterarea_focus.json")
    elif area_based:
        lookup_file = Path("area_based_lookup.json")

    if lookup_file.is_file():
        f = open(lookup_file)
        content = json.load(f)

        ret_dict = {}
        for k, v in content.items():
            new_values = []
            for e in v:
                new_values.append((Location(lat=e[0]['lat'], lon=e[0]['lon']), Location(lat=e[1]['lat'], lon=e[1]['lon'])))
            ret_dict[k] = new_values

        return ret_dict

    return {}

def _distance_range_str(distance_range: tuple[float, float]):
    return f"{distance_range[0]}-{distance_range[1]}"

def get_random_location_pairs(distance_ranges, points_per_range, random_seed: None|int = None, range_based=False, focus_on_water_areas=False, area_based=False)  -> list[tuple[Location, Location]]:
    if random_seed is not None:
        random.seed(random_seed)

    random_pairs = []
    random_pairs_lookup = _load_range_based_lookup(range_based=range_based, focus_on_water_areas=focus_on_water_areas, area_based=area_based)

    with tqdm(total=len(distance_ranges) * points_per_range, desc="Generating random point pairs") as progress_bar:
        for distance_range in distance_ranges:
            loaded_pairs = []

            if _distance_range_str(distance_range) in random_pairs_lookup:
                loaded_pairs = random_pairs_lookup[_distance_range_str(distance_range)]

            progress_bar.update(len(loaded_pairs))
            _generated_pairs = loaded_pairs

            # generate missing pairs
            if len(loaded_pairs) < points_per_range:
                for _ in range(points_per_range - len(loaded_pairs)):
                    if range_based:
                        _generated_pairs.append(generate_random_location_pair_distance_based(min_distance=distance_range[0],
                                                                                             max_distance=distance_range[1],
                                                                                             focus_on_water_areas=focus_on_water_areas))
                    elif area_based:
                        _generated_pairs.append(
                            generate_random_location_pair_area_based(min_distance=distance_range[0],
                                                                     max_distance=distance_range[1]))
                    else:
                        raise NotImplementedError("Not a valid config for random point generation")
                    progress_bar.update(1)

                # store new pairs
                random_pairs_lookup[_distance_range_str(distance_range)] = loaded_pairs
                _store_range_based_lookup(random_pairs_lookup, range_based=range_based, focus_on_water_areas=focus_on_water_areas, area_based=area_based)

            random_pairs += _generated_pairs[0:points_per_range]

    return random_pairs


if __name__ == '__main__':
    # res = get_random_location_pairs([(0, 5_000), (5_000, 10_000)], 10, random_seed=42)
    #
    # for i, e in enumerate(res):
    #     print(f"{e[0]}, {e[1]} - "
    #           f"Haversine: {haversine(e[0], e[1]):.1f}m - "
    #           f"OSRM distance: {OSRM.get_distance_only(e[0], e[1])}m")
    #     if (i + 1) % 10 == 0:
    #         print("---")
    # get_random_location_pairs([(200, 50_000)], 5_000, random_seed=42, area_based=True)

    a = is_location_on_highway(Location.from_string("Location[50.371787,10.314679]"))
    print(a)