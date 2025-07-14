package de.uniwuerzburg.distanceestimation.util;

import java.util.ArrayList;
import java.util.List;

public class Debug {
    public static final boolean DEBUG = false;
    public static final boolean LOG_ONLY = false;
    public static List<String> log = new ArrayList<>();

    public static DurationTimer debugTimer = new DurationTimer();

    public static void startDebugTimer() {
        if (DEBUG) {
            debugTimer.restart();
        }
    }

    public static void stopDebugTimer(String message) {
        if (DEBUG) {
            debugTimer.stop();
            message(message + ": " + debugTimer.getDuration() + " ns, " + debugTimer.getDurationMilliseconds() + " ms.");
        }
    }

    public static void message(String message) {
        if (DEBUG) {
            if (!LOG_ONLY) {
                System.out.println(message);
            }
            log.add(message);
        }
    }
}
