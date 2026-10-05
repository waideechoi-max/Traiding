package com.trendfollow.journal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.trendfollow.journal.data.JournalStore
import com.trendfollow.journal.domain.Account
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.MarketLog
import com.trendfollow.journal.domain.PrevResult
import com.trendfollow.journal.domain.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class JournalState(
    val accounts: List<Account>,
    val selected: Int,
    /** 모든 계좌가 공유하는 시장상황·메모 */
    val marketLogs: List<MarketLog>,
) {
    val account: Account get() = accounts[selected]
    val settings: Settings get() = account.settings
}

class JournalViewModel(app: Application) : AndroidViewModel(app) {
    private val store = JournalStore(app)

    private val _state = MutableStateFlow(
        JournalState(store.loadAccounts(), store.loadSelectedAccount(), store.loadMarketLogs())
    )
    val state: StateFlow<JournalState> = _state.asStateFlow()

    fun selectAccount(id: Int) {
        _state.update { it.copy(selected = id) }
        store.saveSelectedAccount(id)
    }

    fun saveAccount(nickname: String, settings: Settings) =
        updateAccount { it.copy(nickname = nickname, settings = settings) }

    fun setMarket(date: LocalDate, condition: MarketCondition) = updateMarketLog(date) { it.copy(condition = condition) }

    fun setPrevResult(date: LocalDate, result: PrevResult?) = updateAccount { a ->
        val others = a.logs.filter { it.date != date }
        a.copy(logs = if (result == null) others else others + MarketLog(date, prevResult = result))
    }

    /** 일지 한 줄 저장: 시장상황·메모는 공유 기록에, 이전 수익율은 현재 계좌에 */
    fun saveLog(log: MarketLog) {
        updateMarketLog(log.date) { it.copy(condition = log.condition, memo = log.memo) }
        setPrevResult(log.date, log.prevResult)
    }

    /** 일지 삭제: 공유 시장상황과 현재 계좌의 이전 수익율을 함께 지움 */
    fun deleteLog(date: LocalDate) {
        _state.update { s -> s.copy(marketLogs = s.marketLogs.filter { it.date != date }) }
        store.saveMarketLogs(_state.value.marketLogs)
        setPrevResult(date, null)
    }

    private fun updateMarketLog(date: LocalDate, change: (MarketLog) -> MarketLog) {
        _state.update { s ->
            val old = s.marketLogs.firstOrNull { it.date == date } ?: MarketLog(date)
            s.copy(marketLogs = s.marketLogs.filter { it.date != date } + change(old))
        }
        store.saveMarketLogs(_state.value.marketLogs)
    }

    private fun updateAccount(change: (Account) -> Account) {
        _state.update { s ->
            s.copy(accounts = s.accounts.map { if (it.id == s.selected) change(it) else it })
        }
        store.saveAccounts(_state.value.accounts)
    }
}
