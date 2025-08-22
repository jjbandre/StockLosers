package com.example.stocklosers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL_ID = "losers_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Biggest Losers Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alerts when stocks fall beyond your threshold" }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun notifyLosers(context: Context, threshold: Double, hits: List<Quote>) {
        ensureChannel(context)
        val title = "↓ ${hits.size} hit(s) ≤ −${"%.1f".format(threshold)}%"
        val content = hits.joinToString { "${it.symbol} ${"%.1f".format(it.percentChange)}%" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(content.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}
