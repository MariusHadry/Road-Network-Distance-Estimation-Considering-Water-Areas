import json

import requests

from generate_random_coordinate_pairs import get_random_points_clustering
from interleaved_trials import DistanceEstimationApproach
from util import Location


class ClusterRequest:
    # BASE_URL: str = 'http://marius-distance-estimation.cloud.descartes.tools:8080/estimation/cluster?'
    BASE_URL: str = 'http://localhost:8080/estimation/cluster?'

    @staticmethod
    def get_cluster(locations: list[Location], estimation_approach, k=2):
        locations_body = []

        for l in locations:
            locations_body.append({"lat": l.lat, "lon": l.lon})

        body_object = {
            "locations": locations_body,
            "approachType": estimation_approach.value,
            "k": k
        }

        response = requests.post(ClusterRequest.BASE_URL, json=body_object)
        return response.json()


def cluster_centroids_to_geojson(clusters):
    color_list = ['#81B29A', '#1F271B', '#F7A072', '#6C4B5E', '#B3679B']
    symbol_list = ['triangle', 'circle', 'square', 'cross', 'marker']

    cluster_list = []

    if len(clusters) > len(color_list):
        raise Exception(f"not enough colors/markers for more than {len(color_list)} clusters")

    for i, c in enumerate(clusters):
        coordinates = []
        coordinates.append([c['representation']['lon'], c['representation']['lat']])

        to_add = {
            "type": "Feature",
            "geometry": {
                "type": "MultiPoint",
                "coordinates": coordinates
            },
            "properties": {
                "marker-color": color_list[i],
                "marker-size": "large",
                "marker-symbol": symbol_list[i]
            }
        }

        cluster_list.append(to_add)

    return {"type": "FeatureCollection", "features": cluster_list}

def cluster_to_geojson(clusters):
    # The output allows to be directly pasted into geojson.io for visualization
    # The visualization in the paper, however, is obtained through `clustering.ipynb`

    color_list = ['#81B29A', '#000000', '#F7A072', '#6C4B5E', '#B3679B']
    symbol_list = ['triangle', 'circle', 'square', 'cross', 'marker']

    cluster_list = []

    if len(clusters) > len(color_list):
        raise Exception(f"not enough colors/markers for more than {len(color_list)} clusters")

    for i, c in enumerate(clusters):
        coordinates = []
        for e in c['elements']:
            coordinates.append([e['lon'], e['lat']])

        to_add = {
            "type": "Feature",
            "geometry": {
                "type": "MultiPoint",
                "coordinates": coordinates
            },
            "properties": {
                "marker-color": color_list[i],
                "marker-size": "medium",
                "marker-symbol": symbol_list[i],
                "centroid-lat": c['representation']['lat'],
                "centroid-lon": c['representation']['lon']
            }
        }
        cluster_list.append(to_add)

    return {"type": "FeatureCollection", "features": cluster_list}


if __name__ == '__main__':
    random_points = get_random_points_clustering(20, random_seed=42)
    print(len(random_points))

    for approach in [DistanceEstimationApproach.HAVERSINE,
                     DistanceEstimationApproach.OSRM,
                     DistanceEstimationApproach.WATER_GRAPH_CIRCUITY,
                     DistanceEstimationApproach.OVERHEAD_GRAPH_1024]:
        tmp = ClusterRequest.get_cluster(random_points, k=3, estimation_approach=approach)

        geo_json_clusters = cluster_to_geojson(tmp['clusters'])
        # geo_json_centroids = cluster_centroids_to_geojson(tmp['clusters'])
        print(approach.value, ":\n", "---")

        print(json.dumps(geo_json_clusters))
        print()
