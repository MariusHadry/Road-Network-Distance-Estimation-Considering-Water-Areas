# Distance Estimation


This is the companion repository for "Road Network Distance Estimation Considering Water Areas". 
The repository is structured as follows:

* `custom-osrm` is our modified version of [OSRM](https://project-osrm.org/) which allows measuring the execution time for single queries
* `distance-estimator` contains the Java (Spring Boot) code for a REST-service that can be used to query the distance estimation approaches
* `experiments` is a collection of Python scripts and Jupyter notebooks that can be used for executing our experiments and analyzing their results
* The `docker-compose.yml` allows starting all services. Make sure to follow the instructions below for a successful setup



## Distance Estimation Setup

1. Install [Docker](https://docs.docker.com/engine/install/)
2. Prepare custom OSRM
    * Go to the `custom-osrm` directory and execute `docker build --tag 'custom-osrm' . -f docker/Dockerfile`
    * Download the desired OSM Data, e.g., for lower Franconia using `wget https://download.geofabrik.de/europe/germany/bayern/unterfranken-latest.osm.pbf` and store it in the directory `data/`
    * Prepare data for OSRM running the following commands in the `data/` directory, but make sure to adjust the name (`Unterfranken_25-01-2025` here) accordingly:
        * `docker run -t -v "${PWD}:/data" custom-osrm-newest osrm-extract -p /opt/car.lua /data/Unterfranken_25-01-2025.osm.pbf`
        * `docker run -t -v "${PWD}:/data" custom-osrm-newest osrm-partition /data/Unterfranken_25-01-2025.osrm || echo "osrm-partition failed"`
        * `docker run -t -v "${PWD}:/data" custom-osrm-newest osrm-customize /data/Unterfranken_25-01-2025.osrm || echo "osrm-customize failed`
        * `docker run -t -i -p 5000:5000 -v "${PWD}:/data" custom-osrm-newest osrm-routed --algorithm mld /data/Unterfranken_25-01-2025.osrm`
3. Prepare Distance Estimation Service
    * [optional] change the database password in the `docker-compose.yml` and change the password in `DatabaseAccess` class accordingly.
    * Build the distance-estimation container: Execute `docker build --no-cache --tag 'distance-estimation' . ` in the `distance-estimator` directory
4. Start the containers
    * [optional] check the volume mappings in the docker `docker-compose.yml` and adjust them to your liking. Make sure to only change the local path. The mapping follows the syntax `<local>:<in container>`. If you want to use the same data as we did for the Overhead Graph, you can use the preprocessing files located in `distance-estimator/preprocessing_files/`.
    * Run `docker compose up -d` in the project root directory
    * **Note**: initially starting the distance-estimation-service requires some time due to the preprocessing taking place!


## Experiment Setup

1. Install [Python](https://www.python.org/)
2. Install Python dependencies
    * in `pip install -r requirements.txt`
3. Update database connection information
    * [optional] Modify `Experiments/generate_random_coordinate_pairs.py` and change the database password in line 22 to match the password provided in the `docker-compose.yml`
4. Run Experiments
    * Run interleaved trials: `python interleaved_trials.py`
    * Run clustering: `python clustering.py`
5. After generating the results, the notebooks in `result_analysis/` can be used for analyzing the results and generating plots



## Additional files

Additional files can be found on [Zenodo](https://zenodo.org/records/14882138?token=eyJhbGciOiJIUzUxMiJ9.eyJpZCI6IjlmMTQzMDg3LTc2MDAtNDA4Yi1iOTNiLTBjZDk0NDU5MDNlYiIsImRhdGEiOnt9LCJyYW5kb20iOiI5OGJkMzFmMDk4OGEzNzY2MjU0ZDY1MDA2MmJiNWEzZCJ9.Kcn9bVHAG8S42cKsGoOX1i9eEgLrVrTVATKiwFTkKJ4pzTs_CNvN_6PJfAHY1P9IxlpqF62gs0nUfPhcfqGnjw).
Here, you can find the raw results and the OpenStreetMap information used for the experiments.


