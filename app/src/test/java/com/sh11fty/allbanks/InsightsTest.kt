package com.sh11fty.allbanks

import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.DefaultCategories
import com.sh11fty.allbanks.domain.Insights
import com.sh11fty.allbanks.domain.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private fun tx(id: String, account: String, date: String, minor: Long, desc: String = "PAYMENT", transfer: String? = null) =
        TxnEntity(
            id = id, accountId = account, bookingDate = date, valueDate = null, amountMinor = minor,
            currency = "GBP", description = desc, counterparty = null, merchantName = null, brandDomain = null,
            providerCategory = null, endToEndId = null, transferId = transfer,
        )

    @Test
    fun providerCategoriesCollapseIntoTheConciseSet() {
        assertEquals("Bills & utilities", DefaultCategories.map("Electricity"))
        assertEquals("Bills & utilities", DefaultCategories.map("Broadband"))
        assertEquals("Groceries", DefaultCategories.map("Supermarkets"))
        assertEquals("Dining & takeaways", DefaultCategories.map("Restaurants"))
        assertEquals("Bank charges & fees", DefaultCategories.map("Overdraft fee"))
        assertEquals("Groceries", DefaultCategories.map("groceries"))
        assertNull(DefaultCategories.map("Zzz unknown"))
        assertNull(DefaultCategories.map(" "))
    }

    @Test
    fun ruleFromAndToBanks() {
        val out = tx("o", "monzo", "2026-10-01", -5000, "TO SAVINGS", transfer = "t1")
        val into = tx("i", "chase", "2026-10-01", 5000, "FROM MONZO", transfer = "t1")
        val cp = RuleEngine.counterparts(listOf(out, into))
        val rule = RuleEntity(priority = 0, field = "description", matchType = "contains", pattern = "o",
            accountId = "monzo", toAccountId = "chase", categoryId = 1)
        assertTrue(RuleEngine.matches(rule, out, cp[out.id]))
        assertTrue(RuleEngine.matches(rule, into, cp[into.id]))
        assertFalse(RuleEngine.matches(rule.copy(accountId = "chase"), out, cp[out.id]))
        // A plain payment out has no destination account, so a "to" rule never matches it.
        val plain = tx("p", "monzo", "2026-10-02", -1000, "TESCO")
        assertFalse(RuleEngine.matches(rule.copy(pattern = "tesco"), plain, null))
        assertTrue(RuleEngine.matches(rule.copy(pattern = "tesco", toAccountId = null), plain, null))
    }

    @Test
    fun detectsMonthlyRecurring() {
        val today = LocalDate.parse("2026-10-15")
        val netflix = (0..5).map { tx("n$it", "a", today.minusDays(10L + it * 30L).toString(), -1099, "NETFLIX.COM") }
        val random = listOf(tx("r1", "a", "2026-10-01", -300, "CAFE"), tx("r2", "a", "2026-09-03", -870, "CAFE"))
        val rec = Insights.recurring(netflix + random, today)
        assertEquals(1, rec.size)
        assertEquals(30, rec[0].everyDays)
        assertEquals(1099, rec[0].amountMinor)
    }

    @Test
    fun balanceTrendWorksBackwards() {
        val today = LocalDate.parse("2026-10-15")
        val acc = com.sh11fty.allbanks.data.AccountEntity(
            id = "a", institution = "X", name = "Main", currency = "GBP", accountType = null, maskedId = null,
            balanceMinor = 10_000, balanceFetchedAt = null, lastSyncedAt = null, consentRenewalDue = null, connectionStatus = null,
        )
        val trend = Insights.balanceTrend(listOf(acc), listOf(tx("1", "a", "2026-10-15", -2000)), 3, today)
        assertEquals(listOf(12_000L, 12_000L, 10_000L), trend)
    }
}
