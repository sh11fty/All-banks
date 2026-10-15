@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.RuleEntity
import com.sh11fty.allbanks.domain.RuleEngine

@Composable
fun RulesScreen(vm: MainVm) {
    val rules by vm.rules.collectAsState()
    val cats by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var sheet by remember { mutableStateOf<RuleEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val catById = remember(cats) { cats.associateBy { it.id } }
    val accById = remember(accounts) { accounts.associateBy { it.id } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Top rule wins. Manually categorised transactions are never changed.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }, enabled = cats.isNotEmpty()) { Text("Add rule") }
                OutlinedButton(onClick = { vm.reapplyRules() }) { Text("Re-apply to history") }
            }
        }
        if (rules.isEmpty()) {
            item { Text("No rules yet. Categorise a transaction with \"Remember for next time\" on, or add one here.", fontSize = 13.sp) }
        }
        items(rules.sortedWith(compareBy({ it.priority }, { it.id })), key = { it.id }) { r ->
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        r.accountId?.let { Tag("from ${accById[it]?.institution ?: "?"}") }
                        r.toAccountId?.let { Tag("to ${accById[it]?.institution ?: "?"}") }
                        when (r.direction) { 1 -> Tag("money in"); -1 -> Tag("money out") }
                        if (r.minAbsMinor != null || r.maxAbsMinor != null) {
                            Tag("£${r.minAbsMinor?.let { "%.2f".format(it / 100.0) } ?: "0"} – ${r.maxAbsMinor?.let { "£%.2f".format(it / 100.0) } ?: "any"}")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("→", fontSize = 14.sp)
                        if (cat != null) Tag(categoryPath(cat, catById), Color(cat.colour).copy(alpha = 0.35f)) else Tag("missing category")
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
    if (adding) RuleSheet(vm, null, rules, cats, accounts) { adding = false }
    sheet?.let { RuleSheet(vm, it, rules, cats, accounts) { sheet = null } }
}

@Composable
private fun RuleSheet(
    vm: MainVm,
    initial: RuleEntity?,
    rules: List<RuleEntity>,
    cats: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
) {
    val txns by vm.txns.collectAsState()
    var pattern by remember { mutableStateOf(initial?.pattern.orEmpty()) }
    var field by remember { mutableStateOf(initial?.field ?: "description") }
    var match by remember { mutableStateOf(initial?.matchType ?: "contains") }
    var direction by remember { mutableStateOf(initial?.direction ?: 0) }
    var cat by remember { mutableStateOf<Long?>(initial?.categoryId) }
    var from by remember { mutableStateOf(initial?.accountId) }
    var to by remember { mutableStateOf(initial?.toAccountId) }
    var min by remember { mutableStateOf(initial?.minAbsMinor?.let { "%.2f".format(it / 100.0) }.orEmpty()) }
    var max by remember { mutableStateOf(initial?.maxAbsMinor?.let { "%.2f".format(it / 100.0) }.orEmpty()) }

    fun pence(s: String) = s.toDoubleOrNull()?.let { Math.round(it * 100) }
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }

    fun draft() = RuleEntity(
        id = initial?.id ?: 0, priority = initial?.priority ?: ((rules.minOfOrNull { it.priority } ?: 0) - 1),
        field = field, matchType = match, pattern = pattern.trim(), direction = direction,
        minAbsMinor = pence(min), maxAbsMinor = pence(max), accountId = from, toAccountId = to,
        categoryId = cat ?: 0, enabled = true,
    )

    val matches = remember(pattern, field, match, direction, min, max, from, to, txns) {
        if (pattern.isBlank()) 0 else {
            val d = draft()
            val cp = RuleEngine.counterparts(txns)
            txns.count { RuleEngine.matches(d, it, cp[it.id]) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "New rule" else "Edit rule", style = MaterialTheme.typography.titleLarge)

            SectionLabel("MATCH TEXT")
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

            SectionLabel("BANKS (OPTIONAL)")
            AccountDropdown("Sending from", accounts, from, "Any bank", onSelect = { from = it })
            AccountDropdown("Receiving to", accounts, to, "Any bank", onSelect = { to = it })
            Text(
                "\"Sending from\" matches money leaving that account. \"Receiving to\" matches money arriving in it " +
                    "(for payments out, only transfers you've linked between your own accounts).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
            CategoryPicker(cats, usage, cat, onSelect = { cat = it }, onCreate = { n -> vm.createCategory(n) { cat = it } })

            Text(
                if (pattern.isBlank()) "Enter text to see how many transactions match."
                else "Matches $matches of your transactions right now.",
                fontWeight = FontWeight.Medium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = pattern.isNotBlank() && cat != null,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.saveRule(draft()); onDismiss() },
                ) { Text("Save rule") }
            }
        }
    }
}
