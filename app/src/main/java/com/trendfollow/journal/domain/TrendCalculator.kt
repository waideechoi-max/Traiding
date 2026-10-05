package com.trendfollow.journal.domain

import java.time.LocalDate
import kotlin.math.floor

/** 종목유닛 변화 한 단계: 그날 선택한 이전 투자 결과가 종목유닛에 준 영향 */
data class StockUnitStep(
    val date: LocalDate,
    val result: PrevResult,
    val before: Int,
    val after: Int,
)

/** 오늘의 투자비중 가이드 */
data class Guide(
    val condition: MarketCondition?,
    val marketUnits: Int,
    val stockUnits: Int,
    val totalUnits: Int,
    /** 오늘 신규 진입 시 종목당 투입 금액 */
    val amountPerStock: Double,
    /** 종목당 최대 투입비중 대비 비율 (%) */
    val ratioOfMaxPosition: Double,
    /** 총 투자금 대비 비율 (%) */
    val ratioOfCapital: Double,
    /** 종목수 = 총 투자금 ÷ 1종목당 최대 투입비중 */
    val maxStocks: Int,
    val messages: List<String>,
) {
    val units: Int get() = marketUnits + stockUnits
}

/** 일지 한 줄: 날짜별 시장상황·이전 수익율·종목유닛 */
data class DailyResult(
    val date: LocalDate,
    val condition: MarketCondition?,
    val memo: String,
    val prevResult: PrevResult?,
    /** 그날 기준 종목유닛 */
    val stockUnits: Int,
)

class TrendCalculator(val settings: Settings) {

    /** 종목당 최소 수익율 = 목표손익비 × 종목당 최대 손실율 */
    val minProfitRate: Double get() = settings.rewardRatio * settings.stopLossRate

    /** 총 투자금 대비 최대 손실 금액 */
    val maxLossAmount: Double get() = settings.totalCapital * settings.maxLossRate / 100

    /** 최대 손실율이 종목당 최대 손실율 이상이면 1종목에 총 투자금 전부가 들어가는 잘못된 설정 */
    val isLossRateInvalid: Boolean get() = settings.maxLossRate >= settings.stopLossRate

    /** 1종목당 최대 투입비중 = 총 투자금 × 최대 손실율 ÷ 종목당 최대 손실율 (총 투자금을 넘지 않음) */
    val maxPositionAmount: Double
        get() = if (settings.stopLossRate > 0) {
            (settings.totalCapital * settings.maxLossRate / settings.stopLossRate)
                .coerceAtMost(settings.totalCapital.toDouble())
        } else 0.0

    /** 1유닛당 금액 = 1종목당 최대 투입비중 ÷ 종목당 총 유닛수 */
    val unitAmount: Double
        get() = if (settings.totalUnits > 0) maxPositionAmount / settings.totalUnits else 0.0

    /** 종목수 = 총 투자금 ÷ 1종목당 최대 투입비중 (소수점 버림) */
    val maxStocks: Int
        get() = if (maxPositionAmount > 0) floor(settings.totalCapital / maxPositionAmount + 1e-9).toInt() else 0

    val maxMarketUnits: Int get() = settings.marketUnits.coerceIn(0, settings.totalUnits.coerceAtLeast(0))

    /** 종목유닛 최대값 = 총 유닛 − 시장유닛 */
    val maxStockUnits: Int get() = (settings.totalUnits - maxMarketUnits).coerceAtLeast(0)

    fun marketUnitsFor(condition: MarketCondition?): Int = when (condition) {
        null, MarketCondition.WEAK -> 0
        MarketCondition.NEUTRAL -> (maxMarketUnits + 1) / 2
        MarketCondition.STRONG -> maxMarketUnits
    }

    val initialStockUnits: Int get() = settings.initialStockUnits.coerceIn(0, maxStockUnits)

    /** 날짜순으로 이전 투자 결과(미달성 −1 / 진행중 0 / 달성 +1)를 누적 (0 ~ 최대 사이로 제한) */
    fun stockUnitHistory(logs: List<MarketLog>): List<StockUnitStep> {
        var units = initialStockUnits
        return logs.filter { it.prevResult != null }
            .sortedBy { it.date }
            .map { log ->
                val before = units
                units = (units + log.prevResult!!.delta).coerceIn(0, maxStockUnits)
                StockUnitStep(log.date, log.prevResult, before, units)
            }
    }

