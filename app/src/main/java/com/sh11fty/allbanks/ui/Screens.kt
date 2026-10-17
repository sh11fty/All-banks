@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)

package com.sh11fty.allbanks.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.BuildConfig
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.BudgetEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.data.daysUntil
import com.sh11fty.allbanks.domain.Insights
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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

/** Where the user currently is. Used to animate between screens and to drive back navigation. */
private data class Route(val tab: Tab, val sub: Sub? = null, val account: String? = null, val category: Long? = null) {
    val depth: Int get() = if (sub != null || account != null || category != null) 1 else 0
}

/** Keeps content a readable width on tablets and in landscape. */
private val ContentMaxWidth = 640.dp

@Composable
fun MainScaffold(vm: MainVm) {
    var route by remember { mutableStateOf(Route(Tab.Overview)) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val hide by vm.hideBalances.collectAsState()
    val secure by vm.blockScreenshots.collectAsState()
    val undo by vm.undo.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
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

    // One back step goes from a secondary screen to its tab; from a category opened inside Categories it returns there.
    BackHandler(enabled = route.depth > 0) {
        route = if (route.category != null && route.sub == Sub.Categories) route.copy(category = null) else Route(route.tab)
    }

    CompositionLocalProvider(LocalHide provides hide) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = route.tab == t,
                            onClick = { route = Route(t) },
                            icon = {
                                if (t == Tab.Review && reviewCount > 0) {
                                    BadgedBox(badge = { Badge { Text(if (reviewCount > 99) "99+" else "$reviewCount") } }) { Icon(t.icon, t.label) }
                                } else Icon(t.icon, t.label)
                            },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
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
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            if (targetState.depth > initialState.depth)
                                (slideInHorizontally(tween(220)) { it / 8 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120))
                            else if (targetState.depth < initialState.depth)
                                fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(200)) { it / 8 } + fadeOut(tween(140)))
                            else fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                        },
                        label = "route",
                    ) { r ->
                        val back = { route = if (r.category != null && r.sub == Sub.Categories) r.copy(category = null) else Route(r.tab) }
                        when {
                            r.category != null -> SubScaffold("Category", onBack = back) { CategoryDetailScreen(vm, r.category) }
                            r.sub != null -> SubScaffold(r.sub.title, onBack = back) {
                                when (r.sub) {
                                    Sub.Rules -> RulesScreen(vm)
                                    Sub.Categories -> CategoriesScreen(vm) { route = r.copy(category = it) }
                                    Sub.Merchants -> MerchantsScreen(vm)
                                    Sub.Budgets -> BudgetsScreen(vm)
                                    Sub.Recurring -> RecurringScreen(vm)
                                }
                            }
                            r.account != null -> SubScaffold("Account", onBack = back) { AccountScreen(vm, r.account) }
                            else -> when (r.tab) {
                                Tab.Overview -> OverviewScreen(
                                    vm,
                                    onOpenAccount = { route = Route(Tab.Overview, account = it) },
                                    onOpenBudgets = { route = Route(Tab.Overview, sub = Sub.Budgets) },
                                    onOpenRecurring = { route = Route(Tab.Overview, sub = Sub.Recurring) },
                                    onSeeActivity = { route = Route(Tab.Activity) },
                                    onOpenCategory = { route = Route(Tab.Overview, category = it) },
                                )
                                Tab.Activity -> ActivityScreen(vm)
                                Tab.Review -> ReviewScreen(vm)
                                Tab.More -> SettingsScreen(vm) { route = Route(Tab.More, sub = it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Overview

internal fun syncedAgo(iso: String?): String? {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, action, onAction)
        content()
    }
}

/** Balance first: one GBP total, then this month's cash flow, in a single dark panel. */
@Composable
fun HeroCard(
    total: Long,
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
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(Brand.Ink2, Brand.Ink)))
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(Brand.Mint.copy(alpha = 0.12f), Color.Transparent), center = Offset(1000f, 0f), radius = 800f)
            )
        )
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total balance", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = white.copy(alpha = 0.75f))
                Text(
                    if (hide) "Show" else "Hide", style = MaterialTheme.typography.labelMedium, color = white,
                    modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(onClick = onToggleHide)
                        .semantics { contentDescription = if (hide) "Show balances" else "Hide balances" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Box(
                    Modifier.minimumInteractiveComponentSize().size(36.dp).clip(CircleShape)
                        .clickable(enabled = canSync && !syncing, onClick = onSync)
                        .semantics { contentDescription = if (syncing) "Refreshing" else "Refresh accounts" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = white)
                    else Icon(Icons.Filled.Refresh, null, tint = white.copy(alpha = if (canSync) 1f else 0.4f), modifier = Modifier.size(20.dp))
                }
            }
            Text(balText(total, "GBP"), style = MaterialTheme.typography.displayMedium, color = white, maxLines = 1)
            Text(
                "$accountCount account${if (accountCount == 1) "" else "s"}" + (updated?.let { " · updated $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = white.copy(alpha = 0.6f),
            )
            if (trend.size > 1 && !hide) {
                LineChart(trend, Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp), colour = Brand.Mint)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = Dimens.Hairline.dp, color = white.copy(alpha = 0.18f))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("↓ Money in · this month", style = MaterialTheme.typography.labelSmall, color = white.copy(alpha = 0.65f))
                    Text(fmt(moneyIn), style = MaterialTheme.typography.titleMedium, color = Brand.Mint, maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    Text("↑ Money out · this month", style = MaterialTheme.typography.labelSmall, color = white.copy(alpha = 0.65f))
                    Text(fmt(moneyOut), style = MaterialTheme.typography.titleMedium, color = white, maxLines = 1)
                }
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

    OverviewContent(
        accounts, txns, cats, budgets, includeSavings, syncing, hasKey, hide,
        onToggleHide = { vm.setHideBalances(!hide) },
        onToggleSavings = { vm.setIncludeSavings(!includeSavings) },
        onSync = { vm.sync() },
        onOpenAccount = onOpenAccount, onOpenBudgets = onOpenBudgets, onOpenRecurring = onOpenRecurring,
        onSeeActivity = onSeeActivity, onOpenCategory = onOpenCategory, onOpenTxn = { editing = it },
    )
    editing?.let { TxnSheet(it, vm, onDismiss = { editing = null }) }
}

/** The dashboard, free of the view-model so it can be rendered with any data. */
@Composable
fun OverviewContent(
    accounts: List<AccountEntity>,
    txns: List<TxnEntity>,
    cats: List<CategoryEntity>,
    budgets: List<BudgetEntity>,
    includeSavings: Boolean,
    syncing: Boolean,
    hasKey: Boolean,
    hide: Boolean,
    onToggleHide: () -> Unit,
    onToggleSavings: () -> Unit,
    onSync: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onSeeActivity: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenTxn: (TxnEntity) -> Unit,
) {
    // All accounts are in GBP, so the combined balance is a plain sum.
    val counted = accounts.filter { it.included && (includeSavings || it.accountType != "savings") }
    val total = counted.sumOf { it.balanceMinor ?: 0L }
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

    val months6 = remember { (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) } }
    val monthly6 = remember(real) { Insights.monthlySpend(real, months6) }
    val insightChanges = remember(thisMonth, prevMonth, cats) { Insights.changes(thisMonth, prevMonth, cats) }
    val insightMerchants = remember(thisMonth) { Insights.merchants(thisMonth.filter { it.amountMinor < 0 }).take(3) }
    val trend = remember(counted, txns) { Insights.balanceTrend(counted, txns, 30) }
    val recurring = remember(txns) { Insights.recurring(txns) }
    val soon = recurring.filter { !it.next.isAfter(now.plusDays(7)) }
    val recent = remember(txns) { txns.take(5) }
    val lastSync = accounts.mapNotNull { it.balanceFetchedAt }.maxOrNull()

    val broken = accounts.filter { it.connectionStatus in setOf("expired", "suspended", "error", "rejected") }
    val expiring = accounts.filter { it !in broken && (daysUntil(it.consentRenewalDue) ?: 99) <= 10 }

    Column(Modifier.fillMaxSize()) {
        LoadingBar(syncing)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap.dp)) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Overview", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (broken.isNotEmpty() || expiring.isNotEmpty()) {
                item {
                    val names = (broken + expiring).map { it.institution }.distinct().joinToString(", ")
                    StatusBanner(
                        title = if (broken.isNotEmpty()) "Connection lost" else "Reconnect soon",
                        body = "$names — reconnect in Endute so balances keep updating.",
                        kind = if (broken.isNotEmpty()) BannerKind.Error else BannerKind.Warning,
                    )
                }
            }
            item {
                HeroCard(total, counted.size, trend, hide, onToggleHide, moneyIn, moneyOut, syncedAgo(lastSync), syncing, hasKey, onSync)
                if (!hasKey) {
                    StatusBanner(
                        "Add your Endute key", "Open More and paste your API key to start syncing.", BannerKind.Info,
                        Modifier.padding(top = 10.dp),
                    )
                }
            }

            item {
                Section("Recent activity", if (recent.isNotEmpty()) "See all" else null, onSeeActivity) {
                    if (recent.isEmpty()) {
                        ListGroup { EmptyState("No transactions yet", if (hasKey) "Tap refresh to fetch your latest activity." else "They'll appear here after your first sync.") }
                    } else {
                        ListGroup {
                            recent.forEachIndexed { i, t ->
                                TxnRow(t, accById[t.accountId], t.categoryId?.let(catById::get)) { onOpenTxn(t) }
                                if (i < recent.lastIndex) Hairline(70)
                            }
                        }
                    }
                }
            }

            item {
                Section("Accounts") {
                    if (accounts.isEmpty()) {
                        ListGroup { EmptyState("No accounts yet", "Add your Endute key in More, then tap refresh.") }
                    } else {
                        ListGroup {
                            accounts.forEachIndexed { i, a ->
                                AccountRow(a) { onOpenAccount(a.id) }
                                if (i < accounts.lastIndex) Hairline(66)
                            }
                            Hairline(0)
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onToggleSavings).minimumInteractiveComponentSize().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Include savings in total", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(includeSavings, null)
                            }
                        }
                    }
                }
            }

            if (soon.isNotEmpty()) {
                item {
                    Section("Coming up", "All recurring", onOpenRecurring) {
                        ListGroup {
                            soon.forEachIndexed { i, r ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(dayLabel(r.next.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(fmt(r.amountMinor), style = MaterialTheme.typography.titleSmall)
                                }
                                if (i < soon.lastIndex) Hairline()
                            }
                        }
                    }
                }
            }

            if (budgets.isNotEmpty()) {
                item {
                    Section("Budgets", "Manage", onOpenBudgets) {
                        ListGroup {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

            item {
                Section("Spending this month") {
                    ListGroup {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Donut(parts.map { it.value.toFloat() to partColour(it.key) }, Modifier.size(104.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        parts.forEach { (id, v) ->
                                            Row(
                                                Modifier.fillMaxWidth().let { m -> if (id != null) m.clickable { onOpenCategory(id) } else m }
                                                    .heightIn(min = 36.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CategoryBadge(id?.let(catById::get), 22)
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

            if (insightChanges.isNotEmpty() || insightMerchants.isNotEmpty()) {
                item { Section("Insights") { InsightsCard(insightChanges, insightMerchants, catById, onOpenCategory) } }
            }

            item {
                Section("Last 6 months") {
                    ListGroup {
                        Column(Modifier.padding(16.dp)) {
                            BarChart(months6.mapIndexed { i, m -> m.month.getDisplayName(TextStyle.SHORT, Locale.UK) to monthly6[i] })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccountRow(a: AccountEntity, onClick: () -> Unit) {
    val days = daysUntil(a.consentRenewalDue)
    val broken = a.connectionStatus in setOf("expired", "suspended", "error", "rejected")
    val expiring = !broken && days != null && days <= 10
    val status = when {
        broken -> "Connection lost" to MaterialTheme.colorScheme.error
        expiring -> "Reconnect in ${maxOf(days ?: 0, 0)}d" to Semantic.warning()
        else -> (syncedAgo(a.balanceFetchedAt)?.let { "Synced $it" } ?: "Connected") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${a.institution}, ${a.name}, ${status.first}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankAvatar(a.institution, 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.institution, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(a.name, a.maskedId?.let { "••$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(balText(a.balanceMinor, "GBP"), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                (if (!a.included) "Not counted · " else "") + status.first,
                style = MaterialTheme.typography.labelSmall, color = status.second, maxLines = 1,
            )
        }
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
