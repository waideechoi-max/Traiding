package com.trendfollow.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trendfollow.journal.domain.MarketCondition
import java.time.LocalDate
import java.time.format.DateTimeParseException

val ProfitColor = Color(0xFFD32F2F) // 국내 관례: 수익 빨강
val LossColor = Color(0xFF1565C0)   // 손실 파랑

fun pnlColor(v: Double): Color = when {
    v > 0 -> ProfitColor
    v < 0 -> LossColor
    else -> Color.Unspecified
}

/** 가격·수량 표시: 정수면 콤마, 소수면 소수 둘째 자리까지 */
fun num(v: Double): String =
    if (v == Math.rint(v)) "%,d".format(v.toLong()) else "%,.2f".format(v)

fun pct(v: Double): String = "%+.2f%%".format(v)

fun parseDouble(s: String): Double? = s.replace(",", "").trim().toDoubleOrNull()
fun parseLong(s: String): Long? = s.replace(",", "").trim().toLongOrNull()
fun parseDate(s: String): LocalDate? = try {
    LocalDate.parse(s.trim())
} catch (e: DateTimeParseException) {
    null
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun ValueRow(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = true,
    suffix: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun MarketSelector(selected: MarketCondition?, unitsFor: (MarketCondition) -> Int, onSelect: (MarketCondition) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarketCondition.entries.forEach { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(c) },
                label = { Text("${c.label} (${unitsFor(c)})") },
            )
        }
    }
}
