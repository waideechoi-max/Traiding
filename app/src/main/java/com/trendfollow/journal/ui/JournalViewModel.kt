package com.trendfollow.journal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.trendfollow.journal.data.JournalStore
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.MarketLog
import com.trendfollow.journal.domain.PrevResult
import com.trendfollow.journal.domain.Settings
import com.trendfollow.journal.domain.Trade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class JournalState(
    val settings: Settings,
    val trades: List<Trade>,
    val logs: List<MarketLog>,
)

class JournalViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JournalStore(app)

    private val _state = MutableStateFlow(
        JournalState(store.loadSettings(), store.loadTrades(), store.loadLogs())
    )
    val state: StateFlow<JournalState> = _state.asStateFlow()

    fun saveSettings(settings: Settings) {
        _state.update { it.copy(settings = settings) }
        store.saveSettings(settings)
    }

    fun upsertTrade(trade: Trade) {
        val t = if (trade.id == 0L) trade.copy(id = System.currentTimeMillis()) else trade
        _state.update { s -> s.copy(trades = s.trades.filter { it.id != t.id } + t) }
        store.saveTrades(_state.value.trades)
    }

    fun deleteTrade(id: Long) {
        _state.update { s -> s.copy(trades = s.trades.filter { it.id != id }) }
        store.saveTrades(_state.value.trades)
    }

    fun setMarket(date: LocalDate, condition: MarketCondition) = updateLog(date) { it.copy(condition = condition) }

    fun setPrevResult(date: LocalDate, result: PrevResult) = updateLog(date) { it.copy(prevResult = result) }

    fun saveLog(log: MarketLog) = updateLog(log.date) { log }

    private fun updateLog(date: LocalDate, change: (MarketLog) -> MarketLog) {
        _state.update { s ->
            val old = s.logs.firstOrNull { it.date == date } ?: MarketLog(date)
            s.copy(logs = s.logs.filter { it.date != date } + change(old))
        }
        store.saveLogs(_state.value.logs)
    }

    fun deleteLog(date: LocalDate) {
        _state.update { s -> s.copy(logs = s.logs.filter { it.date != date }) }
        store.saveLogs(_state.value.logs)
    }
}
