package com.powergauge.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * 电池容量与充放电统计。
 *
 * 系统没有公开「设计容量 / 健康度」接口，所以这里做两件事：
 *  1. 设计容量由用户在「电池信息」里手动填写；
 *  2. 用电流对时间积分（库仑计数）估算满电容量，再和设计容量比较得到健康度。
 *
 * 积分只在服务持续采样时进行；进程重启、长时间休眠后的空档不补算，
 * 以免把最后一条电流值乘到几小时的空档上。
 */
public final class BatteryStats {

    private static final String FILE = "powergauge_battery_stats";
    private static final long MAX_GAP_MS = 60_000L;       // 超过 1 分钟的空档不积分
    private static final long RESTART_GAP_MS = 5 * 60_000L; // 进程重启且电量变了，重置统计窗口
    private static final double MIN_ESTIMATE_MAH = 1_500.0;
    private static final double MAX_ESTIMATE_MAH = 30_000.0;

    private static BatteryStats sInstance;

    private final Prefs prefs;
    private final SharedPreferences sp;

    /** 上一次积分时刻，elapsedRealtime；0 表示还没开始。 */
    private long lastElapsed;
    /** 上一次采样时刻，wall clock；跨进程重启时用来判断空档。 */
    private long lastWall;
    private long startedWall;

    private int startLevel = -1;
    private int lastLevel = -1;

    /** 本次统计窗口内的累计值，充电为正、放电为负。 */
    private double netMah;
    private double chargedMah;
    private double dischargedMah;
    private double netWh;
    private double chargedWh;
    private double dischargedWh;

    /** 当前方向上的估算窗口：从某个电量点开始，只累计同一方向的电流。 */
    private int segStartLevel = -1;
    private double segMah;
    private int segSign;

    /** 估算出的满电容量（mAh）；0 表示还没有足够数据。 */
    private double fullMah;
    /** 是否在满电时直接读到了 charge_counter（比积分更可信）。 */
    private boolean fullExact;

    private long lastPersistWall;

    private BatteryStats(Context context) {
        Context app = context.getApplicationContext();
        prefs = Prefs.get(app);
        sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);

