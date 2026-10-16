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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.data.daysUntil
import com.sh11fty.allbanks.domain.Insights
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Filled.Home),
    Activity("Activity", Icons.AutoMirrored.Filled.List),
    Review("Review", Icons.Filled.Check),
    More("More", Icons.Filled.Settings),
}

private enum class Sub(val title: String) {
    Rules("Rules"), Categories("Categories"), Merchants("Merchants"), Budgets("Budgets"), Recurring("Recurring payments")
}

/** Keeps content a readable width on tablets and in landscape. */
private val ContentMaxWidth = 640.dp

@Composable
fun MainScaffold(vm: MainVm) {
    var tab by remember { mutableStateOf(Tab.Overview) }
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var categoryDetail by remember { mutableStateOf<Long?>(null) }
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
    val undo by vm.undo.collectAsState()
    LaunchedEffect(undo?.id) {
        val item = undo ?: return@LaunchedEffect
        val r = snack.showSnackbar(item.snapshot.label, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long)
        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.performUndo(item)
    }
    LaunchedEffect(secure) {
        findActivity(ctx)?.window?.let {
            if (secure) it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    CompositionLocalProvider(LocalHide provides hide) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t; selectedAccount = null; sub = null; categoryDetail = null },
                            icon = {
                                if (t == Tab.Review && reviewCount > 0) {
                                    BadgedBox(badge = { Badge { Text(if (reviewCount > 99) "99+" else "$reviewCount") } }) { Icon(t.icon, t.label) }
                                } else Icon(t.icon, t.label)
                            },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).statusBarsPadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                    val s = sub
                    val acct = selectedAccount
                    when {
                        s != null -> {
                            BackHandler { sub = null }
                            SubScaffold(s.title, onBack = { sub = null }) {
                                when (s) {
                                    Sub.Rules -> RulesScreen(vm)
                                    Sub.Categories -> CategoriesScreen(vm) { categoryDetail = it }
                                    Sub.Merchants -> MerchantsScreen(vm)
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
                                onSeeActivity = { tab = Tab.Activity },
                                onOpenCategory = { categoryDetail = it },
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
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title, action, onAction)
        content()
    }
}

@Composable
private fun StatPill(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
    }
}

@Composable
private fun HeroCard(
    totals: Map<String, Long>,
    accountCount: Int,
    trend: List<Long>,
    hide: Boolean,
    onToggleHide: () -> Unit,
    moneyIn: Long,
    moneyOut: Long,
    updated: String?,
    syncing: Boolean,
    canSync: Boolean,
    onSync: () -> Unit,
) {
    val white = Color.White
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Brand.Ink2, Brand.Ink)))
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(Brand.Mint.copy(alpha = 0.18f), Color.Transparent), center = Offset(1000f, 0f), radius = 900f)
            )
        )
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge, color = white.copy(alpha = 0.7f))
                    Text(
                        "$accountCount accounts" + (updated?.let { " · updated $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = white.copy(alpha = 0.5f),
                    )
                }
                Text(
                    if (hide) "Show" else "Hide", style = MaterialTheme.typography.labelMedium, color = white,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(white.copy(alpha = 0.1f))
                        .clickable(onClick = onToggleHide).padding(horizontal = 12.dp, vertical = 7.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(white.copy(alpha = 0.1f))
                        .clickable(enabled = canSync && !syncing, onClick = onSync),
                    contentAlignment = Alignment.Center,
                ) {
                    if (syncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = white)
                    else Icon(Icons.Filled.Refresh, "Refresh", tint = white.copy(alpha = if (canSync) 1f else 0.4f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (totals.isEmpty()) Text("—", style = MaterialTheme.typography.displayLarge, color = white)
            totals.entries.forEachIndexed { i, (cur, v) ->
                Text(
                    balText(v, cur),
                    style = if (i == 0) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleLarge,
                    color = white,
                )
            }
            if (trend.size > 1 && !hide) {
                LineChart(trend, Modifier.fillMaxWidth().height(56.dp).padding(top = 10.dp), colour = Brand.Mint)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("In this month", fmt(moneyIn), Brand.Mint, Modifier.weight(1f))
                StatPill("Out this month", fmt(moneyOut), Color(0xFFFF8A8F), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun OverviewScreen(
    vm: MainVm,
    onOpenAccount: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onSeeActivity: () -> Unit,
    onOpenCategory: (Long) -> Unit,
) {
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val cats by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val includeSavings by vm.includeSavings.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val hide by vm.hideBalances.collectAsState()
    var editing by remember { mutableStateOf<TxnEntity?>(null) }

    val counted = accounts.filter { it.included && (includeSavings || it.accountType != "savings") }
    val totals = counted.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.balanceMinor ?: 0L } }
    val countedIds = counted.map { it.id }.toSet()

    val now = LocalDate.now()
    val month = now.toString().substring(0, 7)
    val lastMonth = now.minusMonths(1).toString().substring(0, 7)
    val real = remember(txns, accounts, includeSavings) { txns.filter { it.transferId == null && it.accountId in countedIds } }
    val thisMonth = remember(real) { real.filter { it.bookingDate.startsWith(month) } }
    val prevMonth = remember(real) { real.filter { it.bookingDate.startsWith(lastMonth) } }
    val moneyIn = thisMonth.filter { it.amountMinor > 0 && !it.excluded }.sumOf { it.amountMinor }
    val moneyOut = thisMonth.filter { it.amountMinor < 0 && !it.excluded }.sumOf { -it.amountMinor }
    val prevOut = prevMonth.filter { it.amountMinor < 0 && !it.excluded }.sumOf { -it.amountMinor }

    val catById = remember(cats) { cats.associateBy { it.id } }
    val accById = remember(accounts) { accounts.associateBy { it.id } }
    val parts = remember(thisMonth, cats) {
        Insights.spendByTopCategory(thisMonth, cats).entries.sortedByDescending { it.value }.take(6)
    }
    fun partColour(id: Long?): Color = id?.let(catById::get)?.let { Color(it.colour) } ?: Color.Gray

    val months6 = remember { (5 downTo 0).map { java.time.YearMonth.now().minusMonths(it.toLong()) } }
    val monthly6 = remember(real) { Insights.monthlySpend(real, months6) }
    val insightChanges = remember(thisMonth, prevMonth, cats) { Insights.changes(thisMonth, prevMonth, cats) }
    val insightMerchants = remember(thisMonth) { Insights.merchants(thisMonth.filter { it.amountMinor < 0 }).take(3) }

    val trend = remember(counted, txns) { Insights.balanceTrend(counted, txns, 30) }
    val recurring = remember(txns) { Insights.recurring(txns) }
    val soon = recurring.filter { !it.next.isAfter(now.plusDays(7)) }
    val recent = remember(txns) { txns.take(6) }
    val lastSync = accounts.mapNotNull { it.balanceFetchedAt }.maxOrNull()

    val problems = accounts.filter {
        it.connectionStatus in setOf("expired", "suspended", "error", "rejected") || (daysUntil(it.consentRenewalDue) ?: 99) <= 10
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column {
                Text("Overview", style = MaterialTheme.typography.headlineSmall)
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (problems.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Reconnect needed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                problems.map { it.institution }.distinct().joinToString(", ") + " — reconnect in Endute.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
        item {
            HeroCard(
                totals, counted.size, trend, hide, { vm.setHideBalances(!hide) }, moneyIn, moneyOut,
                syncedAgo(lastSync), syncing, hasKey, { vm.sync() },
            )
            if (!hasKey) {
                Text(
                    "Add your Endute API key in More to start.", Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accounts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    FilterChip(includeSavings, { vm.setIncludeSavings(!includeSavings) }, label = { Text("Savings in total") })
                }
                if (accounts.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) { EmptyState("No accounts yet", "Add your Endute key in More, then tap refresh.") }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        accounts.forEachIndexed { i, a ->
                            AccountRow(a) { onOpenAccount(a.id) }
                            if (i < accounts.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Section("Recent activity", "See all", onSeeActivity) {
                    Card(Modifier.fillMaxWidth()) {
                        recent.forEachIndexed { i, t ->
                            TxnRow(t, accById[t.accountId], t.categoryId?.let(catById::get)) { editing = t }
                            if (i < recent.lastIndex) HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        item {
            Section("Spending this month") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text(fmt(moneyOut), style = MaterialTheme.typography.headlineMedium)
                                Text("Excludes internal transfers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (prevOut > 0) {
                                val pct = ((moneyOut - prevOut) * 100.0 / prevOut).toInt()
                                val up = pct > 0
                                Tag(
                                    "${if (up) "▲" else "▼"} ${kotlin.math.abs(pct)}% vs last month",
                                    if (up) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                )
                            }
                        }
                        if (parts.isEmpty()) {
                            Text("No spending recorded yet this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Donut(parts.map { it.value.toFloat() to partColour(it.key) }, Modifier.size(112.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    parts.forEach { (id, v) ->
                                        Row(
                                            Modifier.fillMaxWidth().let { m -> if (id != null) m.clickable { onOpenCategory(id) } else m },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CategoryBadge(id?.let(catById::get), 24)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                id?.let(catById::get)?.name ?: "Uncategorised", Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(fmt(v), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Section("Last 6 months") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        BarChart(months6.mapIndexed { i, m -> m.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) to monthly6[i] })
                    }
                }
            }
        }

        if (insightChanges.isNotEmpty() || insightMerchants.isNotEmpty()) {
            item { InsightsCard(insightChanges, insightMerchants, catById, onOpenCategory) }
        }

        if (budgets.isNotEmpty()) {
            item {
                Section("Budgets", "Manage", onOpenBudgets) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            budgets.mapNotNull { b -> catById[b.categoryId]?.let { b to it } }
                                .map { (b, c) ->
                                    val ids = Insights.withChildren(b.categoryId, cats)
                                    Triple(c, b, thisMonth.filter { it.categoryId in ids && it.amountMinor < 0 && !it.excluded }.sumOf { -it.amountMinor })
                                }
                                .sortedByDescending { it.third.toDouble() / it.second.limitMinor }
                                .take(4)
                                .forEach { (c, b, spent) -> BudgetProgress(c.name, spent, b.limitMinor, c.colour) }
                        }
                    }
                }
            }
        }

        if (soon.isNotEmpty()) {
            item {
                Section("Coming up", "All recurring", onOpenRecurring) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            soon.forEach {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(it.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(dayLabel(it.next.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(fmt(it.amountMinor, it.currency), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { TxnSheet(it, vm, onDismiss = { editing = null }) }
}

@Composable
private fun AccountRow(a: AccountEntity, onClick: () -> Unit) {
    val days = daysUntil(a.consentRenewalDue)
    val stale = a.connectionStatus in setOf("expired", "suspended", "error", "rejected")
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BankAvatar(a.institution, 42)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.institution, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(a.name, a.maskedId?.let { "••$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (stale) Text("Reconnect in Endute (${a.connectionStatus})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            else if (days != null && days <= 10) Text("Reconnect within ${maxOf(days, 0)} days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(balText(a.balanceMinor, a.currency), style = MaterialTheme.typography.titleSmall)
            if (!a.included) Text("Not counted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else syncedAgo(a.balanceFetchedAt)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- More / Settings

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineSmall)

        Group("Organise") {
            Card(Modifier.fillMaxWidth()) {
                NavRow("Rules", "Auto-categorise by text, bank and amount") { onNav(Sub.Rules) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                NavRow("Categories", "Icons, subsections, merge") { onNav(Sub.Categories) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                NavRow("Merchants", "Tidy names, merge duplicates") { onNav(Sub.Merchants) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                NavRow("Budgets", "Monthly limits with alerts") { onNav(Sub.Budgets) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                NavRow("Recurring payments", "Subscriptions and standing orders we spotted") { onNav(Sub.Recurring) }
            }
        }

        Group("Privacy") {
            Card(Modifier.fillMaxWidth()) {
                ToggleRow("Hide balances", "Shows •••• instead of balances", hide) { vm.setHideBalances(it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                ToggleRow("Block screenshots", "Also hides the app in the recent-apps view", secure) { vm.setBlockScreenshots(it) }
            }
        }

        Group("Endute connection") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (hasKey) "A key is stored, encrypted in the Android Keystore." else "No key stored yet.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        key, { key = it }, label = { Text("Paste edk_… key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
                        if (hasKey) OutlinedButton(onClick = { vm.saveKey("") }) { Text("Remove") }
                    }
                }
            }
        }

        Group("App updates") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Installed ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                    update?.let { Text("New: ${it.versionName}\n${it.notes}", style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (update != null) vm.installUpdate() else vm.checkUpdate() }, enabled = !busy) {
                            Text(if (update != null) "Update to ${update!!.versionName}" else "Check for update")
                        }
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        Group("Encrypted backup") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Backup passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("all-banks-backup.abk") }, enabled = pass.length >= 8) { Text("Export") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = pass.length >= 8) { Text("Restore") }
                    }
                    Text(
                        "Passphrase needs 8+ characters. The API key is not included in backups.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Group("Alerts") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Consent-expiry warnings, failed syncs and budget limits. Background sync runs about every 6 hours.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }) { Text("Allow notifications") }
                }
            }
        }
    }
}
