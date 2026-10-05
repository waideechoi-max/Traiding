package com.trendfollow.journal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trendfollow.journal.domain.Trade
import com.trendfollow.journal.domain.TrendCalculator
import com.trendfollow.journal.domain.fmt
import com.trendfollow.journal.domain.won
import java.time.LocalDate

private enum class TradeFilter(val label: String) { OPEN("보유"), CLOSED("청산"), ALL("전체") }

@Composable
fun TradesScreen(state: JournalState, vm: JournalViewModel) {
    val calc = TrendCalculator(state.settings)
    var filter by remember { mutableStateOf(TradeFilter.OPEN) }
    var editing by remember { mutableStateOf<Trade?>(null) }
    var creating by remember { mutableStateOf(false) }

    val list = state.trades
        .filter {
            when (filter) {
                TradeFilter.OPEN -> !it.isClosed
                TradeFilter.CLOSED -> it.isClosed
                TradeFilter.ALL -> true
            }
        }
        .sortedWith(compareByDescending<Trade> { it.exitDate ?: it.entryDate }.thenByDescending { it.id })

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (list.isEmpty()) {
                item { Text("기록이 없습니다. + 버튼으로 매매를 추가하세요.", modifier = Modifier.padding(top = 24.dp)) }
            }
            items(list, key = { it.id }) { t -> TradeCard(t, calc) { editing = t } }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "매매 추가") }
    }

    if (creating || editing != null) {
        TradeEditor(
            initial = editing,
            state = state,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.upsertTrade(it); creating = false; editing = null },
            onDelete = { vm.deleteTrade(it); creating = false; editing = null },
        )
    }
}

