package de.uniwuerzburg.distanceestimation.util;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DurationTimer {
    private long startTime;
    private long endTime;
    private long duration;
    private boolean started;
    private boolean stopped;

    public DurationTimer() {
    }

    public DurationTimer(boolean start) {
        if (start) {
            start();
        }
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("Start Timer: Timer already started!");
        }
        started = true;
        startTime = System.nanoTime();
    }

    public void restart() {
        started = false;
        stopped = false;
        start();
    }

    public long stop() {
        long temp = System.nanoTime();
        if (!started) {
            throw new IllegalStateException("Stop Timer: Timer not started!");
        }
        if (stopped) {
            throw new IllegalStateException("Stop Timer: Timer already stopped!");
        }
        endTime = temp;
        duration = endTime - startTime;
        stopped = true;
        return duration;
    }

    public long getStartTime() {
        if (!started) {
            throw new IllegalStateException("Get Start Time: Timer not started!");
        }
        return startTime;
    }

    public long getEndTime() {
        if (!stopped) {
            throw new IllegalStateException("Get End Time: Timer not stopped!");
        }
        return endTime;
    }

    public long getDuration() {
        if (!started) {
            throw new IllegalStateException("Get Duration Time: Timer not started!");
        }
        if (!stopped) {
            throw new IllegalStateException("Get Duration Time: Timer not stopped!");
        }
        return duration;
    }

    public long getDurationSeconds() {
        return TimeUnit.SECONDS.convert(getDuration(), TimeUnit.NANOSECONDS);
    }

    public long getDurationMilliseconds() {
        return TimeUnit.MILLISECONDS.convert(getDuration(), TimeUnit.NANOSECONDS);
    }

    public long getDurationMinutes() {
        return TimeUnit.MINUTES.convert(getDuration(), TimeUnit.NANOSECONDS);
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isStopped() {
        return stopped;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DurationTimer timer = (DurationTimer) o;
        return startTime == timer.startTime && endTime == timer.endTime && duration == timer.duration && started == timer.started && stopped == timer.stopped;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime, duration, started, stopped);
    }
}
