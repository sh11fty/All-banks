package com.sh11fty.allbanks

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sh11fty.allbanks.data.AppDb
import com.sh11fty.allbanks.data.Repository
import com.sh11fty.allbanks.data.Vault
import com.sh11fty.allbanks.net.ApiException
import java.io.IOException
import java.util.concurrent.TimeUnit

class AllBanksApp : Application() {
    val vault by lazy { Vault(this) }
    val db by lazy { AppDb.create(this, vault.dbPassphrase()) }
    val repo by lazy { Repository(db, vault) }
    val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Alerts", NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val CHANNEL = "alerts"

        fun notify(context: Context, id: Int, title: String, text: String) {
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AllBanksApp
        if (app.vault.apiKey == null) return Result.success()
        return try {
            val s = app.repo.sync()
            if (s.warnings.isNotEmpty()) {
                AllBanksApp.notify(app, 1, "Bank connection needs attention", s.warnings.joinToString("\n"))
            }
            if (s.errors.isNotEmpty()) {
                AllBanksApp.notify(app, 2, "Some accounts failed to sync", s.errors.joinToString("\n"))
            }
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: ApiException) {
            AllBanksApp.notify(app, 3, "Sync failed", e.message ?: "Endute API error")
            Result.success()
        } catch (e: Exception) {
            AllBanksApp.notify(app, 3, "Sync failed", e.message ?: "Unknown error")
            Result.success()
        }
    }
}
