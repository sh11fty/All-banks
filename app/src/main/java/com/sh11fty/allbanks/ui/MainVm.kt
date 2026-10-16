package com.sh11fty.allbanks.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sh11fty.allbanks.AllBanksApp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.BudgetEntity
import com.sh11fty.allbanks.data.Backup
import com.sh11fty.allbanks.data.CandidateEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.Repository
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.UndoSnapshot
import com.sh11fty.allbanks.domain.DefaultCategories
import com.sh11fty.allbanks.domain.Suggest
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.update.Updater
import com.sh11fty.allbanks.update.VersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val budgets = dao.budgets().state(emptyList<BudgetEntity>())

    /** Transfers awaiting a decision plus uncategorised items not snoozed or dismissed. */
    val reviewCount = combine(candidates, txns, categories) { c, t, cats ->
        val other = cats.firstOrNull { it.name == DefaultCategories.OTHER && it.parentId == null }?.id
        c.count { !it.later } + t.count { Suggest.isUnsorted(it, other) }
    }.state(0)

    class UndoItem(val id: Long, val snapshot: UndoSnapshot)

    /** The last reversible action; the UI shows it as a snackbar with an Undo button. */
    val undo = MutableStateFlow<UndoItem?>(null)
    private var undoCounter = 0L

    private fun offerUndo(s: UndoSnapshot) { undo.value = UndoItem(++undoCounter, s) }

    fun performUndo(item: UndoItem) = run {
        repo.undo(item.snapshot)
        message.value = "Undone"
    }

    private val dismissedKey = "dismissedRuleIdeas"
    val dismissedRuleIdeas = MutableStateFlow(ab.prefs.getStringSet(dismissedKey, emptySet()).orEmpty())

    fun dismissRuleIdea(key: String) {
        val next = dismissedRuleIdeas.value + key
        dismissedRuleIdeas.value = next
        ab.prefs.edit().putStringSet(dismissedKey, next).apply()
    }

    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val hasKey = MutableStateFlow(ab.vault.apiKey != null)
    val includeSavings = MutableStateFlow(ab.prefs.getBoolean("includeSavings", true))
    val hideBalances = MutableStateFlow(ab.prefs.getBoolean("hideBalances", false))
    val blockScreenshots = MutableStateFlow(ab.prefs.getBoolean("blockScreenshots", false))
    val update = MutableStateFlow<VersionInfo?>(null)
    val updateBusy = MutableStateFlow(false)

    private val updater = Updater(app)

    init {
        run {
            if (!ab.prefs.getBoolean("catsV2", false)) {
                repo.migrateLegacyCategories()
                ab.prefs.edit().putBoolean("catsV2", true).apply()
            } else {
                repo.ensureCategories()
            }
            // v3 adds category sources, rule links and merchant names; fill them in once for existing data.
            if (!ab.prefs.getBoolean("catsV3", false)) {
                repo.categorise(all = true)
                ab.prefs.edit().putBoolean("catsV3", true).apply()
            } else {
                repo.categorise(all = false)
            }
        }
    }

    fun setHideBalances(v: Boolean) {
        hideBalances.value = v
        ab.prefs.edit().putBoolean("hideBalances", v).apply()
    }

    fun setBlockScreenshots(v: Boolean) {
        blockScreenshots.value = v
        ab.prefs.edit().putBoolean("blockScreenshots", v).apply()
    }

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

    /**
     * Saves the transaction sheet. [similar] are look-alike transactions that get the same category;
     * [rule] makes it automatic from now on.
     */
    fun saveTxn(
        t: TxnEntity,
        categoryId: Long?,
        similar: List<TxnEntity>,
        rule: Repository.RuleDraft?,
        exclude: Boolean,
        note: String,
        done: () -> Unit = {},
    ) = run {
        val name = categoryId?.let { id -> categories.value.firstOrNull { it.id == id }?.name }
        val n = 1 + similar.size
        val label = when {
            name != null -> "Categorised $n as $name"
            else -> "Saved"
        }
        offerUndo(repo.saveEdit(t, categoryId, similar, rule, exclude, note, label))
        done()
    }

    /** One-tap categorise from a suggestion chip; also sorts look-alike transactions that still need a category. */
    fun quickCategorise(t: TxnEntity, categoryId: Long) {
        val other = categories.value.firstOrNull { it.name == DefaultCategories.OTHER && it.parentId == null }?.id
        val similar = Suggest.similar(t, txns.value) { Suggest.isUnsorted(it, other) }
        saveTxn(t, categoryId, similar, null, t.excluded, t.note.orEmpty())
    }

    /** Applies one category to several selected transactions. */
    fun categoriseMany(ts: List<TxnEntity>, categoryId: Long, rule: Repository.RuleDraft?) = run {
        val name = categories.value.firstOrNull { it.id == categoryId }?.name ?: "category"
        val first = ts.first()
        offerUndo(repo.saveEdit(first, categoryId, ts.drop(1), rule, first.excluded, first.note.orEmpty(), "Categorised ${ts.size} as $name"))
    }

    fun setExcluded(ts: List<TxnEntity>, excluded: Boolean) = run {
        offerUndo(repo.mutate(ts.map { it.id }, if (excluded) "Excluded ${ts.size} from spending" else "Included ${ts.size} in spending") { it.copy(excluded = excluded) })
    }

    fun setReviewState(ts: List<TxnEntity>, state: Int) = run {
        val label = when (state) { 1 -> "Parked ${ts.size} for later"; 2 -> "Marked ${ts.size} done"; else -> "Moved back" }
        offerUndo(repo.mutate(ts.map { it.id }, label) { it.copy(reviewState = state) })
    }

    fun renameMerchant(oldName: String, newName: String) = run {
        if (newName.isNotBlank() && newName != oldName) offerUndo(repo.renameMerchant(oldName, newName))
    }

    fun createCategory(name: String, parentId: Long? = null, then: (Long) -> Unit = {}) = run {
        then(repo.newCategory(name, parentId))
    }

    fun renameCategory(c: CategoryEntity, name: String) = run { repo.renameCategory(c, name) }
    fun recolourCategory(c: CategoryEntity, colour: Int) = run { repo.recolourCategory(c, colour) }
    fun setCategoryIcon(c: CategoryEntity, icon: String) = run { dao.upsertCategories(listOf(c.copy(icon = icon))) }
    fun mergeCategory(from: CategoryEntity, into: Long?) = run {
        repo.mergeCategory(from, into)
        message.value = "Merged \"${from.name}\""
    }

    fun setBudget(categoryId: Long, limitMinor: Long?) = run {
        if (limitMinor == null || limitMinor <= 0) dao.deleteBudget(categoryId)
        else dao.upsertBudget(BudgetEntity(categoryId, limitMinor))
    }

    fun checkLater(t: TxnEntity) = setReviewState(listOf(t), 1)
    fun dismissReview(t: TxnEntity) = setReviewState(listOf(t), 2)
    fun restoreReview(t: TxnEntity) = setReviewState(listOf(t), 0)
    fun checkLater(c: CandidateEntity) = run { repo.candidateLater(c, true) }
    fun restoreCandidate(c: CandidateEntity) = run { repo.candidateLater(c, false) }

    fun exportCsv(rows: List<TxnEntity>, write: (ByteArray) -> Unit) = run {
        val accounts = accounts.value.associateBy { it.id }
        val cats = categories.value.associateBy { it.id }
        fun q(v: String?) = "\"" + (v ?: "").replace("\"", "\"\"") + "\""
        val sb = StringBuilder("date,bank,account,amount,currency,merchant,counterparty,description,category,transfer,note\n")
        rows.forEach { t ->
            val a = accounts[t.accountId]
            sb.append(listOf(
                t.bookingDate, q(a?.institution), q(a?.name), "%.2f".format(t.amountMinor / 100.0), t.currency,
                q(t.merchantName), q(t.counterparty), q(t.description), q(cats[t.categoryId]?.name),
                if (t.transferId != null) "yes" else "no", q(t.note),
            ).joinToString(",")).append('\n')
        }
        write(sb.toString().toByteArray())
        message.value = "Exported ${rows.size} transactions"
    }

    fun unlink(t: TxnEntity) = run { repo.unlink(t) }
    fun confirm(c: CandidateEntity) = run { repo.confirmTransfer(c) }
    fun reject(c: CandidateEntity) = run { repo.rejectTransfer(c) }

    fun saveRule(rule: RuleEntity, applyToPast: Boolean = true) = run { repo.saveRule(rule, applyToPast) }

    /** Swap priority with the neighbouring rule (up = higher priority = lower number). */
    fun moveRule(r: RuleEntity, up: Boolean) = run {
        val list = rules.value.sortedWith(compareBy({ it.priority }, { it.id }))
        val i = list.indexOfFirst { it.id == r.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j < 0 || j >= list.size) return@run
        // Renumber everything so ties can never leave the order ambiguous.
        val order = list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        dao.upsertRules(order.mapIndexed { idx, x -> x.copy(priority = idx) })
        repo.categorise(all = true)
    }

    fun deleteRule(r: RuleEntity) = run {
        dao.deleteRule(r.id)
        repo.categorise(all = true)
    }
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
