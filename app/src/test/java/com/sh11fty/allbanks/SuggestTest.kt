package com.sh11fty.allbanks

import com.sh11fty.allbanks.data.NO_CATEGORY
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.RuleEngine
import com.sh11fty.allbanks.domain.Suggest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {
    private fun tx(
        id: String, desc: String, merchant: String? = null, cat: Long? = null, source: Int = 0,
        manual: Boolean = false, minor: Long = -1000, date: String = "2026-10-01",
    ) = TxnEntity(
        id = id, accountId = "a", bookingDate = date, valueDate = null, amountMinor = minor, currency = "GBP",
        description = desc, counterparty = null, merchantName = merchant, brandDomain = null, providerCategory = null,
        endToEndId = null, categoryId = cat, categoryManual = manual, categorySource = source,
    )

    @Test
    fun merchantKeyIgnoresDigitsAndCase() {
        assertEquals(Suggest.merchantKey(tx("1", "TESCO STORES 4421")), Suggest.merchantKey(tx("2", "Tesco Stores 9")))
    }

    @Test
    fun suggestionsPreferWhatYouChoseForThatMerchant() {
        val history = listOf(
            tx("1", "TESCO STORES 1", cat = 1, source = 3, manual = true),
            tx("2", "TESCO STORES 2", cat = 1, source = 3, manual = true),
            tx("3", "TESCO STORES 3", cat = 9, source = 1),
        )
        val idx = Suggest.index(history)
        val s = Suggest.suggestions(tx("4", "TESCO STORES 77"), idx, bankDefault = 5)
        assertEquals(1L, s.first())
        assertTrue(5L in s)
    }

    @Test
    fun suggestionsFallBackToFirstWord() {
        val idx = Suggest.index(listOf(tx("1", "Netflix.com Amsterdam", cat = 7, source = 3, manual = true)))
        assertEquals(listOf(7L), Suggest.suggestions(tx("2", "Netflix payment"), idx, null))
    }

    @Test
    fun similarSkipsTransfersAndManualChoices() {
        val a = tx("a", "COSTA 1")
        val others = listOf(tx("b", "COSTA 2"), tx("c", "COSTA 3", cat = 2, manual = true, source = 3), tx("d", "OTHER"))
        val sim = Suggest.similar(a, others + a) { !it.categoryManual }
        assertEquals(listOf("b"), sim.map { it.id })
    }

    @Test
    fun otherFromTheBankStillNeedsALook() {
        assertTrue(Suggest.isUnsorted(tx("1", "x", cat = 14, source = 1), otherId = 14))
        assertFalse(Suggest.isUnsorted(tx("2", "x", cat = 14, source = 3, manual = true), otherId = 14))
        assertTrue(Suggest.isUnsorted(tx("3", "x"), otherId = 14))
        assertFalse(Suggest.isUnsorted(tx("4", "x", cat = 2, source = 1), otherId = 14))
    }

    @Test
    fun rulesCombineActionsFromAllMatches() {
        val t = tx("1", "AMZN MKTP UK 123")
        val rename = RuleEntity(id = 1, priority = 5, field = "description", matchType = "contains", pattern = "amzn", categoryId = NO_CATEGORY, renameTo = "Amazon")
        val cat = RuleEntity(id = 2, priority = 6, field = "description", matchType = "contains", pattern = "mktp", categoryId = 3)
        val excl = RuleEntity(id = 3, priority = 7, field = "description", matchType = "contains", pattern = "uk", categoryId = NO_CATEGORY, excludeFromSpending = true)
        val out = RuleEngine.resolve(listOf(cat, excl, rename), t)
        assertEquals(2L, out.categoryRule?.id)
        assertEquals("Amazon", out.renameTo)
        assertTrue(out.exclude)
        assertEquals(3, out.matched.size)
    }

    @Test
    fun ruleIdeasAppearAfterThreeManualChoices() {
        val list = (1..3).map { tx("m$it", "GREGGS $it", cat = 4, source = 3, manual = true) }
        val ideas = Suggest.ruleIdeas(list, emptyList(), emptySet())
        assertEquals(1, ideas.size)
        assertEquals(4L, ideas[0].categoryId)
        // Covered by an existing rule, or dismissed: no suggestion.
        val rule = RuleEntity(priority = 0, field = "description", matchType = "contains", pattern = "greggs", categoryId = 4)
        assertTrue(Suggest.ruleIdeas(list, listOf(rule), emptySet()).isEmpty())
        assertTrue(Suggest.ruleIdeas(list, emptyList(), setOf(ideas[0].key)).isEmpty())
    }
}