    /** 해당 날짜의 선택까지 반영한 종목유닛 */
    fun stockUnitsOn(date: LocalDate, logs: List<MarketLog>): Int =
        stockUnitHistory(logs).lastOrNull { !it.date.isAfter(date) }?.after ?: initialStockUnits

    /** 해당 날짜 선택 전(전날까지) 종목유닛 */
    fun stockUnitsBefore(date: LocalDate, logs: List<MarketLog>): Int =
        stockUnitHistory(logs).lastOrNull { it.date.isBefore(date) }?.after ?: initialStockUnits

    fun guide(condition: MarketCondition?, stockUnits: Int): Guide {
        val market = marketUnitsFor(condition)
        val stock = stockUnits.coerceIn(0, maxStockUnits)
        val units = market + stock
        val amount = units * unitAmount
        val messages = mutableListOf<String>()

        if (settings.totalCapital <= 0) messages += "계산기 탭에서 총 투자금을 먼저 입력하세요."
        if (isLossRateInvalid) {
            messages += "⚠ 최대 손실율(${fmt(settings.maxLossRate)}%)이 종목당 최대 손실율(${fmt(settings.stopLossRate)}%)보다 작아야 합니다. 계산기 탭에서 확인하세요."
        }
        if (condition == null) messages += "오늘 시장상황(약세/보합/강세)을 입력하세요. 입력 전에는 시장유닛 0으로 계산합니다."

        when (condition) {
            MarketCondition.STRONG -> messages += "강세장: 시장유닛 $market. 추세에 올라타 계획한 비중대로 진입 가능합니다."
            MarketCondition.NEUTRAL -> messages += "보합장: 시장유닛 $market. 선별적으로 진입하고 비중을 줄입니다."
            MarketCondition.WEAK -> messages += "약세장: 시장유닛 0. 신규 진입을 줄이고 보유 종목 손절가를 엄격히 지킵니다."
            null -> Unit
        }

        if (stock == 0) {
            messages += "종목유닛 0: 이전 투자가 목표 수익률(${fmt(minProfitRate)}%)에 연속 미달했습니다. 매매 방식을 점검하세요."
        } else if (stock == maxStockUnits && maxStockUnits > 0) {
            messages += "종목유닛 최대($stock): 이전 투자가 목표 수익률을 달성하고 있습니다."
        }

        if (units == 0) messages += "오늘 투입 가능 유닛 0 → 신규 매수 없이 관망(현금 보유)."

        if (amount > 0) {
            messages += "신규 진입 시 손절(-${fmt(settings.stopLossRate)}%) 손실액 약 ${won(amount * settings.stopLossRate / 100)}, " +
                "목표(+${fmt(minProfitRate)}%) 수익액 약 ${won(amount * minProfitRate / 100)}."
        }

        val total = settings.totalUnits
        return Guide(
            condition = condition,
            marketUnits = market,
            stockUnits = stock,
            totalUnits = total,
            amountPerStock = amount,
            ratioOfMaxPosition = if (total > 0) units * 100.0 / total else 0.0,
            ratioOfCapital = if (settings.totalCapital > 0) amount * 100 / settings.totalCapital else 0.0,
            maxStocks = maxStocks,
            messages = messages,
        )
    }

    /** 날짜별 일지 (최근 날짜 먼저) */
    fun dailyResults(logs: List<MarketLog>): List<DailyResult> =
        logs.sortedByDescending { it.date }.map { log ->
            DailyResult(
                date = log.date,
                condition = log.condition,
                memo = log.memo,
                prevResult = log.prevResult,
                stockUnits = stockUnitsOn(log.date, logs),
            )
        }
}

fun won(value: Double): String = "%,d원".format(Math.round(value))

fun fmt(value: Double): String =
    if (value == Math.rint(value)) "%d".format(value.toLong()) else "%.2f".format(value).trimEnd('0').trimEnd('.')
