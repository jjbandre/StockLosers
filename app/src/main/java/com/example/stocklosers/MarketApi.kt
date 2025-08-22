package com.example.stocklosers

data class Quote(
    val symbol: String,
    val percentChange: Double
)

interface MarketApi {
    suspend fun getDayLosers(limit: Int = 100): List<Quote>
}
