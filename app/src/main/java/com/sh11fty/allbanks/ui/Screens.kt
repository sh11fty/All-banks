@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.sh11fty.allbanks.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.BuildConfig
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CandidateEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.data.daysUntil
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun fmt(minor: Long, currency: String = "GBP"): String {
    val f = NumberFormat.getCurrencyInstance(Locale.UK)
    runCatching { f.currency = Currency.getInstance(currency) }
    return f.format(minor / 100.0)
}

fun bankColour(institution: String): Color {
    val hue = (abs(institution.hashCode()) % 360).toFloat()
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.75f)))
}

@Composable
fun BankChip(institution: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bankColour(institution))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(institution, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, colour: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colour)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) { Text(text, fontSize = 11.sp, maxLines = 1) }
}

private enum class Tab(val label: String) {
    Overview("Overview"), Transactions("Activity"), Review("Review"), Rules("Rules"), Settings("Settings")
}

@Composable
fun MainScaffold(vm: MainVm) {
    var tab by remember { mutableStateOf(Tab.Overview) }
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val pending by vm.candidates.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; selectedAccount = null },
                        icon = {},
                        label = {
                            Text(
                                if (t == Tab.Review && pending.isNotEmpty()) "Review (${pending.size})" else t.label,
                                fontSize = 12.sp,
                            )
                        },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            val sel = selectedAccount
            if (sel != null && tab == Tab.Overview) {
                BackHandler { selectedAccount = null }
                AccountScreen(vm, sel) { selectedAccount = null }
                return@Box
            }
            when (tab) {
                Tab.Overview -> OverviewScreen(vm) { selectedAccount = it }
                Tab.Transactions -> TransactionsScreen(vm)
                Tab.Review -> ReviewScreen(vm)
                Tab.Rules -> RulesScreen(vm)
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
}

// ---------------------------------------------------------------- Overview

