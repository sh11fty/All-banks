package com.sh11fty.allbanks.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("UPDATE transactions SET reviewState = :state WHERE id = :id")
    suspend fun setReviewState(id: String, state: Int)

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignTxnCategory(from: Long, to: Long?)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id AND categoryManual = 0")
    suspend fun setAutoCategory(id: String, categoryId: Long?)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTxns()

    @Query("DELETE FROM transactions WHERE bookingDate < :start")
    suspend fun deleteTxnsBefore(start: String)

    @Query("DELETE FROM candidates WHERE outId NOT IN (SELECT id FROM transactions) OR inId NOT IN (SELECT id FROM transactions)")
    suspend fun deleteOrphanCandidates()

    // merchant aliases
    @Query("SELECT * FROM merchant_aliases")
    suspend fun aliasesOnce(): List<MerchantAlias>

    @Upsert
    suspend fun upsertAliases(items: List<MerchantAlias>)

    @Query("DELETE FROM merchant_aliases WHERE rawKey IN (:keys)")
    suspend fun deleteAliases(keys: List<String>)

    @Query("DELETE FROM merchant_aliases")
    suspend fun deleteAllAliases()

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

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE categories SET parentId = :to WHERE parentId = :from")
    suspend fun reparentChildren(from: Long, to: Long?)

    // budgets
    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun budgetsOnce(): List<BudgetEntity>

    @Upsert
    suspend fun upsertBudget(item: BudgetEntity)

    @Upsert
    suspend fun upsertBudgets(items: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudget(categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    // rules
    @Query("SELECT * FROM rules ORDER BY priority, id")
    fun rules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY priority, id")
    suspend fun rulesOnce(): List<RuleEntity>

    @Upsert
    suspend fun upsertRule(item: RuleEntity): Long

    @Upsert
    suspend fun upsertRules(items: List<RuleEntity>)

    @Query("UPDATE rules SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignRuleCategory(from: Long, to: Long)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()

    // transfer candidates
    @Query("SELECT * FROM candidates WHERE rejected = 0 ORDER BY score DESC")
    fun candidates(): Flow<List<CandidateEntity>>

    @Query("SELECT * FROM candidates WHERE rejected = 1")
    suspend fun rejectedOnce(): List<CandidateEntity>

    @Query("DELETE FROM candidates WHERE rejected = 0 AND later = 0")
    suspend fun deletePendingCandidates()

    @Query("UPDATE candidates SET later = :later WHERE outId = :outId AND inId = :inId")
    suspend fun setCandidateLater(outId: String, inId: String, later: Boolean)

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
        BudgetEntity::class,
        MerchantAlias::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN reviewState INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE candidates ADD COLUMN later INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rules ADD COLUMN toAccountId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS budgets (categoryId INTEGER NOT NULL, limitMinor INTEGER NOT NULL, PRIMARY KEY(categoryId))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN categorySource INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN ruleId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN displayName TEXT")
                db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT")
                db.execSQL("ALTER TABLE rules ADD COLUMN renameTo TEXT")
                db.execSQL("ALTER TABLE rules ADD COLUMN excludeFromSpending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rules ADD COLUMN markReviewed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS merchant_aliases (rawKey TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(rawKey))")
                db.execSQL("UPDATE transactions SET categorySource = 3 WHERE categoryManual = 1 AND categoryId IS NOT NULL")
                db.execSQL("UPDATE transactions SET categorySource = 1 WHERE categoryManual = 0 AND categoryId IS NOT NULL")
            }
        }

        fun create(context: Context, passphrase: ByteArray): AppDb {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDb::class.java, "allbanks.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
