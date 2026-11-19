package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.NoticeCategory
import com.example.domain.PriorityNotice
import kotlin.math.abs

object SoleBookNotificationHelper {

    const val CHANNEL_URGENT = "solebook_urgent_stock"
    const val CHANNEL_DIGEST = "solebook_smart_digest"
    const val EXTRA_OPEN_NOTICES = "extra_open_notices"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val urgent = NotificationChannel(
                CHANNEL_URGENT,
                "Urgent Stock & Sale Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate alerts when shoes go out of stock, low stock, or sell"
            }
            val digest = NotificationChannel(
                CHANNEL_DIGEST,
                "Smart Demand & Margin Tips",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic alerts for high demand, slow/dead stock, and price/margin tips"
            }
            mgr.createNotificationChannel(urgent)
            mgr.createNotificationChannel(digest)
        }
    }

    fun postPriorityNotice(context: Context, notice: PriorityNotice): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTICES, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            abs(notice.noticeKey.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isUrgent = notice.category == NoticeCategory.OUT_OF_STOCK ||
            notice.category == NoticeCategory.LOW_STOCK ||
            notice.category == NoticeCategory.SOLD_ALERT ||
            notice.priorityScore >= 85
        val channelId = if (isUrgent) CHANNEL_URGENT else CHANNEL_DIGEST
        val priority = if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_solebook_notice)
            .setContentTitle(notice.title)
            .setContentText(notice.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
            .setSubText("SoleBook • ${notice.category.label}")
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(abs(notice.noticeKey.hashCode()), notif)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