@Composable
fun OverviewScreen(vm: MainVm, onOpenAccount: (String) -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val cats by vm.categories.collectAsState()
    val includeSavings by vm.includeSavings.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val hasKey by vm.hasKey.collectAsState()

    val counted = accounts.filter { it.included && (includeSavings || it.accountType != "savings") }
    val totals = counted.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.balanceMinor ?: 0L } }

    val month = LocalDate.now().toString().substring(0, 7)
    val countedIds = counted.map { it.id }.toSet()
    val thisMonth = txns.filter {
        it.bookingDate.startsWith(month) && it.transferId == null && it.accountId in countedIds
    }
    val moneyIn = thisMonth.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    val moneyOut = thisMonth.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }
    val catName = cats.associate { it.id to it.name }
    val byCat = thisMonth.filter { it.amountMinor < 0 }
        .groupBy { it.categoryId?.let(catName::get) ?: "Uncategorised" }
        .mapValues { (_, l) -> l.sumOf { -it.amountMinor } }
        .entries.sortedByDescending { it.value }.take(6)

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total across ${counted.size} accounts", style = MaterialTheme.typography.labelLarge)
                    if (totals.isEmpty()) Text("—", style = MaterialTheme.typography.headlineLarge)
                    totals.forEach { (cur, v) ->
                        Text(fmt(v, cur), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Booked balances only. Cards and loans count as negative.", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(includeSavings, { vm.setIncludeSavings(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Include savings")
                    }
                    Button(onClick = { vm.sync() }, enabled = !syncing && hasKey) {
                        if (syncing) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        else Text("Refresh now")
                    }
                    if (!hasKey) Text("Add your Endute API key in Settings to start.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        items(accounts, key = { it.id }) { a -> AccountRow(a, vm) { onOpenAccount(a.id) } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This month (excludes internal transfers)", style = MaterialTheme.typography.labelLarge)
                    Text("In ${fmt(moneyIn)}   Out ${fmt(-moneyOut)}")
                    byCat.forEach { (name, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name); Text(fmt(v))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(a: AccountEntity, vm: MainVm, onClick: () -> Unit) {
    val days = daysUntil(a.consentRenewalDue)
    val stale = a.connectionStatus in setOf("expired", "suspended", "error", "rejected")
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankChip(a.institution)
                Spacer(Modifier.width(8.dp))
                Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.balanceMinor?.let { fmt(it, a.currency) } ?: "—", fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(a.accountType, a.maskedId?.let { "••$it" }, a.balanceFetchedAt?.take(16)?.replace('T', ' '))
                        .joinToString(" · "),
                    fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                Switch(a.included, { vm.setIncluded(a, it) })
            }
            if (stale) Text("Connection ${a.connectionStatus}: reconnect this bank in Endute", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            else if (days != null && days <= 10) Text("Reconnect within ${maxOf(days, 0)} days", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- Account detail

@Composable
fun AccountScreen(vm: MainVm, accountId: String, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TxnEntity?>(null) }
    val account = accounts.firstOrNull { it.id == accountId }
    val catById = cats.associateBy { it.id }
    val shown = remember(txns, query, accountId) {
        txns.filter { t ->
            t.accountId == accountId &&
                (query.isBlank() || listOfNotNull(t.description, t.counterparty, t.merchantName)
                    .any { it.contains(query, ignoreCase = true) })
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
        }
        if (account != null) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                BankChip(account.institution)
                Spacer(Modifier.height(4.dp))
                Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    account.balanceMinor?.let { fmt(it, account.currency) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text("${shown.size} transactions", fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            query, { query = it }, label = { Text("Search this account") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (shown.isEmpty()) {
            Text("No transactions to show.", Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.id }) { t ->
                TxnRow(t, null, catById[t.categoryId]) { editing = t }
                HorizontalDivider()
            }
        }
    }
    editing?.let { CategoriseSheet(it, vm) { editing = null } }
}

// ---------------------------------------------------------------- Transactions

@Composable
fun TransactionsScreen(vm: MainVm) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf<String?>(null) }
    var hideTransfers by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TxnEntity?>(null) }

    val byId = accounts.associateBy { it.id }
    val catById = cats.associateBy { it.id }
    val institutions = accounts.map { it.institution }.distinct()
    val shown = remember(txns, query, bank, hideTransfers, accounts) {
        txns.filter { t ->
            val inst = byId[t.accountId]?.institution
            (bank == null || inst == bank) &&
                (!hideTransfers || t.transferId == null) &&
                (query.isBlank() || listOfNotNull(t.description, t.counterparty, t.merchantName)
                    .any { it.contains(query, ignoreCase = true) })
        }.take(1000)
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            query, { query = it }, label = { Text("Search") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(bank == null, { bank = null }, label = { Text("All banks") })
            institutions.forEach { FilterChip(bank == it, { bank = if (bank == it) null else it }, label = { Text(it) }) }
            FilterChip(hideTransfers, { hideTransfers = !hideTransfers }, label = { Text("Hide transfers") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.id }) { t ->
                TxnRow(t, byId[t.accountId], catById[t.categoryId]) { editing = t }
                HorizontalDivider()
            }
        }
    }
    editing?.let { CategoriseSheet(it, vm) { editing = null } }
}

@Composable
fun TxnRow(t: TxnEntity, account: AccountEntity?, cat: CategoryEntity?, onClick: () -> Unit) {
    val title = t.merchantName ?: t.counterparty ?: t.description
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.bookingDate, fontSize = 12.sp)
                account?.let { BankChip(it.institution) }
                if (t.transferId != null) Tag("Internal transfer", MaterialTheme.colorScheme.tertiaryContainer)
                else cat?.let { Tag(it.name, Color(it.colour).copy(alpha = 0.35f)) }
            }
        }
        Text(
            fmt(t.amountMinor, t.currency),
            fontWeight = FontWeight.SemiBold,
            color = if (t.amountMinor > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ColourDot(colour: Int, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(colour)))
}

