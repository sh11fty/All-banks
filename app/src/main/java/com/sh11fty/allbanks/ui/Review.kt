@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CandidateEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.Repository
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.DefaultCategories
import com.sh11fty.allbanks.domain.RuleEngine
import com.sh11fty.allbanks.domain.Suggest

// ---------------------------------------------------------------- shared helpers

@Composable
internal fun rememberOtherId(cats: List<CategoryEntity>): Long? =
    remember(cats) { cats.firstOrNull { it.name == DefaultCategories.OTHER && it.parentId == null }?.id }

private fun sourceText(t: TxnEntity, rules: List<RuleEntity>): String = when (t.categorySource) {
    3 -> "You chose this category"
    2 -> rules.firstOrNull { it.id == t.ruleId }?.let { "Set by your rule \"${it.pattern}\"" } ?: "Set by a rule"
    1 -> "From your bank's label" + (t.providerCategory?.let { " (\"$it\")" } ?: "")
    else -> "No category yet"
}

// ---------------------------------------------------------------- transaction sheet

/**
 * Everything about one transaction: suggestions, category, look-alikes, "remember", exclude, note, details.
 * [onSaved] replaces plain dismissal so Review can move straight on to the next item.
 */
@Composable
fun TxnSheet(
    t: TxnEntity,
    vm: MainVm,
    onDismiss: () -> Unit,
    onSaved: (() -> Unit)? = null,
    saveLabel: String = "Save",
    progress: String? = null,
    startExpanded: Boolean = false,
) {
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val rules by vm.rules.collectAsState()
    val haptic = LocalHapticFeedback.current
    val catById = remember(cats) { cats.associateBy { it.id } }
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }
    val index = remember(txns) { Suggest.index(txns) }
    val account = accounts.firstOrNull { it.id == t.accountId }
    val title = Suggest.title(t)

    val bankDefault = remember(cats, t.providerCategory) {
        DefaultCategories.map(t.providerCategory)?.let { n -> cats.firstOrNull { it.name.equals(n, true) && it.parentId == null }?.id }
    }
    val suggestions = remember(t, index, bankDefault) { Suggest.suggestions(t, index, bankDefault) }

    var chosen by remember(t.id) { mutableStateOf(t.categoryId) }
    var note by remember(t.id) { mutableStateOf(t.note.orEmpty()) }
    var exclude by remember(t.id) { mutableStateOf(t.excluded) }
    var applySimilar by remember(t.id) { mutableStateOf(true) }
    var learnRule by remember(t.id) { mutableStateOf(t.categorySource != 3) }
    var pattern by remember(t.id) { mutableStateOf(Suggest.ruleTarget(t).second) }
    var details by remember(t.id) { mutableStateOf(startExpanded) }

    val similar = remember(t, txns, chosen) {
        Suggest.similar(t, txns) { !it.categoryManual && it.categoryId != chosen }
    }
    val field = if (pattern == Suggest.ruleTarget(t).second) Suggest.ruleTarget(t).first else "description"
    val broad = remember(pattern, field, txns) {
        if (pattern.isBlank()) 0 else {
            val d = RuleEntity(priority = 0, field = field, matchType = "contains", pattern = pattern.trim(), categoryId = 1)
            txns.filter { RuleEngine.matches(d, it) }.map { Suggest.merchantKey(it) }.distinct().size
        }
    }
    val canSave = chosen != null || note != t.note.orEmpty() || exclude != t.excluded

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (progress != null) Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(dayLabel(t.bookingDate), account?.let { accountLabel(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(fmt(t.amountMinor, t.currency), style = MaterialTheme.typography.titleLarge)
            }

            if (t.transferId != null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Linked as an internal transfer", Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.unlink(t); onDismiss() }) { Text("Unlink") }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.mapNotNull { catById[it] }.forEach { c ->
                            FilterChip(chosen == c.id, { chosen = c.id }, label = { Text("${c.icon.orEmpty()} ${c.name}".trim()) })
                        }
                    }
                }
                CategoryPicker(
                    cats = cats, usage = usage, selectedId = chosen,
                    onSelect = { chosen = it },
                    onCreate = { name -> vm.createCategory(name) { chosen = it } },
                    label = if (suggestions.isEmpty()) "Pick a category" else "Or pick another",
                )
                Text(sourceText(t, rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (chosen != null && similar.isNotEmpty()) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Also apply to ${similar.size} similar", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    similar.take(3).joinToString(" · ") { "${Suggest.title(it)} ${dayLabel(it.bookingDate)}" } +
                                        if (similar.size > 3) " · +${similar.size - 3} more" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(applySimilar, { applySimilar = it })
                        }
                    }
                }
            }

            if (chosen != null) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Always do this", style = MaterialTheme.typography.titleSmall)
                                Text("Categorise future matches automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(learnRule, { learnRule = it })
                        }
                        if (learnRule) {
                            OutlinedTextField(
                                pattern, { pattern = it }, label = { Text("When the text contains") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            if (pattern.isNotBlank() && (pattern.trim().length < 4 || broad > 3)) {
                                Text(
                                    if (broad > 3) "Broad: this text appears in $broad different merchants." else "Very short text may match unrelated merchants.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Leave out of spending", style = MaterialTheme.typography.titleSmall)
                    Text("For refunds and one-offs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(exclude, { exclude = it })
            }

            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)

            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details ▴" else "All details ▾") }
            if (details) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) { TxnDetails(t, account, t.categoryId?.let(catById::get)?.let { categoryPath(it, catById) }) }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = canSave,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val draft = if (learnRule && chosen != null && pattern.isNotBlank()) Repository.RuleDraft(field, pattern) else null
                    vm.saveTxn(t, chosen, if (applySimilar) similar else emptyList(), draft, exclude, note) { (onSaved ?: onDismiss)() }
                },
            ) { Text(saveLabel) }

            if (t.transferId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (t.reviewState == 0) {
                        OutlinedButton(onClick = { vm.checkLater(t); (onSaved ?: onDismiss)() }, modifier = Modifier.weight(1f)) { Text("Check later") }
                        OutlinedButton(onClick = { vm.dismissReview(t); (onSaved ?: onDismiss)() }, modifier = Modifier.weight(1f)) { Text("Leave as is") }
                    } else {
                        OutlinedButton(onClick = { vm.restoreReview(t); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Move back to Review") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- bulk sheet

/** Categorise several selected transactions at once. */
@Composable
fun BulkSheet(ts: List<TxnEntity>, vm: MainVm, onDismiss: () -> Unit, onDone: () -> Unit) {
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }
    var chosen by remember { mutableStateOf<Long?>(null) }
    val sameMerchant = remember(ts) { ts.map { Suggest.merchantKey(it) }.distinct().size == 1 && Suggest.merchantKey(ts.first()).isNotEmpty() }
    var rule by remember { mutableStateOf(false) }
    val (field, pattern) = Suggest.ruleTarget(ts.first())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Categorise ${ts.size} transactions", style = MaterialTheme.typography.titleLarge)
            CategoryPicker(cats, usage, chosen, onSelect = { chosen = it }, onCreate = { n -> vm.createCategory(n) { chosen = it } })
            if (sameMerchant) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Always categorise \"$pattern\" like this", style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(rule, { rule = it })
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp), enabled = chosen != null,
                onClick = {
                    vm.categoriseMany(ts, chosen!!, if (rule) Repository.RuleDraft(field, pattern) else null)
                    onDone()
                },
            ) { Text("Apply") }
        }
    }
}

