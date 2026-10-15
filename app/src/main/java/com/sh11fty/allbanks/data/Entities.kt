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
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colour: Int,
    /** Optional parent: this category is a subsection of it. */
    val parentId: Long? = null,
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
    val categoryId: Long,
    val enabled: Boolean = true,
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