        startedWall = sp.getLong("started_wall", 0);
        startLevel = sp.getInt("start_level", -1);
        lastLevel = sp.getInt("last_level", -1);
        netMah = sp.getFloat("net_mah", 0f);
        chargedMah = sp.getFloat("charged_mah", 0f);
        dischargedMah = sp.getFloat("discharged_mah", 0f);
        netWh = sp.getFloat("net_wh", 0f);
        chargedWh = sp.getFloat("charged_wh", 0f);
        dischargedWh = sp.getFloat("discharged_wh", 0f);
        segStartLevel = sp.getInt("seg_start_level", -1);
        segMah = sp.getFloat("seg_mah", 0f);
        segSign = sp.getInt("seg_sign", 0);
        fullMah = sp.getFloat("full_mah", 0f);
        fullExact = sp.getBoolean("full_exact", false);
        lastWall = sp.getLong("last_wall", 0);
    }

    public static synchronized BatteryStats get(Context context) {
        if (sInstance == null) {
            sInstance = new BatteryStats(context);
        }
        return sInstance;
    }

    /** 服务每采到一条有效读数就调用一次。 */
    public void update(Sampler.Reading r) {
        if (r == null || !r.valid) {
            return;
        }
        long nowElapsed = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();

        if (lastElapsed == 0L) {
            // 进程刚起来：如果中间隔了很久且电量已经变了，旧窗口的积分不完整，重新开始
            if (lastWall > 0 && nowWall - lastWall > RESTART_GAP_MS
                    && r.percent >= 0 && lastLevel >= 0 && r.percent != lastLevel) {
                resetWindow(r.percent, nowWall);
            } else {
                if (startLevel < 0 && r.percent >= 0) {
                    startLevel = r.percent;
                    startedWall = nowWall;
                }
                if (lastLevel < 0) {
                    lastLevel = r.percent;
                }
                if (segStartLevel < 0) {
                    segStartLevel = r.percent;
                }
            }
            lastElapsed = nowElapsed;
            lastWall = nowWall;
            persist(true);
            return;
        }

        long dtMs = nowElapsed - lastElapsed;
        lastElapsed = nowElapsed;
        if (dtMs <= 0) {
            return;
        }
        if (dtMs > MAX_GAP_MS) {
            // 服务被冻结/休眠过：不把上一帧电流乘到整段空档上
            if (r.percent >= 0) {
                lastLevel = r.percent;
            }
            lastWall = nowWall;
            persist(false);
            return;
        }

        double dt = dtMs / 1000.0;
        double mah = r.amps * dt / 3.6;          // A * s / 3.6 = mAh
        netMah += mah;
        if (mah >= 0) {
            chargedMah += mah;
        } else {
            dischargedMah -= mah;
        }
        double wh = r.watts * dt / 3600.0;
        netWh += wh;
        if (wh >= 0) {
            chargedWh += wh;
        } else {
            dischargedWh -= wh;
        }

        // 估算满电容量：只在一个方向内累计，避免「先充后放」把净电量抵消掉
        int sign = mah > 0 ? 1 : (mah < 0 ? -1 : 0);
        if (sign != 0) {
            if (segSign == 0) {
                segSign = sign;
                segStartLevel = r.percent;
                segMah = 0;
            } else if (sign != segSign) {
                segSign = sign;
                segStartLevel = r.percent;
                segMah = 0;
            }
            segMah += mah;
        }

        if (r.percent >= 0) {
            if (startLevel < 0) {
                startLevel = r.percent;
                startedWall = nowWall;
            }
            if (segStartLevel < 0) {
                segStartLevel = r.percent;
            }
            lastLevel = r.percent;
            if (segStartLevel >= 0 && Math.abs(segMah) >= 100) {
                int delta = r.percent - segStartLevel;
                if (Math.abs(delta) >= 10) {
                    estimateFull(Math.abs(segMah) / Math.abs(delta) * 100.0);
                    // 每跨过 10% 重新开一个估算窗口，后续还能继续修正
                    segStartLevel = r.percent;
                    segMah = 0;
                }
            }
        }

        // charge_counter 是可用的绝对值时，用「剩余 / 电量」直接估算，可信度高于积分
        if (r.chargeMah > 0 && r.percent >= 20) {
            estimateFull(r.chargeMah / r.percent * 100.0);
        }
        if (r.full() && r.chargeMah > 0 && plausibleFull(r.chargeMah)) {
            fullMah = r.chargeMah;
            fullExact = true;
        }

        lastWall = nowWall;
        persist(false);
    }

    private void estimateFull(double candidate) {
        if (fullExact || !plausibleFull(candidate)) {
            return;
        }
        fullMah = fullMah <= 0 ? candidate : fullMah * 0.7 + candidate * 0.3;
    }

    /**
     * 过滤明显不可信的满电容量：例如本机 100% 时 charge_counter 只有 1490 mAh，
     * 这是带偏移的计数器，不是真实满电容量；设了设计容量时再按 0.4~1.3 倍约束。
     */
    private boolean plausibleFull(double mah) {
        if (mah < MIN_ESTIMATE_MAH || mah > MAX_ESTIMATE_MAH) {
            return false;
        }
        int design = prefs.designMah();
        if (design > 0) {
            return mah >= design * 0.4 && mah <= design * 1.3;
        }
        return true;
    }

    /** 当前剩余容量（mAh）：优先 charge_counter，其次满电容量 × 电量，最后设计容量 × 电量。 */
    public double currentMah(Sampler.Reading r) {
        if (r == null) {
            return Double.NaN;
        }
        if (r.chargeMah > 0) {
            return r.chargeMah;
        }
        double full = fullMah > 0 ? fullMah : prefs.designMah();
        if (full > 0 && r.percent >= 0) {
            return full * r.percent / 100.0;
        }
        return Double.NaN;
    }

    /** 当前容量是不是估算值（不是系统 charge_counter 直接读到的）。 */
    public boolean currentMahEstimated(Sampler.Reading r) {
        return r != null && !(r.chargeMah > 0) && currentMah(r) > 0;
    }

    /** 健康度百分比；设计容量或满电容量未知时返回 NaN。 */
    public double healthPercent() {
        int design = prefs.designMah();
        if (design <= 0 || fullMah <= 0) {
            return Double.NaN;
        }
        return Math.max(0, Math.min(150, fullMah / design * 100.0));
    }

    /** 损耗百分比；健康度未知时返回 NaN。 */
    public double wearPercent() {
        double h = healthPercent();
        return Double.isNaN(h) ? Double.NaN : Math.max(0, 100.0 - h);
    }

    /** 损耗掉的容量（mAh）；未知时返回 NaN。 */
    public double wearMah() {
        int design = prefs.designMah();
        if (design <= 0 || fullMah <= 0) {
            return Double.NaN;
        }
        return Math.max(0, design - fullMah);
    }

    public double fullMah() {
        return fullMah;
    }

    public boolean fullExact() {
        return fullExact;
    }

    public int startLevel() {
        return startLevel;
    }

    public int lastLevel() {
        return lastLevel;
    }

    public long startedWall() {
        return startedWall;
    }

    public long durationMs() {
        return startedWall > 0 ? Math.max(0, System.currentTimeMillis() - startedWall) : 0;
    }

    public double netMah() {
        return netMah;
    }

    public double chargedMah() {
        return chargedMah;
    }

    public double dischargedMah() {
        return dischargedMah;
    }

    public double netWh() {
        return netWh;
    }

    public double chargedWh() {
        return chargedWh;
    }

    public double dischargedWh() {
        return dischargedWh;
    }

    /** 只清空本次统计窗口，保留已经学到的满电容量。 */
    public void reset() {
        Sampler.Reading r = Bus.last();
        resetWindow(r != null ? r.percent : -1, System.currentTimeMillis());
        // 满电容量是本次统计窗口推出来的，重置统计时一并清掉，
        // 否则会出现「累计充入 0，却还显示上次的满电容量/健康度」。
        fullMah = 0;
        fullExact = false;
        persist(true);
    }

    private void resetWindow(int level, long nowWall) {
        startLevel = level;
        lastLevel = level;
        startedWall = nowWall;
        lastWall = nowWall;
        lastElapsed = 0;
        netMah = 0;
        chargedMah = 0;
        dischargedMah = 0;
        netWh = 0;
        chargedWh = 0;
        dischargedWh = 0;
        segStartLevel = level;
        segMah = 0;
        segSign = 0;
    }

    private void persist(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastPersistWall < 10_000L) {
            return;
        }
        lastPersistWall = now;
        sp.edit()
                .putLong("started_wall", startedWall)
                .putInt("start_level", startLevel)
                .putInt("last_level", lastLevel)
                .putFloat("net_mah", (float) netMah)
                .putFloat("charged_mah", (float) chargedMah)
                .putFloat("discharged_mah", (float) dischargedMah)
                .putFloat("net_wh", (float) netWh)
                .putFloat("charged_wh", (float) chargedWh)
                .putFloat("discharged_wh", (float) dischargedWh)
                .putInt("seg_start_level", segStartLevel)
                .putFloat("seg_mah", (float) segMah)
                .putInt("seg_sign", segSign)
                .putFloat("full_mah", (float) fullMah)
                .putBoolean("full_exact", fullExact)
                .putLong("last_wall", lastWall)
                .apply();
    }
}
