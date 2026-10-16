@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.drx.casefiles.data.DiagnosisDictionary
import com.drx.casefiles.data.Mark
import com.drx.casefiles.data.NameOptions
import com.drx.casefiles.data.Region
import com.drx.casefiles.data.Side
import com.drx.casefiles.data.ViewPos
import com.drx.casefiles.data.regionText
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.drx.casefiles.R
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Stage
import com.drx.casefiles.data.sideShort
import com.drx.casefiles.data.stageText
import com.drx.casefiles.data.viewText
import kotlin.math.abs

val HealthyGreen = Color(0xFF2E9E5B)

// ---------------------------------------------------------------- zoom

class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

val AnnColors = listOf(
    0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E88E5.toInt()
)

private fun DrawScope.drawMark(
    type: String, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, filled: Boolean, r: Rect
) {
    val p1 = Offset(r.left + x1 * r.width, r.top + y1 * r.height)
    val p2 = Offset(r.left + x2 * r.width, r.top + y2 * r.height)
    val c = Color(color)
    val sw = 3.dp.toPx()
    if (type == "ARROW") {
        drawLine(c, p1, p2, strokeWidth = sw)
        val ang = atan2(p2.y - p1.y, p2.x - p1.x)
        val len = 18.dp.toPx()
        for (da in listOf(0.5f, -0.5f)) {
            val a = ang + Math.PI.toFloat() - da
            drawLine(c, p2, Offset(p2.x + len * cos(a), p2.y + len * sin(a)), strokeWidth = sw)
        }
    } else {
        val rad = hypot(p2.x - p1.x, p2.y - p1.y)
        if (filled) drawCircle(c.copy(alpha = 0.3f), rad, p1)
        drawCircle(c, rad, p1, style = Stroke(width = sw))
    }
}

/**
 * Pinch / double-tap zoom and pan, plus optional drawing of circles and arrows.
 * With [onSwipeUp] / [onSwipeDown] a vertical one-finger swipe at scale 1 triggers them;
 * horizontal swipes are left to a parent pager. With [drawTool] set, one finger draws
 * and two fingers zoom.
 */
@Composable
fun ZoomableImage(
    model: Any,
    state: ZoomState,
    modifier: Modifier = Modifier,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    annotations: List<Mark> = emptyList(),
    drawTool: String? = null,
    drawColor: Int = AnnColors[0],
    drawFilled: Boolean = false,
    onDraw: ((Float, Float, Float, Float) -> Unit)? = null,
    mirror: Boolean = false
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imgSize by remember { mutableStateOf(Size.Zero) }
    var draft by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val up by rememberUpdatedState(onSwipeUp)
    val down by rememberUpdatedState(onSwipeDown)
    val tool by rememberUpdatedState(drawTool)
    val drawCb by rememberUpdatedState(onDraw)

    fun fit(): Rect {
        val w = boxSize.width.toFloat()
        val h = boxSize.height.toFloat()
        if (imgSize.width <= 0f || imgSize.height <= 0f) return Rect(0f, 0f, w, h)
        val sc = minOf(w / imgSize.width, h / imgSize.height)
        val dw = imgSize.width * sc
        val dh = imgSize.height * sc
        return Rect((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
    }

    fun toNorm(p: Offset): Offset {
        val c = Offset(boxSize.width / 2f, boxSize.height / 2f)
        val q = c + (p - c - state.offset) / state.scale
        val r = fit()
        return Offset((q.x - r.left) / r.width, (q.y - r.top) / r.height)
    }

    Box(
        modifier
            .onSizeChanged { boxSize = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (state.scale > 1f) state.reset() else state.scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val startN = toNorm(first.position)
                    var multi = false
                    var dx = 0f
                    var dy = 0f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            multi = true
                            draft = null
                        }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (multi || (state.scale > 1f && tool == null)) {
                            val ns = (state.scale * zoom).coerceIn(1f, 6f)
                            state.scale = ns
                            if (ns > 1f) {
                                val maxX = boxSize.width * (ns - 1f) / 2f
                                val maxY = boxSize.height * (ns - 1f) / 2f
                                val o = state.offset + pan
                                state.offset = Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                            } else {
                                state.offset = Offset.Zero
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (tool != null) {
                            draft = Pair(startN, toNorm(event.changes.first().position))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            dx += pan.x
                            dy += pan.y
                            if ((up != null || down != null) && abs(dy) > 24f && abs(dy) > abs(dx) * 1.5f) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    val d = draft
                    if (d != null && tool != null && !multi) {
                        drawCb?.invoke(d.first.x, d.first.y, d.second.x, d.second.y)
                    }
                    draft = null
                    if (tool == null && !multi && state.scale <= 1f && abs(dy) > 140f && abs(dy) > abs(dx) * 1.5f) {
                        if (dy < 0f) up?.invoke() else down?.invoke()
                    }
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (mirror) -state.scale else state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                }
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { st ->
                    val sz = st.painter.intrinsicSize
                    if (sz.width > 0f && sz.height > 0f && sz.width.isFinite() && sz.height.isFinite()) imgSize = sz
                },
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                val r = fit()
                annotations.forEach { drawMark(it.type, it.x1, it.y1, it.x2, it.y2, it.color, it.filled, r) }
                draft?.let { drawMark(tool ?: "CIRCLE", it.first.x, it.first.y, it.second.x, it.second.y, drawColor, drawFilled, r) }
            }
        }
    }
}

// ---------------------------------------------------------------- thumbnails and grid

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, bg: Color = Color.Black.copy(alpha = 0.6f)) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun Thumb(
    m: Media,
    repo: Repo,
    selected: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.DarkGray)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        AsyncImage(
            model = repo.file(m.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (m.healthy) {
            Tag(stringResource(R.string.healthy_short), Modifier.align(Alignment.TopStart).padding(3.dp), HealthyGreen)
        }
        if (m.isVideo) Tag("▶", Modifier.align(Alignment.TopEnd).padding(3.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val sv = listOf(sideShort(m.side), viewText(m.viewPos)).filter { it.isNotBlank() }.joinToString(" ")
            if (sv.isNotBlank()) Tag(sv)
            if (m.stage.isNotBlank()) Tag(stageText(m.stage))
            if (!label.isNullOrBlank()) Tag(label)
        }
        if (selected) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, null, tint = Color.White) }
        }
    }
}

