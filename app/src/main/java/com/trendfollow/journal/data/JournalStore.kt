package com.trendfollow.journal.data

import android.content.Context
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.MarketLog
import com.trendfollow.journal.domain.PrevResult
import com.trendfollow.journal.domain.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 설정·일지를 기기 내부(SharedPreferences)에 JSON으로 저장 */
class JournalStore(context: Context) {
    private val prefs = context.getSharedPreferences("trend_journal", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return Settings()
        val o = JSONObject(raw)
        val d = Settings()
        return Settings(
            totalCapital = o.optLong("totalCapital", d.totalCapital),
            maxLossRate = o.optDouble("maxLossRate", d.maxLossRate),
            rewardRatio = o.optDouble("rewardRatio", d.rewardRatio),
            stopLossRate = o.optDouble("stopLossRate", d.stopLossRate),
            totalUnits = o.optInt("totalUnits", d.totalUnits),
            marketUnits = o.optInt("marketUnits", d.marketUnits),
            initialStockUnits = o.optInt("initialStockUnits", d.initialStockUnits),
        )
    }

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("totalCapital", s.totalCapital)
            .put("maxLossRate", s.maxLossRate)
            .put("rewardRatio", s.rewardRatio)
            .put("stopLossRate", s.stopLossRate)
            .put("totalUnits", s.totalUnits)
            .put("marketUnits", s.marketUnits)
            .put("initialStockUnits", s.initialStockUnits)
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    fun loadLogs(): List<MarketLog> {
        val arr = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MarketLog(
                date = LocalDate.parse(o.getString("date")),
                condition = o.optNullableString("condition")?.let(MarketCondition::valueOf),
                memo = o.optString("memo", ""),
                prevResult = o.optNullableString("prevResult")?.let(PrevResult::valueOf),
            )
        }
    }

    fun saveLogs(logs: List<MarketLog>) {
        val arr = JSONArray()
        logs.forEach { l ->
            arr.put(
                JSONObject()
                    .put("date", l.date.toString())
                    .put("condition", l.condition?.name ?: JSONObject.NULL)
                    .put("memo", l.memo)
                    .put("prevResult", l.prevResult?.name ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_LOGS = "market_logs"
    }
}
