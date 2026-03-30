import os
import random
import datetime
import subprocess
import time
from pathlib import Path
from statistics import mean, stdev

import pandas as pd
from scipy import stats
from tqdm import tqdm

from generate_random_coordinate_pairs import get_random_location_pairs
from util import DistanceEstimation, OSRM, DistanceEstimationApproach, HERE, Location


# important subset
approaches_to_evaluate = [DistanceEstimationApproach.HAVERSINE,
                      DistanceEstimationApproach.OSRM,
                      DistanceEstimationApproach.HYBRID_BRIDGE_SPLIT_HAVERSINE,
                      DistanceEstimationApproach.HYBRID_BRIDGE_SPLIT_OHG,
                      DistanceEstimationApproach.BRIDGE_SPLIT_REC,
                      DistanceEstimationApproach.BRIDGE_SPLIT_NO_REC,
                      DistanceEstimationApproach.WATER_GRAPH_CIRCUITY,
                      DistanceEstimationApproach.HYBRID_WATER_GRAPH_HAVERSINE,
                      DistanceEstimationApproach.HYBRID_WATER_GRAPH_OHG,
                      DistanceEstimationApproach.OVERHEAD_GRAPH_256,
                      DistanceEstimationApproach.OVERHEAD_GRAPH_512,
                      DistanceEstimationApproach.OVERHEAD_GRAPH_1024]

# all approaches
# approaches_to_evaluate = [DistanceEstimationApproach.HAVERSINE,
#                       DistanceEstimationApproach.OSRM,
#                       DistanceEstimationApproach.WATER_GRAPH_CIRCUITY,
#                       DistanceEstimationApproach.HYBRID_BRIDGE_SPLIT_HAVERSINE,
#                       DistanceEstimationApproach.HYBRID_BRIDGE_SPLIT_OHG,
#                       DistanceEstimationApproach.BRIDGE_SPLIT_REC,
#                       DistanceEstimationApproach.BRIDGE_SPLIT_NO_REC,
#                       DistanceEstimationApproach.OVERHEAD_GRAPH_256,
#                       DistanceEstimationApproach.OVERHEAD_GRAPH_512,
#                       DistanceEstimationApproach.OVERHEAD_GRAPH_1024]

# settings for cedric
# approaches_to_evaluate = [DistanceEstimationApproach.HAVERSINE,
#                       DistanceEstimationApproach.OSRM,
#                       DistanceEstimationApproach.WATER_GRAPH_CIRCUITY,
#                       DistanceEstimationApproach.BRIDGE_NO_REC,
#                       DistanceEstimationApproach.BRIDGE_SPLIT_NO_REC,
#                       DistanceEstimationApproach.OVERHEAD_GRAPH_1024]

class SingleRun:
    def __init__(self, start_location, dest_location, approach: DistanceEstimationApproach):
        self.start_location = start_location
        self.dest_location = dest_location
        self.approach = approach

    def __str__(self):
        return f"SingleRun[{self.start_location},{self.dest_location},{self.approach}]"


def create_interleaved_order(start_dest_list, repetitions,
                             ignore_approaches: list[DistanceEstimationApproach] | None = None,
                             use_approaches: list[DistanceEstimationApproach] | None = None
                             ) -> list:
    ret = []
    initial_list = get_distance_only_runs(start_dest_list, ignore_approaches, use_approaches)
    randomness = random.Random(1234)

    for _ in range(repetitions):
        ret += randomness.sample(initial_list, len(initial_list))

    return ret

def get_distance_only_runs(start_dest_list, ignore_approaches: list[DistanceEstimationApproach] | None = None,
                           use_approaches: list[DistanceEstimationApproach] | None = None) -> list:
    if ignore_approaches is not None and use_approaches is not None:
        raise Exception("Illegal arguments. Can only provide one of both!")

    ret = []

    if use_approaches is None:
        approaches_to_use =  [approach for approach in DistanceEstimationApproach]
    else:
        approaches_to_use = use_approaches

    if ignore_approaches is None:
        ignore_approaches = []

    for start_loc, dest_loc in start_dest_list:
        for approach in approaches_to_use:
            if approach not in ignore_approaches:
                ret.append(SingleRun(start_loc, dest_loc, approach))

    return ret

def extract_results(query_result):
    distance_meters = -1
    duration_nanos = query_result['durationNanos']

    if 'distanceMeters' in query_result:
        # our approaches
        distance_meters = query_result['distanceMeters']
    elif 'routes' in query_result:
        # OSRM
        distance_meters = query_result['routes'][0]['legs'][0]['distance']
    else:
        raise Exception("Do not know how to parse: " + query_result)

    return distance_meters, duration_nanos


