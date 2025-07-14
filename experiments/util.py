import json
from enum import Enum
from urllib.parse import urlencode

import time
import math
import requests

EARTH_RADIUS = 6_371_000 # in meters
DISTANCE_ESTIMATION_SERVICE_ADDRESS = "127.0.0.1:8080"

class Location(object):
    def __init__(self, lat: float, lon: float):
        self.lat = lat
        self.lon = lon

    def __str__(self):
        return f"Location[{self.lat},{self.lon}]"

    @classmethod
    def from_string(cls, string):
        # Expecting a format like "Location[lat,lon]"
        try:
            # Remove the prefix "Location[" and suffix "]"
            content = string[len("Location["):-1]
            # Split the content by the comma
            lat_str, lon_str = content.split(",")
            # Convert to float and return a new Location instance
            return cls(float(lat_str), float(lon_str))
        except (ValueError, IndexError):
            raise ValueError(f"String format is incorrect: {string}")

    def in_bounding_box(self, lower_left, upper_right) -> bool:
        """
        Checks if self is within the given bounding box

        :param lower_left: Location of lower left coordinate
        :param upper_right: Location of upper right coordinate
        :return: True if self is in bounding box
        """
        return lower_left.lat <= self.lat <= upper_right.lat and lower_left.lon <= self.lon <= upper_right.lon


class ExperimentConfiguration:
    def __init__(self, start_location: Location, dest_location: Location, n_repetitions: int = 1):
        self.start_location = start_location
        self.dest_location = dest_location
        self.n_repetitions = n_repetitions

class DistanceEstimationApproach(Enum):
    WATER_GRAPH_CIRCUITY = "WATER_GRAPH_CIRCUITY"
    HAVERSINE = "HAVERSINE"
    WATER_GRAPH = "WATER_GRAPH"
    BRIDGE_REC = "BRIDGE_REC"
    BRIDGE_NO_REC = "BRIDGE_NO_REC"
    BRIDGE_SPLIT_REC = "BRIDGE_SPLIT_REC"
    BRIDGE_SPLIT_NO_REC = "BRIDGE_SPLIT_NO_REC"
    OVERHEAD_GRAPH_128 = "OVERHEAD_GRAPH_128"
    OVERHEAD_GRAPH_256 = "OVERHEAD_GRAPH_256"
    OVERHEAD_GRAPH_512 = "OVERHEAD_GRAPH_512"
    OVERHEAD_GRAPH_1024 = "OVERHEAD_GRAPH_1024"
    OSRM = "OSRM"
    # VALHALLA = "VALHALLA"

    @staticmethod
    def get_estimation_approaches():
        return [DistanceEstimationApproach.WATER_GRAPH, DistanceEstimationApproach.WATER_GRAPH_CIRCUITY,
                DistanceEstimationApproach.HAVERSINE, DistanceEstimationApproach.BRIDGE_REC,
                DistanceEstimationApproach.BRIDGE_NO_REC, DistanceEstimationApproach.BRIDGE_SPLIT_REC,
                DistanceEstimationApproach.BRIDGE_SPLIT_NO_REC]

    @staticmethod
    def get_ground_truth_approach():
        return DistanceEstimationApproach.OSRM


     # check if approach is within distance estimation tool or not
    def is_distance_estimation(self) -> bool:
        return not self.value in {DistanceEstimationApproach.OSRM.value}



class Valhalla:
    BASE_URL = f'http://{DISTANCE_ESTIMATION_SERVICE_ADDRESS}/sources_to_targets?'

    @staticmethod
    def get_distance(start_location: Location, dest_location: Location):
        api_url = Valhalla._build_url(start_location, dest_location)
        response = requests.get(api_url)
        return response.json()

    @staticmethod
    def _build_url(start_location: Location, dest_location: Location) -> str:
        params = {"sources": [{"lat": start_location.lat, "lon": start_location.lon}],
                  "targets": [{"lat": dest_location.lat, "lon": dest_location.lon}],
                  "costing": "auto", "units": "km", "json": "True"}

        return Valhalla.BASE_URL + 'json=' + json.dumps(params)


class OSRM:
    BASE_URL: str = 'http://127.0.0.1:5000/'

    @staticmethod
    def get_distance_nearest_road(location: Location):
        response = requests.get(OSRM._build_url_nearest_road(location))
        _json = response.json()
        return _json['waypoints'][0]['distance'], _json['waypoints'][0]['location']

    @staticmethod
    def get_snapped_location(location: Location):
        _ref_location = Location(lat=49.793094, lon=9.936605)
        _response = OSRM.get_distance_response(location, _ref_location)

        if 'waypoints' not in _response or 'location' not in _response['waypoints'][0]:
            return None

        return _response['waypoints'][0]['location']

    @staticmethod
    def _build_url_nearest_road(location: Location):
        return OSRM.BASE_URL + "nearest/v1/driving/" + f"{location.lon},{location.lat}"

    @staticmethod
    def overhead_query():
        params = {
            'steps': 'false',
            'alternatives': 'false',
            'overview': 'false',
            'exclude': 'ferry'
        }
        api_url = OSRM.BASE_URL + "route/v1/driving/" + f"9.974192,49.782036?" + urlencode(params)

        try:
            response = requests.get(api_url)
            return response.json()
        except Exception:
            time.sleep(0.05)
            return None

    @staticmethod
    def get_distance_response(start_location: Location, dest_location: Location):
        api_url = OSRM._build_url_distance(start_location, dest_location)
        response = requests.get(api_url)
        return response.json()

    @staticmethod
    def get_distance_only(start_location: Location, dest_location: Location):
        api_url = OSRM._build_url_distance(start_location, dest_location)
        response = requests.get(api_url).json()
        if 'routes' in response:
            return response['routes'][0]['legs'][0]['distance']
        return -1

    @staticmethod
    def _build_url_distance(start_location: Location, dest_location: Location) -> str:
        params = {
            'steps': 'false',
            'alternatives': 'false',
            'overview': 'false',
            'exclude': 'ferry'
        }
        return OSRM.BASE_URL + "route/v1/driving/" + f"{start_location.lon},{start_location.lat};{dest_location.lon},{dest_location.lat}?" + urlencode(params)


