package com.trendfollow.journal.data

import android.content.Context
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.MarketLog
import com.trendfollow.journal.domain.Settings
import com.trendfollow.journal.domain.Trade
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 설정·매매기록·시장기록을 기기 내부(SharedPreferences)에 JSON으로 저장 */
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

    fun loadTrades(): List<Trade> {
        val arr = JSONArray(prefs.getString(KEY_TRADES, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Trade(
                id = o.getLong("id"),
                name = o.getString("name"),
                entryDate = LocalDate.parse(o.getString("entryDate")),
                entryPrice = o.getDouble("entryPrice"),
                quantity = o.getLong("quantity"),
                units = o.optInt("units", 0),
                currentPrice = o.optNullableDouble("currentPrice"),
                exitDate = o.optNullableString("exitDate")?.let(LocalDate::parse),
                exitPrice = o.optNullableDouble("exitPrice"),
                memo = o.optString("memo", ""),
            )
        }
    }

    fun saveTrades(trades: List<Trade>) {
        val arr = JSONArray()
        trades.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("entryDate", t.entryDate.toString())
                    .put("entryPrice", t.entryPrice)
                    .put("quantity", t.quantity)
                    .put("units", t.units)
                    .put("currentPrice", t.currentPrice ?: JSONObject.NULL)
                    .put("exitDate", t.exitDate?.toString() ?: JSONObject.NULL)
                    .put("exitPrice", t.exitPrice ?: JSONObject.NULL)
                    .put("memo", t.memo)
            )
        }
        prefs.edit().putString(KEY_TRADES, arr.toString()).apply()
    }

    fun loadLogs(): List<MarketLog> {
        val arr = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MarketLog(
                date = LocalDate.parse(o.getString("date")),
                condition = MarketCondition.valueOf(o.getString("condition")),
                memo = o.optString("memo", ""),
            )
        }
    }

    fun saveLogs(logs: List<MarketLog>) {
        val arr = JSONArray()
        logs.forEach { l ->
            arr.put(
                JSONObject()
                    .put("date", l.date.toString())
                    .put("condition", l.condition.name)
                    .put("memo", l.memo)
            )
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_TRADES = "trades"
        const val KEY_LOGS = "market_logs"
    }
}
