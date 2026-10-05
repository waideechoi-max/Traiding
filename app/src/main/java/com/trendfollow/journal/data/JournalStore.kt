package com.trendfollow.journal.data

import android.content.Context
import com.trendfollow.journal.domain.ACCOUNT_COUNT
import com.trendfollow.journal.domain.Account
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.MarketLog
import com.trendfollow.journal.domain.PrevResult
import com.trendfollow.journal.domain.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 계좌·시장일지를 기기 내부(SharedPreferences)에 JSON으로 저장 */
class JournalStore(context: Context) {
    private val prefs = context.getSharedPreferences("trend_journal", Context.MODE_PRIVATE)

    /** 계좌 5개. 계좌 기능 이전에 쓰던 설정·종목유닛 기록은 첫 번째 계좌로 옮깁니다. */
    fun loadAccounts(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null)
        val saved = if (raw != null) {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { accountFromJson(arr.getJSONObject(it)) }
        } else {
            val legacySettings = prefs.getString(KEY_SETTINGS, null)?.let { settingsFromJson(JSONObject(it)) }
            val legacyLogs = loadRawLogs().filter { it.prevResult != null }.map { MarketLog(it.date, prevResult = it.prevResult) }
            if (legacySettings != null || legacyLogs.isNotEmpty()) {
                listOf(Account(0, defaultNickname(0), legacySettings ?: Settings(), legacyLogs))
            } else emptyList()
        }
        val accounts = (0 until ACCOUNT_COUNT).map { id -> saved.firstOrNull { it.id == id } ?: Account(id, defaultNickname(id)) }
        // 옮긴 결과를 바로 저장해야 시장일지 저장 시 예전 종목유닛 기록이 사라지지 않습니다.
        if (raw == null) saveAccounts(accounts)
        return accounts
    }

    fun saveAccounts(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(accountToJson(it)) }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    fun loadSelectedAccount(): Int = prefs.getInt(KEY_SELECTED, 0).coerceIn(0, ACCOUNT_COUNT - 1)

    fun saveSelectedAccount(id: Int) {
        prefs.edit().putInt(KEY_SELECTED, id).apply()
    }

    /** 모든 계좌가 공유하는 시장상황·메모 */
    fun loadMarketLogs(): List<MarketLog> =
        loadRawLogs().filter { it.condition != null || it.memo.isNotBlank() }.map { it.copy(prevResult = null) }

    fun saveMarketLogs(logs: List<MarketLog>) {
        prefs.edit().putString(KEY_LOGS, logsToJson(logs.map { it.copy(prevResult = null) }).toString()).apply()
    }

    private fun loadRawLogs(): List<MarketLog> = logsFromJson(JSONArray(prefs.getString(KEY_LOGS, "[]")))

    private fun accountFromJson(o: JSONObject) = Account(
        id = o.getInt("id"),
        nickname = o.optString("nickname", defaultNickname(o.getInt("id"))),
        settings = o.optJSONObject("settings")?.let(::settingsFromJson) ?: Settings(),
        logs = o.optJSONArray("logs")?.let(::logsFromJson).orEmpty(),
    )

    private fun accountToJson(a: Account) = JSONObject()
        .put("id", a.id)
        .put("nickname", a.nickname)
        .put("settings", settingsToJson(a.settings))
        .put("logs", logsToJson(a.logs))

    private fun settingsFromJson(o: JSONObject): Settings {
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

    private fun settingsToJson(s: Settings) = JSONObject()
        .put("totalCapital", s.totalCapital)
        .put("maxLossRate", s.maxLossRate)
        .put("rewardRatio", s.rewardRatio)
        .put("stopLossRate", s.stopLossRate)
        .put("totalUnits", s.totalUnits)
        .put("marketUnits", s.marketUnits)
        .put("initialStockUnits", s.initialStockUnits)

    private fun logsFromJson(arr: JSONArray): List<MarketLog> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        MarketLog(
            date = LocalDate.parse(o.getString("date")),
            condition = o.optNullableString("condition")?.let(MarketCondition::valueOf),
            memo = o.optString("memo", ""),
            prevResult = o.optNullableString("prevResult")?.let(PrevResult::valueOf),
        )
    }

    private fun logsToJson(logs: List<MarketLog>): JSONArray {
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
        return arr
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    companion object {
        fun defaultNickname(id: Int) = "계좌${id + 1}"

        private const val KEY_SETTINGS = "settings"
        private const val KEY_LOGS = "market_logs"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SELECTED = "selected_account"
    }
}
