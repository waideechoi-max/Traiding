package com.trendfollow.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.Settings
import com.trendfollow.journal.domain.TrendCalculator
import com.trendfollow.journal.domain.fmt
import com.trendfollow.journal.domain.won

/** 추세추종계산기: 입력값 저장 + 계산값 표시 */
@Composable
fun SettingsScreen(state: JournalState, vm: JournalViewModel) {
    val s = state.settings
    var capital by remember { mutableStateOf(if (s.totalCapital > 0) s.totalCapital.toString() else "") }
    var maxLoss by remember { mutableStateOf(fmt(s.maxLossRate)) }
    var reward by remember { mutableStateOf(fmt(s.rewardRatio)) }
    var stopLoss by remember { mutableStateOf(fmt(s.stopLossRate)) }
    var totalUnits by remember { mutableStateOf(s.totalUnits.toString()) }
    var marketUnits by remember { mutableStateOf(s.marketUnits.toString()) }
    var initialStock by remember { mutableStateOf(s.initialStockUnits.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    // 입력 중인 값으로 바로 미리보기
    val draft = Settings(
        totalCapital = parseLong(capital) ?: 0,
        maxLossRate = parseDouble(maxLoss) ?: 0.0,
        rewardRatio = parseDouble(reward) ?: 0.0,
        stopLossRate = parseDouble(stopLoss) ?: 0.0,
        totalUnits = totalUnits.toIntOrNull() ?: 0,
        marketUnits = marketUnits.toIntOrNull() ?: 0,
        initialStockUnits = initialStock.toIntOrNull() ?: 0,
    )
    val calc = TrendCalculator(draft)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("추세추종계산기 입력") {
            InputField("총 투자금", capital, { capital = it }, suffix = "원")
            InputField("최대 손실율 (총 투자금 대비)", maxLoss, { maxLoss = it }, suffix = "%")
            InputField("목표손익비 1 :", reward, { reward = it })
            InputField("종목당 최대 손실율", stopLoss, { stopLoss = it }, suffix = "%")
            InputField("종목당 총 유닛", totalUnits, { totalUnits = it }, suffix = "유닛")
            InputField(
                "시장유닛", marketUnits, { marketUnits = it }, suffix = "유닛",
                supporting = "종목유닛 = 총 유닛 − 시장유닛 = ${calc.maxStockUnits}",
            )
            InputField(
                "시작 종목유닛", initialStock, { initialStock = it }, suffix = "유닛",
                supporting = "청산 기록이 없을 때의 종목유닛 (0 ~ ${calc.maxStockUnits})",
            )
            Button(
                onClick = {
                    message = when {
                        draft.totalCapital <= 0 -> "총 투자금을 입력하세요."
                        draft.maxLossRate <= 0 || draft.stopLossRate <= 0 -> "손실율은 0보다 커야 합니다."
                        draft.rewardRatio <= 0 -> "목표손익비를 입력하세요."
                        draft.totalUnits <= 0 -> "총 유닛은 1 이상이어야 합니다."
                        draft.marketUnits !in 0..draft.totalUnits -> "시장유닛은 0 ~ 총 유닛 사이여야 합니다."
                        draft.initialStockUnits !in 0..calc.maxStockUnits -> "시작 종목유닛은 0 ~ ${calc.maxStockUnits} 사이여야 합니다."
                        else -> null
                    }
                    if (message == null) {
                        vm.saveSettings(draft)
                        message = "저장했습니다."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        SectionCard("계산 결과") {
            ValueRow("최대 손실 금액 (총 투자금 × ${fmt(draft.maxLossRate)}%)", won(calc.maxLossAmount))
            ValueRow(
                "종목당 최소 수익율 (${fmt(draft.rewardRatio)} × ${fmt(draft.stopLossRate)}%)",
                "${fmt(calc.minProfitRate)}%",
            )
            ValueRow("1종목당 최대 투입비중", won(calc.maxPositionAmount), bold = true)
            Text(
                "= 총 투자금 × 최대 손실율 ÷ 종목당 최대 손실율",
                style = MaterialTheme.typography.bodySmall,
            )
            ValueRow("1유닛당 금액", won(calc.unitAmount), bold = true)
            Text("= 1종목당 최대 투입비중 ÷ 종목당 총 유닛수", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("유닛 규칙") {
            Text("• 종목당 총 ${draft.totalUnits}유닛 = 시장유닛 ${calc.maxMarketUnits} + 종목유닛 ${calc.maxStockUnits}")
            Text(
                "• 시장유닛: 약세(${calc.marketUnitsFor(MarketCondition.WEAK)}), " +
                    "보합(${calc.marketUnitsFor(MarketCondition.NEUTRAL)}), " +
                    "강세(${calc.marketUnitsFor(MarketCondition.STRONG)})"
            )
            Text("• 종목유닛: 직전 청산 매매가 최소 수익율(${fmt(calc.minProfitRate)}%) 달성 시 +1, 미달 시 −1 (0 ~ ${calc.maxStockUnits})")
            Text("• 오늘 종목당 투입금액 = (시장유닛 + 종목유닛) × 1유닛당 금액")
        }
        Text(
            "모든 데이터는 이 기기에만 저장됩니다.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
