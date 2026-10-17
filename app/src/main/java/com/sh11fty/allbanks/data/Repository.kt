package com.sh11fty.allbanks.data

import android.graphics.Color
import androidx.room.withTransaction
import com.sh11fty.allbanks.domain.DefaultCategories
import com.sh11fty.allbanks.domain.Insights
import com.sh11fty.allbanks.domain.Money
import com.sh11fty.allbanks.domain.RuleEngine
import com.sh11fty.allbanks.domain.Suggest
import com.sh11fty.allbanks.domain.TransferMatcher
import com.sh11fty.allbanks.net.EnduteApi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/** What an action changed, so it can be reversed. */
data class UndoSnapshot(
    val label: String,
    val before: List<TxnEntity>,
    val createdRuleIds: List<Long> = emptyList(),
    val aliasKeys: List<String> = emptyList(),
)

data class SyncSummary(
    val newTxns: Int,
    val errors: List<String>,
    val warnings: List<String>,
)

fun colourFor(name: String): Int =
    Color.HSVToColor(floatArrayOf((abs(name.hashCode()) % 360).toFloat(), 0.55f, 0.85f))

fun daysUntil(iso: String?): Long? = try {
    if (iso == null) null else Duration.between(Instant.now(), Instant.parse(iso)).toDays()
} catch (e: Exception) {
    null
}

class Repository(val db: AppDb, private val vault: Vault) {
    private val dao get() = db.dao()

    companion object {
        /** The app tracks money from this date onwards; anything earlier is ignored. */
        const val START_DATE = "2026-09-01"
    }

    /** Removes anything booked before [START_DATE] (old imports, restored backups). */
    suspend fun purgeBeforeStart() {
        db.withTransaction {
            dao.deleteTxnsBefore(START_DATE)
            dao.deleteOrphanCandidates()
        }
    }

    suspend fun sync(): SyncSummary {
        val key = vault.apiKey ?: throw IllegalStateException("Add your Endute API key in Settings first")
        val api = EnduteApi(key)
        val existing = dao.accountsOnce().associateBy { it.id }
        val connections = runCatching { api.connections() }.getOrDefault(emptyList())
            .associateBy { it.institution }
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        var added = 0

        for (a in api.accounts()) {
            val old = existing[a.id]
            val bal = runCatching { api.balance(a.id) }.getOrNull()
            val conn = connections[a.institution]
            val account = AccountEntity(
                id = a.id,
                institution = a.institution,
                name = a.name,
                currency = a.currency,
                accountType = a.accountType,
                maskedId = a.maskedIdentifier,
                balanceMinor = bal?.let { Money.parseMinor(it.balance) } ?: old?.balanceMinor,
                balanceFetchedAt = bal?.fetchedAt ?: old?.balanceFetchedAt,
                lastSyncedAt = a.lastSyncedAt,
                consentRenewalDue = a.consentRenewalDue,
                connectionStatus = conn?.status ?: a.status,
                included = old?.included ?: true,
                sandbox = a.sandbox,
            )
            dao.upsertAccounts(listOf(account))

            val days = daysUntil(a.consentRenewalDue)
            if (days != null && days <= 10) {
                warnings += "${a.institution}: reconnect within ${maxOf(days, 0)} days"
            }
            if (conn != null && conn.status in setOf("expired", "suspended", "error", "rejected")) {
                warnings += "${a.institution}: connection ${conn.status}, data is no longer updating"
            }

            try {
                val latest = dao.latestBooking(a.id)?.let { LocalDate.parse(it).minusDays(7).toString() }
                val from = if (latest != null && latest > START_DATE) latest else START_DATE
                val dtos = api.transactions(a.id, from).filter { it.bookingDate >= START_DATE }
                val known = HashMap<String, TxnEntity>()
                dtos.map { it.id }.chunked(500).forEach { ids ->
                    dao.txnsByIds(ids).forEach { known[it.id] = it }
                }
                val merged = dtos.map { d ->
                    val prev = known[d.id]
                    TxnEntity(
                        id = d.id,
                        accountId = a.id,
                        bookingDate = d.bookingDate,
                        valueDate = d.valueDate,
                        amountMinor = Money.parseMinor(d.amount),
                        currency = d.currency,
                        description = d.description,
                        counterparty = d.counterparty,
                        merchantName = d.enrichment?.merchantName,
                        brandDomain = d.enrichment?.brandDomain,
                        providerCategory = d.enrichment?.category,
                        endToEndId = d.endToEndId,
                        categoryId = prev?.categoryId,
                        categoryManual = prev?.categoryManual ?: false,
                        transferId = prev?.transferId,
                        note = prev?.note,
                        reviewState = prev?.reviewState ?: 0,
                        categorySource = prev?.categorySource ?: 0,
                        ruleId = prev?.ruleId,
                        excluded = prev?.excluded ?: false,
                        displayName = prev?.displayName,
                    )
                }
                added += merged.count { it.id !in known }
                dao.upsertTxns(merged)
            } catch (e: Exception) {
                errors += "${a.institution} ${a.name}: ${e.message}"
            }
        }

        purgeBeforeStart()
        categorise(all = false)
        matchTransfers()
        return SyncSummary(added, errors, warnings)
    }

