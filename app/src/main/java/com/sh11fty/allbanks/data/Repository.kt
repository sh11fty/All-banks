package com.sh11fty.allbanks.data

import android.graphics.Color
import androidx.room.withTransaction
import com.sh11fty.allbanks.domain.Money
import com.sh11fty.allbanks.domain.RuleEngine
import com.sh11fty.allbanks.domain.TransferMatcher
import com.sh11fty.allbanks.net.EnduteApi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

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
                val from = dao.latestBooking(a.id)?.let { LocalDate.parse(it).minusDays(7).toString() }
                val dtos = api.transactions(a.id, from)
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
                    )
                }
                added += merged.count { it.id !in known }
                dao.upsertTxns(merged)
            } catch (e: Exception) {
                errors += "${a.institution} ${a.name}: ${e.message}"
            }
        }

        categorise(all = false)
        matchTransfers()
        return SyncSummary(added, errors, warnings)
    }

    /** Rule match first, then Endute's own enrichment category. Manual choices are never touched. */
    suspend fun categorise(all: Boolean) {
        val rules = dao.rulesOnce()
        val cats = dao.categoriesOnce().associateBy { it.name.lowercase() }.toMutableMap()
        val txns = dao.transactionsOnce().filter { !it.categoryManual && (all || it.categoryId == null) }
        db.withTransaction {
            for (t in txns) {
                var target = RuleEngine.firstMatch(rules, t)?.categoryId
                if (target == null) {
                    val name = t.providerCategory?.trim().orEmpty()
                    if (name.isNotEmpty()) {
                        target = cats.getOrPut(name.lowercase()) {
                            val c = CategoryEntity(name = name, colour = colourFor(name))
                            c.copy(id = dao.insertCategory(c))
                        }.id
                    }
                }
                if (target != t.categoryId) dao.setAutoCategory(t.id, target)
            }
        }
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

    suspend fun categoriseOne(t: TxnEntity, categoryId: Long?, rulePattern: String?) {
        dao.setCategory(t.id, categoryId, manual = true)
        if (categoryId != null && !rulePattern.isNullOrBlank()) {
            val field = when {
                rulePattern == t.merchantName -> "merchant"
                rulePattern == t.counterparty -> "counterparty"
                else -> "description"
            }
            val top = dao.rulesOnce().minOfOrNull { it.priority } ?: 0
            dao.upsertRule(
                RuleEntity(
                    priority = top - 1,
                    field = field,
                    matchType = "contains",
                    pattern = rulePattern.trim(),
                    categoryId = categoryId,
                )
            )
            categorise(all = true)
        }
    }

    suspend fun newCategory(name: String): Long {
        val clean = name.trim()
        dao.categoriesOnce().firstOrNull { it.name.equals(clean, ignoreCase = true) }
            ?.let { return it.id }
        return dao.insertCategory(CategoryEntity(name = clean, colour = colourFor(clean)))
    }
}