private sealed interface Cell {
    data class Item(val m: Media) : Cell
    data class More(val n: Int) : Cell
}

/** Grid inside a scrolling column. With [maxRows] the last tile becomes a "+N more" tile. */
@Composable
fun MediaGrid(
    items: List<Media>,
    repo: Repo,
    selected: Set<Long>,
    maxRows: Int?,
    onExpand: () -> Unit,
    onClick: (Media) -> Unit,
    onLong: (Media) -> Unit,
    columns: Int = 3
) {
    val cap = if (maxRows != null) maxRows * columns else null
    val cells: List<Cell> =
        if (cap != null && items.size > cap) {
            items.take(cap - 1).map { Cell.Item(it) } + Cell.More(items.size - (cap - 1))
        } else {
            items.map { Cell.Item(it) }
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c ->
                    when (c) {
                        is Cell.Item -> Thumb(
                            c.m, repo, c.m.id in selected, Modifier.weight(1f),
                            onClick = { onClick(c.m) }, onLong = { onLong(c.m) }
                        )
                        is Cell.More -> Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .combinedClickable(onClick = onExpand),
                            contentAlignment = Alignment.Center
                        ) { Text(stringResource(R.string.more_fmt, c.n), style = MaterialTheme.typography.titleSmall) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs and fields

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun TextInputDialog(title: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onConfirm(v.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Options are (key, label). With [customHint] the user may also type a free-text key. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    customHint: String? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    TextButton(onClick = { onPick(key) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
                if (customHint != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            custom, { custom = it }, singleLine = true,
                            label = { Text(customHint) }, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { if (custom.isNotBlank()) onPick(custom.trim()) }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun stageOptions(): List<Pair<String, String>> =
    listOf("" to stringResource(R.string.stage_none)) + Stage.values().map { it.name to stringResource(it.label) }

/** Text field where Enter moves to the next field instead of adding a new line. */
@Composable
fun FormField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    last: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    val fm = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val typedEnter = v.length == value.length + 1 && v.contains('\n') && v.replace("\n", "") == value
            if (typedEnter) {
                if (last) fm.clearFocus() else fm.moveFocus(FocusDirection.Next)
            } else {
                onChange(v)
            }
        },
        label = { Text(label) },
        maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next, keyboardType = keyboard),
        keyboardActions = KeyboardActions(
            onNext = { fm.moveFocus(FocusDirection.Next) },
            onDone = { fm.clearFocus() }
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            FilterChip(selected = isSelected(item), onClick = { onClick(item) }, label = { Text(label(item)) })
        }
    }
}


@Composable
fun DatePickDialog(initial: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val d = st.selectedDateMillis
                if (d != null) onPick(d) else onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        }
    ) { DatePicker(state = st) }
}

/** Type two letters (and optionally L or R) and pick the full image name, e.g. "Ankle R Mortise". */
@Composable
fun NamePickerDialog(
    initialSide: String,
    onPick: (Region, ViewPos?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    var side by remember { mutableStateOf(initialSide) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    val tokens = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
    val sideTok = tokens.firstOrNull { it == "l" || it == "r" }
    val textTokens = tokens.filter { it != "l" && it != "r" }
    val effSide = when (sideTok) {
        "l" -> "L"
        "r" -> "R"
        else -> side
    }
    val labeled = NameOptions.map { o ->
        o to (regionText(o.region.name) + (o.view?.let { " " + viewText(it.name) } ?: ""))
    }
    val shown = labeled.filter { (o, label) ->
        textTokens.all { t ->
            label.lowercase().contains(t) ||
                o.region.name.lowercase().contains(t) ||
                (o.view?.name?.lowercase()?.contains(t) ?: false)
        }
    }.take(40)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    q, { q = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.name_search_hint)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Side.values().forEach { sd ->
                        FilterChip(effSide == sd.name, { side = sd.name }, { Text(stringResource(sd.label)) })
                    }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(shown) { (o, label) ->
                        TextButton(onClick = { onPick(o.region, o.view, effSide) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                (label + " " + sideShort(effSide)).trim(),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Free text diagnosis with suggestions; set once, it shows on every view of that body part. */
@Composable
fun DiagnosisDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val tokens = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
    val shown = DiagnosisDictionary.filter { d -> tokens.all { d.lowercase().contains(it) } }.take(30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnosis)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    q, { q = it }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(shown) { d ->
                        TextButton(onClick = { q = d }, modifier = Modifier.fillMaxWidth()) {
                            Text(d, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(q.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}


/** Pick a limb (region + side) for examination; no radiographic view needed. */
@Composable
fun LimbPickerDialog(onPick: (Region, String) -> Unit, onDismiss: () -> Unit) {
    var side by remember { mutableStateOf("NONE") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.limb)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Side.values().forEach { sd ->
                        FilterChip(side == sd.name, { side = sd.name }, { Text(stringResource(sd.label)) })
                    }
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(Region.values().filter { it != Region.OTHER }) { r ->
                        TextButton(onClick = { onPick(r, side) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(r.label), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
