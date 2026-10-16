@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drx.casefiles.R
import com.drx.casefiles.data.Category
import com.drx.casefiles.data.Diagnosis
import com.drx.casefiles.data.Mark
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Region
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Stage
import com.drx.casefiles.data.ViewPos
import com.drx.casefiles.data.categoryText
import com.drx.casefiles.data.enumOrNull
import com.drx.casefiles.data.regionText
import com.drx.casefiles.data.sideShort
import com.drx.casefiles.data.stageText
import com.drx.casefiles.data.viewText
import com.drx.casefiles.data.visitLabels
import kotlinx.coroutines.launch

private fun List<Diagnosis>.textFor(m: Media): String =
    firstOrNull { it.patientId == m.patientId && it.region == m.region && it.side == m.side }?.text.orEmpty()

@Composable
fun ViewerScreen(initial: List<Media>, startIndex: Int, repo: Repo, nav: Nav) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Media>().also { it.addAll(initial) } }
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (initial.size - 1).coerceAtLeast(0)),
        pageCount = { items.size }
    )
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())
    val marks by remember { repo.dao.allMarks() }.collectAsState(emptyList())

    var panel by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var showStage by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var drawMode by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf("CIRCLE") }
    var colorIdx by remember { mutableIntStateOf(0) }
    var filled by remember { mutableStateOf(false) }

    fun update(f: (Media) -> Media) {
        val i = pager.currentPage
        val cur = items.getOrNull(i) ?: return
        val n = f(cur)
        items[i] = n
        scope.launch { repo.dao.updateMedia(n) }
    }

    LaunchedEffect(items.size) { if (items.isEmpty()) nav.pop() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isNotEmpty()) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed && !drawMode,
                modifier = Modifier.fillMaxSize(),
                key = { items.getOrNull(it)?.id ?: it }
            ) { page ->
                val m = items.getOrNull(page)
                if (m != null) {
                    val zs = remember(m.id) { ZoomState() }
                    LaunchedEffect(zs.scale, pager.currentPage) {
                        if (pager.currentPage == page) zoomed = zs.scale > 1.01f
                    }
                    if (m.isVideo) VideoPlayer(repo.file(m.path), Modifier.fillMaxSize()) else ZoomableImage(
                        model = repo.file(m.path),
                        state = zs,
                        modifier = Modifier.fillMaxSize(),
                        onSwipeUp = if (drawMode) null else ({ panel = true }),
                        onSwipeDown = if (drawMode) null else ({ if (panel) panel = false else nav.pop() }),
                        annotations = marks.filter { it.mediaId == m.id },
                        drawTool = if (drawMode) tool else null,
                        drawColor = AnnColors[colorIdx],
                        drawFilled = filled,
                        onDraw = { x1, y1, x2, y2 ->
                            scope.launch {
                                repo.dao.insertMark(
                                    Mark(mediaId = m.id, type = tool, x1 = x1, y1 = y1, x2 = x2, y2 = y2, color = AnnColors[colorIdx], filled = filled)
                                )
                            }
                        }
                    )
                }
            }

            val cur = items.getOrNull(pager.currentPage)
            if (cur != null) {
                val diag = diags.textFor(cur)
                FlowRow(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).fillMaxWidth(0.62f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (diag.isNotBlank()) Tag(diag, bg = Color(0xFFB71C1C))
                    val info = listOf(regionText(cur.region), sideShort(cur.side), viewText(cur.viewPos))
                        .filter { it.isNotBlank() }.joinToString(" ")
                    if (info.isNotBlank()) Tag(info)
                    if (cur.stage.isNotBlank()) Tag(stageText(cur.stage))
                    if (cur.healthy) Tag(stringResource(R.string.healthy), bg = HealthyGreen)
                }

                Column(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row {
                        IconButton(onClick = { drawMode = !drawMode }) { Text(if (drawMode) "✅" else "✏", fontSize = 20.sp) }
                        IconButton(onClick = { nav.push(Screen.Compare(cur, null)) }) { Text("↔", fontSize = 20.sp, color = Color.White) }
                    }
                    if (drawMode) {
                        Row(
                            Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { tool = if (tool == "CIRCLE") "ARROW" else "CIRCLE" }) {
                                Text(if (tool == "CIRCLE") "○" else "➜", fontSize = 20.sp, color = Color.White)
                            }
                            IconButton(onClick = { colorIdx = (colorIdx + 1) % AnnColors.size }) {
                                Box(Modifier.size(22.dp).background(Color(AnnColors[colorIdx]), CircleShape))
                            }
                            IconButton(onClick = { filled = !filled }) {
                                Text(if (filled) "◐" else "◯", fontSize = 20.sp, color = Color.White)
                            }
                            IconButton(onClick = { scope.launch { repo.dao.deleteLastMark(cur.id) } }) {
                                Text("↩", fontSize = 20.sp, color = Color.White)
                            }
                        }
                    }
                }

                if (!panel && cur.caption.isNotBlank()) {
                    Text(
                        cur.caption,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .navigationBarsPadding()
                            .padding(12.dp)
                    )
                }

                AnimatedVisibility(visible = panel, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                        Column(
                            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    true, { showName = true },
                                    {
                                        Text(
                                            listOf(regionText(cur.region), sideShort(cur.side), viewText(cur.viewPos))
                                                .filter { it.isNotBlank() }.joinToString(" ")
                                        )
                                    }
                                )
                                FilterChip(
                                    diag.isNotBlank(), { showDiag = true },
                                    {
                                        Text(
                                            if (diag.isBlank()) stringResource(R.string.diagnosis_add) else diag,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = if (diag.isNotBlank()) {
                                        {
                                            Icon(
                                                Icons.Default.Close, null,
                                                Modifier.size(18.dp).clickable {
                                                    scope.launch { repo.dao.clearDiagnosis(cur.patientId, cur.region, cur.side) }
                                                }
                                            )
                                        }
                                    } else null
                                )
                                FilterChip(cur.stage.isNotBlank(), { showStage = true }, { Text(stageText(cur.stage)) })
                                FilterChip(cur.healthy, { update { it.copy(healthy = !it.healthy) } }, { Text(stringResource(R.string.healthy)) })
                                IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, null) }
                            }
                            OutlinedTextField(
                                value = cur.caption,
                                onValueChange = { v -> update { it.copy(caption = v) } },
                                placeholder = { Text(stringResource(R.string.caption_hint)) },
                                minLines = 3,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (showName) {
                    NamePickerDialog(
                        initialSide = cur.side,
                        onPick = { r, v, sd ->
                            showName = false
                            update { it.copy(region = r.name, viewPos = v?.name ?: "", side = sd) }
                        },
                        onDismiss = { showName = false }
                    )
                }
                if (showDiag) {
                    DiagnosisDialog(
                        initial = diag,
                        onConfirm = { t ->
                            showDiag = false
                            scope.launch {
                                if (t.isBlank()) repo.dao.clearDiagnosis(cur.patientId, cur.region, cur.side)
                                else repo.dao.upsertDiagnosis(Diagnosis(patientId = cur.patientId, region = cur.region, side = cur.side, text = t))
                            }
                        },
                        onDismiss = { showDiag = false }
                    )
                }
            }
        }
    }

    if (showStage) {
        ChoiceDialog(
            stringResource(R.string.stage), stageOptions(), stringResource(R.string.custom_stage),
            onPick = { s -> showStage = false; update { it.copy(stage = s) } },
            onDismiss = { showStage = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_media_q),
            onConfirm = {
                showDelete = false
                val i = pager.currentPage
                val cur = items.getOrNull(i)
                if (cur != null) {
                    items.removeAt(i)
                    scope.launch { repo.deleteMedia(listOf(cur)) }
                }
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
fun SearchScreen(repo: Repo, nav: Nav) {
    val media by remember { repo.dao.allMedia() }.collectAsState(emptyList())
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())

    var q by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var side by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf<String?>(null) }
    var stage by remember { mutableStateOf<String?>(null) }
    var healthyOnly by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    val pMap = remember(patients) { patients.associateBy { it.id } }
    val results = remember(media, q, region, side, cat, stage, healthyOnly, pMap, diags) {
        val tokens = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
        // Diagnosis of the exact body part; a healthy-limb image also matches its patient's diagnosis of that region.
        val exact = diags.associate { "${it.patientId}|${it.region}|${it.side}" to it.text }
        val byRegion = diags.groupBy { "${it.patientId}|${it.region}" }.mapValues { e -> e.value.joinToString(" ") { it.text } }
        media.filter { m ->
            val d = exact["${m.patientId}|${m.region}|${m.side}"].orEmpty() +
                (if (m.healthy) " " + byRegion["${m.patientId}|${m.region}"].orEmpty() else "")
            val hay = (d + " " + (pMap[m.patientId]?.name ?: "") + " " + m.caption + " " + m.stage).lowercase()
            tokens.all { hay.contains(it) } &&
                (region == null || m.region == region) &&
                (side == null || m.side == side) &&
                (cat == null || m.category == cat) &&
                (stage == null || m.stage == stage) &&
                (!healthyOnly || m.healthy)
        }
    }
    val selMedia = results.filter { it.id in selected }

    Scaffold(
        topBar = {
            if (selected.isEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.results_fmt, results.size)) },
                    navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    actions = { IconButton(onClick = { showFilters = !showFilters }) { Text("⚙", fontSize = 20.sp) } }
                )
            } else {
                TopAppBar(
                    title = { Text("${selected.size}") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, null) } },
                    actions = {
                        if (selMedia.size == 2) {
                            IconButton(onClick = { nav.push(Screen.Compare(selMedia[0], selMedia[1])) }) {
                                Text("↔", fontSize = 20.sp)
                            }
                        }
                    }
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                q, { q = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp)
            )
            if (showFilters) {
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.region), style = MaterialTheme.typography.labelMedium)
                    ChipRow(
                        listOf<String?>(null) + Region.values().map { it.name },
                        { it == region },
                        { if (it == null) stringResource(R.string.all) else regionText(it) },
                        { region = if (region == it) null else it }
                    )
                    Text(stringResource(R.string.side), style = MaterialTheme.typography.labelMedium)
                    ChipRow(
                        listOf<String?>(null, "L", "R"),
                        { it == side },
                        { if (it == null) stringResource(R.string.all) else it },
                        { side = if (side == it) null else it }
                    )
                    ChipRow(
                        listOf<String?>(null) + Category.values().map { it.name },
                        { it == cat },
                        { if (it == null) stringResource(R.string.all) else categoryText(it) },
                        { cat = if (cat == it) null else it }
                    )
                    Text(stringResource(R.string.stage), style = MaterialTheme.typography.labelMedium)
                    ChipRow(
                        listOf<String?>(null) + Stage.values().map { it.name },
                        { it == stage },
                        { if (it == null) stringResource(R.string.all) else stageText(it) },
                        { stage = if (stage == it) null else it }
                    )
                    FilterChip(healthyOnly, { healthyOnly = !healthyOnly }, { Text(stringResource(R.string.healthy_only)) })
                }
            }
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_results)) }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.id }) { m ->
                        Thumb(
                            m, repo, m.id in selected,
                            label = pMap[m.patientId]?.name,
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    selected = if (m.id in selected) selected - m.id else selected + m.id
                                } else {
                                    nav.push(Screen.Viewer(results, results.indexOf(m)))
                                }
                            },
                            onLong = { selected = if (m.id in selected) selected - m.id else selected + m.id }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ compare

