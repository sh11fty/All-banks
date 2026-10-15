package com.sh11fty.allbanks.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class AccountDto(
    val id: String,
    val institution: String = "",
    val name: String = "",
    val currency: String = "GBP",
    val status: String? = null,
    val sandbox: Boolean = false,
    @SerialName("masked_identifier") val maskedIdentifier: String? = null,
    @SerialName("account_type") val accountType: String? = null,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
    @SerialName("consent_renewal_due") val consentRenewalDue: String? = null,
)

@Serializable
data class BalanceDto(
    val balance: String,
    val currency: String = "GBP",
    @SerialName("fetched_at") val fetchedAt: String? = null,
)

@Serializable
data class EnrichmentDto(
    @SerialName("merchant_name") val merchantName: String? = null,
    val category: String? = null,
    @SerialName("brand_domain") val brandDomain: String? = null,
)

@Serializable
data class TxnDto(
    val id: String,
    @SerialName("booking_date") val bookingDate: String,
    @SerialName("value_date") val valueDate: String? = null,
    val amount: String,
    val currency: String = "GBP",
    val description: String = "",
    val counterparty: String? = null,
    val enrichment: EnrichmentDto? = null,
    @SerialName("end_to_end_id") val endToEndId: String? = null,
)

@Serializable
data class TxnPage(val next: String? = null, val results: List<TxnDto> = emptyList())

@Serializable
data class ConnectionDto(
    val institution: String = "",
    val status: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
)

class ApiException(val httpCode: Int, val code: String) :
    Exception("Endute API error $httpCode ($code)")

/** Read-only client for https://api.endute.com/v1 (see endute.com/connect/docs). */
class EnduteApi(
    private val apiKey: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        // Never send the key anywhere except the Endute API, even if a "next" URL says otherwise.
        require(url.startsWith(BASE)) { "Refusing to call non-Endute URL" }
        repeat(4) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> return@withContext body
                    r.code == 429 -> {
                        val wait = (r.header("Retry-After")?.toLongOrNull() ?: 5L).coerceIn(1L, 60L)
                        delay(wait * 1000)
                    }
                    else -> throw ApiException(r.code, errorCode(body))
                }
            }
        }
        throw ApiException(429, "throttled")
    }

    private fun errorCode(body: String): String = try {
        Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    suspend fun accounts(): List<AccountDto> =
        json.decodeFromString(get("$BASE/accounts"))

    suspend fun balance(accountId: String): BalanceDto =
        json.decodeFromString(get("$BASE/accounts/$accountId/balances"))

    suspend fun connections(): List<ConnectionDto> =
        json.decodeFromString(get("$BASE/connections"))

    /** Follows `next` until the feed ends. [from] is an inclusive booking-date lower bound. */
    suspend fun transactions(accountId: String, from: String?): List<TxnDto> {
        val all = ArrayList<TxnDto>()
        var url: String? = "$BASE/accounts/$accountId/transactions" +
            (if (from != null) "?from=$from" else "")
        var pages = 0
        while (url != null && pages < 500) {
            val page: TxnPage = json.decodeFromString(get(url))
            all += page.results
            url = page.next
            pages++
        }
        return all
    }

    companion object {
        const val BASE = "https://api.endute.com/v1"
    }
}
