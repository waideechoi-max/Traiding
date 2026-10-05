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

    private fun log(dayOffset: Int, r: PrevResult?, c: MarketCondition? = null) =
        MarketLog(day.plusDays(dayOffset.toLong()), c, prevResult = r)

    @Test
    fun maxStocks() {
        // 1억 ÷ 2,500만 = 4종목
        assertEquals(4, calc.maxStocks)
        assertEquals(3, TrendCalculator(Settings(totalCapital = 90_000_000, stopLossRate = 7.0)).maxStocks) // 9천만 ÷ 2,571만 = 3.5 → 3
        assertEquals(0, TrendCalculator(Settings()).maxStocks)
    }

    @Test
    fun stockUnitsFollowDailySelectionAndClamp() {
        assertEquals(1, calc.stockUnitsOn(day, emptyList()))
        val logs = listOf(
            log(3, PrevResult.MISSED),   // 3 → 2
            log(0, PrevResult.ACHIEVED), // 1 → 2 (날짜순 정렬 확인)
            log(1, PrevResult.ONGOING),  // 2 → 2
            log(2, PrevResult.ACHIEVED), // 2 → 3
            log(5, null, MarketCondition.STRONG), // 결과 미선택은 변화 없음
        )
        assertEquals(listOf(2, 2, 3, 2), calc.stockUnitHistory(logs).map { it.after })
        assertEquals(3, calc.stockUnitsOn(day.plusDays(2), logs))
        assertEquals(3, calc.stockUnitsBefore(day.plusDays(3), logs))
        assertEquals(2, calc.stockUnitsOn(day.plusDays(3), logs))
        assertEquals(2, calc.stockUnitsOn(day.plusDays(10), logs))
        assertEquals(1, calc.stockUnitsBefore(day, logs))

        val up = (0..5).map { log(it, PrevResult.ACHIEVED) }
        assertEquals(3, calc.stockUnitsOn(day.plusDays(5), up))
        val down = (0..5).map { log(it, PrevResult.MISSED) }
        assertEquals(0, calc.stockUnitsOn(day.plusDays(5), down))
    }

    @Test
    fun suggestedPrevResultFromTrades() {
        assertEquals(null, calc.suggestedPrevResult(emptyList()))
        assertEquals(PrevResult.ACHIEVED, calc.suggestedPrevResult(listOf(closed(1, 100.0, 130.0, 1))))
        assertEquals(PrevResult.MISSED, calc.suggestedPrevResult(listOf(closed(1, 100.0, 110.0, 1))))
        val open = Trade(2, "B", day.plusDays(1), 100.0, 1, 3)
        assertEquals(PrevResult.ONGOING, calc.suggestedPrevResult(listOf(closed(1, 100.0, 130.0, 1), open)))
    }

    @Test
    fun guideAmounts() {
        val g = calc.guide(MarketCondition.STRONG, 1, emptyList())
        assertEquals(3, g.units) // 시장 2 + 종목 1
        assertEquals(15_000_000.0, g.amountPerStock, 1e-6)
        assertEquals(60.0, g.ratioOfMaxPosition, 1e-9)
        assertEquals(15.0, g.ratioOfCapital, 1e-9)

        assertEquals(4, g.maxStocks)
        assertTrue(g.messages.any { it.contains("최대 4종목") })

        val weak = calc.guide(MarketCondition.WEAK, 0, emptyList())
        assertEquals(0, weak.units)
        assertEquals(0.0, weak.amountPerStock, 1e-9)
        assertTrue(weak.messages.any { it.contains("관망") })
    }

    @Test
    fun stopAndTargetAlerts() {
        val open = Trade(1, "A", day, 10000.0, 10, 3, currentPrice = 9100.0)
        assertTrue(calc.guide(MarketCondition.NEUTRAL, 1, listOf(open)).messages.any { it.contains("손절") && it.contains("A") })
        val win = open.copy(currentPrice = 12500.0)
        assertTrue(calc.guide(MarketCondition.NEUTRAL, 1, listOf(win)).messages.any { it.contains("목표가") })
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
        val logs = listOf(
            MarketLog(day, MarketCondition.STRONG, prevResult = PrevResult.ACHIEVED),
            MarketLog(day.plusDays(1), MarketCondition.NEUTRAL),
        )
        val r = calc.dailyResults(trades, logs)
        assertEquals(listOf(day.plusDays(1), day), r.map { it.date })
        assertEquals(100.0, r[0].pnl, 1e-9)
        assertEquals(MarketCondition.NEUTRAL, r[0].condition)
        assertEquals(0.0, r[1].pnl, 1e-9)
        assertEquals(PrevResult.ACHIEVED, r[1].prevResult)
        assertEquals(2, r[1].stockUnits)
        assertEquals(2, r[0].stockUnits)
    }

    @Test
    fun fullGuideTooManyPositions() {
        val open = (1..4L).map { Trade(it, "S$it", day, 100.0, 1, 3) }
        val g = calc.guide(MarketCondition.STRONG, 3, open)
        assertEquals(5, g.units)
        assertEquals(25_000_000.0, g.amountPerStock, 1e-6)
        assertTrue(g.messages.any { it.contains("최대 종목수에 도달") })
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