    /** Makes sure the built-in categories exist (with icons); returns them by lower-case name. */
    suspend fun ensureCategories(): Map<String, CategoryEntity> {
        val existing = dao.categoriesOnce().associateBy { it.name.lowercase() }.toMutableMap()
        for (d in DefaultCategories.all) {
            val have = existing[d.name.lowercase()]
            if (have == null) {
                val c = CategoryEntity(name = d.name, colour = d.colour, icon = d.icon)
                existing[d.name.lowercase()] = c.copy(id = dao.insertCategory(c))
            } else if (have.icon == null) {
                val c = have.copy(icon = d.icon)
                dao.upsertCategories(listOf(c))
                existing[d.name.lowercase()] = c
            }
        }
        return existing
    }

    /**
     * One-off tidy-up: older versions created a category for every label Endute sent. Fold those into
     * the concise built-in set. Categories you made yourself (no matching Endute label) are kept.
     */
    suspend fun migrateLegacyCategories() {
        val defaults = ensureCategories()
        val txns = dao.transactionsOnce()
        val providerNames = txns.mapNotNull { it.providerCategory?.trim()?.lowercase() }.toSet()
        db.withTransaction {
            for (c in dao.categoriesOnce()) {
                if (c.name.lowercase() in DefaultCategories.names) continue
                val auto = c.name.lowercase() in providerNames
                val mapped = DefaultCategories.map(c.name)
                val target = when {
                    mapped != null -> defaults[mapped.lowercase()]
                    auto -> defaults[DefaultCategories.OTHER.lowercase()]
                    else -> null
                } ?: continue
                dao.reassignTxnCategory(c.id, target.id)
                dao.reassignRuleCategory(c.id, target.id)
                dao.reparentChildren(c.id, null)
                dao.deleteBudget(c.id)
                dao.deleteCategory(c.id)
            }
        }
    }

    /**
     * Applies rules, merchant renames and the bank's own label. Each rule action comes from the
     * highest-priority rule that sets it. Categories you chose yourself are never changed.
     */
    suspend fun categorise(all: Boolean) {
        val rules = dao.rulesOnce()
        val defaults = ensureCategories()
        val aliases = dao.aliasesOnce().associate { it.rawKey to it.name }
        val every = dao.transactionsOnce()
        val counterpart = RuleEngine.counterparts(every)
        val changed = ArrayList<TxnEntity>()
        for (t in every) {
            var n = t
            if (n.displayName == null) aliases[Suggest.merchantKey(n)]?.let { n = n.copy(displayName = it) }
            if (all || t.categoryId == null) {
                val out = RuleEngine.resolve(rules, n, counterpart[n.id])
                out.renameTo?.let { if (n.displayName != it) n = n.copy(displayName = it) }
                if (out.exclude && !n.excluded) n = n.copy(excluded = true)
                if (out.markReviewed && n.reviewState == 0) n = n.copy(reviewState = 2)
                if (!n.categoryManual) {
                    var target: Long? = null
                    var source = 0
                    var ruleId: Long? = null
                    if (out.categoryRule != null) {
                        target = out.categoryRule.categoryId; source = 2; ruleId = out.categoryRule.id
                    } else if (!n.providerCategory.isNullOrBlank()) {
                        val name = DefaultCategories.map(n.providerCategory) ?: DefaultCategories.OTHER
                        target = defaults[name.lowercase()]?.id
                        source = if (target != null) 1 else 0
                    }
                    if (target != n.categoryId || source != n.categorySource || ruleId != n.ruleId) {
                        n = n.copy(categoryId = target, categorySource = source, ruleId = ruleId)
                    }
                }
            }
            if (n != t) changed += n
        }
        if (changed.isNotEmpty()) db.withTransaction { dao.upsertTxns(changed) }
    }