def execute_run(run_list: list[SingleRun]):
    results = {}

    for run in tqdm(run_list):
        if run.approach.is_distance_estimation():
            query_results = DistanceEstimation.get_distance(run.start_location, run.dest_location, run.approach)
        elif run.approach == DistanceEstimationApproach.OSRM:
            query_results = OSRM.get_distance_response(run.start_location, run.dest_location)
        else:
            raise NotImplementedError()

        distance_meters, duration_nanos = extract_results(query_results)

        result_id = f"{run.approach.value};{str(run.start_location)}->{str(run.dest_location)}"

        if result_id not in results:
            results[result_id] = {
                "start_loc": str(run.start_location),
                "dest_loc": str(run.dest_location),
                "approach": run.approach.value,
                "individual_times_nanos": [duration_nanos],
                "distance_meters": distance_meters,
                "n_repetitions": 1
            }
        else:
            results[result_id]['individual_times_nanos'].append(duration_nanos)
            results[result_id]['n_repetitions'] = results[result_id]['n_repetitions'] + 1

    # calculate metrics of results
    for key in results.keys():
        if len(results[key]['individual_times_nanos']) > 1:
            measured_times_logic = [x / 1e6 for x in results[key]['individual_times_nanos']]
            results[key]["avg_time_ms"] = mean(measured_times_logic)
            results[key]["std_time_ms"] = stdev(measured_times_logic)
            results[key]["trimmed_ms"] = stats.trim_mean(measured_times_logic, 0.05)

    # convert results dict to list
    ret = []
    for value in results.values():
        ret.append(value)

    return ret


def crosses_water_area(start_dest_list):
    ret = []

    for start, dest in start_dest_list:
        _res_crossed = DistanceEstimation.get_crosses_water_area(start, dest)
        _res_analyzed = DistanceEstimation.get_water_areas_analyzed(start, dest)

        # print(_res_crossed)
        # print(_res_analyzed)

        _tmp = {
            'start_loc': start,
            'dest_loc': dest,
            'crosses_water': _res_crossed['crossesWater'],
            'crosses_river': _res_crossed['crossesRiver'],
            'wg_analyzed': _res_analyzed['wgWaterAreasAnalyzed'],
            'wg_intersected': _res_analyzed['wgWaterIntersected'],
            'bre_analyzed': _res_analyzed['breWaterAreasAnalyzed'],
            'bre_intersected': _res_analyzed['breWaterIntersected'],
            'bres_analyzed': _res_analyzed['bresWaterAreasAnalyzed'],
            'bres_intersected': _res_analyzed['bresWaterIntersected'],
        }

        ret.append(_tmp)

    return ret


def experiments_computation_speed_accuracy(name, distance_ranges, points_per_range, area_based=True, range_based=False, focus_on_water_areas=False):
    use_approaches = approaches_to_evaluate
    repetitions = 100

    # configure distance ranges for evaluation
    start_dest_list = get_random_location_pairs(distance_ranges, points_per_range,
                                                area_based=area_based, range_based=range_based,
                                                focus_on_water_areas=focus_on_water_areas)

    lst = create_interleaved_order(start_dest_list, repetitions, use_approaches=use_approaches)
    print("finished creating interleaved trial plan, starting execution...")

    os.makedirs(f"result_files/{name}", exist_ok=True)
    distance_only_trials = get_distance_only_runs(start_dest_list, use_approaches=use_approaches)
    result = execute_run(distance_only_trials)
    df = pd.DataFrame(result)
    df.to_csv(f"result_files/{name}/distances.csv", sep=";", index=False)
    print("\t- calculated distances")

    crosses_water = crosses_water_area(start_dest_list)
    df = pd.DataFrame(crosses_water)
    df.to_csv(f"result_files/{name}/crosses_water.csv", sep=";", index=False)
    print("\t- calculated water crossings")

    result = execute_run(lst)
    df = pd.DataFrame(result)
    df.to_csv(f"result_files/{name}/computation_times.csv", sep=";", index=False)


def calculate_here_truth(distance_ranges, points_per_range,
                         area_based=False,
                         range_based=False, focus_on_water_areas=False):
    print("Calculating here truth...")

    os.makedirs("result_files/", exist_ok=True)
    lookup_file = Path("result_files/here_truth.csv")

    # Load existing data if it exists
    existing_pairs = set()
    existing_df = pd.DataFrame()

    if lookup_file.is_file():
        existing_df = pd.read_csv(lookup_file, sep=";")
        existing_df['start_loc'] = existing_df['start_loc'].apply(Location.from_string)
        existing_df['dest_loc'] = existing_df['dest_loc'].apply(Location.from_string)
        existing_pairs = set(zip(existing_df['start_loc'], existing_df['dest_loc']))
        print(f"\t -> Found {len(existing_pairs)} existing entries.")

    # get random location pairs
    start_dest_list = get_random_location_pairs(distance_ranges, points_per_range,
                                                area_based=area_based, range_based=range_based,
                                                focus_on_water_areas=focus_on_water_areas)

    # filter new pairs
    new_pairs = [pair for pair in start_dest_list if (pair[0], pair[1]) not in existing_pairs]

    if not new_pairs:
        print("\t -> All pairs already calculated!")
        return

    print(f"\t -> Calculating {len(new_pairs)} new pairs...")

    df_entries = []
    for pair in tqdm(new_pairs):
        _tmp = {
            'start_loc': pair[0],
            'dest_loc': pair[1],
            'approach': 'HERE'
        }

        try:
            _tmp['distance_meters'] = HERE.get_distance_only(pair[0], pair[1])
        except Exception as e:
            print(f"Error calculating distance: {e}")
            _tmp['distance_meters'] = -999

        df_entries.append(_tmp)

    # combine results and save
    new_df = pd.DataFrame(df_entries)
    final_df = pd.concat([existing_df, new_df], ignore_index=True)
    final_df.to_csv(lookup_file, sep=";", index=False)
    print(f"\t -> Done! Results saved to {lookup_file}")