class DistanceEstimation:
    BASE_URL: str = f'http://{DISTANCE_ESTIMATION_SERVICE_ADDRESS}/estimation/'

    @staticmethod
    def get_path(start_location: Location, dest_location: Location,
                     estimation_approach):
        api_url = DistanceEstimation._build_url(start_location, dest_location, estimation_approach, "path")
        response = requests.get(api_url)
        return response.json()

    @staticmethod
    def overhead_query(estimation_approach):
        start_location = Location(lat=49.782045, lon=9.974259)
        dest_location = Location(lat=49.792492, lon=9.936275)
        api_url = DistanceEstimation._build_url(start_location, dest_location, estimation_approach,"overheadMeasurement")

        try:
            response = requests.get(api_url)
            return response.json()
        except Exception:
            print("-")
            time.sleep(0.05)
            return None

    @staticmethod
    def get_distance(start_location: Location, dest_location: Location, estimation_approach):
        api_url = DistanceEstimation._build_url(start_location, dest_location, estimation_approach,"distance")
        response = requests.get(api_url)
        return response.json()

    @staticmethod
    def get_crosses_water_area(start_location: Location, dest_location: Location):
        api_url = DistanceEstimation._build_url_cross_water_area(start_location, dest_location)
        response = requests.get(api_url)
        return response.json()

    @staticmethod
    def _build_url_cross_water_area(start_location: Location, dest_location: Location) -> str:
        params = {
            'startLat': start_location.lat,
            'startLon': start_location.lon,
            'destLat': dest_location.lat,
            'destLon': dest_location.lon,
        }
        return DistanceEstimation.BASE_URL + "crossesWater" + "?" + urlencode(params)

    @staticmethod
    def _build_url(start_location: Location, dest_location: Location,
                   estimation_approach, endpoint) -> str:
        params = {
            'startLat': start_location.lat,
            'startLon': start_location.lon,
            'destLat': dest_location.lat,
            'destLon': dest_location.lon,
            'approachType': estimation_approach.value
        }
        return DistanceEstimation.BASE_URL + endpoint + "?" + urlencode(params)


def estimation_path_to_geoJSON(path):
    coordinates = []

    for c in path:
        coordinates.append([c['lon'], c['lat']])

    return {"type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {},
                  "geometry": {
                    "type": "LineString",
                    "coordinates": coordinates
                  }
                }
              ]
            }

def pretty_print_dict(input_dict):
    print(json.dumps(input_dict, sort_keys=False, indent=4))

def haversine(location_one, location_two):
    """
    Calculate the great circle distance in meters between two points on the earth (specified in decimal degrees)
    """
    # convert decimal degrees to radians
    lon1 = location_one.lon
    lat1 = location_one.lat
    lon2 = location_two.lon
    lat2 = location_two.lat

    lon1, lat1, lon2, lat2 = map(math.radians, [lon1, lat1, lon2, lat2])

    # haversine formula
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = math.sin(dlat/2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon/2)**2
    c = 2 * math.asin(math.sqrt(a))
    return c * EARTH_RADIUS

if __name__ == '__main__':
    # This snippet can be used to analyze the found paths of the different approaches!
    start = Location.from_string("Location[50.395066,10.28045]")
    dest = Location.from_string("Location[50.390213988,10.263055874]")

    print("Bridge no rec:\n---")
    response = DistanceEstimation.get_path(start, dest, DistanceEstimationApproach.BRIDGE_NO_REC)
    geo_json = estimation_path_to_geoJSON(response['path'])
    print(json.dumps(geo_json))

    response = DistanceEstimation.get_path(start, dest, DistanceEstimationApproach.WATER_GRAPH_CIRCUITY)
    dist_wg = response['distanceMeters']
    print(f"\nWater Graph (circuity) (dist={dist_wg}):\n---")
    geo_json = estimation_path_to_geoJSON(response['path'])
    print(json.dumps(geo_json))

    print("\nOSRM:\n---")
    response = DistanceEstimation.get_path(start, dest, DistanceEstimationApproach.OSRM)
    dist_osrm = response['distanceMeters']
    geo_json = estimation_path_to_geoJSON(response['path'])
    print(json.dumps(geo_json))

    print()
    print("dist WG:", dist_wg)
    print("dist OSRM:", dist_osrm)
    print("dist diff:", dist_osrm - dist_wg)