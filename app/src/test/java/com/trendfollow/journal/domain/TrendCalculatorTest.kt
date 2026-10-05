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

    private fun log(dayOffset: Int, r: PrevResult?, c: MarketCondition? = null) =
        MarketLog(day.plusDays(dayOffset.toLong()), c, prevResult = r)

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
        assertFalse(calc.isLossRateInvalid)
    }

    @Test
    fun capital128Million() {
        val c = TrendCalculator(Settings(totalCapital = 128_000_000))
        assertEquals(32_000_000.0, c.maxPositionAmount, 1e-6)
        assertEquals(6_400_000.0, c.unitAmount, 1e-6)
        assertEquals(4, c.maxStocks)
        val g = c.guide(MarketCondition.STRONG, 1)
        assertEquals(19_200_000.0, g.amountPerStock, 1e-6)
        assertEquals(15.0, g.ratioOfCapital, 1e-9)
    }

    @Test
    fun invalidLossRatesNeverExceedCapital() {
        val c = TrendCalculator(Settings(totalCapital = 128_000_000, maxLossRate = 8.0, stopLossRate = 2.0))
        assertTrue(c.isLossRateInvalid)
        assertEquals(128_000_000.0, c.maxPositionAmount, 1e-6)
        assertTrue(c.guide(MarketCondition.STRONG, 3).messages.any { it.contains("작아야") })
    }

    @Test
    fun marketUnits() {
        assertEquals(0, calc.marketUnitsFor(MarketCondition.WEAK))
        assertEquals(1, calc.marketUnitsFor(MarketCondition.NEUTRAL))
        assertEquals(2, calc.marketUnitsFor(MarketCondition.STRONG))
        assertEquals(0, calc.marketUnitsFor(null))
    }

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
    fun guideAmounts() {
        val g = calc.guide(MarketCondition.STRONG, 1)
        assertEquals(3, g.units) // 시장 2 + 종목 1
        assertEquals(15_000_000.0, g.amountPerStock, 1e-6)
        assertEquals(60.0, g.ratioOfMaxPosition, 1e-9)
        assertEquals(15.0, g.ratioOfCapital, 1e-9)
        assertEquals(4, g.maxStocks)

        val full = calc.guide(MarketCondition.STRONG, 3)
        assertEquals(5, full.units)
        assertEquals(25_000_000.0, full.amountPerStock, 1e-6)

        val weak = calc.guide(MarketCondition.WEAK, 0)
        assertEquals(0, weak.units)
        assertEquals(0.0, weak.amountPerStock, 1e-9)
        assertTrue(weak.messages.any { it.contains("관망") })
    }

    @Test
    fun dailyResultsMergeSharedMarketAndAccountLogs() {
        val market = listOf(
            MarketLog(day, MarketCondition.STRONG, memo = "코스피 상승"),
            MarketLog(day.plusDays(1), MarketCondition.NEUTRAL),
        )
        val account = listOf(
            MarketLog(day, prevResult = PrevResult.ACHIEVED),
            MarketLog(day.plusDays(2), prevResult = PrevResult.MISSED),
        )
        val r = calc.dailyResults(market, account)
        assertEquals(listOf(day.plusDays(2), day.plusDays(1), day), r.map { it.date })
        assertEquals(null, r[0].condition)
        assertEquals(PrevResult.MISSED, r[0].prevResult)
        assertEquals(1, r[0].stockUnits)
        assertEquals(MarketCondition.NEUTRAL, r[1].condition)
        assertEquals(2, r[1].stockUnits)
        assertEquals("코스피 상승", r[2].memo)
        assertEquals(PrevResult.ACHIEVED, r[2].prevResult)
        assertEquals(2, r[2].stockUnits)
    }

    @Test
    fun accountsAreIndependent() {
        val a = TrendCalculator(Settings(totalCapital = 128_000_000))
        val b = TrendCalculator(Settings(totalCapital = 50_000_000, initialStockUnits = 3))
        assertEquals(19_200_000.0, a.guide(MarketCondition.STRONG, a.stockUnitsOn(day, emptyList())).amountPerStock, 1e-6)
        // 5천만 × 2% ÷ 8% = 1,250만, 5유닛 전부 = 1,250만
        assertEquals(12_500_000.0, b.guide(MarketCondition.STRONG, b.stockUnitsOn(day, emptyList())).amountPerStock, 1e-6)
    }
}
