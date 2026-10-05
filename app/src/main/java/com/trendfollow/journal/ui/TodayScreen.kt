package com.trendfollow.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trendfollow.journal.domain.PrevResult
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
    val todayLog = state.logs.firstOrNull { it.date == today }
    val stockBefore = calc.stockUnitsBefore(today, state.logs)
    val stockToday = calc.stockUnitsOn(today, state.logs)
    val suggested = calc.suggestedPrevResult(state.trades)
    val guide = calc.guide(todayLog?.condition, stockToday, state.trades)
    val perf = calc.performance(state.trades, today)
    val history = calc.stockUnitHistory(state.logs)
    var price by remember { mutableStateOf("") }

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
            SectionCard("오늘의 시장상황") {
                MarketSelector(todayLog?.condition, calc::marketUnitsFor) { vm.setMarket(today, it) }
                if (todayLog?.condition == null) {
                    val last = state.logs.filter { it.condition != null }.maxByOrNull { it.date }
                    Text(
                        "아직 입력하지 않았습니다." + (last?.let { " (최근 기록: ${it.date} ${it.condition?.label})" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            SectionCard("종목유닛 선택 (이전 수익율)") {
                PrevResultSelector(todayLog?.prevResult) { vm.setPrevResult(today, it) }
                Text(
                    "목표(${fmt(calc.minProfitRate)}%) 미달성 −1 · 진행중 0 · 목표달성 +1",
                    style = MaterialTheme.typography.bodySmall,
                )
                ValueRow(
                    "종목유닛",
                    "$stockBefore → $stockToday  (최대 ${calc.maxStockUnits})",
                    bold = true,
                )
                if (todayLog?.prevResult == null) {
                    Text("아직 선택하지 않았습니다. 선택 전에는 이전 종목유닛($stockBefore)을 그대로 씁니다.", style = MaterialTheme.typography.bodySmall)
                }
                suggested?.let {
                    Text("매매기록 기준 추천: ${it.label}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SectionCard("투자비중 가이드") {
                ValueRow("시장유닛", "${guide.marketUnits} / ${calc.maxMarketUnits}")
                ValueRow("종목유닛", "${guide.stockUnits} / ${calc.maxStockUnits}")
                ValueRow("오늘 투입 유닛", "${guide.units} / ${guide.totalUnits}", bold = true)
                HorizontalDivider()
                ValueRow("종목당 투입 금액", won(guide.amountPerStock), bold = true)
                ValueRow("최대 투입비중 대비", "%.0f%%".format(guide.ratioOfMaxPosition))
                ValueRow("총 투자금 대비", "%.1f%%".format(guide.ratioOfCapital))
                ValueRow("1유닛 금액", won(calc.unitAmount))
                ValueRow("종목수 (보유 / 최대)", "${guide.openCount} / ${guide.maxStocks}종목", bold = true)
                HorizontalDivider()
                guide.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        item {
            SectionCard("매수 수량 계산") {
                InputField("매수 예정가", price, { price = it }, suffix = "원")
                val p = parseDouble(price)
                if (p != null && p > 0) {
                    val qty = calc.suggestedQuantity(p, guide.amountPerStock)
                    ValueRow("매수 수량", "%,d주".format(qty), bold = true)
                    ValueRow("실제 투입 금액", won(qty * p))
                    ValueRow("손절가 (-${fmt(state.settings.stopLossRate)}%)", num(calc.stopPrice(p)) + "원", LossColor)
                    ValueRow("목표가 (+${fmt(calc.minProfitRate)}%)", num(calc.targetPrice(p)) + "원", ProfitColor)
                }
            }
        }

        item {
            SectionCard("오늘의 매매성과") {
                ValueRow("오늘 실현손익", won(perf.todayPnl), pnlColor(perf.todayPnl), bold = true)
                ValueRow("오늘 청산 건수", "${perf.todayClosedCount}건")
                ValueRow("보유 종목", "${perf.openCount}개 / 투입 ${won(perf.openInvested)}")
                ValueRow("보유 평가손익 (현재가 입력분)", won(perf.unrealizedPnl), pnlColor(perf.unrealizedPnl))
            }
        }

        item {
            SectionCard("누적 성과") {
                ValueRow("누적 실현손익", won(perf.totalPnl), pnlColor(perf.totalPnl), bold = true)
                ValueRow("총 투자금 대비", pct(perf.returnOnCapital), pnlColor(perf.returnOnCapital))
                ValueRow("청산 매매", "${perf.closedCount}건")
                ValueRow("승률 (수익 청산)", "%.1f%%".format(perf.winRate))
                ValueRow("목표 달성률 (≥${fmt(calc.minProfitRate)}%)", "%.1f%%".format(perf.achieveRate))
                ValueRow("평균 수익률 / 평균 손실률", "${pct(perf.avgWinRate)} / ${pct(perf.avgLossRate)}")
                ValueRow(
                    "실제 손익비 (목표 1:${fmt(state.settings.rewardRatio)})",
                    perf.realizedRewardRatio?.let { "1 : %.2f".format(it) } ?: "-",
                )
                if (perf.stopViolations > 0) {
                    Text(
                        "⚠ 손절 기준(-${fmt(state.settings.stopLossRate)}%)을 넘긴 손실 ${perf.stopViolations}건",
                        color = LossColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                SectionCard("종목유닛 변화 (최근 5일)") {
                    history.takeLast(5).reversed().forEach { step ->
                        ValueRow(
                            "${step.date} ${step.result.label}",
                            "${step.before} → ${step.after}",
                            when (step.result) {
                                PrevResult.ACHIEVED -> ProfitColor
                                PrevResult.MISSED -> LossColor
                                PrevResult.ONGOING -> Color.Unspecified
                            },
                        )
                    }
                }
            }
        }
    }
}