// ---------------------------------------------------------------- review screen

@Composable
fun ReviewScreen(vm: MainVm) {
    val candidates by vm.candidates.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val haptic = LocalHapticFeedback.current
    var editingId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulk by remember { mutableStateOf(false) }
    var showLater by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(25) }

    val otherId = rememberOtherId(cats)
    val byTxn = remember(txns) { txns.associateBy { it.id } }
    val acc = remember(accounts) { accounts.associateBy { it.id } }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val index = remember(txns) { Suggest.index(txns) }

    val pendingTransfers = candidates.filter { !it.later }
    val laterTransfers = candidates.filter { it.later }
    val queue = remember(txns, otherId) { txns.filter { Suggest.isUnsorted(it, otherId) } }
    val laterTxns = remember(txns) { txns.filter { it.reviewState == 1 } }
    val laterCount = laterTransfers.size + laterTxns.size
    val total = remember { mutableStateOf(0) }
    if (queue.size > total.value) total.value = queue.size
    val done = (total.value - queue.size).coerceAtLeast(0)

    fun bankDefault(t: TxnEntity): Long? =
        DefaultCategories.map(t.providerCategory)?.let { n -> cats.firstOrNull { it.name.equals(n, true) && it.parentId == null }?.id }

    Column(Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) {
            val chosenTxns = txns.filter { it.id in selected }
            SelectionBar(
                selected.size,
                listOf(
                    "Categorise" to { bulk = true },
                    "Exclude" to { vm.setExcluded(chosenTxns, true); selected = emptySet() },
                    "Later" to { vm.setReviewState(chosenTxns, 1); selected = emptySet() },
                    "Done" to { vm.setReviewState(chosenTxns, 2); selected = emptySet() },
                ),
                onClear = { selected = emptySet() },
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Review", style = MaterialTheme.typography.headlineSmall)
                    if (queue.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { if (total.value == 0) 0f else done.toFloat() / total.value },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${queue.size} left to sort · tap a suggestion, or press and hold to select several", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (pendingTransfers.isNotEmpty()) {
                item { SectionHeader("Possible internal transfers · ${pendingTransfers.size}") }
                items(pendingTransfers, key = { "c" + it.outId + it.inId }) { c ->
                    TransferCard(c, byTxn[c.outId], byTxn[c.inId], acc, catById, vm, later = false)
                }
            }

            item { SectionHeader("Needs a category · ${queue.size}") }
            if (queue.isEmpty()) item { Card(Modifier.fillMaxWidth()) { EmptyState("All caught up", "Every transaction has a category.") } }
            items(queue.take(visible), key = { "u" + it.id }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    TxnRow(
                        t, acc[t.accountId], t.categoryId?.let(catById::get),
                        selected = t.id in selected,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selected = selected + t.id
                        },
                    ) { if (selected.isNotEmpty()) selected = if (t.id in selected) selected - t.id else selected + t.id else editingId = t.id }
                    if (selected.isEmpty()) {
                        val sug = Suggest.suggestions(t, index, bankDefault(t), 3).mapNotNull { catById[it] }.filter { it.id != otherId }
                        FlowRow(Modifier.padding(start = 74.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sug.forEach { c ->
                                AssistChip(onClick = { vm.quickCategorise(t, c.id) }, label = { Text("${c.icon.orEmpty()} ${c.name}".trim(), maxLines = 1) })
                            }
                            AssistChip(onClick = { editingId = t.id }, label = { Text("More…") })
                        }
                    }
                }
            }
            if (queue.size > visible) item { TextButton(onClick = { visible += 25 }) { Text("Show more (${queue.size - visible} left)") } }

            item {
                TextButton(onClick = { showLater = !showLater }) {
                    Text((if (showLater) "▾ " else "▸ ") + "Check later ($laterCount)", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (showLater) {
                if (laterCount == 0) item { Text("Nothing parked.", style = MaterialTheme.typography.bodyMedium) }
                items(laterTransfers, key = { "lc" + it.outId + it.inId }) { c ->
                    TransferCard(c, byTxn[c.outId], byTxn[c.inId], acc, catById, vm, later = true)
                }
                items(laterTxns, key = { "lu" + it.id }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        TxnRow(t, acc[t.accountId], t.categoryId?.let(catById::get)) { editingId = t.id }
                        Row(Modifier.padding(start = 74.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { vm.restoreReview(t) }, label = { Text("Move back") })
                            AssistChip(onClick = { vm.dismissReview(t) }, label = { Text("Done") })
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val t = byTxn[id]
        if (t == null) editingId = null else key(id) {
            val pos = queue.indexOfFirst { it.id == id }
            TxnSheet(
                t, vm,
                onDismiss = { editingId = null },
                onSaved = if (pos >= 0) ({ editingId = queue.getOrNull(pos + 1)?.id }) else null,
                saveLabel = if (pos >= 0 && pos < queue.lastIndex) "Save & next" else "Save",
                progress = if (pos >= 0) "${pos + 1} of ${queue.size}" else null,
                startExpanded = pos >= 0,
            )
        }
    }
    if (bulk) {
        val ts = txns.filter { it.id in selected }
        if (ts.isEmpty()) bulk = false else BulkSheet(ts, vm, onDismiss = { bulk = false }) { bulk = false; selected = emptySet() }
    }
}

@Composable
private fun TransferCard(
    c: CandidateEntity,
    out: TxnEntity?,
    into: TxnEntity?,
    acc: Map<String, AccountEntity>,
    catById: Map<Long, CategoryEntity>,
    vm: MainVm,
    later: Boolean,
) {
    if (out == null || into == null) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(-out.amountMinor, out.currency), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Tag("${c.score}% match")
            }
            Tag("Out", MaterialTheme.colorScheme.errorContainer)
            TxnDetails(out, acc[out.accountId], out.categoryId?.let(catById::get)?.name)
            Tag("In", MaterialTheme.colorScheme.primaryContainer)
            TxnDetails(into, acc[into.accountId], into.categoryId?.let(catById::get)?.name)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.confirm(c) }) { Text("Link") }
                OutlinedButton(onClick = { vm.reject(c) }) { Text("Not a transfer") }
                if (later) OutlinedButton(onClick = { vm.restoreCandidate(c) }) { Text("Move back") }
                else OutlinedButton(onClick = { vm.checkLater(c) }) { Text("Check later") }
            }
        }
    }
}
