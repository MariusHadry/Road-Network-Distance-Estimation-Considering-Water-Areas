import subprocess
import datetime
import pandas as pd
import argparse

def get_cpu_percentage(container_name) -> float:
    process = subprocess.Popen(['docker', 'stats', container_name, '--no-stream', '--format', '"{{.CPUPerc}}"'],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    stdout, stderr = process.communicate()

    if stdout:
        return float(stdout[1:-3])

    return -1


if __name__ == '__main__':
    measurements = pd.DataFrame(columns=['timestamp', 'cpu-percentage'])

    parser = argparse.ArgumentParser()
    parser.add_argument('-f', '--fileName', help='Name of the .csv file in which the results are stored', required=True)
    parser.add_argument('-c', '--containerName', help='Name of the container which should be monitored', required=True)
    args = vars(parser.parse_args())

    container_name = args['containerName']
    file_name = args['fileName']
    first = True

    while True:
        cpu_percentage = get_cpu_percentage(container_name)
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        measurements = pd.concat([pd.DataFrame([[timestamp, cpu_percentage]], columns=measurements.columns), measurements], ignore_index=True)

        if first:
            measurements.to_csv(f"{file_name}.csv", index=False)
        else:
            with open(f"{file_name}.csv", "a") as f:
                measurements.to_csv(f, header=False, index=False)
