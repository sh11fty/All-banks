package com.sh11fty.allbanks.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sh11fty.allbanks.AllBanksApp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.Backup
import com.sh11fty.allbanks.data.CandidateEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.update.Updater
import com.sh11fty.allbanks.update.VersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainVm(app: Application) : AndroidViewModel(app) {
    private val ab = app as AllBanksApp
    private val dao get() = ab.db.dao()
    private val repo get() = ab.repo

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val accounts = dao.accounts().state(emptyList<AccountEntity>())
    val txns = dao.transactions().state(emptyList<TxnEntity>())
    val categories = dao.categories().state(emptyList<CategoryEntity>())
    val rules = dao.rules().state(emptyList<RuleEntity>())
    val candidates = dao.candidates().state(emptyList<CandidateEntity>())

    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val hasKey = MutableStateFlow(ab.vault.apiKey != null)
    val includeSavings = MutableStateFlow(ab.prefs.getBoolean("includeSavings", true))
    val update = MutableStateFlow<VersionInfo?>(null)
    val updateBusy = MutableStateFlow(false)

    private val updater = Updater(app)

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            message.value = e.message ?: e.javaClass.simpleName
        }
    }

    fun sync() = run {
        syncing.value = true
        try {
            val s = repo.sync()
            message.value = buildString {
                append("Synced. ${s.newTxns} new transactions.")
                s.warnings.forEach { append("\n⚠ $it") }
                s.errors.forEach { append("\n✖ $it") }
            }
        } finally {
            syncing.value = false
        }
    }

    fun saveKey(key: String) {
        ab.vault.apiKey = key
        hasKey.value = ab.vault.apiKey != null
        message.value = if (hasKey.value) "API key saved securely" else "API key removed"
    }

    fun setIncludeSavings(v: Boolean) {
        includeSavings.value = v
        ab.prefs.edit().putBoolean("includeSavings", v).apply()
    }

    fun setIncluded(a: AccountEntity, v: Boolean) = run { dao.setIncluded(a.id, v) }

    fun categorise(t: TxnEntity, categoryId: Long?, rulePattern: String?) =
        run { repo.categoriseOne(t, categoryId, rulePattern) }

    fun categoriseNew(t: TxnEntity, name: String, rulePattern: String?) = run {
        repo.categoriseOne(t, repo.newCategory(name), rulePattern)
    }

    fun unlink(t: TxnEntity) = run { repo.unlink(t) }
    fun confirm(c: CandidateEntity) = run { repo.confirmTransfer(c) }
    fun reject(c: CandidateEntity) = run { repo.rejectTransfer(c) }

    fun saveRule(rule: RuleEntity) = run {
        dao.upsertRule(rule)
        repo.categorise(all = true)
    }

    fun deleteRule(r: RuleEntity) = run { dao.deleteRule(r.id) }
    fun toggleRule(r: RuleEntity) = run { dao.upsertRule(r.copy(enabled = !r.enabled)) }
    fun reapplyRules() = run {
        repo.categorise(all = true)
        message.value = "Rules re-applied"
    }

    fun newCategory(name: String) = run { repo.newCategory(name) }

    fun exportBackup(pass: CharArray, write: (ByteArray) -> Unit) = run {
        write(Backup.export(ab.db, pass))
        message.value = "Encrypted backup saved"
    }

    fun importBackup(data: ByteArray, pass: CharArray) = run {
        Backup.import(ab.db, data, pass)
        message.value = "Backup restored"
    }

    fun checkUpdate() = run {
        updateBusy.value = true
        try {
            val info = updater.check()
            update.value = info
            message.value = if (info == null) "You're on the latest version" else "Update ${info.versionName} available"
        } finally {
            updateBusy.value = false
        }
    }

    fun installUpdate() = run {
        val info = update.value ?: return@run
        updateBusy.value = true
        try {
            val file = updater.download(info)
            if (!updater.install(file)) {
                message.value = "Allow \"install unknown apps\" for All Banks, then press Update again"
            }
        } finally {
            updateBusy.value = false
        }
    }
}
