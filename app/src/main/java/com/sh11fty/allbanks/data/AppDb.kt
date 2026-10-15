package com.sh11fty.allbanks.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface AppDao {
    // accounts
    @Query("SELECT * FROM accounts ORDER BY institution, name")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun accountsOnce(): List<AccountEntity>

    @Upsert
    suspend fun upsertAccounts(items: List<AccountEntity>)

    @Query("UPDATE accounts SET included = :included WHERE id = :id")
    suspend fun setIncluded(id: String, included: Boolean)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    // transactions
    @Query("SELECT * FROM transactions ORDER BY bookingDate DESC, id DESC")
    fun transactions(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun transactionsOnce(): List<TxnEntity>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun txnsByIds(ids: List<String>): List<TxnEntity>

    @Query("SELECT * FROM transactions WHERE transferId = :transferId")
    suspend fun txnsByTransfer(transferId: String): List<TxnEntity>

    @Query("SELECT MAX(bookingDate) FROM transactions WHERE accountId = :accountId")
    suspend fun latestBooking(accountId: String): String?

    @Upsert
    suspend fun upsertTxns(items: List<TxnEntity>)

    @Query("SELECT * FROM transactions WHERE transferId IS NULL AND bookingDate >= :since")
    suspend fun unlinkedSince(since: String): List<TxnEntity>

    @Query("UPDATE transactions SET transferId = :transferId WHERE id IN (:ids)")
    suspend fun setTransfer(ids: List<String>, transferId: String?)

    @Query("UPDATE transactions SET categoryId = :categoryId, categoryManual = :manual WHERE id = :id")
    suspend fun setCategory(id: String, categoryId: Long?, manual: Boolean)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id AND categoryManual = 0")
    suspend fun setAutoCategory(id: String, categoryId: Long?)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTxns()

    // categories
    @Query("SELECT * FROM categories ORDER BY name")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun categoriesOnce(): List<CategoryEntity>

    @Insert
    suspend fun insertCategory(item: CategoryEntity): Long

    @Upsert
    suspend fun upsertCategories(items: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // rules
    @Query("SELECT * FROM rules ORDER BY priority, id")
    fun rules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY priority, id")
    suspend fun rulesOnce(): List<RuleEntity>

    @Upsert
    suspend fun upsertRule(item: RuleEntity): Long

    @Upsert
    suspend fun upsertRules(items: List<RuleEntity>)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()

    // transfer candidates
    @Query("SELECT * FROM candidates WHERE rejected = 0 ORDER BY score DESC")
    fun candidates(): Flow<List<CandidateEntity>>

    @Query("SELECT * FROM candidates WHERE rejected = 1")
    suspend fun rejectedOnce(): List<CandidateEntity>

    @Query("DELETE FROM candidates WHERE rejected = 0")
    suspend fun deletePendingCandidates()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandidates(items: List<CandidateEntity>)

    @Upsert
    suspend fun upsertCandidate(item: CandidateEntity)

    @Query("DELETE FROM candidates WHERE rejected = 0 AND (outId IN (:ids) OR inId IN (:ids))")
    suspend fun deleteCandidatesFor(ids: List<String>)
}

@Database(
    entities = [
        AccountEntity::class,
        TxnEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        CandidateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun create(context: Context, passphrase: ByteArray): AppDb {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDb::class.java, "allbanks.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
