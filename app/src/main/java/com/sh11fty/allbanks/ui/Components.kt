@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.sh11fty.allbanks.ui

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** True when balances should be masked. */
val LocalHide = compositionLocalOf { false }

fun fmt(minor: Long, currency: String = "GBP"): String {
    val f = NumberFormat.getCurrencyInstance(Locale.UK)
    runCatching { f.currency = Currency.getInstance(currency) }
    return f.format(minor / 100.0)
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

@Composable
fun BankChip(institution: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bankColour(institution))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(institution, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, colour: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colour)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) { Text(text, fontSize = 11.sp, maxLines = 1) }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ColourDot(colour: Int, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(colour)))
}

@Composable
fun LabelValue(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.width(92.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontSize = 13.sp)
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
        LabelValue("Category", category)
        LabelValue("Bank says", t.providerCategory)
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
                    leadingIcon = { ColourDot(c.colour) },
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

/** Compact filter chip that opens a menu: options are (value, label); null value = "all". */
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
            label = { Text(if (selected == null) "$title ▾" else "$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

// ---------------------------------------------------------------- transaction list

@Composable
fun TxnRow(t: TxnEntity, account: AccountEntity?, cat: CategoryEntity?, showBank: Boolean = true, onClick: () -> Unit) {
    val title = t.merchantName ?: t.counterparty ?: t.description
    val tint = if (t.transferId != null) MaterialTheme.colorScheme.tertiary else cat?.let { Color(it.colour) }
        ?: MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text(title.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val sub = listOfNotNull(
                account?.institution?.takeIf { showBank },
                when {
                    t.transferId != null -> "Internal transfer"
                    cat != null -> cat.name
                    else -> "Uncategorised"
                },
                if (!t.note.isNullOrBlank()) "✎" else null,
            ).joinToString(" · ")
            Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            fmt(t.amountMinor, t.currency),
            fontWeight = FontWeight.SemiBold,
            color = if (t.amountMinor > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
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
    onClick: (TxnEntity) -> Unit,
) {
    val groups = remember(txns) { txns.take(2000).groupBy { it.bookingDate }.entries.toList() }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { (date, list) ->
            stickyHeader(key = "h$date") {
                val net = list.filter { it.transferId == null }.sumOf { it.amountMinor }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayLabel(date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    if (net != 0L) Text(fmt(net, list.first().currency), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { t ->
                TxnRow(t, accounts[t.accountId], t.categoryId?.let(cats::get), showBank) { onClick(t) }
                HorizontalDivider(Modifier.padding(start = 68.dp), thickness = 0.5.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- charts

@Composable
fun Donut(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier.size(120.dp)) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        if (total <= 0f) {
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            return@Canvas
        }
        var start = -90f
        parts.forEach { (v, c) ->
            val sweep = v / total * 360f
            drawArc(c, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            start += sweep
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

@Composable
fun SpacerH(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun SimpleRowArrangement(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
