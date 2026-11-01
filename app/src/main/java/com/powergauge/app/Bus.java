package com.powergauge.app;

import java.util.concurrent.CopyOnWriteArrayList;

/** 进程内的读数总线：前台服务采样，界面订阅，顺便统计本次运行的功率区间。 */
public final class Bus {

    public interface Listener {
        void onReading(Sampler.Reading reading);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Sampler.Reading sLast;
    /** 本次运行期间出现过的功率区间：放电为负、充电为正。 */
    private static volatile double sMinWatts = Double.POSITIVE_INFINITY;
    private static volatile double sMaxWatts = Double.NEGATIVE_INFINITY;

    private Bus() {
    }

    public static void subscribe(Listener listener) {
        LISTENERS.addIfAbsent(listener);
        Sampler.Reading last = sLast;
        if (last != null) {
            listener.onReading(last);
        }
    }

    public static void unsubscribe(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void publish(Sampler.Reading reading) {
        sLast = reading;
        if (reading != null && reading.valid) {
            double watts = reading.watts;
            if (watts < sMinWatts) {
                sMinWatts = watts;
            }
            if (watts > sMaxWatts) {
                sMaxWatts = watts;
            }
        }
        for (Listener listener : LISTENERS) {
            listener.onReading(reading);
        }
    }

    public static Sampler.Reading last() {
        return sLast;
    }

    public static double minWatts() {
        return sMinWatts;
    }

    public static double maxWatts() {
        return sMaxWatts;
    }

    public static boolean hasRange() {
        return sMinWatts != Double.POSITIVE_INFINITY && sMaxWatts != Double.NEGATIVE_INFINITY;
    }

    /** 服务重新起来时算「新的一次运行」，区间归零。 */
    public static void resetRange() {
        sMinWatts = Double.POSITIVE_INFINITY;
        sMaxWatts = Double.NEGATIVE_INFINITY;
    }
}
