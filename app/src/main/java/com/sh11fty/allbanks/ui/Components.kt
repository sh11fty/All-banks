@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sh11fty.allbanks.domain.Suggest
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sh11fty.allbanks.data.AccountEntity
import com.sh11fty.allbanks.data.CategoryEntity
import com.sh11fty.allbanks.data.TxnEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------- theme + formatting

/** True when balances should be masked. */
val LocalHide = compositionLocalOf { false }

fun fmt(minor: Long, @Suppress("UNUSED_PARAMETER") currency: String = "GBP"): String {
    // Every account is in GBP, so amounts are always shown in pounds.
    return NumberFormat.getCurrencyInstance(Locale.UK).format(minor / 100.0)
}

@Composable
fun balText(minor: Long?, currency: String): String =
    if (LocalHide.current) "••••" else minor?.let { fmt(it, currency) } ?: "—"

fun bankColour(institution: String): Color {
    val hue = (abs(institution.hashCode()) % 360).toFloat()
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.75f)))
}

fun accountLabel(a: AccountEntity): String =
    "${a.institution} · ${a.name}" + (a.maskedId?.let { " ••$it" } ?: "")

fun findActivity(ctx: Context): Activity? {
    var c = ctx
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun dayLabel(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.UK))
    }
}

/** "Parent › Child" label for a category. */
fun categoryPath(c: CategoryEntity, byId: Map<Long, CategoryEntity>): String {
    val parent = c.parentId?.let(byId::get)
    return if (parent != null) "${parent.name} › ${c.name}" else c.name
}

// ---------------------------------------------------------------- small pieces

/** Round monogram for a bank, tinted with its own hue. */
@Composable
fun BankAvatar(institution: String, size: Int = 40) {
    val c = bankColour(institution)
    Box(Modifier.size(size.dp).clip(CircleShape).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(
            institution.firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Bold, color = c, fontSize = (size * 0.4f).sp,
        )
    }
}

@Composable
fun BankChip(institution: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bankColour(institution).copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(bankColour(institution)))
        Spacer(Modifier.width(6.dp))
        Text(institution, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, colour: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colour)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
}

/** Quiet caption used above groups of content. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ColourDot(colour: Int, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(colour)))
}

/** Round category badge: the category's emoji on a soft tint of its colour. */
@Composable
fun CategoryBadge(c: CategoryEntity?, size: Int = 40, fallback: String = "?") {
    val tint = c?.let { Color(it.colour) } ?: MaterialTheme.colorScheme.outline
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(c?.icon ?: c?.name?.firstOrNull()?.uppercase() ?: fallback, fontSize = (size * 0.45f).sp, color = tint, fontWeight = FontWeight.Bold)
    }
}

/** Section title with an optional trailing action. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Compact filled pill search field (44dp). */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().height(Dimens.ControlHeight.dp),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(Dimens.IconSm.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close, "Clear search",
                        Modifier.size(Dimens.IconSm.dp).clickable { onChange("") },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
fun LabelValue(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Every detail Endute gives us about a transaction, untruncated. */
@Composable
fun TxnDetails(t: TxnEntity, account: AccountEntity?, category: String?) {
    Column {
        LabelValue("Amount", fmt(t.amountMinor, t.currency))
        LabelValue("Merchant", t.merchantName)
        LabelValue("Counterparty", t.counterparty)
        LabelValue("Description", t.description)
        LabelValue("Booked", t.bookingDate)
        if (t.valueDate != null && t.valueDate != t.bookingDate) LabelValue("Value date", t.valueDate)
        LabelValue("Bank", account?.let { accountLabel(it) })
        LabelValue("Reference", t.endToEndId)
        LabelValue("Note", t.note)
    }
}

// ---------------------------------------------------------------- pickers

/** Type-to-search dropdown over the category tree; can create a new category on the spot. */
@Composable
fun CategoryPicker(
    cats: List<CategoryEntity>,
    usage: Map<Long, Int>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Category",
    onCreate: ((String) -> Unit)? = null,
) {
    val byId = remember(cats) { cats.associateBy { it.id } }
    val selectedLabel = selectedId?.let(byId::get)?.let { categoryPath(it, byId) }.orEmpty()
    var expanded by remember { mutableStateOf(false) }
    var text by remember(selectedLabel) { mutableStateOf(selectedLabel) }
    var typed by remember { mutableStateOf(false) }

    val query = if (typed) text.trim() else ""
    val options = remember(cats, usage, query) {
        cats.map { it to categoryPath(it, byId) }
            .filter { query.isEmpty() || it.second.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<Pair<CategoryEntity, String>> { usage[it.first.id] ?: 0 }.thenBy { it.second })
    }
    val exact = options.any { it.second.equals(query, ignoreCase = true) }

    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; typed = true; expanded = true },
            label = { Text(label) },
            placeholder = { Text("Search or pick…") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false; typed = false; text = selectedLabel }) {
            options.forEach { (c, path) ->
                DropdownMenuItem(
                    text = { Text(path) },
                    leadingIcon = { CategoryBadge(c, 28) },
                    onClick = {
                        text = path; typed = false; expanded = false
                        onSelect(c.id)
                    },
                )
            }
            if (onCreate != null && query.isNotEmpty() && !exact) {
                DropdownMenuItem(
                    text = { Text("Create \"$query\"", fontWeight = FontWeight.SemiBold) },
                    onClick = { expanded = false; typed = false; onCreate(query) },
                )
            }
            if (options.isEmpty() && (onCreate == null || query.isEmpty())) {
                DropdownMenuItem(text = { Text("No matches") }, onClick = {}, enabled = false)
            }
        }
    }
}

