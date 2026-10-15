package com.sh11fty.allbanks.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
private data class BackupFile(
    val accounts: List<AccountEntity>,
    val transactions: List<TxnEntity>,
    val categories: List<CategoryEntity>,
    val rules: List<RuleEntity>,
)

/**
 * Passphrase-encrypted export you can keep anywhere. Format: "ABK1" | salt(16) | iv(12) | AES-GCM.
 * The Endute API key is deliberately not included.
 */
object Backup {
    private val magic = "ABK1".toByteArray()
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, 210_000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    suspend fun export(db: AppDb, pass: CharArray): ByteArray {
        val d = db.dao()
        val plain = json.encodeToString(
            BackupFile(
                d.accountsOnce(), d.transactionsOnce(), d.categoriesOnce(), d.rulesOnce()
            )
        ).toByteArray()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        return magic + salt + iv + c.doFinal(plain)
    }

    suspend fun import(db: AppDb, data: ByteArray, pass: CharArray) {
        require(data.size > 32 && data.copyOfRange(0, 4).contentEquals(magic)) { "Not an All Banks backup" }
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        val plain = try {
            c.doFinal(data, 32, data.size - 32)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong passphrase or damaged file")
        }
        val b = json.decodeFromString<BackupFile>(String(plain))
        val d = db.dao()
        db.withTransaction {
            d.deleteAllTxns(); d.deleteAllRules(); d.deleteAllCategories(); d.deleteAllAccounts()
            d.upsertAccounts(b.accounts)
            d.upsertCategories(b.categories)
            d.upsertRules(b.rules)
            d.upsertTxns(b.transactions)
        }
    }
}
