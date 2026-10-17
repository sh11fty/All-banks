@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CategoryEntity
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
    var editing by remember { mutableStateOf<TxnEntity?>(null) }
    var bulk by remember { mutableStateOf<List<TxnEntity>?>(null) }
    var toExport by remember { mutableStateOf<List<TxnEntity>>(emptyList()) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(toExport) { bytes -> ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
    }

    ActivityContent(
        txns, accounts, cats,
        onOpen = { editing = it },
        onExport = { toExport = it; export.launch("all-banks-transactions.csv") },
        onBulkCategorise = { bulk = it },
        onSetExcluded = { list, ex -> vm.setExcluded(list, ex) },
    )
    editing?.let { TxnSheet(it, vm, onDismiss = { editing = null }) }
    bulk?.let { ts -> BulkSheet(ts, vm, onDismiss = { bulk = null }) { bulk = null } }
}

/** The transaction list and its controls, free of the view-model so it can be rendered with any data. */
@Composable
fun ActivityContent(
    txns: List<TxnEntity>,
    accounts: List<AccountEntity>,
    cats: List<CategoryEntity>,
    onOpen: (TxnEntity) -> Unit,
    onExport: (List<TxnEntity>) -> Unit,
    onBulkCategorise: (List<TxnEntity>) -> Unit,
    onSetExcluded: (List<TxnEntity>, Boolean) -> Unit,
    initialMonth: YearMonth? = YearMonth.now(),
) {
    var query by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<Long?>(null) } // -1 = uncategorised
    var direction by remember { mutableStateOf<Int?>(null) }
    var month by remember { mutableStateOf(initialMonth) }
    var hideTransfers by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val institutions = remember(accounts) { accounts.map { it.institution }.distinct() }
    val catIds = remember(category, cats) {
        val c = category
        if (c != null && c != -1L) Insights.withChildren(c, cats) else emptySet()
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
                    t.description, t.counterparty, t.merchantName, t.displayName, t.note,
                    t.categoryId?.let(catById::get)?.name, "%.2f".format(kotlin.math.abs(t.amountMinor) / 100.0),
                ).any { it.contains(query, ignoreCase = true) })
        }
    }
    val real = shown.filter { it.transferId == null && !it.excluded }
    val moneyIn = real.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    val moneyOut = real.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }
    val activeFilters = listOf(bank != null, category != null, direction != null, hideTransfers, query.isNotBlank()).count { it }

    Column(Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) {
            val chosen = txns.filter { it.id in selected }
            SelectionBar(
                selected.size,
                listOf(
                    "Categorise" to { onBulkCategorise(chosen); selected = emptySet() },
                    "Exclude" to { onSetExcluded(chosen, true); selected = emptySet() },
                    "Include" to { onSetExcluded(chosen, false); selected = emptySet() },
                ),
                onClear = { selected = emptySet() },
            )
        }

        // Row 1: title and overflow
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export ${shown.size} as CSV") }, enabled = shown.isNotEmpty(),
                        onClick = { menu = false; onExport(shown) },
                    )
                }
            }
        }
        // Row 2: search
        SearchField(query, { query = it }, "Search merchant, note, category, amount", Modifier.padding(horizontal = 16.dp))
        // Row 3: month
        val m = month
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = (m ?: YearMonth.now()).minusMonths(1) }, enabled = m != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
            }
            Text(
                m?.format(monthFmt) ?: "All transactions", Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
            )
            IconButton(onClick = { month = m?.plusMonths(1) }, enabled = m != null && m < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
            ToggleFilter("All time", m == null) { month = if (m == null) YearMonth.now() else null }
            Spacer(Modifier.width(8.dp))
        }
        // Row 4: filters; active ones show a ✕
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterMenu("Bank", listOf<Pair<String?, String>>(null to "All banks") + institutions.map { it to it }, bank) { bank = it }
            FilterMenu(
                "Category",
                listOf<Pair<Long?, String>>(null to "All categories", -1L to "Uncategorised") +
                    cats.map { it.id to categoryPath(it, catById) }.sortedBy { it.second },
                category,
            ) { category = it }
            FilterMenu("Money", listOf<Pair<Int?, String>>(null to "In & out", 1 to "Money in", -1 to "Money out"), direction) { direction = it }
            ToggleFilter("No transfers", hideTransfers) { hideTransfers = !hideTransfers }
            if (activeFilters > 0) {
                TextButton(onClick = { bank = null; category = null; direction = null; hideTransfers = false; query = "" }) { Text("Clear all") }
            }
        }
        // Row 5: one-line summary
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${shown.size} transactions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "In ${fmt(moneyIn)}  ·  Out ${fmt(moneyOut)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        if (shown.isEmpty()) EmptyState("Nothing to show", if (activeFilters > 0 || month != null) "Try another month or clear a filter." else "Transactions appear here after a sync.")
        TxnList(
            shown, byId, catById, Modifier.weight(1f),
            selected = selected,
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selected = selected + it.id },
        ) { t -> if (selected.isNotEmpty()) selected = if (t.id in selected) selected - t.id else selected + t.id else onOpen(t) }
    }
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
            t.accountId == accountId && (query.isBlank() || listOfNotNull(t.description, t.counterparty, t.merchantName, t.displayName, t.note)
                .any { it.contains(query, ignoreCase = true) })
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (account != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankAvatar(account.institution, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.institution, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(account.name, account.accountType, account.maskedId?.let { "••$it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(balText(account.balanceMinor, "GBP"), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.displayMedium)
                Text(
                    listOfNotNull(syncedAgo(account.balanceFetchedAt)?.let { "Synced $it" }, "${shown.size} transactions").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Count in total balance", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(account.included, { vm.setIncluded(account, it) })
                }
            }
        }
        SearchField(query, { query = it }, "Search this account", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (shown.isEmpty()) EmptyState("No transactions", "Nothing to show for this account yet.")
        TxnList(shown, accounts.associateBy { it.id }, catById, Modifier.weight(1f), showBank = false) { editing = it }
    }
    editing?.let { TxnSheet(it, vm, onDismiss = { editing = null }) }
}
