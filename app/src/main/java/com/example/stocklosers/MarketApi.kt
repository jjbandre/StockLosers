package com.example.stocklosers


interface MarketApi {
    suspend fun getDayLosers(limit: Int = 100): List<Quote>
}