/** Bank/account dropdown with an "Any" entry. */
@Composable
fun AccountDropdown(
    label: String,
    accounts: List<AccountEntity>,
    selectedId: String?,
    anyLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == selectedId }?.let(::accountLabel) ?: anyLabel
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = current, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(anyLabel) }, onClick = { onSelect(null); expanded = false })
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = { Text(accountLabel(a)) },
                    leadingIcon = { ColourDot(bankColour(a.institution).toArgbInt()) },
                    onClick = { onSelect(a.id); expanded = false },
                )
            }
        }
    }
}

fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/** Filter chip that opens a menu. When a value is chosen it shows that value with a ✕ to clear it. */
@Composable
fun <T> FilterMenu(
    title: String,
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: title
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(if (selected == null) "$title ▾" else label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = if (selected != null) {
                { Icon(Icons.Filled.Close, "Clear $title filter", Modifier.size(16.dp).clickable { onSelect(null) }) }
            } else null,
        )
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

/** A toggle chip that shows a ✕ while active so it is obvious how to remove it. */
@Composable
fun ToggleFilter(label: String, active: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = active, onClick = onToggle, label = { Text(label, maxLines = 1) },
        trailingIcon = if (active) { { Icon(Icons.Filled.Close, "Remove $label filter", Modifier.size(16.dp)) } } else null,
    )
}

// ---------------------------------------------------------------- transaction list

