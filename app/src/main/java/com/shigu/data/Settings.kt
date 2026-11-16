package com.shigu.data

import android.content.Context
import com.shigu.core.Universe

class Settings(context: Context) {
    private val p = context.getSharedPreferences("shigu_settings", Context.MODE_PRIVATE)

    var dailyUniverse: Universe
        get() = runCatching { Universe.valueOf(p.getString("daily_universe", Universe.TOP_800.name)!!) }
            .getOrDefault(Universe.TOP_800)
        set(value) = p.edit().putString("daily_universe", value.name).apply()

    var autoUpdate: Boolean
        get() = p.getBoolean("auto_update", true)
        set(value) = p.edit().putBoolean("auto_update", value).apply()

    var lastSync: Long
        get() = p.getLong("last_sync", 0L)
        set(value) = p.edit().putLong("last_sync", value).apply()

    var backtestUniverse: Universe
        get() = runCatching { Universe.valueOf(p.getString("backtest_universe", Universe.TOP_800.name)!!) }
            .getOrDefault(Universe.TOP_800)
        set(value) = p.edit().putString("backtest_universe", value.name).apply()
}