def measure_cpu(name, distance_ranges, points_per_range, number_of_runs,
                area_based=False,
                range_based=False, focus_on_water_areas=False
                ):
    use_approaches = approaches_to_evaluate
    repetitions = 10
    start_dest_list = get_random_location_pairs(distance_ranges, points_per_range,
                                                area_based=area_based,
                                                range_based=range_based, focus_on_water_areas=focus_on_water_areas)

    os.makedirs(f"result_files/{name}/cpu", exist_ok=True)
    results_path = f"{os.getcwd()}/result_files/{name}/cpu"
    distance_only_trials = get_distance_only_runs(start_dest_list, use_approaches=use_approaches)
    execute_run(distance_only_trials)
    print("\t- warmup done")


    for run in range(number_of_runs):
        print("===============")
        print(f"==  run: {run:02d}  ==")
        print("===============")

        for approach in use_approaches:
            # make sure to use only one approach
            lst = create_interleaved_order(start_dest_list, repetitions, use_approaches=[approach])
            container = "root-custom-osrm-service-1" if approach == DistanceEstimationApproach.OSRM else "root-distance-estimator-1"
            process = subprocess.Popen(["python3", "monitor_container.py", "-c", container, "-f", f"{results_path}/{run}_{approach}_cpu"])
            time.sleep(5)
            execute_run(lst)
            time.sleep(5)
            process.terminate()

        # measure overhead of OSRM
        process = subprocess.Popen(["python3", "monitor_container.py", "-c", "root-custom-osrm-service-1", "-f", f"{results_path}/{run}_OSRM_overhead_cpu"])
        time.sleep(5)
        for i in range(repetitions * points_per_range * len(distance_ranges)):
            OSRM.overhead_query()
        time.sleep(5)
        process.terminate()

        # measure overhead of other approaches
        for approach in use_approaches:
            if not approach.is_distance_estimation():
                continue

            process = subprocess.Popen(["python3", "monitor_container.py", "-c", "root-distance-estimator-1", "-f", f"{results_path}/{run}_{approach}_overhead_cpu"])
            # process = subprocess.Popen(["python3", "monitor_container.py", "-c", "root-distance-estimator-cedric-1", "-f", f"{results_path}/{run}_{approach}_overhead_cpu"])
            time.sleep(5)
            for i in range(repetitions * points_per_range * len(distance_ranges)):
                DistanceEstimation.overhead_query(approach)
            time.sleep(5)
            process.terminate()



if __name__ == '__main__':
    range_based = False
    focus_on_water_areas = False
    area_based = True
    multiple_ranges = False

    name = datetime.datetime.now().strftime('%Y-%m-%d %H-%M-%S')

    if range_based:
        name += f" (range_based, focus_on_water_areas={focus_on_water_areas}"
    elif area_based:
        name += " (area_based"

    if multiple_ranges:
        name += ", multiple_ranges)"
    else:
        name += ")"

    if multiple_ranges:
        distance_ranges = [(500, 5_000), (5_001, 10_000), (10_001, 15_000), (15_001, 20_000), (20_001, 25_000),
                           (25_001, 30_000), (30_001, 35_000), (35_001, 40_000), (40_001, 45_000), (45_001, 50_000),
                           (50_001, 55_000)]
        points_per_range = 2_000
    else:
        distance_ranges = [(200, 50_000)]
        # points_per_range = 20_000
        points_per_range = 33_000

    calculate_here_truth(distance_ranges=distance_ranges, points_per_range=points_per_range,
                         area_based=area_based,
                         range_based=range_based, focus_on_water_areas=focus_on_water_areas)

    experiments_computation_speed_accuracy(name,
                                           distance_ranges=distance_ranges, points_per_range=points_per_range,
                                           area_based=area_based,
                                           range_based=range_based, focus_on_water_areas=focus_on_water_areas)

    measure_cpu(name, distance_ranges=distance_ranges, points_per_range=points_per_range,
                area_based=area_based, range_based=range_based, focus_on_water_areas=focus_on_water_areas,
                number_of_runs=10)
