package com.example.stocklosers

import android.net.Uri

fun yahooQuoteUrl(tickerRaw: String): String {
    val ticker = tickerRaw.trim().uppercase()
    val normalized = if (Regex("""^[A-Z]{1,5}\.[A-Z]$""").matches(ticker)) {
        ticker.replace('.', '-') // e.g. BRK.B -> BRK-B
    } else ticker
    return "https://finance.yahoo.com/quote/${Uri.encode(normalized)}"
}
