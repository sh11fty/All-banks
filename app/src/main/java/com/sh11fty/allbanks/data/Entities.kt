package com.sh11fty.allbanks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Amounts are stored in minor units (pence). */
@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val institution: String,
    val name: String,
    val currency: String,
    val accountType: String?,
    val maskedId: String?,
    val balanceMinor: Long?,
    val balanceFetchedAt: String?,
    val lastSyncedAt: String?,
    val consentRenewalDue: String?,
    val connectionStatus: String?,
    val included: Boolean = true,
    val sandbox: Boolean = false,
)

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("bookingDate"), Index("transferId")],
)
data class TxnEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val bookingDate: String,
    val valueDate: String?,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val counterparty: String?,
    val merchantName: String?,
    val brandDomain: String?,
    val providerCategory: String?,
    val endToEndId: String?,
    val categoryId: Long? = null,
    val categoryManual: Boolean = false,
    val transferId: String? = null,
    val note: String? = null,
    /** 0 = normal, 1 = check later, 2 = dismissed from Review. */
    @ColumnInfo(defaultValue = "0") val reviewState: Int = 0,
    /** Where the category came from: 0 none, 1 the bank's label, 2 a rule, 3 you. */
    @ColumnInfo(defaultValue = "0") val categorySource: Int = 0,
    /** The rule that supplied the category when [categorySource] is 2. */
    val ruleId: Long? = null,
    /** Left out of spending totals, budgets and charts (refunds, one-offs). */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    /** Cleaned-up merchant name (from a rule or a merchant rename). */
    val displayName: String? = null,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colour: Int,
    /** Optional parent: this category is a subsection of it. */
    val parentId: Long? = null,
    /** Emoji shown in the category badge. */
    val icon: String? = null,
)

/**
 * field: "description" | "counterparty" | "merchant"
 * matchType: "contains" | "equals" | "regex"
 * direction: 0 any, 1 money in, -1 money out
 * Lower priority number wins.
 */
@Serializable
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val priority: Int,
    val field: String,
    val matchType: String,
    val pattern: String,
    val direction: Int = 0,
    val minAbsMinor: Long? = null,
    val maxAbsMinor: Long? = null,
    /** Money leaves this account. */
    val accountId: String? = null,
    /** Money arrives in this account. */
    val toAccountId: String? = null,
    /** [NO_CATEGORY] means the rule leaves the category alone (e.g. it only renames). */
    val categoryId: Long,
    val enabled: Boolean = true,
    val renameTo: String? = null,
    @ColumnInfo(defaultValue = "0") val excludeFromSpending: Boolean = false,
    @ColumnInfo(defaultValue = "0") val markReviewed: Boolean = false,
)

const val NO_CATEGORY = 0L

/** Merchant rename/merge memory: raw merchant key -> the name you want to see. */
@Serializable
@Entity(tableName = "merchant_aliases")
data class MerchantAlias(
    @PrimaryKey val rawKey: String,
    val name: String,
)

/** A possible internal transfer awaiting confirmation (or rejected by the user). */
@Entity(tableName = "candidates", primaryKeys = ["outId", "inId"])
data class CandidateEntity(
    val outId: String,
    val inId: String,
    val score: Int,
    val rejected: Boolean = false,
    @ColumnInfo(defaultValue = "0") val later: Boolean = false,
)

/** Optional monthly spending limit for a category (includes its subcategories). */
@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    val limitMinor: Long,
)
