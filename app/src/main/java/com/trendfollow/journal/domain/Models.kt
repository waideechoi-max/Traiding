package com.trendfollow.journal.domain

import java.time.LocalDate

/** 시장 상황. 시장유닛: 약세(0), 보합(1), 강세(2) */
enum class MarketCondition(val label: String) {
    WEAK("약세"),
    NEUTRAL("보합"),
    STRONG("강세"),
}

/** 이전 투자 수익율 결과 → 종목유닛 변화 */
enum class PrevResult(val label: String, val delta: Int) {
    MISSED("목표 미달성", -1),
    ONGOING("진행중", 0),
    ACHIEVED("목표 달성", 1),
}

/** 추세추종계산기 입력값 (괄호 안 입력 항목) */
data class Settings(
    /** 총 투자금 (원) */
    val totalCapital: Long = 0,
    /** 최대 손실율: 총 투자금 대비 (%) */
    val maxLossRate: Double = 2.0,
    /** 목표손익비 1 : rewardRatio */
    val rewardRatio: Double = 3.0,
    /** 종목당 최대 손실율 (%) */
    val stopLossRate: Double = 8.0,
    /** 종목당 총 유닛 수 */
    val totalUnits: Int = 5,
    /** 시장유닛 (최대) */
    val marketUnits: Int = 2,
    /** 첫 기록 이전의 시작 종목유닛 */
    val initialStockUnits: Int = 1,
)

/** 일별 기록: 시장 상황 + 이전 투자 결과(종목유닛 증감) */
data class MarketLog(
    val date: LocalDate,
    val condition: MarketCondition? = null,
    val memo: String = "",
    val prevResult: PrevResult? = null,
)

const val ACCOUNT_COUNT = 5

/**
 * 계좌: 별명 + 계좌별 계산기 설정 + 계좌별 종목유닛 기록.
 * logs에는 날짜별 이전 수익율(prevResult)만 담고, 시장상황은 모든 계좌가 공유합니다.
 */
data class Account(
    val id: Int,
    val nickname: String,
    val settings: Settings = Settings(),
    val logs: List<MarketLog> = emptyList(),
)
