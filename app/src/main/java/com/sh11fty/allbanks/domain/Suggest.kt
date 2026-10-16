package com.sh11fty.allbanks.domain

import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.NO_CATEGORY
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity

/** Merchant naming, "what do you usually call this?" suggestions and look-alike matching. */
object Suggest {
    /** Stable key for "the same merchant": ignores case, digits and punctuation. */
    fun merchantKey(t: TxnEntity): String =
        (t.merchantName ?: t.counterparty ?: t.description).lowercase()
            .replace(Regex("[0-9]+"), "").replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The name to show: your rename, else the bank's merchant, counterparty or description. */
    fun title(t: TxnEntity): String = t.displayName ?: t.merchantName ?: t.counterparty ?: t.description

    /** Text a "remember" rule should match, and the field it lives in. */
    fun ruleTarget(t: TxnEntity): Pair<String, String> = when {
        !t.merchantName.isNullOrBlank() -> "merchant" to t.merchantName
        !t.counterparty.isNullOrBlank() -> "counterparty" to t.counterparty
        else -> "description" to t.description
    }

    class Index(val byKey: Map<String, Map<Long, Int>>, val byWord: Map<String, Map<Long, Int>>)

    private fun firstWord(key: String): String? = key.substringBefore(' ').takeIf { it.length >= 4 }

    /** Counts how often each category was used per merchant, weighting your own choices highest. */
    fun index(all: List<TxnEntity>): Index {
        val key = HashMap<String, HashMap<Long, Int>>()
        val word = HashMap<String, HashMap<Long, Int>>()
        for (t in all) {
            val cat = t.categoryId ?: continue
            if (t.transferId != null) continue
            val weight = when (t.categorySource) { 3 -> 4; 2 -> 3; else -> 1 }
            val k = merchantKey(t)
            if (k.isEmpty()) continue
            key.getOrPut(k) { HashMap() }.merge(cat, weight, Int::plus)
            firstWord(k)?.let { word.getOrPut(it) { HashMap() }.merge(cat, weight, Int::plus) }
        }
        return Index(key, word)
    }

    /** Most likely categories for [t], best first. [bankDefault] is the mapped bank label, if any. */
    fun suggestions(t: TxnEntity, index: Index, bankDefault: Long?, max: Int = 3): List<Long> {
        val k = merchantKey(t)
        val out = LinkedHashSet<Long>()
        index.byKey[k]?.entries?.sortedByDescending { it.value }?.forEach { out += it.key }
        if (out.size < max) {
            firstWord(k)?.let { w -> index.byWord[w]?.entries?.sortedByDescending { it.value }?.forEach { out += it.key } }
        }
        if (bankDefault != null) out += bankDefault
        return out.take(max)
    }

    /** Other transactions from the same merchant that [predicate] says are worth changing too. */
    fun similar(t: TxnEntity, all: List<TxnEntity>, predicate: (TxnEntity) -> Boolean): List<TxnEntity> {
        val k = merchantKey(t)
        if (k.isEmpty()) return emptyList()
        return all.filter { it.id != t.id && it.transferId == null && merchantKey(it) == k && predicate(it) }
    }

    /** Needs a decision: no category, or only the bank's vague "Other" guess. */
    fun isUnsorted(t: TxnEntity, otherId: Long?): Boolean =
        t.transferId == null && t.reviewState == 0 &&
            (t.categoryId == null || (t.categorySource == 1 && t.categoryId == otherId))

    /** Rule suggestions: merchants you've categorised the same way at least [min] times without a rule covering them. */
    data class RuleIdea(val key: String, val label: String, val field: String, val pattern: String, val categoryId: Long, val count: Int)

    fun ruleIdeas(all: List<TxnEntity>, rules: List<RuleEntity>, dismissed: Set<String>, min: Int = 3): List<RuleIdea> {
        val cp = RuleEngine.counterparts(all)
        return all.filter { it.categorySource == 3 && it.categoryId != null && it.transferId == null }
            .groupBy { merchantKey(it) }
            .filter { it.key.length >= 3 && it.key !in dismissed }
            .mapNotNull { (key, list) ->
                val top = list.groupingBy { it.categoryId!! }.eachCount().maxByOrNull { it.value } ?: return@mapNotNull null
                if (top.value < min || top.value < list.size * 0.8) return@mapNotNull null
                val latest = list.maxByOrNull { it.bookingDate }!!
                if (rules.any { it.enabled && it.categoryId != NO_CATEGORY && RuleEngine.matches(it, latest, cp[latest.id]) }) return@mapNotNull null
                val (field, pattern) = ruleTarget(latest)
                RuleIdea(key, title(latest), field, pattern, top.key, top.value)
            }
            .sortedByDescending { it.count }
    }

    fun categoryLabel(c: CategoryEntity?, byId: Map<Long, CategoryEntity>): String =
        c?.let { cat -> cat.parentId?.let(byId::get)?.let { "${it.name} › ${cat.name}" } ?: cat.name } ?: "Uncategorised"
}