@Composable
private fun TradeCard(t: Trade, calc: TrendCalculator, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val status = if (t.isClosed) {
                    if (calc.isAchieved(t)) "청산 · 목표달성" else "청산 · 미달"
                } else "보유중"
                Text(status, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "매수 ${t.entryDate} · ${num(t.entryPrice)}원 × ${"%,d".format(t.quantity)}주 · ${t.units}유닛",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("투입 ${won(t.investedAmount)}", style = MaterialTheme.typography.bodySmall)
            if (t.isClosed) {
                val r = t.returnRate ?: 0.0
                Text(
                    "매도 ${t.exitDate} · ${num(t.exitPrice!!)}원 → ${pct(r)} / ${won(t.realizedPnl ?: 0.0)}",
                    color = pnlColor(r),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "손절가 ${num(calc.stopPrice(t.entryPrice))} · 목표가 ${num(calc.targetPrice(t.entryPrice))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                t.unrealizedRate?.let { r ->
                    Text(
                        "현재가 ${num(t.currentPrice!!)}원 → ${pct(r)} / ${won(t.unrealizedPnl ?: 0.0)}",
                        color = pnlColor(r),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (t.memo.isNotBlank()) Text(t.memo, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TradeEditor(
    initial: Trade?,
    state: JournalState,
    onDismiss: () -> Unit,
    onSave: (Trade) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val calc = TrendCalculator(state.settings)
    val today = LocalDate.now()
    val todayCondition = state.logs.firstOrNull { it.date == today }?.condition
    val guide = calc.guide(todayCondition, calc.stockUnitsOn(today, state.logs), state.trades.filter { it.id != initial?.id })

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var entryDate by remember { mutableStateOf((initial?.entryDate ?: today).toString()) }
    var entryPrice by remember { mutableStateOf(initial?.entryPrice?.let(::plain) ?: "") }
    var quantity by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var units by remember { mutableStateOf((initial?.units ?: guide.units).toString()) }
    var currentPrice by remember { mutableStateOf(initial?.currentPrice?.let(::plain) ?: "") }
    var exitDate by remember { mutableStateOf(initial?.exitDate?.toString() ?: "") }
    var exitPrice by remember { mutableStateOf(initial?.exitPrice?.let(::plain) ?: "") }
    var memo by remember { mutableStateOf(initial?.memo ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (initial == null) "매매 추가" else "매매 수정", style = MaterialTheme.typography.headlineSmall)

                InputField("종목명", name, { name = it }, numeric = false)
                InputField("매수일 (yyyy-MM-dd)", entryDate, { entryDate = it }, numeric = false, isError = parseDate(entryDate) == null)
                InputField("매수가", entryPrice, { entryPrice = it }, suffix = "원")
                InputField(
                    "투입 유닛", units, { units = it }, suffix = "유닛",
                    supporting = "오늘 가이드: 시장 ${guide.marketUnits} + 종목 ${guide.stockUnits} = ${guide.units}유닛",
                )

                val p = parseDouble(entryPrice)
                val u = units.toIntOrNull()
                if (p != null && p > 0 && u != null) {
                    val suggested = calc.suggestedQuantity(p, u * calc.unitAmount)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "추천 수량 ${"%,d".format(suggested)}주 (${won(u * calc.unitAmount)})",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { quantity = suggested.toString() }) { Text("적용") }
                    }
                    Text(
                        "손절가 ${num(calc.stopPrice(p))}원 (-${fmt(state.settings.stopLossRate)}%) · " +
                            "목표가 ${num(calc.targetPrice(p))}원 (+${fmt(calc.minProfitRate)}%)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                InputField("수량", quantity, { quantity = it }, suffix = "주")

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("보유 중 (선택)", style = MaterialTheme.typography.titleSmall)
                InputField("현재가", currentPrice, { currentPrice = it }, suffix = "원", supporting = "입력하면 손절/목표가 도달 알림을 표시합니다")

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("청산 (매도 시 입력)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        InputField("매도일 (yyyy-MM-dd)", exitDate, { exitDate = it }, numeric = false)
                    }
                    OutlinedButton(onClick = { exitDate = today.toString() }) { Text("오늘") }
                }
                InputField("매도가", exitPrice, { exitPrice = it }, suffix = "원")
                InputField("메모", memo, { memo = it }, numeric = false)

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (initial != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = {
                        val ed = parseDate(entryDate)
                        val ep = parseDouble(entryPrice)
                        val q = parseLong(quantity)
                        val un = units.toIntOrNull()
                        val cp = if (currentPrice.isBlank()) null else parseDouble(currentPrice)
                        val xd = if (exitDate.isBlank()) null else parseDate(exitDate)
                        val xp = if (exitPrice.isBlank()) null else parseDouble(exitPrice)
                        error = when {
                            name.isBlank() -> "종목명을 입력하세요."
                            ed == null -> "매수일 형식이 올바르지 않습니다. (예: 2026-10-05)"
                            ep == null || ep <= 0 -> "매수가를 입력하세요."
                            q == null || q <= 0 -> "수량을 입력하세요."
                            un == null || un < 0 -> "투입 유닛을 입력하세요."
                            currentPrice.isNotBlank() && cp == null -> "현재가 형식이 올바르지 않습니다."
                            exitDate.isNotBlank() && xd == null -> "매도일 형식이 올바르지 않습니다."
                            exitPrice.isNotBlank() && xp == null -> "매도가 형식이 올바르지 않습니다."
                            (xd == null) != (xp == null) -> "청산하려면 매도일과 매도가를 모두 입력하세요."
                            xd != null && xd.isBefore(ed) -> "매도일이 매수일보다 빠릅니다."
                            else -> null
                        }
                        if (error == null) {
                            onSave(
                                Trade(
                                    id = initial?.id ?: 0L,
                                    name = name.trim(),
                                    entryDate = ed!!,
                                    entryPrice = ep!!,
                                    quantity = q!!,
                                    units = un!!,
                                    currentPrice = cp,
                                    exitDate = xd,
                                    exitPrice = xp,
                                    memo = memo.trim(),
                                )
                            )
                        }
                    }) { Text("저장") }
                }
            }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제") },
            text = { Text("${initial.name} 매매기록을 삭제할까요?") },
            confirmButton = { TextButton(onClick = { onDelete(initial.id) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

private fun plain(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()
