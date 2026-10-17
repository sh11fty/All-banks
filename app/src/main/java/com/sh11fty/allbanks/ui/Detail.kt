@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.Insights
import com.sh11fty.allbanks.domain.Suggest
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------- category detail

@Composable
fun CategoryDetailScreen(vm: MainVm, categoryId: Long) {
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var editing by remember { mutableStateOf<TxnEntity?>(null) }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val cat = catById[categoryId]
    val ids = remember(categoryId, cats) { Insights.withChildren(categoryId, cats) }
    val mine = remember(txns, ids) { txns.filter { it.categoryId in ids && it.transferId == null } }
    val spending = remember(mine) { mine.filter { it.amountMinor < 0 && !it.excluded } }

    val now = YearMonth.now()
    val months = remember { (5 downTo 0).map { now.minusMonths(it.toLong()) } }
    val perMonth = remember(spending) { Insights.monthlySpend(spending, months) }
    val thisMonth = perMonth.last()
    val lastMonth = perMonth.getOrNull(perMonth.size - 2) ?: 0L
    val merchants = remember(spending) {
        val m = now.toString()
        Insights.merchants(spending.filter { it.bookingDate.startsWith(m) }).take(5)
    }

    TxnList(
        mine, accounts.associateBy { it.id }, catById, Modifier.fillMaxSize(),
        header = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(cat, 52)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(cat?.let { categoryPath(it, catById) } ?: "Category", style = MaterialTheme.typography.titleLarge)
                        Text("${mine.size} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("This month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(thisMonth), style = MaterialTheme.typography.headlineMedium)
                        if (lastMonth > 0) {
                            val pct = ((thisMonth - lastMonth) * 100.0 / lastMonth).toInt()
                            Text(
                                "${if (pct >= 0) "Up" else "Down"} ${kotlin.math.abs(pct)}% on last month (${fmt(lastMonth)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pct > 0) MaterialTheme.colorScheme.error else positiveColour(),
                            )
                        }
                        BarChart(months.mapIndexed { i, m -> m.month.getDisplayName(TextStyle.SHORT, Locale.UK) to perMonth[i] })
                    }
                }
                if (merchants.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Top merchants this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            merchants.forEach {
                                Row {
                                    Text(it.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(fmt(it.spent), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
                Text("Transactions", style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { editing = it }
    editing?.let { TxnSheet(it, vm, onDismiss = { editing = null }) }
}

// ---------------------------------------------------------------- merchants

@Composable
fun MerchantsScreen(vm: MainVm) {
    val txns by vm.txns.collectAsState()
    val cats by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val all = remember(txns) { Insights.merchants(txns) }
    val shown = remember(all, query) { all.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) } }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Rename a merchant to tidy it everywhere. Give two merchants the same name to merge them.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SearchField(query, { query = it }, "Search merchants", Modifier.padding(16.dp, 10.dp))
        if (shown.isEmpty()) EmptyState("No merchants", if (query.isBlank()) "Merchants appear after your first sync." else "Nothing matches \"$query\".")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.name }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { renaming = m.name }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(m.topCategory?.let(catById::get), 44, fallback = m.name.firstOrNull()?.uppercase() ?: "?")
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${m.count} transaction${if (m.count == 1) "" else "s"}" + (m.topCategory?.let(catById::get)?.let { " · ${it.name}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(fmt(m.spent), style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    renaming?.let { old ->
        TextPromptDialog("Rename merchant", initial = old, label = "Name", confirm = "Rename", onDismiss = { renaming = null }) { vm.renameMerchant(old, it) }
    }
}

/** Two small "what changed" lines for the Overview. */
@Composable
fun InsightsCard(
    changes: List<Insights.Change>,
    merchants: List<Insights.MerchantTotal>,
    catById: Map<Long, com.sh11fty.allbanks.data.CategoryEntity>,
    onOpenCategory: (Long) -> Unit,
) {
    if (changes.isEmpty() && merchants.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (changes.isNotEmpty()) {
                Text("Biggest changes vs last month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                changes.forEach { ch ->
                    val c = ch.categoryId?.let(catById::get)
                    Row(
                        Modifier.fillMaxWidth().let { m -> if (c != null) m.clickable { onOpenCategory(c.id) } else m },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(c, 32)
                        Spacer(Modifier.width(10.dp))
                        Text(c?.name ?: "Uncategorised", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (if (ch.delta > 0) "+" else "−") + fmt(kotlin.math.abs(ch.delta)),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ch.delta > 0) MaterialTheme.colorScheme.error else positiveColour(),
                        )
                    }
                }
            }
            if (merchants.isNotEmpty()) {
                Text("Top merchants this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                merchants.forEach {
                    Row {
                        Text(it.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt(it.spent), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
