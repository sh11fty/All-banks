package com.sh11fty.allbanks

import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.Money
import com.sh11fty.allbanks.domain.RuleEngine
import com.sh11fty.allbanks.domain.TransferMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private fun acc(id: String, inst: String, masked: String? = null) = AccountEntity(
        id = id, institution = inst, name = "$inst Account", currency = "GBP",
        accountType = "current", maskedId = masked, balanceMinor = null, balanceFetchedAt = null,
        lastSyncedAt = null, consentRenewalDue = null, connectionStatus = null,
    )

    private fun tx(id: String, account: String, date: String, minor: Long, desc: String = "PAYMENT") =
        TxnEntity(
            id = id, accountId = account, bookingDate = date, valueDate = null, amountMinor = minor,
            currency = "GBP", description = desc, counterparty = null, merchantName = null,
            brandDomain = null, providerCategory = null, endToEndId = null,
        )

    private val accounts = mapOf(
        "a" to acc("a", "Monzo"),
        "b" to acc("b", "Barclays"),
        "c" to acc("c", "Barclays"),
    )

    @Test
    fun parsesMoney() {
        assertEquals(-1280L, Money.parseMinor("-12.80"))
        assertEquals(128456L, Money.parseMinor("1284.5600"))
        assertEquals(185000L, Money.parseMinor("1850.00"))
    }

    @Test
    fun autoLinksWithInstitutionHint() {
        val r = TransferMatcher.match(
            listOf(
                tx("1", "a", "2026-06-01", -5000, "TRANSFER TO BARCLAYS"),
                tx("2", "b", "2026-06-01", 5000, "FROM MONZO"),
            ),
            accounts,
        )
        assertEquals(1, r.linked.size)
        assertTrue(r.candidates.isEmpty())
    }

    @Test
    fun queuesWhenNoTextHint() {
        val r = TransferMatcher.match(
            listOf(tx("1", "a", "2026-06-01", -5000), tx("2", "b", "2026-06-01", 5000, "CREDIT")),
            accounts,
        )
        assertTrue(r.linked.isEmpty())
        assertEquals(1, r.candidates.size)
    }

    @Test
    fun ignoresOutsideWindowAndDifferentAmounts() {
        val r = TransferMatcher.match(
            listOf(
                tx("1", "a", "2026-06-01", -5000, "TRANSFER TO BARCLAYS"),
                tx("2", "b", "2026-06-06", 5000),
                tx("3", "b", "2026-06-01", 4999),
            ),
            accounts,
        )
        assertTrue(r.linked.isEmpty())
        assertTrue(r.candidates.isEmpty())
    }

    @Test
    fun ambiguousPairsAreNotAutoLinked() {
        val r = TransferMatcher.match(
            listOf(
                tx("1", "a", "2026-06-01", -5000, "TRANSFER TO BARCLAYS"),
                tx("2", "b", "2026-06-01", 5000),
                tx("3", "c", "2026-06-01", 5000),
            ),
            accounts,
        )
        assertTrue(r.linked.isEmpty())
        assertEquals(2, r.candidates.size)
    }

    @Test
    fun rejectedPairsAreSkipped() {
        val r = TransferMatcher.match(
            listOf(
                tx("1", "a", "2026-06-01", -5000, "TRANSFER TO BARCLAYS"),
                tx("2", "b", "2026-06-01", 5000),
            ),
            accounts,
            rejected = setOf("1" to "2"),
        )
        assertTrue(r.linked.isEmpty() && r.candidates.isEmpty())
    }

    @Test
    fun rulesMatchByFieldDirectionAndRegex() {
        val t = tx("1", "a", "2026-06-01", -1280, "TESCO STORES 3421 LONDON GB")
        val contains = RuleEntity(priority = 1, field = "description", matchType = "contains",
            pattern = "tesco", categoryId = 1)
        val regex = RuleEntity(priority = 2, field = "description", matchType = "regex",
            pattern = "^TESCO.*GB$", categoryId = 2)
        val incomeOnly = contains.copy(direction = 1)
        assertTrue(RuleEngine.matches(contains, t))
        assertTrue(RuleEngine.matches(regex, t))
        assertFalse(RuleEngine.matches(incomeOnly, t))
        assertEquals(1L, RuleEngine.firstMatch(listOf(regex, contains), t)?.categoryId)
    }
}
