@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.Insights
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)

@Composable
fun ActivityScreen(vm: MainVm) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val ctx = LocalContext.current

    var query by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<Long?>(null) } // -1 = uncategorised
    var direction by remember { mutableStateOf<Int?>(null) }
    var month by remember { mutableStateOf<YearMonth?>(YearMonth.now()) }
    var hideTransfers by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TxnEntity?>(null) }

    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val institutions = remember(accounts) { accounts.map { it.institution }.distinct() }
    val catIds = remember(category, cats) {
        if (category != null && category != -1L) Insights.withChildren(category!!, cats) else emptySet()
    }

    val shown = remember(txns, query, bank, category, direction, month, hideTransfers, accounts, cats) {
        txns.filter { t ->
            val inst = byId[t.accountId]?.institution
            (bank == null || inst == bank) &&
                (month == null || t.bookingDate.startsWith(month.toString())) &&
                (direction == null || (direction == 1 && t.amountMinor > 0) || (direction == -1 && t.amountMinor < 0)) &&
                (!hideTransfers || t.transferId == null) &&
                when (category) {
                    null -> true
                    -1L -> t.categoryId == null && t.transferId == null
                    else -> t.categoryId in catIds
                } &&
                (query.isBlank() || listOfNotNull(
                    t.description, t.counterparty, t.merchantName, t.note,
                    t.categoryId?.let(catById::get)?.name, "%.2f".format(kotlin.math.abs(t.amountMinor) / 100.0),
                ).any { it.contains(query, ignoreCase = true) })
        }
    }
    val real = shown.filter { it.transferId == null }
    val moneyIn = real.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    val moneyOut = real.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(shown) { bytes -> ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { export.launch("all-banks-transactions.csv") }, enabled = shown.isNotEmpty()) { Text("Export CSV") }
        }
        OutlinedTextField(
            query, { query = it }, label = { Text("Search merchant, note, category, amount") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = (month ?: YearMonth.now()).minusMonths(1) }, enabled = month != null) { Text("‹") }
            Text(
                month?.format(monthFmt) ?: "All time", Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(onClick = { month = month?.plusMonths(1) }, enabled = month != null && month!! < YearMonth.now()) { Text("›") }
            FilterChip(month == null, { month = if (month == null) YearMonth.now() else null }, label = { Text("All time") })
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterMenu("Bank", listOf<Pair<String?, String>>(null to "All banks") + institutions.map { it to it }, bank) { bank = it }
            FilterMenu(
                "Category",
                listOf<Pair<Long?, String>>(null to "All categories", -1L to "Uncategorised") +
                    cats.map { it.id to categoryPath(it, catById) }.sortedBy { it.second },
                category,
            ) { category = it }
            FilterMenu("Money", listOf<Pair<Int?, String>>(null to "In & out", 1 to "Money in", -1 to "Money out"), direction) { direction = it }
            FilterChip(hideTransfers, { hideTransfers = !hideTransfers }, label = { Text("Hide transfers") })
        }
        Text(
            "${shown.size} transactions · In ${fmt(moneyIn)} · Out ${fmt(moneyOut)}",
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (shown.isEmpty()) Text("No transactions match.", Modifier.padding(16.dp))
        TxnList(shown, byId, catById, Modifier.weight(1f)) { editing = it }
    }
    editing?.let { TxnSheet(it, vm) { editing = null } }
}

@Composable
fun AccountScreen(vm: MainVm, accountId: String) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TxnEntity?>(null) }
    val account = accounts.firstOrNull { it.id == accountId }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val shown = remember(txns, query, accountId) {
        txns.filter { t ->
            t.accountId == accountId && (query.isBlank() || listOfNotNull(t.description, t.counterparty, t.merchantName, t.note)
                .any { it.contains(query, ignoreCase = true) })
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (account != null) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                BankChip(account.institution)
                Spacer(Modifier.height(4.dp))
                Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(balText(account.balanceMinor, account.currency), style = MaterialTheme.typography.headlineMedium)
                Text("${shown.size} transactions", fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            query, { query = it }, label = { Text("Search this account") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (shown.isEmpty()) Text("No transactions to show.", Modifier.padding(16.dp))
        TxnList(shown, accounts.associateBy { it.id }, catById, Modifier.weight(1f), showBank = false) { editing = it }
    }
    editing?.let { TxnSheet(it, vm) { editing = null } }
}
