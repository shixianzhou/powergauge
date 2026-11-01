package com.powergauge.app;

import android.content.Intent;
import android.os.BatteryManager;

/**
 * 把一次 ACTION_BATTERY_CHANGED 广播解析成结构化读数。
 *
 * 关键点（也是原版视频里踩坑的地方）：
 *  - 厂商上报的电流单位并不统一，多数是 µA，部分机型是 mA，所以单位由用户手动指定；
 *  - 电流的正负号各家相反，方向以「充电状态」为准，不依赖符号；
 *  - 电压是电池端（降压后）的电压，不是 Type-C 口电压。
 */
public final class Sampler {

    public static final class Reading {
        public boolean valid;
        public int percent = -1;
        public int status = -1;
        public int plugged = 0;
        public double volts;
        public double amps;   // 充电为正，放电为负
        public double watts;  // 充电为正，放电为负
        public double celsius = Double.NaN;
        public double chargeMah = Double.NaN;  // 剩余电量，读不到就是 NaN
        public String hint;
        public long atMs = System.currentTimeMillis();

        public boolean charging() {
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        public boolean full() {
            return status == BatteryManager.BATTERY_STATUS_FULL;
        }
    }

    private Sampler() {
    }

    public static Reading read(Intent intent, BatteryManager manager, Prefs prefs) {
        Reading r = new Reading();
        if (intent == null) {
            r.hint = "没有收到电池广播";
            return r;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level >= 0 && scale > 0) {
            r.percent = Math.round(level * 100f / scale);
        }

        r.status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        r.plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        // 剩余电量，单位 µAh。广播里没有就再问一次 BatteryManager；都没有就保持 NaN，界面不显示。
        // 注意 EXTRA_CHARGE_COUNTER 是 @hide 常量，只能写字符串字面量 "charge_counter"
        int chargeUah = intent.getIntExtra("charge_counter", Integer.MIN_VALUE);
        if (chargeUah <= 0 && manager != null) {
            chargeUah = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        }
        if (chargeUah > 0) {
            r.chargeMah = chargeUah / 1000.0;
        }

        int millivolts = intent.getIntExtra("voltage", 0);
        int rawCurrent = intent.getIntExtra("current_now", Integer.MIN_VALUE);
        if (rawCurrent == Integer.MIN_VALUE && manager != null) {
            rawCurrent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }

        int tenths = intent.getIntExtra("temperature", 0);
        if (tenths != 0) {
            r.celsius = tenths / 10.0;
        }

        if (millivolts <= 0) {
            r.hint = "读不到电压，可能是厂商没有开放 battery 服务";
            return r;
        }
        r.volts = millivolts / 1000.0;

        double amps;
        if (rawCurrent == Integer.MIN_VALUE) {
            r.hint = "读不到电流，试试切换单位 µA / mA";
            amps = 0;
        } else if (prefs.unit() == Prefs.UNIT_MA) {
            amps = rawCurrent / 1000.0;
        } else {
            amps = rawCurrent / 1_000_000.0;
        }

        // 方向按充电状态判定，避免不同厂商符号相反导致误判
        amps = r.charging() ? Math.abs(amps) : -Math.abs(amps);
        r.amps = amps;

        int cells = prefs.cells();
        double watts = r.volts * amps * cells;
        if (r.charging() && r.plugged != 0) {
            watts *= prefs.compFactor();
        }
        r.watts = watts;

        if (rawCurrent != Integer.MIN_VALUE && Math.abs(r.watts) > 500) {
            r.hint = "功率异常偏大，电流单位可能选错了";
        }
        r.valid = true;
        return r;
    }
}