@Composable
fun CategoriseSheet(t: TxnEntity, vm: MainVm, onDismiss: () -> Unit) {
    val cats by vm.categories.collectAsState()
    var newName by remember { mutableStateOf("") }
    var makeRule by remember { mutableStateOf(true) }
    var pattern by remember { mutableStateOf(t.merchantName ?: t.counterparty ?: t.description) }
    val rulePattern = if (makeRule && pattern.isNotBlank()) pattern else null
    val title = t.merchantName ?: t.counterparty ?: t.description

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(fmt(t.amountMinor, t.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(t.bookingDate + if (title != t.description) " · ${t.description}" else "", fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (t.transferId != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Linked as an internal transfer", Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.unlink(t); onDismiss() }) { Text("Unlink") }
                    }
                }
            }

            SectionLabel("CATEGORY")
            if (cats.isEmpty()) Text("No categories yet — create one below.", fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cats.forEach { c ->
                    FilterChip(
                        selected = t.categoryId == c.id,
                        onClick = { vm.categorise(t, c.id, rulePattern); onDismiss() },
                        label = { Text(c.name) },
                        leadingIcon = { ColourDot(c.colour) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    newName, { newName = it }, label = { Text("New category") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.categoriseNew(t, newName, rulePattern); onDismiss() },
                    enabled = newName.isNotBlank(),
                ) { Text("Add") }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Remember for next time", fontWeight = FontWeight.Medium)
                            Text("Auto-categorise matching transactions", fontSize = 12.sp)
                        }
                        Switch(makeRule, { makeRule = it })
                    }
                    if (makeRule) {
                        OutlinedTextField(
                            pattern, { pattern = it }, label = { Text("When the text contains") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Review

@Composable
fun ReviewScreen(vm: MainVm) {
    val candidates by vm.candidates.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<TxnEntity?>(null) }
    val byTxn = txns.associateBy { it.id }
    val acc = accounts.associateBy { it.id }
    val catById = cats.associateBy { it.id }
    val uncategorisedAll = txns.filter { it.categoryId == null && it.transferId == null }
    val uncategorised = uncategorisedAll.take(30)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item { SectionLabel("POSSIBLE INTERNAL TRANSFERS · ${candidates.size}") }
        if (candidates.isEmpty()) item { Text("Nothing to confirm.", fontSize = 13.sp) }
        items(candidates, key = { it.outId + it.inId }) { c: CandidateEntity ->
            val o = byTxn[c.outId]; val i = byTxn[c.inId]
            if (o != null && i != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt(-o.amountMinor, o.currency), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Tag("${c.score}% match")
                        }
                        TransferLeg("OUT", acc[o.accountId]?.institution, o.bookingDate, o.description)
                        TransferLeg("IN", acc[i.accountId]?.institution, i.bookingDate, i.description)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.confirm(c) }, modifier = Modifier.weight(1f)) { Text("Link") }
                            OutlinedButton(onClick = { vm.reject(c) }, modifier = Modifier.weight(1f)) { Text("Not a transfer") }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("UNCATEGORISED · ${uncategorisedAll.size}")
            Text("Tap a transaction to categorise it.", fontSize = 12.sp)
        }
        if (uncategorised.isEmpty()) item { Text("All caught up.", fontSize = 13.sp) }
        items(uncategorised, key = { "u" + it.id }) { t ->
            TxnRow(t, acc[t.accountId], catById[t.categoryId]) { editing = t }
            HorizontalDivider()
        }
    }
    editing?.let { CategoriseSheet(it, vm) { editing = null } }
}

@Composable
private fun TransferLeg(label: String, bank: String?, date: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(label, MaterialTheme.colorScheme.surfaceVariant)
        Column {
            Text("${bank ?: "?"} · $date", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Rules

@Composable
fun RulesScreen(vm: MainVm) {
    val rules by vm.rules.collectAsState()
    val cats by vm.categories.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val catById = cats.associateBy { it.id }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Top rule wins. Manually categorised transactions are never changed.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }, enabled = cats.isNotEmpty()) { Text("Add rule") }
                OutlinedButton(onClick = { vm.reapplyRules() }) { Text("Re-apply to history") }
            }
            if (cats.isEmpty()) Text("Categories appear after your first sync, or create one by categorising a transaction.", fontSize = 12.sp)
        }
        if (rules.isEmpty()) item { Text("No rules yet. Categorise a transaction and tick \"Remember for next time\", or add one here.", fontSize = 13.sp) }
        items(rules, key = { it.id }) { r ->
            val cat = catById[r.categoryId]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("\"${r.pattern}\"", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${r.field} ${r.matchType}", fontSize = 12.sp)
                        }
                        Switch(r.enabled, { vm.toggleRule(r) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("→", fontSize = 14.sp)
                        if (cat != null) Tag(cat.name, Color(cat.colour).copy(alpha = 0.35f)) else Tag("missing category")
                        when (r.direction) {
                            1 -> Tag("money in")
                            -1 -> Tag("money out")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteRule(r) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (adding) AddRuleSheet(vm, cats, rules) { adding = false }
}

@Composable
private fun AddRuleSheet(vm: MainVm, cats: List<CategoryEntity>, rules: List<RuleEntity>, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("description") }
    var match by remember { mutableStateOf("contains") }
    var direction by remember { mutableStateOf(0) }
    var cat by remember { mutableStateOf(cats.first().id) }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }

    fun pence(s: String) = s.toDoubleOrNull()?.let { Math.round(it * 100) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New rule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SectionLabel("MATCH")
            OutlinedTextField(pattern, { pattern = it }, label = { Text("Text or regex") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Look in", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("description", "counterparty", "merchant").forEach {
                    FilterChip(field == it, { field = it }, label = { Text(it) })
                }
            }
            Text("How", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("contains", "equals", "regex").forEach {
                    FilterChip(match == it, { match = it }, label = { Text(it) })
                }
            }

            SectionLabel("ONLY WHEN (OPTIONAL)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(direction == 0, { direction = 0 }, label = { Text("Any direction") })
                FilterChip(direction == 1, { direction = 1 }, label = { Text("Money in") })
                FilterChip(direction == -1, { direction = -1 }, label = { Text("Money out") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(min, { min = it }, label = { Text("Min £") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(max, { max = it }, label = { Text("Max £") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }

            SectionLabel("CATEGORISE AS")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cats.forEach {
                    FilterChip(cat == it.id, { cat = it.id }, label = { Text(it.name) }, leadingIcon = { ColourDot(it.colour) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = pattern.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.saveRule(
                            RuleEntity(
                                priority = (rules.minOfOrNull { it.priority } ?: 0) - 1,
                                field = field, matchType = match, pattern = pattern.trim(),
                                direction = direction, minAbsMinor = pence(min), maxAbsMinor = pence(max),
                                categoryId = cat,
                            )
                        )
                        onDismiss()
                    },
                ) { Text("Save rule") }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val hasKey by vm.hasKey.collectAsState()
    val update by vm.update.collectAsState()
    val busy by vm.updateBusy.collectAsState()
    var key by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) vm.exportBackup(pass.toCharArray()) { bytes ->
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) vm.importBackup(bytes, pass.toCharArray())
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Endute API key", style = MaterialTheme.typography.titleMedium)
        Text(if (hasKey) "A key is stored (encrypted in the Android Keystore)." else "No key stored.", fontSize = 12.sp)
        OutlinedTextField(
            key, { key = it }, label = { Text("Paste edk_… key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
            if (hasKey) OutlinedButton(onClick = { vm.saveKey("") }) { Text("Remove") }
        }

        HorizontalDivider()
        Text("App updates", style = MaterialTheme.typography.titleMedium)
        Text("Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        update?.let { Text("New: ${it.versionName}\n${it.notes}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (update != null) vm.installUpdate() else vm.checkUpdate() }, enabled = !busy) {
                Text(if (update != null) "Update to ${update!!.versionName}" else "Check for update")
            }
            if (busy) CircularProgressIndicator(Modifier.height(24.dp).width(24.dp), strokeWidth = 2.dp)
        }

        HorizontalDivider()
        Text("Encrypted backup", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Backup passphrase") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportLauncher.launch("all-banks-backup.abk") }, enabled = pass.length >= 8) { Text("Export") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = pass.length >= 8) { Text("Restore") }
        }
        Text("Passphrase needs 8+ characters. The API key is not included in backups.", fontSize = 12.sp)

        HorizontalDivider()
        Text("Alerts", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }) { Text("Allow notifications") }
        Text("Used for consent-expiry warnings and failed syncs. Background sync runs about every 6 hours.", fontSize = 12.sp)
    }
}