@Composable
fun TxnRow(
    t: TxnEntity,
    account: AccountEntity?,
    cat: CategoryEntity?,
    showBank: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val title = Suggest.title(t)
    val subtitle = listOfNotNull(
        account?.institution?.takeIf { showBank },
        when {
            t.transferId != null -> "Internal transfer"
            cat != null && t.amountMinor > 0 && !cat.name.equals("Income", ignoreCase = true) -> "Refund · ${cat.name}"
            cat != null -> cat.name
            else -> "Uncategorised"
        },
        if (t.excluded) "Excluded" else null,
        if (!t.note.isNullOrBlank()) "Note" else null,
    ).joinToString(" · ")
    val amount = (if (t.amountMinor > 0) "+" else "") + fmt(t.amountMinor, t.currency)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle, $amount, ${dayLabel(t.bookingDate)}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else if (t.transferId != null) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("⇄", color = MaterialTheme.colorScheme.tertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else if (cat != null) {
            CategoryBadge(cat, 44)
        } else {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(title.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall,
            color = if (t.amountMinor > 0) positiveColour() else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Transactions grouped by day with sticky headers and a daily net (internal transfers excluded). */
@Composable
fun TxnList(
    txns: List<TxnEntity>,
    accounts: Map<String, AccountEntity>,
    cats: Map<Long, CategoryEntity>,
    modifier: Modifier = Modifier,
    showBank: Boolean = true,
    selected: Set<String> = emptySet(),
    onLongClick: ((TxnEntity) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onClick: (TxnEntity) -> Unit,
) {
    val groups = remember(txns) { txns.take(2000).groupBy { it.bookingDate }.entries.toList() }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (header != null) item(key = "header") { header() }
        groups.forEach { (date, list) ->
            stickyHeader(key = "h$date") {
                val net = list.filter { it.transferId == null && !it.excluded }.sumOf { it.amountMinor }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayLabel(date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (net != 0L) Text(fmt(net, list.first().currency), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { t ->
                TxnRow(
                    t, accounts[t.accountId], t.categoryId?.let(cats::get), showBank,
                    selected = t.id in selected,
                    onLongClick = onLongClick?.let { f -> { f(t) } },
                ) { onClick(t) }
                HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Bar shown while rows are selected; [actions] are (label, onClick). */
@Composable
fun SelectionBar(count: Int, actions: List<Pair<String, () -> Unit>>, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClear) { Text("✕", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text("$count selected", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        actions.forEach { (label, f) -> TextButton(onClick = f) { Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) } }
    }
}

// ---------------------------------------------------------------- charts

@Composable
fun Donut(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier.size(120.dp)) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = remember { Animatable(0f) }
    LaunchedEffect(parts) { progress.snapTo(0f); progress.animateTo(1f, tween(700)) }
    Canvas(modifier) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (total <= 0f) return@Canvas
        var start = -90f
        parts.forEach { (v, c) ->
            val sweep = v / total * 360f * progress.value
            drawArc(c, start, (sweep - 1.5f).coerceAtLeast(0.01f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}

/** Simple bar chart; the last bar is highlighted. Labels sit under each bar. */
@Composable
fun BarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier, barColour: Color = MaterialTheme.colorScheme.primary) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val muted = barColour.copy(alpha = 0.35f)
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, (label, v) ->
            val frac by animateFloatAsState(v.toFloat() / max, tween(600), label = "bar")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth(0.7f).height((88 * frac).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (i == values.lastIndex) barColour else muted)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = labelColour)
            }
        }
    }
}

@Composable
fun LineChart(values: List<Long>, modifier: Modifier = Modifier, colour: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) return
    val lo = values.min().toFloat()
    val hi = values.max().toFloat()
    val range = (hi - lo).takeIf { it > 0f } ?: 1f
    val fill = colour.copy(alpha = 0.12f)
    Canvas(modifier) {
        val step = size.width / (values.size - 1)
        val pad = 6f
        fun y(v: Long) = pad + (size.height - 2 * pad) * (1f - (v - lo) / range)
        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(step * i, y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(area, fill)
        drawPath(line, colour, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------- shared building blocks

/** One surface holding several rows, separated by inset hairlines. Use instead of a card per row. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) { Column(content = content) }
}

@Composable
fun Hairline(inset: Int = 16) {
    HorizontalDivider(Modifier.padding(start = inset.dp, end = 16.dp), thickness = Dimens.Hairline.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

enum class BannerKind { Info, Warning, Error }

/** Inline status message used for connection warnings, errors and notices. */
@Composable
fun StatusBanner(title: String, body: String? = null, kind: BannerKind = BannerKind.Warning, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        BannerKind.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BannerKind.Warning -> Semantic.warningContainer() to Semantic.onWarningContainer()
        BannerKind.Info -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg).padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(title, body).joinToString(". ") },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, null, Modifier.size(Dimens.IconSm.dp + 2.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = fg)
        }
    }
}

/** Thin indeterminate bar for background work such as syncing. */
@Composable
fun LoadingBar(visible: Boolean) {
    if (visible) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    else Spacer(Modifier.height(2.dp))
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick, modifier = modifier.heightIn(min = Dimens.ButtonHeight.dp), enabled = enabled,
    ) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = Dimens.ButtonHeight.dp), enabled = enabled,
    ) { Text(text) }
}

@Composable
fun DestructiveTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = MaterialTheme.colorScheme.error) }
}

/** Icon-only action with a 48dp touch target and a spoken description. */
@Composable
fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier) { Icon(icon, description, tint = tint) }
}

/** Standard content column for bottom sheets: scrolls, and stays clear of the keyboard and system bars. */
@Composable
fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Collapsible group inside a sheet: title, a one-line summary of its current state, and the controls when open. */
@Composable
fun OptionGroup(title: String, summary: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = Dimens.MinTouch.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Amount text with a leading sign for money in, so direction never relies on colour alone. */
@Composable
fun AmountText(minor: Long, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall, signed: Boolean = true) {
    Text(
        (if (signed && minor > 0) "+" else "") + fmt(minor), modifier = modifier, style = style,
        color = if (signed && minor > 0) positiveColour() else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}
