@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.domain.Insights
import java.time.LocalDate

@Composable
fun SubScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String = "",
    label: String,
    confirm: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value, { value = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()); onDismiss() }) { Text(confirm) }
        },
    )
}

// ---------------------------------------------------------------- categories

private val swatches = listOf(
    0xFF43A047, 0xFFFB8C00, 0xFF1E88E5, 0xFF8E24AA, 0xFFE91E63, 0xFF00ACC1, 0xFFE53935,
    0xFF3949AB, 0xFF6D4C41, 0xFF2E7D32, 0xFF00897B, 0xFF7CB342, 0xFFF4511E, 0xFF78909C,
).map { it.toInt() }

@Composable
fun CategoriesScreen(vm: MainVm) {
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    var addParent by remember { mutableStateOf<CategoryEntity?>(null) }
    var addTop by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<CategoryEntity?>(null) }
    var recolour by remember { mutableStateOf<CategoryEntity?>(null) }
    var merge by remember { mutableStateOf<CategoryEntity?>(null) }

    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }
    val tops = cats.filter { it.parentId == null || cats.none { p -> p.id == it.parentId } }.sortedBy { it.name }
    val rows = tops.flatMap { t -> listOf(t to 0) + cats.filter { it.parentId == t.id }.sortedBy { it.name }.map { it to 1 } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Keep it simple: add a subcategory only when you want finer detail. Reports roll subcategories up into their parent.", fontSize = 12.sp)
            Button(onClick = { addTop = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Add category") }
        }
        items(rows, key = { it.first.id }) { (c, depth) ->
            var menu by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().padding(start = (depth * 24).dp)) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColourDot(c.colour, 16)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Medium)
                        Text("${usage[c.id] ?: 0} transactions", fontSize = 12.sp)
                    }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp) }
                        DropdownMenu(menu, { menu = false }) {
                            if (c.parentId == null) DropdownMenuItem(text = { Text("Add subcategory") }, onClick = { menu = false; addParent = c })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = c })
                            DropdownMenuItem(text = { Text("Change colour") }, onClick = { menu = false; recolour = c })
                            DropdownMenuItem(text = { Text("Merge into… / delete") }, onClick = { menu = false; merge = c })
                        }
                    }
                }
            }
        }
    }

    if (addTop) TextPromptDialog("New category", label = "Name", confirm = "Add", onDismiss = { addTop = false }) { vm.createCategory(it) }
    addParent?.let { p ->
        TextPromptDialog("New subcategory of ${p.name}", label = "Name", confirm = "Add", onDismiss = { addParent = null }) { vm.createCategory(it, p.id) }
    }
    rename?.let { c ->
        TextPromptDialog("Rename", initial = c.name, label = "Name", confirm = "Save", onDismiss = { rename = null }) { vm.renameCategory(c, it) }
    }
    recolour?.let { c ->
        AlertDialog(
            onDismissRequest = { recolour = null },
            title = { Text("Colour for ${c.name}") },
            confirmButton = { TextButton(onClick = { recolour = null }) { Text("Close") } },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    swatches.forEach { col ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(col))
                                .clickable { vm.recolourCategory(c, col); recolour = null }
                        )
                    }
                }
            },
        )
    }
    merge?.let { c ->
        var target by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { merge = null },
            title = { Text("Merge \"${c.name}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Move its transactions and rules into another category, then remove it. Leave empty to move them to Other.", fontSize = 13.sp)
                    CategoryPicker(cats.filter { it.id != c.id }, usage, target, onSelect = { target = it }, label = "Merge into")
                }
            },
            dismissButton = { TextButton(onClick = { merge = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { vm.mergeCategory(c, target); merge = null }) { Text("Merge") } },
        )
    }
}

// ---------------------------------------------------------------- budgets

@Composable
fun BudgetProgress(name: String, spent: Long, limit: Long, colour: Int) {
    val frac = if (limit > 0) (spent.toFloat() / limit).coerceAtLeast(0f) else 0f
    val over = frac >= 1f
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${fmt(spent)} of ${fmt(limit)}", fontSize = 12.sp, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { frac.coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (over) MaterialTheme.colorScheme.error else Color(colour),
        )
    }
}

@Composable
fun BudgetsScreen(vm: MainVm) {
    val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.sh11fty.allbanks.data.BudgetEntity?>(null) }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val month = LocalDate.now().toString().substring(0, 7)
    val counted = accounts.filter { it.included }.map { it.id }.toSet()
    val thisMonth = remember(txns, accounts) {
        txns.filter { it.bookingDate.startsWith(month) && it.transferId == null && it.accountId in counted && it.amountMinor < 0 }
    }
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Monthly limits per category (subcategories count towards their parent). You get a notification at 90%.", fontSize = 12.sp)
            Button(onClick = { adding = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Add budget") }
        }
        items(budgets.sortedBy { catById[it.categoryId]?.name }, key = { it.categoryId }) { b ->
            val cat = catById[b.categoryId] ?: return@items
            val ids = Insights.withChildren(b.categoryId, cats)
            val spent = thisMonth.filter { it.categoryId in ids }.sumOf { -it.amountMinor }
            Column(Modifier.clickable { editing = b }) { BudgetProgress(categoryPath(cat, catById), spent, b.limitMinor, cat.colour) }
        }
    }

    if (adding) {
        var cat by remember { mutableStateOf<Long?>(null) }
        var limit by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryPicker(cats, usage, cat, onSelect = { cat = it })
                    OutlinedTextField(
                        limit, { limit = it }, label = { Text("Monthly limit £") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = cat != null && (limit.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = { vm.setBudget(cat!!, Math.round(limit.toDouble() * 100)); adding = false },
                ) { Text("Save") }
            },
        )
    }
    editing?.let { b ->
        var limit by remember(b) { mutableStateOf("%.2f".format(b.limitMinor / 100.0)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Limit for ${catById[b.categoryId]?.name ?: "category"}") },
            text = {
                OutlinedTextField(
                    limit, { limit = it }, label = { Text("Monthly limit £") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            dismissButton = {
                TextButton(onClick = { vm.setBudget(b.categoryId, null); editing = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (limit.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = { vm.setBudget(b.categoryId, Math.round(limit.toDouble() * 100)); editing = null },
                ) { Text("Save") }
            },
        )
    }
}

// ---------------------------------------------------------------- recurring

@Composable
fun RecurringScreen(vm: MainVm) {
    val txns by vm.txns.collectAsState()
    val rec = remember(txns) { Insights.recurring(txns) }
    val monthly = rec.sumOf { it.monthlyMinor }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Detected from repeated payments with a steady amount and interval. About ${fmt(monthly)} a month.", fontSize = 13.sp)
        }
        if (rec.isEmpty()) item { Text("Nothing detected yet. It needs at least three similar payments.", fontSize = 13.sp) }
        items(rec, key = { it.key + it.currency }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${every(r.everyDays)} · next ~${dayLabel(r.next.toString())} · seen ${r.count}×", fontSize = 12.sp)
                    }
                    Text(fmt(r.amountMinor, r.currency), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

fun every(days: Int) = when (days) {
    7 -> "weekly"; 14 -> "fortnightly"; 30 -> "monthly"; 91 -> "quarterly"; 365 -> "yearly"; else -> "every $days days"
}
