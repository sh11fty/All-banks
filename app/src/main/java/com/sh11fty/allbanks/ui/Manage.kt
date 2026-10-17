@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.MoreVert
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
fun SubScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = MaterialTheme.colorScheme.onSurface)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
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
fun CategoriesScreen(vm: MainVm, onOpen: (Long) -> Unit) {
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    var addParent by remember { mutableStateOf<CategoryEntity?>(null) }
    var addTop by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<CategoryEntity?>(null) }
    var recolour by remember { mutableStateOf<CategoryEntity?>(null) }
    var reicon by remember { mutableStateOf<CategoryEntity?>(null) }
    var merge by remember { mutableStateOf<CategoryEntity?>(null) }

    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }
    val tops = cats.filter { it.parentId == null || cats.none { p -> p.id == it.parentId } }.sortedBy { it.name }
    val rows = tops.flatMap { t -> listOf(t to 0) + cats.filter { it.parentId == t.id }.sortedBy { it.name }.map { it to 1 } }

    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Add a subcategory only when you want finer detail. Charts and budgets roll subcategories up into their parent.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Add category", { addTop = true })
        }
        item {
            ListGroup {
                rows.forEachIndexed { i, (c, depth) ->
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(start = (16 + depth * 28).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(c, if (depth == 0) 40 else 32)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${usage[c.id] ?: 0} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconAction(Icons.Filled.MoreVert, "Options for ${c.name}", { menu = true })
                            DropdownMenu(menu, { menu = false }) {
                                if (c.parentId == null) DropdownMenuItem(text = { Text("Add subcategory") }, onClick = { menu = false; addParent = c })
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = c })
                                DropdownMenuItem(text = { Text("Change icon") }, onClick = { menu = false; reicon = c })
                                DropdownMenuItem(text = { Text("Change colour") }, onClick = { menu = false; recolour = c })
                                DropdownMenuItem(text = { Text("Merge into… / delete") }, onClick = { menu = false; merge = c })
                            }
                        }
                    }
                    if (i < rows.lastIndex) Hairline(68)
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
    reicon?.let { c ->
        AlertDialog(
            onDismissRequest = { reicon = null },
            title = { Text("Icon for ${c.name}") },
            confirmButton = { TextButton(onClick = { reicon = null }) { Text("Close") } },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.sh11fty.allbanks.domain.DefaultCategories.iconChoices.forEach { e ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.setCategoryIcon(c, e); reicon = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
            },
        )
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
    val target = if (limit > 0) (spent.toFloat() / limit).coerceAtLeast(0f) else 0f
    val frac by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(600), label = "budget")
    val over = target >= 1f
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (over) "Over · " else "") + "${fmt(spent)} of ${fmt(limit)}",
                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        txns.filter { it.bookingDate.startsWith(month) && it.transferId == null && it.accountId in counted && it.amountMinor < 0 && !it.excluded }
    }
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }

    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Monthly limits per category. Subcategories count towards their parent, and you get a notification at 90%.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Add budget", { adding = true })
        }
        item {
            if (budgets.isEmpty()) {
                ListGroup { EmptyState("No budgets yet", "Set a monthly limit for a category to track it here.") }
            } else {
                ListGroup {
                    val sorted = budgets.sortedBy { catById[it.categoryId]?.name }
                    sorted.forEachIndexed { i, b ->
                        val cat = catById[b.categoryId] ?: return@forEachIndexed
                        val ids = Insights.withChildren(b.categoryId, cats)
                        val spent = thisMonth.filter { it.categoryId in ids }.sumOf { -it.amountMinor }
                        Column(Modifier.fillMaxWidth().clickable { editing = b }.padding(16.dp)) {
                            BudgetProgress(categoryPath(cat, catById), spent, b.limitMinor, cat.colour)
                        }
                        if (i < sorted.lastIndex) Hairline()
                    }
                }
            }
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
    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Found from repeated payments with a steady amount and interval. About ${fmt(monthly)} a month.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            if (rec.isEmpty()) {
                ListGroup { EmptyState("Nothing detected yet", "It needs at least three similar payments in a row.") }
            } else {
                ListGroup {
                    rec.forEachIndexed { i, r ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${every(r.everyDays).replaceFirstChar { it.uppercase() }} · next ~${dayLabel(r.next.toString())} · seen ${r.count}×",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(fmt(r.amountMinor), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        }
                        if (i < rec.lastIndex) Hairline()
                    }
                }
            }
        }
    }
}

fun every(days: Int) = when (days) {
    7 -> "weekly"; 14 -> "fortnightly"; 30 -> "monthly"; 91 -> "quarterly"; 365 -> "yearly"; else -> "every $days days"
}