    suspend fun matchTransfers() {
        val since = LocalDate.now().minusDays(120).toString()
        val accounts = dao.accountsOnce().associateBy { it.id }
        val rejected = dao.rejectedOnce().map { it.outId to it.inId }.toSet()
        val result = TransferMatcher.match(dao.unlinkedSince(since), accounts, rejected)
        db.withTransaction {
            for (t in result.linked) {
                dao.setTransfer(listOf(t.out.id, t.into.id), UUID.randomUUID().toString())
            }
            dao.deletePendingCandidates()
            dao.insertCandidates(
                result.candidates.map { CandidateEntity(it.out.id, it.into.id, it.score) }
            )
        }
    }

    suspend fun confirmTransfer(c: CandidateEntity) = db.withTransaction {
        dao.setTransfer(listOf(c.outId, c.inId), UUID.randomUUID().toString())
        dao.deleteCandidatesFor(listOf(c.outId, c.inId))
    }

    suspend fun rejectTransfer(c: CandidateEntity) {
        dao.upsertCandidate(c.copy(rejected = true))
    }

    /** Undo a (wrong) link and remember that these two are not a pair. */
    suspend fun unlink(t: TxnEntity) {
        val tid = t.transferId ?: return
        val pair = dao.txnsByTransfer(tid)
        db.withTransaction {
            dao.setTransfer(pair.map { it.id }, null)
            val out = pair.firstOrNull { it.amountMinor < 0 }
            val inn = pair.firstOrNull { it.amountMinor > 0 }
            if (out != null && inn != null) {
                dao.upsertCandidate(CandidateEntity(out.id, inn.id, 0, rejected = true))
            }
        }
    }

    /** A "remember this" rule requested alongside a categorisation. */
    data class RuleDraft(val field: String, val pattern: String)

    /**
     * Saves everything from the transaction sheet in one step: the category (for this and any look-alike
     * transactions you ticked), an optional rule, exclude-from-spending and the note.
     */
    suspend fun saveEdit(
        t: TxnEntity,
        categoryId: Long?,
        similar: List<TxnEntity>,
        rule: RuleDraft?,
        exclude: Boolean,
        note: String,
        label: String,
    ): UndoSnapshot {
        val targets = listOf(t) + similar
        val before = dao.txnsByIds(targets.map { it.id })
        val cleanNote = note.trim().ifEmpty { null }
        val updated = before.map { s0 ->
            var n = s0
            if (categoryId != null) {
                n = n.copy(categoryId = categoryId, categoryManual = true, categorySource = 3, ruleId = null, reviewState = 0)
            }
            if (s0.id == t.id) n = n.copy(excluded = exclude, note = cleanNote)
            n
        }
        var createdRule: Long? = null
        db.withTransaction {
            dao.upsertTxns(updated)
            if (categoryId != null && rule != null && rule.pattern.isNotBlank()) {
                val top = dao.rulesOnce().minOfOrNull { it.priority } ?: 0
                createdRule = dao.upsertRule(
                    RuleEntity(priority = top - 1, field = rule.field, matchType = "contains", pattern = rule.pattern.trim(), categoryId = categoryId)
                )
            }
        }
        if (createdRule != null) categorise(all = true)
        return UndoSnapshot(label, before, listOfNotNull(createdRule))
    }

    /** Applies [change] to the given transactions and returns what is needed to undo it. */
    suspend fun mutate(ids: List<String>, label: String, change: (TxnEntity) -> TxnEntity): UndoSnapshot {
        val before = ids.chunked(500).flatMap { dao.txnsByIds(it) }
        dao.upsertTxns(before.map(change))
        return UndoSnapshot(label, before)
    }

