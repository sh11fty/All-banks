@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.BuildConfig
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.daysUntil
import com.sh11fty.allbanks.domain.Insights
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private enum class Tab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Filled.Home),
    Activity("Activity", Icons.AutoMirrored.Filled.List),
    Review("Review", Icons.Filled.Check),
    More("More", Icons.Filled.Settings),
}

private enum class Sub(val title: String) {
    Rules("Rules"), Categories("Categories"), Budgets("Budgets"), Recurring("Recurring payments")
}

@Composable
fun MainScaffold(vm: MainVm) {
    var tab by remember { mutableStateOf(Tab.Overview) }
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val hide by vm.hideBalances.collectAsState()
    val secure by vm.blockScreenshots.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    LaunchedEffect(secure) {
        findActivity(ctx)?.window?.let {
            if (secure) it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    CompositionLocalProvider(LocalHide provides hide) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t; selectedAccount = null; sub = null },
                            icon = {
                                if (t == Tab.Review && reviewCount > 0) {
                                    BadgedBox(badge = { Badge { Text(if (reviewCount > 99) "99+" else "$reviewCount") } }) { Icon(t.icon, t.label) }
                                } else Icon(t.icon, t.label)
                            },
                            label = { Text(t.label, fontSize = 12.sp) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                val s = sub
                val acct = selectedAccount
                when {
                    s != null -> {
                        BackHandler { sub = null }
                        SubScaffold(s.title, onBack = { sub = null }) {
                            when (s) {
                                Sub.Rules -> RulesScreen(vm)
                                Sub.Categories -> CategoriesScreen(vm)
                                Sub.Budgets -> BudgetsScreen(vm)
                                Sub.Recurring -> RecurringScreen(vm)
                            }
                        }
                    }
                    acct != null && tab == Tab.Overview -> {
                        BackHandler { selectedAccount = null }
                        SubScaffold("Account", onBack = { selectedAccount = null }) { AccountScreen(vm, acct) }
                    }
                    else -> when (tab) {
                        Tab.Overview -> OverviewScreen(
                            vm,
                            onOpenAccount = { selectedAccount = it },
                            onOpenBudgets = { sub = Sub.Budgets },
                            onOpenRecurring = { sub = Sub.Recurring },
                        )
                        Tab.Activity -> ActivityScreen(vm)
                        Tab.Review -> ReviewScreen(vm)
                        Tab.More -> SettingsScreen(vm) { sub = it }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Overview

private fun syncedAgo(iso: String?): String? {
    val t = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val m = Duration.between(t, Instant.now()).toMinutes()
    return when {
        m < 2 -> "just now"
        m < 60 -> "${m}m ago"
        m < 48 * 60 -> "${m / 60}h ago"
        else -> "${m / 1440}d ago"
    }
}

@Composable
fun OverviewScreen(vm: MainVm, onOpenAccount: (String) -> Unit, onOpenBudgets: () -> Unit, onOpenRecurring: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val cats by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val includeSavings by vm.includeSavings.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val hide by vm.hideBalances.collectAsState()

    val counted = accounts.filter { it.included && (includeSavings || it.accountType != "savings") }
    val totals = counted.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.balanceMinor ?: 0L } }
    val countedIds = counted.map { it.id }.toSet()

    val now = LocalDate.now()
    val month = now.toString().substring(0, 7)
    val lastMonth = now.minusMonths(1).toString().substring(0, 7)
    val real = remember(txns, accounts, includeSavings) { txns.filter { it.transferId == null && it.accountId in countedIds } }
    val thisMonth = remember(real) { real.filter { it.bookingDate.startsWith(month) } }
    val prevMonth = remember(real) { real.filter { it.bookingDate.startsWith(lastMonth) } }
    val moneyIn = thisMonth.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    val moneyOut = thisMonth.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }
    val prevOut = prevMonth.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }

    val catById = remember(cats) { cats.associateBy { it.id } }
    val parts = remember(thisMonth, cats) {
        Insights.spendByTopCategory(thisMonth, cats).entries.sortedByDescending { it.value }.take(6)
    }
    fun partColour(id: Long?): Color = id?.let(catById::get)?.let { Color(it.colour) } ?: Color.Gray

    val trend = remember(counted, txns) { Insights.balanceTrend(counted, txns, 30) }
    val recurring = remember(txns) { Insights.recurring(txns) }
    val soon = recurring.filter { !it.next.isAfter(now.plusDays(7)) }

    val problems = accounts.filter {
        it.connectionStatus in setOf("expired", "suspended", "error", "rejected") || (daysUntil(it.consentRenewalDue) ?: 99) <= 10
    }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (problems.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Reconnect needed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        problems.map { it.institution }.distinct().forEach {
                            Text("• $it — reconnect in Endute", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Total across ${counted.size} accounts", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { vm.setHideBalances(!hide) }) { Text(if (hide) "Show" else "Hide") }
                    }
                    if (totals.isEmpty()) Text("—", style = MaterialTheme.typography.headlineLarge)
                    totals.forEach { (cur, v) ->
                        Text(balText(v, cur), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Booked balances only. Cards and loans count as negative.", fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(includeSavings, { vm.setIncludeSavings(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Include savings")
                    }
                    Button(onClick = { vm.sync() }, enabled = !syncing && hasKey) {
                        if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Refresh now")
                    }
                    if (!hasKey) Text("Add your Endute API key in More to start.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        items(accounts, key = { it.id }) { a -> AccountRow(a, vm) { onOpenAccount(a.id) } }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This month", style = MaterialTheme.typography.labelLarge)
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("In", fontSize = 12.sp)
                            Text(fmt(moneyIn), fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        Column(Modifier.weight(1f)) { Text("Out", fontSize = 12.sp); Text(fmt(moneyOut), fontWeight = FontWeight.SemiBold) }
                        Column(Modifier.weight(1f)) {
                            Text("Net", fontSize = 12.sp); Text(fmt(moneyIn - moneyOut), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (prevOut > 0) {
                        val pct = ((moneyOut - prevOut) * 100.0 / prevOut).toInt()
                        Text(
                            "Spending ${if (pct >= 0) "up" else "down"} ${kotlin.math.abs(pct)}% on last month (${fmt(prevOut)})",
                            fontSize = 12.sp,
                            color = if (pct > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        )
                    }
                    Text("Excludes internal transfers.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (parts.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Donut(parts.map { it.value.toFloat() to partColour(it.key) }, Modifier.size(110.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                parts.forEach { (id, v) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ColourDot(partColour(id).toArgbInt(), 10)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            id?.let(catById::get)?.name ?: "Uncategorised", Modifier.weight(1f),
                                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(fmt(v), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (budgets.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onOpenBudgets)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Budgets", style = MaterialTheme.typography.labelLarge)
                        budgets.mapNotNull { b -> catById[b.categoryId]?.let { b to it } }
                            .map { (b, c) ->
                                val ids = Insights.withChildren(b.categoryId, cats)
                                Triple(c, b, thisMonth.filter { it.categoryId in ids && it.amountMinor < 0 }.sumOf { -it.amountMinor })
                            }
                            .sortedByDescending { it.third.toDouble() / it.second.limitMinor }
                            .take(4)
                            .forEach { (c, b, spent) -> BudgetProgress(c.name, spent, b.limitMinor, c.colour) }
                    }
                }
            }
        }

        if (soon.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onOpenRecurring)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Coming up (next 7 days)", style = MaterialTheme.typography.labelLarge)
                        soon.forEach {
                            Row {
                                Text(it.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${dayLabel(it.next.toString())} · ${fmt(it.amountMinor, it.currency)}", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (trend.size > 1 && !hide) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Balance, last 30 days", style = MaterialTheme.typography.labelLarge)
                        LineChart(trend, Modifier.fillMaxWidth().height(90.dp).padding(vertical = 8.dp))
                        Text("Estimated from your transactions.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(balText(a.balanceMinor, a.currency), fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(a.accountType, a.maskedId?.let { "••$it" }, syncedAgo(a.balanceFetchedAt)?.let { "synced $it" })
                        .joinToString(" · "),
                    fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                Text("Counted", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Switch(a.included, { vm.setIncluded(a, it) })
            }
            if (stale) Text("Connection ${a.connectionStatus}: reconnect this bank in Endute", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            else if (days != null && days <= 10) Text("Reconnect within ${maxOf(days, 0)} days", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- More / Settings

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 20.sp)
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainVm, onNav: (Sub) -> Unit) {
    val ctx = LocalContext.current
    val hasKey by vm.hasKey.collectAsState()
    val update by vm.update.collectAsState()
    val busy by vm.updateBusy.collectAsState()
    val hide by vm.hideBalances.collectAsState()
    val secure by vm.blockScreenshots.collectAsState()
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
        Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        NavRow("Rules", "Auto-categorise by text, bank and amount") { onNav(Sub.Rules) }
        NavRow("Categories", "Rename, merge, add subsections") { onNav(Sub.Categories) }
        NavRow("Budgets", "Monthly limits with alerts") { onNav(Sub.Budgets) }
        NavRow("Recurring payments", "Subscriptions and standing orders we spotted") { onNav(Sub.Recurring) }

        HorizontalDivider()
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Hide balances"); Text("Shows •••• instead of balances", fontSize = 12.sp) }
            Switch(hide, { vm.setHideBalances(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Block screenshots"); Text("Also hides the app in the recent-apps view", fontSize = 12.sp) }
            Switch(secure, { vm.setBlockScreenshots(it) })
        }

        HorizontalDivider()
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
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
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
        Text("Used for consent-expiry warnings, failed syncs and budget limits. Background sync runs about every 6 hours.", fontSize = 12.sp)
    }
}
