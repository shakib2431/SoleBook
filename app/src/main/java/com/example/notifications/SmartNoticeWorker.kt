package com.example.notifications

import android.content.Context
import androidx.work.*
import com.example.data.local.NoticeStateEntity
import com.example.data.local.ShopSettingsEntity
import com.example.data.local.SoleBookDatabase
import com.example.domain.AnalyticsEngine
import com.example.domain.NoticeEngine
import java.util.concurrent.TimeUnit

class SmartNoticeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = SoleBookDatabase.getInstance(applicationContext).soleBookDao()
        val settings = dao.getSettingsSync() ?: ShopSettingsEntity()
        if (!settings.alertsEnabled) return Result.success()
        if (!SoleBookNotificationHelper.hasNotificationPermission(applicationContext)) return Result.success()

        val products = dao.getAllProductsSync()
        if (products.isEmpty()) return Result.success()

        val variants = dao.getAllVariantsSync()
        val sales = dao.getAllSalesSync()
        val saleItems = dao.getAllSaleItemsSync()
        val returns = dao.getAllReturnsSync()
        val vendors = dao.getAllVendorsSync()
        val noticeStatesList = dao.getAllNoticeStatesSync()
        val noticeStates = noticeStatesList.associateBy { it.noticeKey }

        val now = System.currentTimeMillis()
        val metrics = AnalyticsEngine.computeProductMetrics(
            products = products,
            variants = variants,
            sales = sales,
            saleItems = saleItems,
            returns = returns,
            now = now
        )
        val insights = AnalyticsEngine.computeInsightsReport(
            products = products,
            variants = variants,
            sales = sales,
            saleItems = saleItems,
            returns = returns,
            settings = settings,
            productMetrics = metrics,
            now = now,
            vendors = vendors
        )
        val notices = NoticeEngine.generateNotices(
            products = products,
            variants = variants,
            sales = sales,
            saleItems = saleItems,
            productMetrics = metrics,
            noticeStates = noticeStates,
            settings = settings,
            now = now,
            buyingBuckets = insights.buyingBuckets
        )

        val allowImmediateSale = inputData.getBoolean(KEY_ALLOW_IMMEDIATE_SALE, false)
        val candidate = NoticeEngine.pickNextNotificationCandidate(
            notices = notices,
            noticeStates = noticeStates,
            settings = settings,
            now = now,
            allowImmediateSale = allowImmediateSale
        ) ?: return Result.success()

        val posted = SoleBookNotificationHelper.postPriorityNotice(applicationContext, candidate)
        if (posted) {
            val prev = noticeStates[candidate.noticeKey]
            dao.upsertNoticeState(
                (prev ?: NoticeStateEntity(noticeKey = candidate.noticeKey)).copy(
                    lastNotifiedAt = now,
                    lastSignatureHash = candidate.signatureHash
                )
            )
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "solebook_smart_notice_periodic"
        private const val UNIQUE_IMMEDIATE_WORK = "solebook_smart_notice_immediate"
        const val KEY_ALLOW_IMMEDIATE_SALE = "allow_immediate_sale"

        fun schedulePeriodic(context: Context, intervalHours: Int, enabled: Boolean) {
            try {
                val wm = WorkManager.getInstance(context)
                if (!enabled) {
                    wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
                    return
                }
                val safeHours = intervalHours.toLong().coerceIn(1L, 24L)
                val request = PeriodicWorkRequestBuilder<SmartNoticeWorker>(safeHours, TimeUnit.HOURS)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            } catch (_: Throwable) {
            }
        }

        fun triggerImmediateCheck(context: Context, allowImmediateSale: Boolean = false) {
            try {
                val wm = WorkManager.getInstance(context)
                val request = OneTimeWorkRequestBuilder<SmartNoticeWorker>()
                    .setInputData(workDataOf(KEY_ALLOW_IMMEDIATE_SALE to allowImmediateSale))
                    .build()
                wm.enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_WORK,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (_: Throwable) {
            }
        }
    }
}
