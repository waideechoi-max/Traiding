package com.trendfollow.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trendfollow.journal.domain.TrendCalculator
import com.trendfollow.journal.domain.fmt
import com.trendfollow.journal.domain.won
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(state: JournalState, vm: JournalViewModel) {
    val today = LocalDate.now()
    val calc = TrendCalculator(state.settings)
    val todayLog = state.marketLogs.firstOrNull { it.date == today }
    val accountLogs = state.account.logs
    val todayPrev = accountLogs.firstOrNull { it.date == today }?.prevResult
    val stockBefore = calc.stockUnitsBefore(today, accountLogs)
    val stockToday = calc.stockUnitsOn(today, accountLogs)
    val guide = calc.guide(todayLog?.condition, stockToday)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                today.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            SectionCard("오늘의 시장상황 (모든 계좌 공통)") {
                MarketSelector(todayLog?.condition, calc::marketUnitsFor) { vm.setMarket(today, it) }
                if (todayLog?.condition == null) {
                    val last = state.marketLogs.filter { it.condition != null }.maxByOrNull { it.date }
                    Text(
                        "아직 입력하지 않았습니다." + (last?.let { " (최근 기록: ${it.date} ${it.condition?.label})" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            SectionCard("${state.account.nickname} · 종목유닛 선택 (이전 수익율)") {
                PrevResultSelector(todayPrev) { vm.setPrevResult(today, it) }
                Text(
                    "목표(${fmt(calc.minProfitRate)}%) 미달성 −1 · 진행중 0 · 목표달성 +1",
                    style = MaterialTheme.typography.bodySmall,
                )
                ValueRow(
                    "종목유닛",
                    "$stockBefore → $stockToday  (최대 ${calc.maxStockUnits})",
                    bold = true,
                )
                if (todayPrev == null) {
                    Text("아직 선택하지 않았습니다. 선택 전에는 이전 종목유닛($stockBefore)을 그대로 씁니다.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SectionCard("${state.account.nickname} · 투자비중 가이드") {
                ValueRow("총 투자금", won(state.settings.totalCapital.toDouble()))
                ValueRow("1종목당 최대 투입비중", won(calc.maxPositionAmount))
                ValueRow("종목수", "${guide.maxStocks}종목")
                ValueRow("1유닛 금액", won(calc.unitAmount))
                HorizontalDivider()
                ValueRow("시장유닛", "${guide.marketUnits} / ${calc.maxMarketUnits}")
                ValueRow("종목유닛", "${guide.stockUnits} / ${calc.maxStockUnits}")
                ValueRow("오늘 투입 유닛", "${guide.units} / ${guide.totalUnits}", bold = true)
                HorizontalDivider()
                ValueRow("종목당 투입 금액", won(guide.amountPerStock), bold = true)
                Text(
                    "= ${guide.units}유닛 × ${won(calc.unitAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ValueRow("최대 투입비중 대비", "%.0f%%".format(guide.ratioOfMaxPosition))
                ValueRow("총 투자금 대비", "%.1f%%".format(guide.ratioOfCapital))
                HorizontalDivider()
                guide.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