/**
 * Two panes. Before any divider drag each image fits its own half. After a drag, images keep their
 * full size and one simply covers part of the other, so zoom and pan are never disturbed.
 */
@Composable
private fun SplitPanes(
    vertical: Boolean,
    overlay: Boolean,
    fraction: Float,
    onDrag: (Float) -> Unit,
    modifier: Modifier,
    first: @Composable (Modifier, Alignment) -> Unit,
    second: @Composable (Modifier, Alignment) -> Unit
) {
    var total by remember { mutableStateOf(IntSize.Zero) }
    val tot by rememberUpdatedState(total)
    val fr by rememberUpdatedState(fraction)
    val cb by rememberUpdatedState(onDrag)
    val density = LocalDensity.current
    val handle = with(density) { 22.dp.roundToPx() }
    val f = fraction.coerceIn(0.1f, 0.9f)
    Box(modifier.onSizeChanged { total = it }.background(Color.Black)) {
        val tw = total.width
        val th = total.height
        if (tw > 0 && th > 0) {
            val w1 = (if (vertical) tw else (tw * f).toInt() - handle / 2).coerceAtLeast(1)
            val h1 = (if (vertical) (th * f).toInt() - handle / 2 else th).coerceAtLeast(1)
            val w2 = (if (vertical) tw else tw - w1 - handle).coerceAtLeast(1)
            val h2 = (if (vertical) th - h1 - handle else th).coerceAtLeast(1)
            fun Modifier.pane(cw: Int, ch: Int, ox: Int, oy: Int): Modifier =
                this.clipToBounds().layout { m, _ ->
                    val fw = if (overlay) tw else cw
                    val fh = if (overlay) th else ch
                    val p = m.measure(Constraints.fixed(fw, fh))
                    layout(cw, ch) { p.placeRelative(if (overlay) ox else 0, if (overlay) oy else 0) }
                }
            first(Modifier.align(Alignment.TopStart).pane(w1, h1, 0, 0), Alignment.TopStart)
            second(
                Modifier.align(Alignment.BottomEnd).pane(w2, h2, w2 - tw, h2 - th),
                if (vertical) Alignment.BottomStart else Alignment.TopEnd
            )
            Box(
                Modifier
                    .offset { if (vertical) IntOffset(0, h1) else IntOffset(w1, 0) }
                    .size(
                        with(density) { (if (vertical) tw else handle).toDp() },
                        with(density) { (if (vertical) handle else th).toDp() }
                    )
                    .background(Color.Gray)
                    .pointerInput(Unit) {
                        detectDragGestures { change, d ->
                            change.consume()
                            val t = tot
                            val delta = if (vertical) d.y / t.height.coerceAtLeast(1) else d.x / t.width.coerceAtLeast(1)
                            cb((fr + delta).coerceIn(0.1f, 0.9f))
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text(if (vertical) "═" else "║", color = Color.White, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun ComparePane(
    m: Media?,
    label: String,
    zs: ZoomState,
    repo: Repo,
    marks: List<Mark>,
    diag: String,
    mirror: Boolean,
    onFlip: () -> Unit,
    onHealthy: (Media) -> Unit,
    onDiag: (Media) -> Unit,
    onClearDiag: (Media) -> Unit,
    corner: Alignment,
    modifier: Modifier
) {
    Box(modifier.background(Color(0xFF111111))) {
        if (m == null) {
            Text(stringResource(R.string.no_image), color = Color.Gray, modifier = Modifier.align(Alignment.Center).padding(8.dp), textAlign = TextAlign.Center)
        } else {
            ZoomableImage(
                repo.file(m.path), zs, Modifier.fillMaxSize(),
                annotations = marks.filter { it.mediaId == m.id }, mirror = mirror
            )
            Column(
                Modifier.align(corner).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = if (corner == Alignment.TopEnd) Alignment.End else Alignment.Start
            ) {
                Tag(label)
                val info = listOf(sideShort(m.side), viewText(m.viewPos)).filter { it.isNotBlank() }.joinToString(" ")
                if (info.isNotBlank()) Tag(info)
                if (m.stage.isNotBlank()) Tag(stageText(m.stage))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(m.healthy, { onHealthy(m) }, { Text(stringResource(R.string.healthy_short)) })
                    FilterChip(
                        diag.isNotBlank(), { onDiag(m) },
                        {
                            Text(
                                if (diag.isBlank()) stringResource(R.string.diagnosis_add) else diag,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        },
                        trailingIcon = if (diag.isNotBlank()) {
                            { Icon(Icons.Default.Close, null, Modifier.size(18.dp).clickable { onClearDiag(m) }) }
                        } else null
                    )
                    FilterChip(mirror, { onFlip() }, { Text("⇋") })
                }
            }
        }
    }
}

/**
 * Patient mode ([b] == null): compares two time points (visit + stage, or the healthy limb)
 * of the same body part; swiping sideways shows the next view (AP, lateral, mortise...) of the
 * same two time points. Free mode: compares exactly [a] and [b], even from different patients.
 */
@Composable
fun CompareScreen(a: Media, b: Media?, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val visits by remember { repo.dao.allVisits() }.collectAsState(emptyList())
    val media by remember(a.patientId) { repo.dao.mediaOfPatientFlow(a.patientId) }.collectAsState(emptyList())
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())
    val marks by remember { repo.dao.allMarks() }.collectAsState(emptyList())

    val free = b != null
    val labels = remember(visits) { visitLabels(ctx, visits) }
    val healthyLabel = stringResource(R.string.healthy)

    var sync by remember { mutableStateOf(true) }
    var vertical by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(false) }
    var fraction by remember { mutableStateOf(0.5f) }
    var flipA by remember { mutableStateOf(false) }
    var flipB by remember { mutableStateOf(false) }
    var pickA by remember { mutableStateOf(false) }
    var pickB by remember { mutableStateOf(false) }
    var diagTarget by remember { mutableStateOf<Media?>(null) }

    fun keyOf(m: Media): String = if (m.healthy || m.side != a.side) "H" else "${m.visitId}|${m.stage}"

    val groups = remember(media, a) {
        media.filter {
            it.region == a.region && it.category == a.category &&
                (it.side == a.side || it.healthy || (it.side != "NONE" && a.side != "NONE"))
        }.groupBy { keyOf(it) }
    }
    val sortedKeys = remember(groups) { groups.entries.sortedBy { e -> e.value.minOf { it.takenAt } }.map { it.key } }
    var keyA by remember { mutableStateOf(keyOf(a)) }
    var keyBState by remember { mutableStateOf<String?>(null) }
    val defaultB = remember(sortedKeys, keyA) {
        val idx = sortedKeys.indexOf(keyA)
        (if (idx > 0) sortedKeys[idx - 1] else sortedKeys.firstOrNull { it != keyA }) ?: keyA
    }
    val keyB = keyBState ?: defaultB

    @Composable
    fun tpLabel(key: String): String {
        if (key == "H") return healthyLabel
        val vid = key.substringBefore("|").toLongOrNull() ?: return key
        val st = key.substringAfter("|")
        return labels[vid].orEmpty() + if (st.isNotBlank()) " · " + stageText(st) else ""
    }

    @Composable
    fun mediaLabel(m: Media): String =
        (patients.firstOrNull { it.id == m.patientId }?.name.orEmpty()) + " · " + labels[m.visitId].orEmpty()

    val gA = groups[keyA].orEmpty()
    val gB = groups[keyB].orEmpty()
    val pages: List<Triple<String, Media?, Media?>> =
        if (free) {
            listOf(Triple(a.viewPos, a, b))
        } else {
            (gA + gB).map { it.viewPos }.distinct()
                .sortedBy { enumOrNull<ViewPos>(it)?.ordinal ?: 999 }
                .map { v -> Triple(v, gA.firstOrNull { it.viewPos == v }, gB.firstOrNull { it.viewPos == v }) }
        }

    val pager = rememberPagerState(pageCount = { pages.size })
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(pages.size) {
        if (!positioned && pages.isNotEmpty()) {
            val idx = pages.indexOfFirst { it.first == a.viewPos }
            if (idx > 0) pager.scrollToPage(idx)
            positioned = true
        }
    }

    val onHealthy: (Media) -> Unit = { m -> scope.launch { repo.dao.setHealthy(listOf(m.id), !m.healthy) } }
    val onDiag: (Media) -> Unit = { m -> diagTarget = m }
    val onClear: (Media) -> Unit = { m -> scope.launch { repo.dao.clearDiagnosis(m.patientId, m.region, m.side) } }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.Default.Close, null, tint = Color.White) }
            if (!free) {
                FilterChip(true, { pickA = true }, { Text("A: " + tpLabel(keyA), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                FilterChip(true, { pickB = true }, { Text("B: " + tpLabel(keyB), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sync_zoom), color = Color.White, fontSize = 12.sp)
                Switch(checked = sync, onCheckedChange = { sync = it })
            }
            TextButton(onClick = { vertical = !vertical }) { Text("⇅", color = Color.White, fontSize = 20.sp) }
            TextButton(onClick = { fraction = 0.5f; overlay = false }) { Text("⟲", color = Color.White, fontSize = 20.sp) }
        }

        if (pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_image), color = Color.Gray)
            }
        } else {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { pi ->
                val page = pages.getOrNull(pi)
                if (page != null) {
                    val shared = remember { ZoomState() }
                    val z1 = remember { ZoomState() }
                    val z2 = remember { ZoomState() }
                    Column(Modifier.fillMaxSize()) {
                        val vName = if (page.first.isBlank()) "—" else viewText(page.first)
                        Text(
                            stringResource(R.string.view_counter_fmt, vName, pi + 1, pages.size),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(2.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        val label1 = if (free) mediaLabel(a) else tpLabel(keyA)
                        val label2 = if (free && b != null) mediaLabel(b) else tpLabel(keyB)
                        SplitPanes(
                            vertical = vertical,
                            overlay = overlay,
                            fraction = fraction,
                            onDrag = { fraction = it; overlay = true },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            first = { mod, corner ->
                                ComparePane(
                                    page.second, label1, if (sync) shared else z1, repo, marks,
                                    page.second?.let { diags.textFor(it) }.orEmpty(),
                                    flipA, { flipA = !flipA }, onHealthy, onDiag, onClear, corner, mod
                                )
                            },
                            second = { mod, corner ->
                                ComparePane(
                                    page.third, label2, if (sync) shared else z2, repo, marks,
                                    page.third?.let { diags.textFor(it) }.orEmpty(),
                                    flipB, { flipB = !flipB }, onHealthy, onDiag, onClear, corner, mod
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (pickA || pickB) {
        ChoiceDialog(
            stringResource(R.string.tp_pick),
            sortedKeys.map { it to tpLabel(it) },
            onPick = { k ->
                if (pickA) keyA = k else keyBState = k
                pickA = false
                pickB = false
            },
            onDismiss = { pickA = false; pickB = false }
        )
    }
    diagTarget?.let { m ->
        DiagnosisDialog(
            initial = diags.textFor(m),
            onConfirm = { t ->
                diagTarget = null
                scope.launch {
                    if (t.isBlank()) repo.dao.clearDiagnosis(m.patientId, m.region, m.side)
                    else repo.dao.upsertDiagnosis(Diagnosis(patientId = m.patientId, region = m.region, side = m.side, text = t))
                }
            },
            onDismiss = { diagTarget = null }
        )
    }
}

/** Every medium of a patient in time order, whatever its category. */
@Composable
fun GalleryScreen(patientId: Long, repo: Repo, nav: Nav) {
    val media by remember(patientId) { repo.dao.mediaOfPatientFlow(patientId) }.collectAsState(emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery) + " (" + media.size + ")") },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            items(media, key = { it.id }) { m ->
                Thumb(
                    m, repo, false,
                    onClick = { nav.push(Screen.Viewer(media, media.indexOf(m))) },
                    onLong = {}
                )
            }
        }
    }
}