    suspend fun undo(u: UndoSnapshot) {
        db.withTransaction {
            dao.upsertTxns(u.before)
            u.createdRuleIds.forEach { dao.deleteRule(it) }
            if (u.aliasKeys.isNotEmpty()) dao.deleteAliases(u.aliasKeys)
        }
        if (u.createdRuleIds.isNotEmpty()) categorise(all = true)
    }

    /** Renames (or merges into an existing name) every merchant currently shown as [oldName]. */
    suspend fun renameMerchant(oldName: String, newName: String): UndoSnapshot {
        val clean = newName.trim()
        val all = dao.transactionsOnce()
        val group = all.filter { Suggest.title(it) == oldName }
        val keys = group.map { Suggest.merchantKey(it) }.filter { it.isNotEmpty() }.distinct()
        val before = group
        db.withTransaction {
            dao.upsertTxns(group.map { it.copy(displayName = clean) })
            dao.upsertAliases(keys.map { MerchantAlias(it, clean) })
        }
        return UndoSnapshot("Renamed \"$oldName\" to \"$clean\"", before, aliasKeys = keys)
    }

    /** Saves a rule; optionally re-runs the rules over history straight away. */
    suspend fun saveRule(rule: RuleEntity, applyToPast: Boolean) {
        dao.upsertRule(rule)
        if (applyToPast) categorise(all = true)
    }

    suspend fun newCategory(name: String, parentId: Long? = null): Long {
        val clean = name.trim()
        dao.categoriesOnce().firstOrNull { it.name.equals(clean, ignoreCase = true) && it.parentId == parentId }
            ?.let { return it.id }
        return dao.insertCategory(CategoryEntity(name = clean, colour = colourFor(clean), parentId = parentId))
    }

    suspend fun renameCategory(c: CategoryEntity, name: String) {
        if (name.isNotBlank()) dao.upsertCategories(listOf(c.copy(name = name.trim())))
    }

    suspend fun recolourCategory(c: CategoryEntity, colour: Int) = dao.upsertCategories(listOf(c.copy(colour = colour)))

    /** Moves everything from [from] into [into] (or to Other) and removes [from]; its children move up. */
    suspend fun mergeCategory(from: CategoryEntity, into: Long?) {
        val target = into ?: ensureCategories()[DefaultCategories.OTHER.lowercase()]?.id
        if (target == null || target == from.id) return
        db.withTransaction {
            dao.reassignTxnCategory(from.id, target)
            dao.reassignRuleCategory(from.id, target)
            dao.reparentChildren(from.id, from.parentId)
            dao.deleteBudget(from.id)
            dao.deleteCategory(from.id)
        }
    }

    suspend fun reviewLater(t: TxnEntity, state: Int) = dao.setReviewState(t.id, state)
    suspend fun candidateLater(c: CandidateEntity, later: Boolean) = dao.setCandidateLater(c.outId, c.inId, later)
    suspend fun setNote(t: TxnEntity, note: String) = dao.setNote(t.id, note.trim().ifEmpty { null })

    /** Budgets at or beyond [threshold] of their limit this month, as "Name: £x of £y". */
    suspend fun budgetAlerts(threshold: Double = 0.9): List<Pair<Long, String>> {
        val budgets = dao.budgetsOnce()
        if (budgets.isEmpty()) return emptyList()
        val cats = dao.categoriesOnce()
        val included = dao.accountsOnce().filter { it.included }.map { it.id }.toSet()
        val month = LocalDate.now().toString().substring(0, 7)
        val txns = dao.transactionsOnce().filter {
            it.bookingDate.startsWith(month) && it.transferId == null && it.accountId in included && it.amountMinor < 0 && !it.excluded
        }
        return budgets.mapNotNull { b ->
            val ids = Insights.withChildren(b.categoryId, cats)
            val spent = txns.filter { it.categoryId in ids }.sumOf { -it.amountMinor }
            if (b.limitMinor > 0 && spent >= b.limitMinor * threshold) {
                val name = cats.firstOrNull { it.id == b.categoryId }?.name ?: return@mapNotNull null
                b.categoryId to "$name: £%.2f of £%.2f".format(spent / 100.0, b.limitMinor / 100.0)
            } else null
        }
    }
}
