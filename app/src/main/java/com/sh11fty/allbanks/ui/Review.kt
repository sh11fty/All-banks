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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.data.CandidateEntity
import com.sh11fty.allbanks.data.TxnEntity

// ---------------------------------------------------------------- transaction sheet

/** Full transaction details with category, note, "remember" rule and check-later actions. */
@Composable
fun TxnSheet(t: TxnEntity, vm: MainVm, onDismiss: () -> Unit) {
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val catById = remember(cats) { cats.associateBy { it.id } }
    val usage = remember(txns) { txns.groupingBy { it.categoryId ?: -1L }.eachCount() }
    val account = accounts.firstOrNull { it.id == t.accountId }

    var chosen by remember { mutableStateOf(t.categoryId) }
    var note by remember { mutableStateOf(t.note.orEmpty()) }
    var makeRule by remember { mutableStateOf(t.categoryId == null) }
    var pattern by remember { mutableStateOf(t.merchantName ?: t.counterparty ?: t.description) }
    val title = t.merchantName ?: t.counterparty ?: t.description
    val rulePattern = if (makeRule && chosen != null && pattern.isNotBlank()) pattern else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(fmt(t.amountMinor, t.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    TxnDetails(t, account, t.categoryId?.let(catById::get)?.let { categoryPath(it, catById) })
                }
            }

            if (t.transferId != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Linked as an internal transfer", Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.unlink(t); onDismiss() }) { Text("Unlink") }
                    }
                }
            }

            CategoryPicker(
                cats = cats, usage = usage.mapKeys { it.key }, selectedId = chosen,
                onSelect = { chosen = it },
                onCreate = { name -> vm.createCategory(name) { chosen = it } },
            )

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Remember for next time", fontWeight = FontWeight.Medium)
                            Text("Auto-categorise matching transactions", fontSize = 12.sp)
                        }
                        Switch(makeRule, { makeRule = it })
                    }
                    if (makeRule) {
                        OutlinedTextField(
                            pattern, { pattern = it }, label = { Text("When the text contains") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            OutlinedTextField(
                note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = chosen != null || note != t.note.orEmpty(),
                onClick = {
                    if (note != t.note.orEmpty()) vm.saveNote(t, note)
                    if (chosen != null) vm.categorise(t, chosen, rulePattern)
                    onDismiss()
                },
            ) { Text("Save") }

            if (t.transferId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when (t.reviewState) {
                        1, 2 -> OutlinedButton(onClick = { vm.restoreReview(t); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Text("Move back to Review")
                        }
                        else -> OutlinedButton(onClick = { vm.checkLater(t); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Text("Check later")
                        }
                    }
                    if (t.reviewState != 2) {
                        OutlinedButton(onClick = { vm.dismissReview(t); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Text("Leave uncategorised")
                        }
                    }
                }
            }
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
    var editing by remember { mutableStateOf<TxnEntity?>(null) }
    var showLater by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(20) }

    val byTxn = remember(txns) { txns.associateBy { it.id } }
    val acc = remember(accounts) { accounts.associateBy { it.id } }
    val catById = remember(cats) { cats.associateBy { it.id } }

    val pendingTransfers = candidates.filter { !it.later }
    val laterTransfers = candidates.filter { it.later }
    val uncategorised = remember(txns) { txns.filter { it.categoryId == null && it.transferId == null && it.reviewState == 0 } }
    val laterTxns = remember(txns) { txns.filter { it.reviewState == 1 } }
    val laterCount = laterTransfers.size + laterTxns.size

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        item { SectionLabel("POSSIBLE INTERNAL TRANSFERS · ${pendingTransfers.size}") }
        if (pendingTransfers.isEmpty()) item { Text("Nothing to confirm.", fontSize = 13.sp) }
        items(pendingTransfers, key = { "c" + it.outId + it.inId }) { c ->
            TransferCard(c, byTxn[c.outId], byTxn[c.inId], acc, catById, vm, later = false)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("UNCATEGORISED · ${uncategorised.size}")
            Text("Open one to see everything and categorise it.", fontSize = 12.sp)
        }
        if (uncategorised.isEmpty()) item { Text("All caught up.", fontSize = 13.sp) }
        items(uncategorised.take(visible), key = { "u" + it.id }) { t ->
            UncategorisedCard(t, acc[t.accountId], vm, onOpen = { editing = t }, later = false)
        }
        if (uncategorised.size > visible) {
            item { TextButton(onClick = { visible += 20 }) { Text("Show more (${uncategorised.size - visible} left)") } }
        }

        item {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showLater = !showLater }) {
                Text((if (showLater) "▾ " else "▸ ") + "Check later ($laterCount)", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showLater) {
            if (laterCount == 0) item { Text("Nothing parked.", fontSize = 13.sp) }
            items(laterTransfers, key = { "lc" + it.outId + it.inId }) { c ->
                TransferCard(c, byTxn[c.outId], byTxn[c.inId], acc, catById, vm, later = true)
            }
            items(laterTxns, key = { "lu" + it.id }) { t ->
                UncategorisedCard(t, acc[t.accountId], vm, onOpen = { editing = t }, later = true)
            }
        }
    }
    editing?.let { TxnSheet(it, vm) { editing = null } }
}

@Composable
private fun TransferCard(
    c: CandidateEntity,
    out: TxnEntity?,
    into: TxnEntity?,
    acc: Map<String, com.sh11fty.allbanks.data.AccountEntity>,
    catById: Map<Long, com.sh11fty.allbanks.data.CategoryEntity>,
    vm: MainVm,
    later: Boolean,
) {
    if (out == null || into == null) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(-out.amountMinor, out.currency), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Tag("${c.score}% match")
            }
            Text("OUT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            TxnDetails(out, acc[out.accountId], out.categoryId?.let(catById::get)?.name)
            Text("IN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun UncategorisedCard(t: TxnEntity, account: com.sh11fty.allbanks.data.AccountEntity?, vm: MainVm, onOpen: () -> Unit, later: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TxnDetails(t, account, null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Categorise") }
                if (later) {
                    OutlinedButton(onClick = { vm.restoreReview(t) }) { Text("Move back") }
                    OutlinedButton(onClick = { vm.dismissReview(t) }) { Text("Done") }
                } else {
                    OutlinedButton(onClick = { vm.checkLater(t) }) { Text("Check later") }
                }
            }
        }
    }
}
