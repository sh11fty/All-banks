@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    1 -> "Suggested from your bank's label"
    else -> "No category yet"
}

// ---------------------------------------------------------------- transaction sheet

/**
 * Merchant, amount, category and Save come first. Rules, similar transactions, exclusion, note and
 * details sit in collapsible groups that each state what they will do. [onSaved] replaces plain
 * dismissal so Review can move straight on to the next item.
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
    val willSimilar = chosen != null && applySimilar && similar.isNotEmpty()
    val willRule = chosen != null && learnRule && pattern.isNotBlank()
    val consequences = listOfNotNull(
        if (willSimilar) "updates ${similar.size} similar" else null,
        if (willRule) "creates a rule for \"${pattern.trim()}\"" else null,
    ).joinToString(" · ")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetColumn {
            if (progress != null) Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 1. Merchant and amount
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(dayLabel(t.bookingDate), account?.let { accountLabel(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                AmountText(t.amountMinor, style = MaterialTheme.typography.titleLarge)
            }

            if (t.transferId != null) {
                StatusBanner("Linked as an internal transfer", "It doesn't count as spending or income.", BannerKind.Info)
                SecondaryButton("Unlink transfer", { vm.unlink(t); onDismiss() }, Modifier.fillMaxWidth())
            }

            // 2. Category
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

            // 3. Save, with a plain statement of what it will do
            if (consequences.isNotEmpty()) {
                Text("Saving also $consequences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(
                saveLabel,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val draft = if (willRule) Repository.RuleDraft(field, pattern) else null
                    vm.saveTxn(t, chosen, if (applySimilar) similar else emptyList(), draft, exclude, note) { (onSaved ?: onDismiss)() }
                },
                modifier = Modifier.fillMaxWidth(), enabled = canSave,
            )

            // 4. Less common options
            if (chosen != null && similar.isNotEmpty()) {
                OptionGroup("Similar transactions", if (applySimilar) "${similar.size} will get the same category" else "Only this one") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Apply to ${similar.size} similar", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(applySimilar, { applySimilar = it })
                    }
                    Text(
                        similar.take(4).joinToString("\n") { "${Suggest.title(it)} · ${dayLabel(it.bookingDate)} · ${fmt(it.amountMinor)}" } +
                            if (similar.size > 4) "\n+${similar.size - 4} more" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (chosen != null) {
                OptionGroup("Automatic rule", if (learnRule) "On: \"${pattern.trim()}\" will be categorised automatically" else "Off") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Categorise future matches", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
            OptionGroup("Leave out of spending", if (exclude) "Excluded from totals, budgets and charts" else "Counted normally") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Exclude (for refunds and one-offs)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(exclude, { exclude = it })
                }
            }
            OptionGroup("Note", note.ifBlank { "None" }) {
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
            }
            OptionGroup("All details", "Reference, dates, amount, bank", startOpen = startExpanded) {
                TxnDetails(t, account, null)
            }

            if (t.transferId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (t.reviewState == 0) {
                        SecondaryButton("Check later", { vm.checkLater(t); (onSaved ?: onDismiss)() }, Modifier.weight(1f))
                        SecondaryButton("Leave as is", { vm.dismissReview(t); (onSaved ?: onDismiss)() }, Modifier.weight(1f))
                    } else {
                        SecondaryButton("Move back to Review", { vm.restoreReview(t); onDismiss() }, Modifier.fillMaxWidth())
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
        SheetColumn {
            Text("Categorise ${ts.size} transactions", style = MaterialTheme.typography.titleLarge)
            CategoryPicker(cats, usage, chosen, onSelect = { chosen = it }, onCreate = { n -> vm.createCategory(n) { chosen = it } })
            if (sameMerchant) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Always categorise \"$pattern\" like this", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(rule, { rule = it })
                }
            }
            PrimaryButton(
                "Apply to ${ts.size}",
                onClick = {
                    vm.categoriseMany(ts, chosen!!, if (rule) Repository.RuleDraft(field, pattern) else null)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(), enabled = chosen != null,
            )
        }
    }
}

// ---------------------------------------------------------------- review screen

class ReviewActions(
    val quick: (TxnEntity, Long) -> Unit = { _, _ -> },
    val open: (String) -> Unit = {},
    val link: (CandidateEntity) -> Unit = {},
    val reject: (CandidateEntity) -> Unit = {},
    val candidateLater: (CandidateEntity) -> Unit = {},
    val candidateRestore: (CandidateEntity) -> Unit = {},
    val restore: (TxnEntity) -> Unit = {},
    val done: (TxnEntity) -> Unit = {},
    val bulkCategorise: (List<TxnEntity>) -> Unit = {},
    val exclude: (List<TxnEntity>) -> Unit = {},
    val later: (List<TxnEntity>) -> Unit = {},
    val markDone: (List<TxnEntity>) -> Unit = {},
)

@Composable
fun ReviewScreen(vm: MainVm) {
    val candidates by vm.candidates.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var editingId by remember { mutableStateOf<String?>(null) }
    var bulk by remember { mutableStateOf<List<TxnEntity>?>(null) }
    val otherId = rememberOtherId(cats)
    val queueAll = remember(txns, otherId) { txns.filter { Suggest.isUnsorted(it, otherId) } }
    val byTxn = remember(txns) { txns.associateBy { it.id } }

    ReviewContent(
        candidates, txns, accounts, cats,
        ReviewActions(
            quick = { t, c -> vm.quickCategorise(t, c) },
            open = { editingId = it },
            link = { vm.confirm(it) }, reject = { vm.reject(it) },
            candidateLater = { vm.checkLater(it) }, candidateRestore = { vm.restoreCandidate(it) },
            restore = { vm.restoreReview(it) }, done = { vm.dismissReview(it) },
            bulkCategorise = { bulk = it },
            exclude = { vm.setExcluded(it, true) },
            later = { vm.setReviewState(it, 1) },
            markDone = { vm.setReviewState(it, 2) },
        ),
    )

    editingId?.let { id ->
        val t = byTxn[id]
        if (t == null) editingId = null else key(id) {
            val pos = queueAll.indexOfFirst { it.id == id }
            TxnSheet(
                t, vm,
                onDismiss = { editingId = null },
                onSaved = if (pos >= 0) ({ editingId = queueAll.getOrNull(pos + 1)?.id }) else null,
                saveLabel = if (pos >= 0 && pos < queueAll.lastIndex) "Save & next" else "Save",
                progress = if (pos >= 0) "${pos + 1} of ${queueAll.size}" else null,
                startExpanded = false,
            )
        }
    }
    bulk?.let { ts -> BulkSheet(ts, vm, onDismiss = { bulk = null }) { bulk = null } }
}

/** The review queue, free of the view-model so it can be rendered with any data. */
@Composable
fun ReviewContent(
    candidates: List<CandidateEntity>,
    txns: List<TxnEntity>,
    accounts: List<AccountEntity>,
    cats: List<CategoryEntity>,
    actions: ReviewActions,
) {
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showLater by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(25) }
    var query by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf<String?>(null) }
    var direction by remember { mutableStateOf<Int?>(null) }

    val otherId = rememberOtherId(cats)
    val byTxn = remember(txns) { txns.associateBy { it.id } }
    val acc = remember(accounts) { accounts.associateBy { it.id } }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val index = remember(txns) { Suggest.index(txns) }
    val institutions = remember(accounts) { accounts.map { it.institution }.distinct() }
    val filtering = bank != null || direction != null || query.isNotBlank()

    fun textMatches(t: TxnEntity): Boolean =
        query.isBlank() || listOfNotNull(t.description, t.counterparty, t.merchantName, t.displayName, "%.2f".format(kotlin.math.abs(t.amountMinor) / 100.0))
            .any { it.contains(query, ignoreCase = true) }

    fun matches(t: TxnEntity): Boolean =
        (bank == null || acc[t.accountId]?.institution == bank) &&
            (direction == null || (direction == 1 && t.amountMinor > 0) || (direction == -1 && t.amountMinor < 0)) &&
            textMatches(t)

    fun candidateMatches(c: CandidateEntity): Boolean {
        val o = byTxn[c.outId] ?: return false
        val i = byTxn[c.inId] ?: return false
        return (bank == null || acc[o.accountId]?.institution == bank || acc[i.accountId]?.institution == bank) &&
            (textMatches(o) || textMatches(i))
    }

    val pendingAll = candidates.filter { !it.later }
    val laterAll = candidates.filter { it.later }
    val queueAll = remember(txns, otherId) { txns.filter { Suggest.isUnsorted(it, otherId) } }
    val pendingTransfers = pendingAll.filter(::candidateMatches)
    val laterTransfers = laterAll.filter(::candidateMatches)
    val queue = remember(queueAll, query, bank, direction) { queueAll.filter(::matches) }
    val laterTxns = remember(txns, query, bank, direction) { txns.filter { it.reviewState == 1 }.filter(::matches) }
    val laterCount = laterTransfers.size + laterTxns.size
    val total = remember { mutableStateOf(0) }
    if (queueAll.size > total.value) total.value = queueAll.size
    val done = (total.value - queueAll.size).coerceAtLeast(0)

    fun bankDefault(t: TxnEntity): Long? =
        DefaultCategories.map(t.providerCategory)?.let { n -> cats.firstOrNull { it.name.equals(n, true) && it.parentId == null }?.id }

    Column(Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) {
            val chosen = txns.filter { it.id in selected }
            SelectionBar(
                selected.size,
                listOf(
                    "Categorise" to { actions.bulkCategorise(chosen); selected = emptySet() },
                    "Exclude" to { actions.exclude(chosen); selected = emptySet() },
                    "Later" to { actions.later(chosen); selected = emptySet() },
                    "Done" to { actions.markDone(chosen); selected = emptySet() },
                ),
                onClear = { selected = emptySet() },
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review", style = MaterialTheme.typography.headlineSmall)
                    if (queueAll.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { if (total.value == 0) 0f else done.toFloat() / total.value },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "${pendingAll.size} transfer${if (pendingAll.size == 1) "" else "s"} to confirm · ${queueAll.size} to categorise",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SearchField(query, { query = it }, "Filter by merchant or amount")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterMenu("Bank", listOf<Pair<String?, String>>(null to "All banks") + institutions.map { it to it }, bank) { bank = it }
                        FilterMenu("Money", listOf<Pair<Int?, String>>(null to "In & out", 1 to "Money in", -1 to "Money out"), direction) { direction = it }
                        if (filtering) TextButton(onClick = { bank = null; direction = null; query = "" }) { Text("Clear all") }
                    }
                }
            }

            // Section 1: suspected internal transfers
            if (pendingTransfers.isNotEmpty() || !filtering) {
                item {
                    ReviewSectionHeader(
                        "Possible internal transfers", pendingTransfers.size,
                        "Money moving between your own accounts. Linking them keeps them out of spending and income.",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (pendingTransfers.isEmpty()) item { ListGroup { EmptyState("No transfers to confirm", "Matching pairs between your own accounts show up here.") } }
                items(pendingTransfers, key = { "c" + it.outId + it.inId }) { c ->
                    TransferCard(c, byTxn[c.outId], byTxn[c.inId], acc, actions, later = false)
                }
            }

            // Section 2: needs a category
            item {
                ReviewSectionHeader(
                    "Needs a category", queue.size,
                    "Tap a suggestion to file it. Press and hold to select several.",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (queue.isEmpty()) item {
                ListGroup { EmptyState(if (filtering) "Nothing matches" else "All caught up", if (filtering) "Clear a filter to see the rest." else "Every transaction has a category.") }
            }
            items(queue.take(visible), key = { "u" + it.id }) { t ->
                ListGroup {
                    TxnRow(
                        t, acc[t.accountId], t.categoryId?.let(catById::get),
                        selected = t.id in selected,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selected = selected + t.id
                        },
                    ) { if (selected.isNotEmpty()) selected = if (t.id in selected) selected - t.id else selected + t.id else actions.open(t.id) }
                    if (selected.isEmpty()) {
                        val sug = Suggest.suggestions(t, index, bankDefault(t), 3).mapNotNull { catById[it] }.filter { it.id != otherId }
                        FlowRow(Modifier.padding(start = 70.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sug.forEach { c ->
                                AssistChip(onClick = { actions.quick(t, c.id) }, label = { Text("${c.icon.orEmpty()} ${c.name}".trim(), maxLines = 1) })
                            }
                            AssistChip(onClick = { actions.open(t.id) }, label = { Text("More…") })
                        }
                    }
                }
            }
            if (queue.size > visible) item { TextButton(onClick = { visible += 25 }) { Text("Show more (${queue.size - visible} left)") } }

            // Section 3: parked items
            item {
                TextButton(onClick = { showLater = !showLater }) {
                    Text((if (showLater) "▾ " else "▸ ") + "Check later ($laterCount)", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (showLater) {
                if (laterCount == 0) item { Text("Nothing parked.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(laterTransfers, key = { "lc" + it.outId + it.inId }) { c ->
                    TransferCard(c, byTxn[c.outId], byTxn[c.inId], acc, actions, later = true)
                }
                items(laterTxns, key = { "lu" + it.id }) { t ->
                    ListGroup {
                        TxnRow(t, acc[t.accountId], t.categoryId?.let(catById::get)) { actions.open(t.id) }
                        Row(Modifier.padding(start = 70.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { actions.restore(t) }, label = { Text("Move back") })
                            AssistChip(onClick = { actions.done(t) }, label = { Text("Done") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionHeader(title: String, count: Int, purpose: String, tint: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(tint))
            Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Tag("$count")
        }
        Text(purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransferCard(
    c: CandidateEntity,
    out: TxnEntity?,
    into: TxnEntity?,
    acc: Map<String, AccountEntity>,
    actions: ReviewActions,
    later: Boolean,
) {
    if (out == null || into == null) return
    val from = acc[out.accountId]
    val to = acc[into.accountId]
    ListGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(-out.amountMinor), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Tag("${c.score}% match")
            }
            Text(
                "${from?.institution ?: "?"}  →  ${to?.institution ?: "?"}",
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Hairline(0)
            Text("Out of ${from?.let(::accountLabel) ?: "account"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TxnDetails(out, from, null)
            Text("Into ${to?.let(::accountLabel) ?: "account"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TxnDetails(into, to, null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton("Link", { actions.link(c) })
                SecondaryButton("Not a transfer", { actions.reject(c) })
                if (later) SecondaryButton("Move back", { actions.candidateRestore(c) })
                else SecondaryButton("Check later", { actions.candidateLater(c) })
            }
        }
    }
}
