package com.example.stocklosers

import android.util.Log
// import androidx.compose.foundation.layout.size // Not used in this file based on errors
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.converter.moshi.MoshiConverterFactory

private const val TAG = "YahooApi"

// Your Quote data class (assuming it's defined elsewhere or like this)
// data class Quote(val symbol: String, val percentChange: Float, val price: Double)


// Primary endpoint that usually works for “predefined screeners”
private interface YahooService {
    @GET("v1/finance/screener/predefined/saved")
    suspend fun predefined(
        @Query("scrIds") scrIds: String = "day_losers",
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0
    ): PredefinedResponse

    @GET("v1/finance/screener?lang=en-US&region=US&scrIds=day_losers")
    suspend fun screener(
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0
    ): ScreenerResponse
}

// Minimal models (shared shape)
data class PredefinedResponse(val finance: Finance?)
data class ScreenerResponse(val finance: Finance?)
data class Finance(val result: List<Result>?)
data class Result(val quotes: List<YQuote>?) // Note: Result might conflict with kotlin.Result, consider renaming if so
data class YQuote(
    val symbol: String?,
    val regularMarketChangePercent: Double?
    // val regularMarketPrice: Double? // Add if you plan to get price from here
)

// Single, correct class definition for YahooMarketApi
class YahooMarketApi : MarketApi {

    private val service: YahooService

    init {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val ua = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
                )
                .build()
            chain.proceed(req)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(ua)
            .addInterceptor(logging)
            .build()

        service = Retrofit.Builder()
            .baseUrl("https://query1.finance.yahoo.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
            .create(YahooService::class.java)
    }

    // getDayLosers implementation directly inside the YahooMarketApi class
    override suspend fun getDayLosers(limit: Int): List<Quote> {
        // 1) Try the predefined/saved endpoint first
        runCatching {
            val r = service.predefined(count = limit, offset = 0) // 'service' is now in scope
            val yQuotes = r.finance?.result?.firstOrNull()?.quotes.orEmpty() // Renamed to yQuotes for clarity
            Log.d(TAG, "predefined/saved returned ${yQuotes.size} quotes") // Use .size on the list
            if (yQuotes.isNotEmpty()) {
                return yQuotes.mapNotNull { q ->
                    val sym = q.symbol ?: return@mapNotNull null // q.symbol is now resolved
                    val pctDouble = q.regularMarketChangePercent ?: return@mapNotNull null // q.regularMarketChangePercent is resolved
                    val placeholderPrice = 0.0 // Placeholder for price
                    Quote(
                        symbol = sym,
                        percentChange = pctDouble.toFloat(),
                        price = placeholderPrice
                    )
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "predefined/saved failed: ${e.message}")
        }

        // 2) Fallback to the screener path
        return runCatching {
            val r = service.screener(count = limit, offset = 0) // 'service' is now in scope
            val yQuotes = r.finance?.result?.firstOrNull()?.quotes.orEmpty() // Renamed to yQuotes
            Log.d(TAG, "screener returned ${yQuotes.size} quotes") // Use .size on the list
            yQuotes.mapNotNull { q ->
                val sym = q.symbol ?: return@mapNotNull null // q.symbol is resolved
                val pctDouble = q.regularMarketChangePercent ?: return@mapNotNull null // q.regularMarketChangePercent is resolved
                val placeholderPrice = 0.0 // Placeholder for price
                Quote(
                    symbol = sym,
                    percentChange = pctDouble.toFloat(),
                    price = placeholderPrice
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "screener failed: ${e.message}", e)
        }.getOrElse { emptyList() }
    }

} // This is the single closing brace for the YahooMarketApi class
