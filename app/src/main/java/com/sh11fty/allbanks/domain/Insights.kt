package com.sh11fty.allbanks.domain

import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.TxnEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class Recurring(
    val key: String,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val everyDays: Int,
    val lastDate: LocalDate,
    val next: LocalDate,
    val count: Int,
) {
    val monthlyMinor: Long get() = Math.round(amountMinor * 30.4 / everyDays)
}

object Insights {
    private fun key(t: TxnEntity): String = Suggest.merchantKey(t)

    /** Outgoing, non-transfer payments that repeat at a steady interval with a steady amount. */
    fun recurring(txns: List<TxnEntity>, today: LocalDate = LocalDate.now()): List<Recurring> {
        val groups = txns.filter { it.amountMinor < 0 && it.transferId == null }
            .groupBy { key(it) to it.currency }
            .filter { it.key.first.length >= 3 && it.value.size >= 3 }
        val found = ArrayList<Recurring>()
        for ((k, list) in groups) {
            val sorted = list.sortedBy { it.bookingDate }
            val dates = sorted.map { LocalDate.parse(it.bookingDate) }
            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.filter { it > 0 }
            if (gaps.size < 2) continue
            val median = gaps.sorted()[gaps.size / 2]
            val every = when (median) {
                in 6..8 -> 7
                in 13..16 -> 14
                in 26..35 -> 30
                in 85..95 -> 91
                in 360..370 -> 365
                else -> continue
            }
            val tolerance = maxOf(2, every / 8)
            if (gaps.count { abs(it - every) <= tolerance } < gaps.size * 0.6) continue
            val amounts = sorted.map { -it.amountMinor }.sorted()
            val med = amounts[amounts.size / 2]
            if (amounts.count { abs(it - med) <= med / 10 + 50 } < amounts.size * 0.6) continue
            val last = dates.last()
            var next = last.plusDays(every.toLong())
            if (next.isBefore(today.minusDays(every.toLong() * 2))) continue // lapsed
            while (next.isBefore(today.minusDays(3))) next = next.plusDays(every.toLong())
            val latest = sorted.last()
            found += Recurring(
                key = k.first, title = latest.merchantName ?: latest.counterparty ?: latest.description,
                amountMinor = -latest.amountMinor.coerceAtMost(0), currency = k.second,
                everyDays = every, lastDate = last, next = next, count = sorted.size,
            )
        }
        return found.sortedBy { it.next }
    }

    /** Category plus all its descendants. */
    fun withChildren(id: Long, cats: List<CategoryEntity>): Set<Long> {
        val out = HashSet<Long>()
        fun add(x: Long) { if (out.add(x)) cats.filter { it.parentId == x }.forEach { add(it.id) } }
        add(id)
        return out
    }

    /** Spend (positive minor units) per top-level category; subcategories roll up into their parent. */
    fun spendByTopCategory(txns: List<TxnEntity>, cats: List<CategoryEntity>): Map<Long?, Long> {
        val byId = cats.associateBy { it.id }
        fun top(id: Long?): Long? {
            var c = id?.let(byId::get) ?: return null
            var guard = 0
            while (c.parentId != null && guard++ < 10) c = byId[c.parentId] ?: break
            return c.id
        }
        return txns.filter { it.amountMinor < 0 && !it.excluded }.groupBy { top(it.categoryId) }
            .mapValues { (_, l) -> l.sumOf { -it.amountMinor } }
    }

    /**
     * Estimated total balance per day for the last [days] days, working back from today's booked
     * balances through the stored transactions (assumes the history is complete).
     */
    fun balanceTrend(
        accounts: List<AccountEntity>,
        txns: List<TxnEntity>,
        days: Int,
        today: LocalDate = LocalDate.now(),
    ): List<Long> {
        val ids = accounts.map { it.id }.toSet()
        val perDay = txns.filter { it.accountId in ids }.groupBy { it.bookingDate }
            .mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        var bal = accounts.sumOf { it.balanceMinor ?: 0L }
        val out = ArrayList<Long>()
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            out += bal
            bal -= perDay[d.toString()] ?: 0L
        }
        return out.reversed()
    }

    /** Spend (positive) for each month, oldest first. Internal transfers and excluded items are ignored. */
    fun monthlySpend(txns: List<TxnEntity>, months: List<YearMonth>): List<Long> {
        val spend = txns.filter { it.amountMinor < 0 && it.transferId == null && !it.excluded }
            .groupBy { it.bookingDate.take(7) }
            .mapValues { (_, l) -> l.sumOf { -it.amountMinor } }
        return months.map { spend[it.toString()] ?: 0L }
    }

    data class MerchantTotal(val name: String, val spent: Long, val count: Int, val topCategory: Long?)

    /** Spend per displayed merchant name, biggest first. */
    fun merchants(txns: List<TxnEntity>): List<MerchantTotal> =
        txns.filter { it.transferId == null && !it.excluded }
            .groupBy { Suggest.title(it) }
            .map { (name, l) ->
                MerchantTotal(
                    name, l.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }, l.size,
                    l.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                )
            }
            .sortedByDescending { it.spent }

    data class Change(val categoryId: Long?, val now: Long, val before: Long) { val delta get() = now - before }

    /** Biggest category swings between two sets of transactions, largest absolute change first. */
    fun changes(now: List<TxnEntity>, before: List<TxnEntity>, cats: List<CategoryEntity>, n: Int = 3): List<Change> {
        val a = spendByTopCategory(now, cats)
        val b = spendByTopCategory(before, cats)
        return (a.keys + b.keys).map { Change(it, a[it] ?: 0L, b[it] ?: 0L) }
            .filter { kotlin.math.abs(it.delta) >= 1000 }
            .sortedByDescending { kotlin.math.abs(it.delta) }.take(n)
    }
}
