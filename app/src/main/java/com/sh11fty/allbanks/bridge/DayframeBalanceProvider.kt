package com.sh11fty.allbanks.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.sh11fty.allbanks.AllBanksApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Read-only companion bridge. Only the installed Dayframe UID may request an aggregate GBP balance. */
class DayframeBalanceProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != "getBalance") return Bundle().apply { putString("error", "Unsupported operation") }
        val ctx = context ?: return Bundle().apply { putString("error", "Provider unavailable") }
        val caller = ctx.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if ("app.dayframe" !in caller) throw SecurityException("Only Dayframe may request this balance")
        val app = ctx.applicationContext as AllBanksApp
        if (app.prefs.getBoolean("hideBalances", false)) {
            return Bundle().apply { putString("error", "Balance is hidden in All Banks settings") }
        }
        return try {
            val accounts = runBlocking {
                withContext(Dispatchers.IO) { app.db.dao().accountsOnce() }
            }
            val includeSavings = app.prefs.getBoolean("includeSavings", true)
            val eligible = accounts.filter { a ->
                a.included && a.currency.equals("GBP", true) &&
                    (includeSavings || !a.accountType.contains("saving", true))
            }
            val total = eligible.mapNotNull { it.balanceMinor }.sum()
            Bundle().apply {
                putString("currency", "GBP")
                putLong("minorUnits", total)
                putInt("includedAccounts", eligible.size)
                putInt("accountsWithBalance", eligible.count { it.balanceMinor != null })
                putLong("retrievedAt", System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Bundle().apply { putString("error", e.message ?: "Could not read bank balances") }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
