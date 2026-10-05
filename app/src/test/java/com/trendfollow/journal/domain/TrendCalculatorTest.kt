package com.trendfollow.journal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrendCalculatorTest {
    private val settings = Settings(totalCapital = 100_000_000)
    private val calc = TrendCalculator(settings)
    private val day = LocalDate.of(2026, 10, 5)

    private fun closed(id: Long, entry: Double, exit: Double, exitDay: Int, qty: Long = 10) = Trade(
        id = id, name = "T$id", entryDate = day, entryPrice = entry, quantity = qty, units = 3,
        exitDate = day.plusDays(exitDay.toLong()), exitPrice = exit,
    )

    @Test
    fun basicSheetValues() {
        assertEquals(24.0, calc.minProfitRate, 1e-9)
        assertEquals(2_000_000.0, calc.maxLossAmount, 1e-6)
        // 1억 × 2% ÷ 8% = 2,500만원
        assertEquals(25_000_000.0, calc.maxPositionAmount, 1e-6)
        // 2,500만원 ÷ 5유닛 = 500만원
        assertEquals(5_000_000.0, calc.unitAmount, 1e-6)
        assertEquals(2, calc.maxMarketUnits)
        assertEquals(3, calc.maxStockUnits)
    }

    @Test
    fun marketUnits() {
        assertEquals(0, calc.marketUnitsFor(MarketCondition.WEAK))
        assertEquals(1, calc.marketUnitsFor(MarketCondition.NEUTRAL))
        assertEquals(2, calc.marketUnitsFor(MarketCondition.STRONG))
        assertEquals(0, calc.marketUnitsFor(null))
    }

    @Test
    fun stockUnitsMoveWithResultsAndClamp() {
        assertEquals(1, calc.currentStockUnits(emptyList()))
        val trades = listOf(
            closed(1, 10000.0, 12400.0, 1), // +24% 달성 → 2
            closed(2, 10000.0, 13000.0, 2), // 달성 → 3
            closed(3, 10000.0, 15000.0, 3), // 달성 → 3 (최대)
            closed(4, 10000.0, 11000.0, 4), // +10% 미달 → 2
        )
        assertEquals(listOf(2, 3, 3, 2), calc.stockUnitHistory(trades).map { it.after })
        assertEquals(2, calc.currentStockUnits(trades))

        val losses = (1..5L).map { closed(it, 10000.0, 9200.0, it.toInt()) }
        assertEquals(0, calc.currentStockUnits(losses))
    }

    @Test
    fun historyOrderedByExitDate() {
        val trades = listOf(
            closed(1, 10000.0, 9000.0, 5),   // 나중에 청산 (미달)
            closed(2, 10000.0, 12500.0, 1),  // 먼저 청산 (달성)
        )
        assertEquals(listOf(2L, 1L), calc.stockUnitHistory(trades).map { it.trade.id })
        assertEquals(1, calc.currentStockUnits(trades))
    }

    @Test
    fun guideAmounts() {
        val g = calc.guide(MarketCondition.STRONG, emptyList())
        assertEquals(3, g.units) // 시장 2 + 종목 1
        assertEquals(15_000_000.0, g.amountPerStock, 1e-6)
        assertEquals(60.0, g.ratioOfMaxPosition, 1e-9)
        assertEquals(15.0, g.ratioOfCapital, 1e-9)

        val weak = calc.guide(MarketCondition.WEAK, (1..5L).map { closed(it, 100.0, 90.0, it.toInt()) })
        assertEquals(0, weak.units)
        assertEquals(0.0, weak.amountPerStock, 1e-9)
        assertTrue(weak.messages.any { it.contains("관망") })
    }

    @Test
    fun stopAndTargetAlerts() {
        val open = Trade(1, "A", day, 10000.0, 10, 3, currentPrice = 9100.0)
        assertTrue(calc.guide(MarketCondition.NEUTRAL, listOf(open)).messages.any { it.contains("손절") && it.contains("A") })
        val win = open.copy(currentPrice = 12500.0)
        assertTrue(calc.guide(MarketCondition.NEUTRAL, listOf(win)).messages.any { it.contains("목표가") })
    }

    @Test
    fun performanceStats() {
        val trades = listOf(
            closed(1, 10000.0, 12400.0, 0, qty = 100), // +240,000 (오늘)
            closed(2, 10000.0, 9000.0, 1, qty = 100),  // -100,000, -10% 손절 위반
            Trade(3, "open", day, 5000.0, 100, 2, currentPrice = 5500.0),
        )
        val p = calc.performance(trades, day)
        assertEquals(2, p.closedCount)
        assertEquals(50.0, p.winRate, 1e-9)
        assertEquals(50.0, p.achieveRate, 1e-9)
        assertEquals(140_000.0, p.totalPnl, 1e-6)
        assertEquals(240_000.0, p.todayPnl, 1e-6)
        assertEquals(1, p.todayClosedCount)
        assertEquals(1, p.openCount)
        assertEquals(500_000.0, p.openInvested, 1e-6)
        assertEquals(50_000.0, p.unrealizedPnl, 1e-6)
        assertEquals(1, p.stopViolations)
        assertEquals(2.4, p.realizedRewardRatio!!, 1e-9)
    }

    @Test
    fun dailyResultsMergeLogsAndTrades() {
        val trades = listOf(closed(1, 100.0, 110.0, 1, qty = 10))
        val logs = listOf(MarketLog(day, MarketCondition.STRONG), MarketLog(day.plusDays(1), MarketCondition.NEUTRAL))
        val r = calc.dailyResults(trades, logs)
        assertEquals(listOf(day.plusDays(1), day), r.map { it.date })
        assertEquals(100.0, r[0].pnl, 1e-9)
        assertEquals(MarketCondition.NEUTRAL, r[0].condition)
        assertEquals(0.0, r[1].pnl, 1e-9)
    }

    @Test
    fun quantityAndPrices() {
        assertEquals(1500L, calc.suggestedQuantity(10000.0, 15_000_000.0))
        assertEquals(9200.0, calc.stopPrice(10000.0), 1e-9)
        assertEquals(12400.0, calc.targetPrice(10000.0), 1e-9)
        assertFalse(calc.isAchieved(closed(1, 10000.0, 12399.0, 0)))
        assertTrue(calc.isAchieved(closed(1, 10000.0, 12400.0, 0)))
    }
}
