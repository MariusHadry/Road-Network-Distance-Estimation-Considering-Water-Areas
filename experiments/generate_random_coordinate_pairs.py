import json
import random

import math
from pathlib import Path

import psycopg2
from tqdm import tqdm

import util
from util import Location, OSRM, haversine, DistanceEstimation


def get_random_points_clustering(k_points=50, n_biggest_districts=25, random_seed=42, print_query=False):
    connection = None
    cursor = None
    k_query = k_points

    random_coordinates = []

    try:
        connection = psycopg2.connect(user="admin", password="iWcuUThpbYdmoGRdDFw3UTww4MnU7unb",
                                      host="127.0.0.1", port="5432", database="osm")
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


def generate_random_location_within_distance(base_location: Location, min_distance, max_distance):
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


def generate_random_location_pair(lower_left_location: [Location | None] = None, upper_right_location: [Location | None] = None,
                                  min_distance: [int|None] = None, max_distance: [int|None] = None, retry_counter: int = 0) -> tuple[Location, Location] | None:
    """
    Generate random coordinates within a bounding box.

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
    if _location_snap is None:
        return generate_random_location_pair(lower_left_location, upper_right_location, min_distance,
                                             max_distance, retry_counter)
    random_location = Location(lat=_location_snap[1], lon=_location_snap[0])

    found = False
    unsuccessful_counter: int = 0

    while not found:
        # We only need to lower the min_distance to ensure a faster process of finding random locations. Increasing
        # max_distance does not make sense as the distance cannot be shorter!
        second_location = generate_random_location_within_distance(random_location, min_distance * 0.25, max_distance)
        found = second_location.in_bounding_box(lower_left_location, upper_right_location)

        if found:
            # snap location
            _location_snap = OSRM.get_snapped_location(second_location)
            if _location_snap is None:
                return generate_random_location_pair(lower_left_location, upper_right_location, min_distance,
                                                     max_distance, retry_counter)
            second_location: Location = Location(lat=_location_snap[1], lon=_location_snap[0])
            osrm_dist: float = OSRM.get_distance_only(random_location, second_location)

            # make sure that distance range is met
            if min_distance < osrm_dist <= max_distance:
                # check if crosses water, if not retry max 50 times
                if retry_counter >= 50 or DistanceEstimation.get_crosses_water_area(random_location, second_location)['crossesWater']:
                    return random_location, second_location
                else:
                    # retry
                    # simply using the other mechanism is not wanted here, because we want to choose both coordinates new.
                    # This is especially important for short distances where we might be unable to find a crossed water area!
                    retry_counter += 1
                    return generate_random_location_pair(lower_left_location, upper_right_location, min_distance, max_distance, retry_counter)
            else:
                found = False
                # try other coordinate if not within range
                unsuccessful_counter += 1
                if unsuccessful_counter == 50:
                    return generate_random_location_pair(lower_left_location, upper_right_location, min_distance, max_distance)
    return None


def _store_range_based_lookup(lookup_dict):
    lookup_file = Path("range_based_lookup.json")
    with open(lookup_file, 'w') as f:
        json.dump(lookup_dict, f, default=lambda o: o.__dict__)

def _load_range_based_lookup():
    lookup_file = Path("range_based_lookup.json")

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

def get_random_location_pairs(distance_ranges, points_per_range, random_seed: None|int = None)  -> list[tuple[Location, Location]]:
    if random_seed is not None:
        random.seed(random_seed)

    random_pairs = []
    random_pairs_lookup = _load_range_based_lookup()

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
                    _generated_pairs.append(generate_random_location_pair(min_distance=distance_range[0],
                                                                            max_distance=distance_range[1]))
                    progress_bar.update(1)

                # store new pairs
                random_pairs_lookup[_distance_range_str(distance_range)] = loaded_pairs
                _store_range_based_lookup(random_pairs_lookup)

            random_pairs += _generated_pairs[0:points_per_range]

    return random_pairs


if __name__ == '__main__':
    res = get_random_location_pairs([(0, 5_000), (5_000, 10_000)], 10, random_seed=42)

    for i, e in enumerate(res):
        print(f"{e[0]}, {e[1]} - "
              f"Haversine: {haversine(e[0], e[1]):.1f}m - "
              f"OSRM distance: {OSRM.get_distance_only(e[0], e[1])}m")
        if (i + 1) % 10 == 0:
            print("---")
