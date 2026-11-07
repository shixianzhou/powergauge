package com.powergauge.app;

import android.os.SystemClock;

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
    /**
     * 启动后先让读数稳定 1 秒，再把功率计入最低/最高统计。
     * 系统刚启动时第一次粘性广播常常是充电握手前的瞬时值，会污染区间。
     */
    private static final long STATS_DELAY_MS = 1000L;
    private static volatile long sStatsStartAt;

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
        if (reading != null && reading.valid && SystemClock.elapsedRealtime() >= sStatsStartAt) {
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

    /** 启动后的 1 秒稳定期内为 false，曲线等其它统计也应跳过这段瞬时值。 */
    public static boolean statsReady() {
        return SystemClock.elapsedRealtime() >= sStatsStartAt;
    }

    /** 稳定期开始的时刻（elapsedRealtime），用于判断这次启动是否还在预热。 */
    public static long statsStartAt() {
        return sStatsStartAt;
    }

    /** 服务重新起来时算「新的一次运行」，区间归零，并等 1 秒后再开始统计。 */
    public static void resetRange() {
        sMinWatts = Double.POSITIVE_INFINITY;
        sMaxWatts = Double.NEGATIVE_INFINITY;
        sStatsStartAt = SystemClock.elapsedRealtime() + STATS_DELAY_MS;
    }
}
