package com.sh11fty.allbanks.domain

import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.NO_CATEGORY
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object Money {
    /** "-12.80" -> -1280, "1284.5600" -> 128456. */
    fun parseMinor(value: String): Long =
        BigDecimal(value.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

data class TransferCandidate(val out: TxnEntity, val into: TxnEntity, val score: Int)

data class MatchResult(
    val linked: List<TransferCandidate>,
    val candidates: List<TransferCandidate>,
)

/**
 * Pairs a debit in one of your accounts with an equal credit in another within a few days.
 *
 * Endute's feed has no counterparty sort code / account number, so the signals are:
 * same amount + date proximity + text hints (institution, account name, last four digits,
 * end-to-end id, "transfer" style wording). A pair is only auto-linked when it clears
 * [AUTO] and has no close rival; everything else plausible goes to the confirm queue.
 */
object TransferMatcher {
    const val AUTO = 70
    const val QUEUE = 30
    const val WINDOW_DAYS = 3L

    private val keywords = Regex(
        "transfer|\\btfr\\b|own account|internal|\\bpot\\b|savings|credit card|payment thank you",
        RegexOption.IGNORE_CASE,
    )

    fun score(out: TxnEntity, into: TxnEntity, accounts: Map<String, AccountEntity>): Int {
        var s = 30
        val days = abs(
            ChronoUnit.DAYS.between(LocalDate.parse(out.bookingDate), LocalDate.parse(into.bookingDate))
        )
        s += when (days) {
            0L -> 20
            1L -> 10
            else -> 0
        }
        val outText = "${out.description} ${out.counterparty.orEmpty()}".lowercase()
        val inText = "${into.description} ${into.counterparty.orEmpty()}".lowercase()
        val inAcc = accounts[into.accountId]
        val outAcc = accounts[out.accountId]
        if (mentions(outText, inAcc) || mentions(inText, outAcc)) s += 30
        val e2e = out.endToEndId
        if (!e2e.isNullOrBlank() && e2e == into.endToEndId) s += 15
        if (keywords.containsMatchIn(outText) || keywords.containsMatchIn(inText)) s += 10
        return s.coerceAtMost(100)
    }

    private fun mentions(text: String, acc: AccountEntity?): Boolean {
        if (acc == null) return false
        val inst = acc.institution.lowercase()
        val name = acc.name.lowercase()
        val last4 = acc.maskedId
        return (inst.length >= 3 && text.contains(inst)) ||
            (name.length >= 4 && text.contains(name)) ||
            (!last4.isNullOrBlank() && last4.length >= 4 && text.contains(last4))
    }

    fun match(
        txns: List<TxnEntity>,
        accounts: Map<String, AccountEntity>,
        rejected: Set<Pair<String, String>> = emptySet(),
    ): MatchResult {
        val outs = txns.filter { it.amountMinor < 0 && it.accountId in accounts }
        val ins = txns.filter { it.amountMinor > 0 && it.accountId in accounts }
            .groupBy { it.amountMinor to it.currency }

        val scored = ArrayList<TransferCandidate>()
        for (o in outs) {
            for (i in ins[(-o.amountMinor) to o.currency].orEmpty()) {
                if (i.accountId == o.accountId) continue
                if ((o.id to i.id) in rejected) continue
                val days = abs(
                    ChronoUnit.DAYS.between(LocalDate.parse(o.bookingDate), LocalDate.parse(i.bookingDate))
                )
                if (days > WINDOW_DAYS) continue
                val s = score(o, i, accounts)
                if (s >= QUEUE) scored += TransferCandidate(o, i, s)
            }
        }

        val used = HashSet<String>()
        val linked = ArrayList<TransferCandidate>()
        val queued = ArrayList<TransferCandidate>()
        for (t in scored.sortedByDescending { it.score }) {
            if (t.out.id in used || t.into.id in used) continue
            val rivals = scored.count {
                it !== t &&
                    (it.out.id == t.out.id || it.into.id == t.into.id) &&
                    it.score >= t.score - 15
            }
            if (t.score >= AUTO && rivals == 0) {
                linked += t
                used += t.out.id
                used += t.into.id
            } else {
                queued += t
            }
        }
        return MatchResult(linked, queued.filter { it.out.id !in used && it.into.id !in used })
    }
}

object RuleEngine {
    /** transferId -> legs, so each leg can see the account on the other side of its transfer. */
    fun counterparts(txns: List<TxnEntity>): Map<String, String> {
        val out = HashMap<String, String>()
        txns.filter { it.transferId != null }.groupBy { it.transferId }.values.forEach { legs ->
            if (legs.size == 2) {
                out[legs[0].id] = legs[1].accountId
                out[legs[1].id] = legs[0].accountId
            }
        }
        return out
    }

    /** [counterpartAccount] is the account on the other leg when [t] is part of a linked transfer. */
    fun matches(rule: RuleEntity, t: TxnEntity, counterpartAccount: String? = null): Boolean {
        if (!rule.enabled) return false
        when (rule.direction) {
            1 -> if (t.amountMinor <= 0) return false
            -1 -> if (t.amountMinor >= 0) return false
        }
        val abs = abs(t.amountMinor)
        if (rule.minAbsMinor != null && abs < rule.minAbsMinor) return false
        if (rule.maxAbsMinor != null && abs > rule.maxAbsMinor) return false
        if (rule.accountId != null || rule.toAccountId != null) {
            val outgoing = t.amountMinor < 0
            val from = if (outgoing) t.accountId else counterpartAccount
            val to = if (outgoing) counterpartAccount else t.accountId
            if (rule.accountId != null && rule.accountId != from) return false
            if (rule.toAccountId != null && rule.toAccountId != to) return false
        }
        val text = when (rule.field) {
            "counterparty" -> t.counterparty
            "merchant" -> t.merchantName
            else -> t.description
        }.orEmpty()
        if (rule.pattern.isBlank()) return false
        return when (rule.matchType) {
            "equals" -> text.equals(rule.pattern, ignoreCase = true)
            "regex" -> runCatching {
                Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
            }.getOrDefault(false)
            else -> text.contains(rule.pattern, ignoreCase = true)
        }
    }

    fun firstMatch(rules: List<RuleEntity>, t: TxnEntity, counterpartAccount: String? = null): RuleEntity? =
        rules.sortedBy { it.priority }.firstOrNull { matches(it, t, counterpartAccount) }

    /** All matching rules, highest priority first. */
    fun allMatches(rules: List<RuleEntity>, t: TxnEntity, counterpartAccount: String? = null): List<RuleEntity> =
        rules.sortedWith(compareBy({ it.priority }, { it.id })).filter { matches(it, t, counterpartAccount) }

    /** What the matching rules decide together: each action comes from the highest-priority rule that sets it. */
    data class Outcome(
        val categoryRule: RuleEntity?,
        val renameTo: String?,
        val exclude: Boolean,
        val markReviewed: Boolean,
        val matched: List<RuleEntity>,
    )

    fun resolve(rules: List<RuleEntity>, t: TxnEntity, counterpartAccount: String? = null): Outcome {
        val m = allMatches(rules, t, counterpartAccount)
        return Outcome(
            categoryRule = m.firstOrNull { it.categoryId != NO_CATEGORY },
            renameTo = m.firstNotNullOfOrNull { it.renameTo?.takeIf { n -> n.isNotBlank() } },
            exclude = m.any { it.excludeFromSpending },
            markReviewed = m.any { it.markReviewed },
            matched = m,
        )
    }
}
