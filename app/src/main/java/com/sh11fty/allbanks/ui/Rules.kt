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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.NO_CATEGORY
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.data.TxnEntity
import com.sh11fty.allbanks.domain.RuleEngine
import com.sh11fty.allbanks.domain.Suggest

private fun fieldLabel(f: String) = when (f) { "counterparty" -> "Paid to / from"; "merchant" -> "Merchant"; else -> "Description" }
private fun matchLabel(m: String) = when (m) { "equals" -> "is exactly"; "regex" -> "matches pattern"; else -> "contains" }

private class RuleStats(val matches: Int, val last: String?, val overridden: Int)

@Composable
fun RulesScreen(vm: MainVm) {
    val rules by vm.rules.collectAsState()
    val cats by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val dismissed by vm.dismissedRuleIdeas.collectAsState()
    var sheet by remember { mutableStateOf<RuleEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var draftFrom by remember { mutableStateOf<Suggest.RuleIdea?>(null) }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val accById = remember(accounts) { accounts.associateBy { it.id } }
    val ordered = remember(rules) { rules.sortedWith(compareBy({ it.priority }, { it.id })) }

    val ideas = remember(txns, rules, dismissed) { Suggest.ruleIdeas(txns, rules, dismissed).take(3) }
    val stats = remember(txns, rules) {
        val cp = RuleEngine.counterparts(txns)
        val m = HashMap<Long, MutableList<TxnEntity>>()
        for (t in txns) for (r in RuleEngine.allMatches(rules, t, cp[t.id])) m.getOrPut(r.id) { ArrayList() }.add(t)
        rules.associate { r ->
            val list = m[r.id].orEmpty()
            r.id to RuleStats(
                list.size, list.maxOfOrNull { it.bookingDate },
                if (r.categoryId == NO_CATEGORY) 0 else list.count { it.ruleId != null && it.ruleId != r.id && it.categorySource == 2 },
            )
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Rules tidy things up automatically. If several rules match, each action comes from the rule nearest the top.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }, enabled = cats.isNotEmpty()) { Text("New rule") }
                OutlinedButton(onClick = { vm.reapplyRules() }) { Text("Re-apply to history") }
            }
        }

        if (ideas.isNotEmpty()) {
            item { SectionHeader("Suggested rules") }
            items(ideas, key = { "idea" + it.key }) { idea ->
                val cat = catById[idea.categoryId]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "You've put ${idea.label} in ${cat?.name ?: "a category"} ${idea.count} times.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text("Make it automatic?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { draftFrom = idea }) { Text("Review rule") }
                            TextButton(onClick = { vm.dismissRuleIdea(idea.key) }) { Text("Not now") }
                        }
                    }
                }
            }
        }

        item { SectionHeader("Your rules · ${ordered.size}") }
        if (ordered.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { EmptyState("No rules yet", "Categorise a transaction and switch on \"Always do this\", or create one here.") } }
        }
        items(ordered, key = { it.id }) { r ->
            val cat = catById[r.categoryId]
            val st = stats[r.id]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${fieldLabel(r.field)} ${matchLabel(r.matchType)} \"${r.pattern}\"",
                                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    st == null || st.matches == 0 -> "No matching transactions yet"
                                    else -> "Matches ${st.matches}" + (st.last?.let { " · last ${dayLabel(it)}" } ?: "")
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(r.enabled, { vm.toggleRule(r) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (cat != null) Tag("→ ${cat.icon.orEmpty()} ${categoryPath(cat, catById)}".replace("→  ", "→ "), Color(cat.colour).copy(alpha = 0.3f))
                        r.renameTo?.let { Tag("Rename: $it") }
                        if (r.excludeFromSpending) Tag("Leave out of spending")
                        if (r.markReviewed) Tag("Mark reviewed")
                        r.accountId?.let { Tag("from ${accById[it]?.institution ?: "?"}") }
                        r.toAccountId?.let { Tag("to ${accById[it]?.institution ?: "?"}") }
                        when (r.direction) { 1 -> Tag("money in"); -1 -> Tag("money out") }
                        if (r.minAbsMinor != null || r.maxAbsMinor != null) {
                            Tag("£${r.minAbsMinor?.let { "%.2f".format(it / 100.0) } ?: "0"} – ${r.maxAbsMinor?.let { "£%.2f".format(it / 100.0) } ?: "any"}")
                        }
                    }
                    if (st != null && st.overridden > 0) {
                        Text(
                            "${st.overridden} of its matches use a rule above this one instead.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.moveRule(r, up = true) }) { Text("▲") }
                        TextButton(onClick = { vm.moveRule(r, up = false) }) { Text("▼") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { sheet = r }) { Text("Edit") }
                        TextButton(onClick = { vm.deleteRule(r) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (adding) RuleSheet(vm, null, null, rules, cats, accounts, txns) { adding = false }
    draftFrom?.let { RuleSheet(vm, null, it, rules, cats, accounts, txns) { draftFrom = null } }
    sheet?.let { RuleSheet(vm, it, null, rules, cats, accounts, txns) { sheet = null } }
}

@Composable
private fun RuleSheet(
    vm: MainVm,
    initial: RuleEntity?,
    idea: Suggest.RuleIdea?,
    rules: List<RuleEntity>,
    cats: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    txns: List<TxnEntity>,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf(initial?.pattern ?: idea?.pattern.orEmpty()) }
    var field by remember { mutableStateOf(initial?.field ?: idea?.field ?: "merchant") }
    var match by remember { mutableStateOf(initial?.matchType ?: "contains") }
    var direction by remember { mutableStateOf(initial?.direction ?: 0) }
    var cat by remember { mutableStateOf<Long?>(initial?.categoryId?.takeIf { it != NO_CATEGORY } ?: idea?.categoryId) }
    var rename by remember { mutableStateOf(initial?.renameTo.orEmpty()) }
    var exclude by remember { mutableStateOf(initial?.excludeFromSpending ?: false) }
    var reviewed by remember { mutableStateOf(initial?.markReviewed ?: false) }
    var from by remember { mutableStateOf(initial?.accountId) }
    var to by remember { mutableStateOf(initial?.toAccountId) }
    var min by remember { mutableStateOf(initial?.minAbsMinor?.let { "%.2f".format(it / 100.0) }.orEmpty()) }
    var max by remember { mutableStateOf(initial?.maxAbsMinor?.let { "%.2f".format(it / 100.0) }.orEmpty()) }
    var applyPast by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(from != null || to != null || min.isNotEmpty() || max.isNotEmpty() || direction != 0) }

    fun pence(s: String) = s.toDoubleOrNull()?.let { Math.round(it * 100) }
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }
    val hasAction = cat != null || rename.isNotBlank() || exclude || reviewed

    fun draft() = RuleEntity(
        id = initial?.id ?: 0, priority = initial?.priority ?: ((rules.minOfOrNull { it.priority } ?: 0) - 1),
        field = field, matchType = match, pattern = pattern.trim(), direction = direction,
        minAbsMinor = pence(min), maxAbsMinor = pence(max), accountId = from, toAccountId = to,
        categoryId = cat ?: NO_CATEGORY, enabled = initial?.enabled ?: true,
        renameTo = rename.trim().ifEmpty { null }, excludeFromSpending = exclude, markReviewed = reviewed,
    )

    val matched = remember(pattern, field, match, direction, min, max, from, to, txns) {
        if (pattern.isBlank()) emptyList() else {
            val d = draft().copy(enabled = true)
            val cp = RuleEngine.counterparts(txns)
            txns.filter { RuleEngine.matches(d, it, cp[it.id]) }
        }
    }
    val distinctMerchants = remember(matched) { matched.map { Suggest.merchantKey(it) }.distinct().size }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "New rule" else "Edit rule", style = MaterialTheme.typography.titleLarge)

            SectionLabel("When")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("merchant", "counterparty", "description").forEach {
                    FilterChip(field == it, { field = it }, label = { Text(fieldLabel(it)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("contains", "equals", "regex").forEach {
                    FilterChip(match == it, { match = it }, label = { Text(matchLabel(it)) })
                }
            }
            OutlinedTextField(
                pattern, { pattern = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (match == "regex") "Pattern (advanced)" else "Text") },
            )

            TextButton(onClick = { more = !more }) { Text(if (more) "Fewer conditions ▴" else "Only if… (bank, amount, direction) ▾") }
            if (more) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(direction == 0, { direction = 0 }, label = { Text("Money in or out") })
                    FilterChip(direction == 1, { direction = 1 }, label = { Text("Money in") })
                    FilterChip(direction == -1, { direction = -1 }, label = { Text("Money out") })
                }
                AccountDropdown("Sending from", accounts, from, "Any bank", onSelect = { from = it })
                AccountDropdown("Receiving to", accounts, to, "Any bank", onSelect = { to = it })
                Text(
                    "\"Receiving to\" matches money arriving in that account. For payments out it only applies to transfers you've linked between your own accounts.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(min, { min = it }, label = { Text("Min £") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(max, { max = it }, label = { Text("Max £") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            }

            SectionLabel("Then")
            CategoryPicker(cats, usage, cat, onSelect = { cat = it }, onCreate = { n -> vm.createCategory(n) { cat = it } }, label = "Set category (optional)")
            if (cat != null) TextButton(onClick = { cat = null }) { Text("Don't change the category") }
            OutlinedTextField(
                rename, { rename = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Rename merchant to (optional)") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Leave out of spending", style = MaterialTheme.typography.titleSmall)
                    Text("Hide from totals, budgets and charts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(exclude, { exclude = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mark as reviewed", style = MaterialTheme.typography.titleSmall)
                    Text("Keep it out of the Review list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(reviewed, { reviewed = it })
            }

            SectionLabel("Preview")
            if (pattern.isBlank()) {
                Text("Enter some text to see what it would match.", style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Matches ${matched.size} transactions · $distinctMerchants merchant${if (distinctMerchants == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                        matched.take(5).forEach {
                            Row {
                                Text(Suggest.title(it), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${dayLabel(it.bookingDate)} · ${fmt(it.amountMinor, it.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (matched.size > 5) Text("+${matched.size - 5} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (pattern.trim().length < 4 || distinctMerchants > 3) {
                            Text(
                                if (distinctMerchants > 3) "This looks broad: it hits $distinctMerchants different merchants." else "Very short text may match unrelated merchants.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Apply to past transactions", style = MaterialTheme.typography.titleSmall)
                    Text(if (applyPast) "Existing matches change now" else "Only new transactions from now on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(applyPast, { applyPast = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = pattern.isNotBlank() && hasAction,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.saveRule(draft(), applyPast); onDismiss() },
                ) { Text("Save rule") }
            }
        }
    }
}
