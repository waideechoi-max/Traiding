package com.trendfollow.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trendfollow.journal.domain.MarketCondition
import com.trendfollow.journal.domain.MarketLog
import com.trendfollow.journal.domain.TrendCalculator
import com.trendfollow.journal.domain.won
import java.time.LocalDate

/** 일지: 날짜별 시장상황 + 실현손익 */
@Composable
fun MarketScreen(state: JournalState, vm: JournalViewModel) {
    val calc = TrendCalculator(state.settings)
    val days = calc.dailyResults(state.trades, state.logs)
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("일지 기록 추가") }
        }
        if (days.isEmpty()) {
            item { Text("기록이 없습니다.", modifier = Modifier.padding(top = 24.dp)) }
        }
        items(days, key = { it.date.toString() }) { d ->
            Card(Modifier.fillMaxWidth().clickable { editingDate = d.date }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.date.toString(), fontWeight = FontWeight.Bold)
                        Text(
                            d.condition?.let { "${it.label} · 시장유닛 ${calc.marketUnitsFor(it)}" } ?: "시장상황 미입력",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        (d.prevResult?.let { "이전 수익율 ${it.label} · " } ?: "") + "종목유닛 ${d.stockUnits}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (d.closedCount > 0) {
                        Text("청산 ${d.closedCount}건 · 실현손익 ${won(d.pnl)}", color = pnlColor(d.pnl))
                    }
                    if (d.memo.isNotBlank()) Text(d.memo, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (creating || editingDate != null) {
        val existing = state.logs.firstOrNull { it.date == editingDate }
        MarketLogEditor(
            initialDate = editingDate ?: LocalDate.now(),
            initial = existing,
            unitsFor = calc::marketUnitsFor,
            onDismiss = { creating = false; editingDate = null },
            onSave = { log ->
                if (editingDate != null && editingDate != log.date) vm.deleteLog(editingDate!!)
                vm.saveLog(log)
                creating = false; editingDate = null
            },
            onDelete = if (existing != null) {
                { vm.deleteLog(existing.date); creating = false; editingDate = null }
            } else null,
        )
    }
}

@Composable
private fun MarketLogEditor(
    initialDate: LocalDate,
    initial: MarketLog?,
    unitsFor: (MarketCondition) -> Int,
    onDismiss: () -> Unit,
    onSave: (MarketLog) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var date by remember { mutableStateOf(initialDate.toString()) }
    var condition by remember { mutableStateOf(initial?.condition) }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    var prevResult by remember { mutableStateOf(initial?.prevResult) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일지 기록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField("날짜 (yyyy-MM-dd)", date, { date = it }, numeric = false, isError = parseDate(date) == null)
                Text("시장상황", style = MaterialTheme.typography.titleSmall)
                MarketSelector(condition, unitsFor) { condition = it }
                Text("이전 수익율 (종목유닛)", style = MaterialTheme.typography.titleSmall)
                PrevResultSelector(prevResult) { prevResult = it }
                InputField("메모 (지수, 이슈 등)", memo, { memo = it }, numeric = false)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = parseDate(date)
                error = when {
                    d == null -> "날짜 형식이 올바르지 않습니다."
                    condition == null && prevResult == null -> "시장상황 또는 이전 수익율을 선택하세요."
                    else -> null
                }
                if (d != null && error == null) onSave(MarketLog(d, condition, memo.trim(), prevResult))
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
