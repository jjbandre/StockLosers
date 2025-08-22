package com.example.stocklosers

import android.util.Log
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

// Primary endpoint that usually works for “predefined screeners”
private interface YahooService {
    // Example:
    // https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved?scrIds=day_losers&count=100&offset=0
    @GET("v1/finance/screener/predefined/saved")
    suspend fun predefined(
        @Query("scrIds") scrIds: String = "day_losers",
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0
    ): PredefinedResponse

    // Fallback old path (some regions/setups)
    // https://query1.finance.yahoo.com/v1/finance/screener?lang=en-US&region=US&scrIds=day_losers&count=100&offset=0
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
data class Result(val quotes: List<YQuote>?)
data class YQuote(
    val symbol: String?,
    val regularMarketChangePercent: Double?
)

class YahooMarketApi : MarketApi {

    private val service: YahooService

    init {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val logging = HttpLoggingInterceptor().apply {
            // TIP: change to BODY temporarily if you want full JSON in Logcat
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

    override suspend fun getDayLosers(limit: Int): List<Quote> {
        // 1) Try the predefined/saved endpoint first
        runCatching {
            val r = service.predefined(count = limit, offset = 0)
            val quotes = r.finance?.result?.firstOrNull()?.quotes.orEmpty()
            Log.d(TAG, "predefined/saved returned ${quotes.size} quotes")
            if (quotes.isNotEmpty()) {
                return quotes.mapNotNull { q ->
                    val sym = q.symbol ?: return@mapNotNull null
                    val pct = q.regularMarketChangePercent ?: return@mapNotNull null
                    Quote(sym, pct)
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "predefined/saved failed: ${e.message}")
        }

        // 2) Fallback to the screener path
        return runCatching {
            val r = service.screener(count = limit, offset = 0)
            val quotes = r.finance?.result?.firstOrNull()?.quotes.orEmpty()
            Log.d(TAG, "screener returned ${quotes.size} quotes")
            quotes.mapNotNull { q ->
                val sym = q.symbol ?: return@mapNotNull null
                val pct = q.regularMarketChangePercent ?: return@mapNotNull null
                Quote(sym, pct)
            }
        }.onFailure { e ->
            Log.e(TAG, "screener failed: ${e.message}", e)
        }.getOrElse { emptyList() }
    }
}
