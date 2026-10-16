@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import com.drx.casefiles.data.Fill
import com.drx.casefiles.data.LabValue
import com.drx.casefiles.data.SupportItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drx.casefiles.R
import com.drx.casefiles.data.Category
import com.drx.casefiles.data.ClinicalSign
import com.drx.casefiles.data.Exam
import com.drx.casefiles.data.GeneralCondition
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.MedOrder
import com.drx.casefiles.data.NeuroState
import com.drx.casefiles.data.OrderKind
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.VascularState
import com.drx.casefiles.data.Visit
import com.drx.casefiles.data.Vital
import com.drx.casefiles.data.VitalType
import com.drx.casefiles.data.VitalsState
import com.drx.casefiles.data.enumOrNull
import com.drx.casefiles.data.limbText
import com.drx.casefiles.data.regionText
import com.drx.casefiles.data.sharePdf
import com.drx.casefiles.data.visitLabels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun parseExtras(s: String): List<Pair<String, String>> =
    s.split("\u0002").filter { it.isNotBlank() }.map {
        val i = it.indexOf('\u0001')
        if (i < 0) it to "" else it.substring(0, i) to it.substring(i + 1)
    }

private fun buildExtras(l: List<Pair<String, String>>): String = l.joinToString("\u0002") { it.first + "\u0001" + it.second }

private fun setExtra(s: String, title: String, text: String): String =
    buildExtras(parseExtras(s).map { if (it.first == title) title to text else it })

private fun nowHm(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

/** Text field that saves itself shortly after typing stops and when the screen closes. */
@Composable
fun DebouncedField(label: String, dbValue: String, last: Boolean = false, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(dbValue) }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(dbValue) { if (!touched) text = dbValue }
    LaunchedEffect(text) {
        if (touched) {
            delay(500)
            onSave(text)
        }
    }
    val latest by rememberUpdatedState(text)
    val latestTouched by rememberUpdatedState(touched)
    val cb by rememberUpdatedState(onSave)
    DisposableEffect(Unit) { onDispose { if (latestTouched) cb(latest) } }
    FormField(label, text, last = last) { text = it; touched = true }
}

/** Key points / hashtags. Enter adds a new keyword, it does not add a line. */
@Composable
private fun KeywordsSection(current: String, all: List<String>, onChange: (String) -> Unit, onOpen: (String) -> Unit) {
    val list = current.split("|").filter { it.isNotBlank() }
    var text by remember { mutableStateOf("") }
    fun add(raw: String) {
        val w = raw.trim().trimStart('#').trim()
        if (w.isNotBlank() && list.none { it.equals(w, true) }) onChange((list + w).joinToString("|"))
        text = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.sec_keywords), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            text, { v -> if (v.contains('\n')) add(v) else text = v },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.keyword_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(text) }),
            modifier = Modifier.fillMaxWidth()
        )
        if (list.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { w ->
                    FilterChip(
                        selected = true,
                        onClick = { onOpen(w) },
                        label = { Text("#$w") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close, null,
                                Modifier.size(16.dp).clickable { onChange(list.filter { it != w }.joinToString("|")) }
                            )
                        }
                    )
                }
            }
        }
        val suggestions = all.filter { s -> list.none { it.equals(s, true) } && (text.isBlank() || s.contains(text, true)) }.take(12)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { s -> FilterChip(false, { add(s) }, { Text("#$s") }) }
            }
        }
    }
}

@Composable
private fun ExamCard(e: Exam, onChange: (Exam) -> Unit, onDelete: () -> Unit) {
    val limb = limbText(e.region, e.side) + " (" + regionText(e.region) + ")"
    val signs = e.clinical.split(",").filter { it.isNotBlank() }
    val supports = e.support.split(",").filter { it.isNotBlank() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(limb, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
            Text(stringResource(R.string.exam_clinical) + ": " + limb, style = MaterialTheme.typography.labelMedium)
            ChipRow(
                ClinicalSign.values().toList(), { it.name in signs }, { stringResource(it.label) },
                { c ->
                    val n = if (c.name in signs) signs - c.name else signs + c.name
                    onChange(e.copy(clinical = n.joinToString(",")))
                }
            )
            Text(stringResource(R.string.exam_support) + ": " + limb, style = MaterialTheme.typography.labelMedium)
            ChipRow(
                SupportItem.values().toList(), { it.name in supports }, { stringResource(it.label) },
                { c ->
                    val n = if (c.name in supports) supports - c.name else supports + c.name
                    onChange(e.copy(support = n.joinToString(",")))
                }
            )
            Text(stringResource(R.string.exam_vascular) + ": " + limb, style = MaterialTheme.typography.labelMedium)
            ChipRow(
                VascularState.values().toList(), { it.name == e.vascular }, { stringResource(it.label) },
                { c -> onChange(e.copy(vascular = if (e.vascular == c.name) "" else c.name)) }
            )
            Text(stringResource(R.string.exam_neuro) + ": " + limb, style = MaterialTheme.typography.labelMedium)
            ChipRow(
                NeuroState.values().toList(), { it.name == e.neuro }, { stringResource(it.label) },
                { c -> onChange(e.copy(neuro = if (e.neuro == c.name) "" else c.name)) }
            )
            DebouncedField(stringResource(R.string.note), e.note) { t -> onChange(e.copy(note = t)) }
        }
    }
}

@Composable
private fun VitalDialog(onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(VitalType.BP.name) }
    var value by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(nowHm()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_reading)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(VitalType.values().toList(), { it.name == type }, { stringResource(it.label) }, { type = it.name })
                OutlinedTextField(value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.value_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, singleLine = true, label = { Text(stringResource(R.string.time_hint)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(type, value.trim(), time.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun OrderDialog(previous: List<MedOrder>, onConfirm: (MedOrder) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(OrderKind.DRUG.name) }
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var perDay by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(nowHm()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_order)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipRow(OrderKind.values().toList(), { it.name == kind }, { stringResource(it.label) }, { kind = it.name })
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.ord_name)) }, modifier = Modifier.fillMaxWidth())
                val sugg = if (name.length >= 2) {
                    previous.filter { it.name.contains(name, true) && !it.name.equals(name, true) }.map { it.name }.distinct().take(5)
                } else {
                    emptyList()
                }
                if (sugg.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sugg.forEach { sName ->
                            FilterChip(false, {
                                name = sName
                                previous.lastOrNull { it.name == sName }?.let { p ->
                                    kind = p.kind
                                    dose = p.dose
                                    qty = p.qty
                                    perDay = p.perDay
                                }
                            }, { Text(sName) })
                        }
                    }
                }
                OutlinedTextField(dose, { dose = it }, singleLine = true, label = { Text(stringResource(R.string.ord_dose)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(qty, { qty = it }, singleLine = true, label = { Text(stringResource(R.string.ord_qty)) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(perDay, { perDay = it }, singleLine = true, label = { Text(stringResource(R.string.ord_perday)) }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(time, { time = it }, singleLine = true, label = { Text(stringResource(R.string.time_hint)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(MedOrder(visitId = 0, kind = kind, name = name.trim(), dose = dose.trim(), qty = qty.trim(), perDay = perDay.trim(), time = time.trim()))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun VisitScreen(id: Long, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val visit by remember(id) { repo.dao.visit(id) }.collectAsState(null)
    val media by remember(id) { repo.dao.media(id) }.collectAsState(emptyList())
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val allVisits by remember { repo.dao.allVisits() }.collectAsState(emptyList())
    val admissions by remember { repo.dao.allAdmissions() }.collectAsState(emptyList())
    val exams by remember(id) { repo.dao.exams(id) }.collectAsState(emptyList())
    val vitals by remember(id) { repo.dao.vitals(id) }.collectAsState(emptyList())
    val orders by remember(id) { repo.dao.orders(id) }.collectAsState(emptyList())
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())
    val allOrders by remember { repo.dao.allOrders() }.collectAsState(emptyList())
    val refs by remember { repo.dao.refRanges() }.collectAsState(emptyList())
    val allLabValues by remember { repo.dao.allLabValues() }.collectAsState(emptyList())
    val patientMedia by remember(visit?.patientId) { repo.dao.mediaOfPatientFlow(visit?.patientId ?: -1L) }.collectAsState(emptyList())

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showStage by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showVital by remember { mutableStateOf(false) }
    var showOrder by remember { mutableStateOf(false) }
    var showLimb by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showLabValue by remember { mutableStateOf(false) }
    var showExtra by remember { mutableStateOf(false) }
    var showClassify by remember { mutableStateOf(false) }
    var classifyCat by remember { mutableStateOf<String?>(null) }
    val openMap = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var pendingCat by remember { mutableStateOf(Category.CLINICAL) }

    fun saveVisit(f: (Visit) -> Visit) {
        repo.appScope.launch {
            val cur = repo.dao.visitOnce(id) ?: return@launch
            repo.dao.updateVisit(f(cur))
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val v = visit
        if (v != null && uris.isNotEmpty()) {
            scope.launch {
                repo.importImages(
                    uris,
                    Media(
                        visitId = v.id, patientId = v.patientId, path = "",
                        category = pendingCat.name, region = CamPrefs.region,
                        side = CamPrefs.side, viewPos = CamPrefs.view
                    )
                )
            }
        }
    }

    val labels = remember(allVisits) { visitLabels(ctx, allVisits) }
    val patientName = patients.firstOrNull { it.id == visit?.patientId }?.name.orEmpty()
    val title = "$patientName · ${labels[id].orEmpty()}"
    val selMedia = media.filter { it.id in selected }
    val allKeywords = remember(allVisits) {
        allVisits.flatMap { it.keywords.split("|") }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }

    // Lab images: show the other lab results of the same admission first when swiping.
    fun labList(): List<Media> {
        val v = visit ?: return emptyList()
        val ids = if (v.admissionId != null) allVisits.filter { it.admissionId == v.admissionId }.map { it.id } else listOf(id)
        return patientMedia.filter { it.category == Category.LAB.name && it.visitId in ids }
            .sortedWith(compareByDescending<Media> { it.visitId == id }.thenByDescending { it.takenAt })
    }

    fun LazyListScope.catSection(cat: Category) {
        val list = media.filter { it.category == cat.name }.reversed()
        item(key = "cat_" + cat.name) {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(cat.label)} (${list.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        pendingCat = cat
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Text("🖼", fontSize = 20.sp) }
                    IconButton(onClick = { nav.push(Screen.Camera(id, cat.name)) }) { Text("📷", fontSize = 20.sp) }
                }
                if (list.isNotEmpty()) {
                    MediaGrid(
                        items = list, repo = repo, selected = selected,
                        maxRows = if (expanded[cat.name] == true) null else 2,
                        onExpand = { expanded[cat.name] = true },
                        onClick = { m ->
                            if (selected.isNotEmpty()) {
                                selected = if (m.id in selected) selected - m.id else selected + m.id
                            } else if (cat == Category.LAB) {
                                val l = labList()
                                nav.push(Screen.Viewer(l, l.indexOfFirst { it.id == m.id }.coerceAtLeast(0)))
                            } else {
                                nav.push(Screen.Viewer(list, list.indexOf(m)))
                            }
                        },
                        onLong = { m -> selected = if (m.id in selected) selected - m.id else selected + m.id }
                    )
                }
            }
        }
    }

    fun LazyListScope.sectionTitle(key: String, @StringRes res: Int) {
        item(key = "t_$key") {
            Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
        }
    }

    /** Collapsible section title; returns true when the section is open. */
    fun LazyListScope.sectionHeader(key: String, @StringRes res: Int, default: Boolean): Boolean {
        val isOpen = openMap[key] ?: default
        item(key = "h_$key") {
            Text(
                (if (isOpen) "▾ " else "▸ ") + stringResource(res),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable { openMap[key] = !isOpen }.padding(top = 10.dp, bottom = 4.dp)
            )
        }
        return isOpen
    }

    fun copyPrevious() {
        val v0 = visit ?: return
        val prev = allVisits.filter { it.patientId == v0.patientId && it.id != v0.id && it.date <= v0.date }
            .sortedByDescending { it.date }
            .firstOrNull { pv -> allOrders.any { it.visitId == pv.id } } ?: return
        scope.launch {
            allOrders.filter { it.visitId == prev.id }.forEach { repo.dao.insertOrder(it.copy(id = 0, visitId = id, given = false)) }
        }
    }

    Scaffold(
        topBar = {
            if (selected.isEmpty()) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    actions = {
                        IconButton(onClick = { showShare = true }) { Text("📤", fontSize = 20.sp) }
                        IconButton(onClick = { showDate = true }) { Text("📅", fontSize = 20.sp) }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("${selected.size}") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, null) } },
                    actions = {
                        if (selMedia.size == 2) {
                            IconButton(onClick = { nav.push(Screen.Compare(selMedia[0], selMedia[1])) }) { Text("↔", fontSize = 20.sp) }
                        }
                        IconButton(onClick = { showClassify = true }) { Text("🗂", fontSize = 20.sp) }
                        IconButton(onClick = { showStage = true }) { Text("🏷", fontSize = 20.sp) }
                        IconButton(onClick = {
                            val value = selMedia.any { !it.healthy }
                            scope.launch { repo.dao.setHealthy(selected.toList(), value) }
                            selected = emptySet()
                        }) { Text("🟢", fontSize = 20.sp) }
                        IconButton(onClick = { showMove = true }) { Text("📁", fontSize = 20.sp) }
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, null) }
                    }
                )
            }
        }
    ) { pad ->
        val v = visit
        if (v == null) {
            Column(Modifier.padding(pad).fillMaxSize()) {}
        } else {
            val radioFirst = v.admissionId == null || v.kind == "DISCHARGE"
            val order = if (radioFirst) {
                listOf("keys", "radio", "clinical", "consult", "lab", "orders", "notes", "extras", "general", "vitals", "lines", "needs", "other")
            } else {
                listOf("keys", "general", "vitals", "lines", "clinical", "needs", "orders", "consult", "lab", "radio", "notes", "extras", "other")
            }
            val radioKeys = media.filter { it.category == "XRAY" || it.category == "MRI" || it.category == "CT" }
                .map { it.region + "|" + it.side }
            val dxKeys = diags.filter { it.patientId == v.patientId && it.region != "OTHER" }.map { it.region + "|" + it.side }
            val limbKeys = (radioKeys + exams.map { it.region + "|" + it.side } + dxKeys).distinct()
            val admission = admissions.firstOrNull { it.id == v.admissionId }

            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (admission != null) {
                    item(key = "discharge") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = v.kind == "DISCHARGE",
                                onCheckedChange = { on ->
                                    saveVisit { it.copy(kind = if (on) "DISCHARGE" else "AM") }
                                    scope.launch {
                                        repo.dao.updateAdmission(admission.copy(discharged = on))
                                    }
                                }
                            )
                            Text(stringResource(R.string.discharged_check))
                        }
                    }
                }
                order.forEach { key ->
                    when (key) {
                        "keys" -> item(key = "keys") {
                            KeywordsSection(
                                v.keywords, allKeywords,
                                onChange = { k -> saveVisit { it.copy(keywords = k) } },
                                onOpen = { w -> nav.push(Screen.Keyword(w)) }
                            )
                        }
                        "general" -> item(key = "general") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.sec_general), style = MaterialTheme.typography.titleSmall)
                                ChipRow(
                                    GeneralCondition.values().toList(), { it.name == v.generalCondition }, { stringResource(it.label) },
                                    { c -> saveVisit { x -> x.copy(generalCondition = if (x.generalCondition == c.name) "" else c.name) } }
                                )
                            }
                        }
                        "vitals" -> item(key = "vitals") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.sec_vitals), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { showVital = true }) { Icon(Icons.Default.Add, null) }
                                }
                                ChipRow(
                                    VitalsState.values().toList(), { it.name == v.vitalsStable }, { stringResource(it.label) },
                                    { c -> saveVisit { x -> x.copy(vitalsStable = if (x.vitalsStable == c.name) "" else c.name) } }
                                )
                                vitals.forEach { r ->
                                    val t = enumOrNull<VitalType>(r.type)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            r.time + " · " + (if (t != null) stringResource(t.label) else r.type) + " · " + r.value,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { scope.launch { repo.dao.deleteVital(r) } }) { Icon(Icons.Default.Close, null) }
                                    }
                                }
                            }
                        }
                        "clinical" -> {
                            if (sectionHeader("clinical", R.string.sec_clinical, true)) {
                                item(key = "gross_note") {
                                    DebouncedField(stringResource(R.string.gross_hint), v.gross) { t -> saveVisit { it.copy(gross = t) } }
                                }
                                catSection(Category.CLINICAL)
                            item(key = "exams_header") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.sec_exams), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { showLimb = true }) { Icon(Icons.Default.Add, null) }
                                }
                            }
                            items(limbKeys, key = { "exam_$it" }) { k ->
                                val region = k.substringBefore("|")
                                val side = k.substringAfter("|")
                                val e = exams.firstOrNull { it.region == region && it.side == side } ?: Exam(visitId = id, region = region, side = side)
                                ExamCard(
                                    e,
                                    onChange = { n -> repo.appScope.launch { repo.dao.upsertExam(n) } },
                                    onDelete = { if (e.id != 0L) scope.launch { repo.dao.deleteExam(e) } }
                                )
                            }
                            }
                        }
                        "orders" -> {
                            item(key = "orders_header") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.sec_orders), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { copyPrevious() }) { Text(stringResource(R.string.copy_prev), fontSize = 12.sp) }
                                    if (orders.isNotEmpty()) {
                                        TextButton(onClick = { scope.launch { repo.dao.clearOrders(id) } }) { Text(stringResource(R.string.clear_all), fontSize = 12.sp) }
                                    }
                                    IconButton(onClick = { showOrder = true }) { Icon(Icons.Default.Add, null) }
                                }
                            }
                            items(orders, key = { "order_" + it.id }) { o ->
                                val k = enumOrNull<OrderKind>(o.kind)
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = o.given, onCheckedChange = { g -> scope.launch { repo.dao.updateOrder(o.copy(given = g)) } })
                                        Column(Modifier.weight(1f)) {
                                            Text((if (k != null) stringResource(k.label) + ": " else "") + o.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                listOf(o.dose, o.qty.takeIf { it.isNotBlank() }?.let { "× $it" }, o.perDay.takeIf { it.isNotBlank() }?.let { "$it/d" }, o.time)
                                                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        IconButton(onClick = { scope.launch { repo.dao.deleteOrder(o) } }) { Icon(Icons.Default.Delete, null) }
                                    }
                                }
                            }
                        }
                        "notes" -> {
                            catSection(Category.RX)
                            item(key = "notes") {
                                DebouncedField(stringResource(R.string.notes), v.notes, last = true) { t -> saveVisit { it.copy(notes = t) } }
                            }
                        }
                        "consult" -> catSection(Category.CONSULT)
                        "lines" -> item(key = "lines") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.lines_title), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.catheter), style = MaterialTheme.typography.labelMedium)
                                ChipRow(
                                    Fill.values().toList(), { it.name == v.catheter }, { stringResource(it.label) },
                                    { c -> saveVisit { x -> x.copy(catheter = if (x.catheter == c.name) "" else c.name) } }
                                )
                                Text(stringResource(R.string.drain), style = MaterialTheme.typography.labelMedium)
                                ChipRow(
                                    Fill.values().toList(), { it.name == v.drain }, { stringResource(it.label) },
                                    { c -> saveVisit { x -> x.copy(drain = if (x.drain == c.name) "" else c.name) } }
                                )
                            }
                        }
                        "needs" -> item(key = "needs") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.sec_needs), style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(v.needBlood, { saveVisit { it.copy(needBlood = !it.needBlood) } }, { Text(stringResource(R.string.need_blood)) })
                                    FilterChip(v.needPlasma, { saveVisit { it.copy(needPlasma = !it.needPlasma) } }, { Text(stringResource(R.string.need_plasma)) })
                                    FilterChip(v.needConsult, { saveVisit { it.copy(needConsult = !it.needConsult) } }, { Text(stringResource(R.string.need_consult)) })
                                    if (admission != null) {
                                        FilterChip(
                                            admission.surgeryNeeded,
                                            {
                                                scope.launch {
                                                    repo.dao.updateAdmission(
                                                        admission.copy(
                                                            surgeryNeeded = !admission.surgeryNeeded,
                                                            surgeryDone = if (admission.surgeryNeeded) false else admission.surgeryDone
                                                        )
                                                    )
                                                }
                                            },
                                            { Text(stringResource(R.string.need_surgery)) }
                                        )
                                    }
                                }
                                if (admission != null && admission.surgeryNeeded) {
                                    FilterChip(
                                        admission.surgeryDone,
                                        { scope.launch { repo.dao.updateAdmission(admission.copy(surgeryDone = !admission.surgeryDone)) } },
                                        { Text(stringResource(if (admission.surgeryDone) R.string.surgery_done else R.string.surgery_prep)) }
                                    )
                                    if (admission.surgeryDone) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(stringResource(R.string.postop_needed), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                                            IconButton(onClick = {
                                                CamPrefs.stage = "POST_OP"
                                                nav.push(Screen.Camera(id, Category.XRAY.name))
                                            }) { Text("📷", fontSize = 20.sp) }
                                        }
                                    }
                                }
                                DebouncedField(stringResource(R.string.needs_hint), v.needsNote) { t -> saveVisit { it.copy(needsNote = t) } }
                            }
                        }
                        "extras" -> {
                            val parts = parseExtras(v.extras)
                            parts.forEachIndexed { i, (t, x) ->
                                item(key = "ex$i") {
                                    DebouncedField(t, x) { nt -> saveVisit { cur -> cur.copy(extras = setExtra(cur.extras, t, nt)) } }
                                }
                            }
                            item(key = "ex_add") {
                                TextButton(onClick = { showExtra = true }) { Text("+ " + stringResource(R.string.add_field)) }
                            }
                        }
                        "lab" -> {
                            if (sectionHeader("lab", R.string.sec_lab, false)) {
                                catSection(Category.LAB)
                                catSection(Category.HISTO)
                                item(key = "labvals_header") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.lab_values), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { showLabValue = true }) { Icon(Icons.Default.Add, null) }
                                    }
                                }
                                items(allLabValues.filter { it.visitId == id }, key = { "lv" + it.id }) { lv ->
                                    val ref = refs.firstOrNull { it.name.equals(lv.name, true) }
                                    val num = lv.value.replace(',', '.').toDoubleOrNull()
                                    val flag = if (ref != null && num != null) {
                                        if (num < ref.low) " ↓" else if (num > ref.high) " ↑" else ""
                                    } else {
                                        ""
                                    }
                                    val dateOf = allVisits.associate { it.id to it.date }
                                    val prev = allLabValues.filter {
                                        it.name.equals(lv.name, true) && it.visitId != id && (dateOf[it.visitId] ?: 0L) <= v.date
                                    }.maxByOrNull { dateOf[it.visitId] ?: 0L }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            lv.name + ": " + lv.value + " " + lv.unit + flag + (prev?.let { "   (prev " + it.value + ")" } ?: ""),
                                            modifier = Modifier.weight(1f),
                                            color = if (flag.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Unspecified
                                        )
                                        IconButton(onClick = { scope.launch { repo.dao.deleteLabValue(lv) } }) { Icon(Icons.Default.Close, null) }
                                    }
                                }
                                item(key = "lab_text") {
                                    DebouncedField(stringResource(R.string.sec_lab_text), v.labText) { t -> saveVisit { it.copy(labText = t) } }
                                }
                            }
                        }
                        "radio" -> {
                            if (sectionHeader("radio", R.string.sec_radio, radioFirst)) {
                                catSection(Category.XRAY)
                                catSection(Category.CT)
                                catSection(Category.MRI)
                                catSection(Category.ECHO)
                            }
                        }
                        "other" -> {
                            catSection(Category.REPORT)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    if (showStage) {
        ChoiceDialog(
            stringResource(R.string.set_stage), stageOptions(), stringResource(R.string.custom_stage),
            onPick = { s ->
                showStage = false
                scope.launch { repo.dao.setStage(selected.toList(), s) }
                selected = emptySet()
            },
            onDismiss = { showStage = false }
        )
    }
    if (showMove) {
        val opts = allVisits.filter { it.id != id }.map { x ->
            val pn = patients.firstOrNull { it.id == x.patientId }?.name.orEmpty()
            x.id.toString() to "$pn — ${labels[x.id].orEmpty()}"
        }
        ChoiceDialog(
            stringResource(R.string.move_to), opts,
            onPick = { key ->
                showMove = false
                val target = allVisits.firstOrNull { it.id.toString() == key }
                if (target != null) scope.launch { repo.dao.moveMedia(selected.toList(), target.id, target.patientId) }
                selected = emptySet()
            },
            onDismiss = { showMove = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_media_q),
            onConfirm = {
                showDelete = false
                val items = selMedia
                selected = emptySet()
                scope.launch { repo.deleteMedia(items) }
            },
            onDismiss = { showDelete = false }
        )
    }
    if (showDate) {
        DatePickDialog(
            initial = visit?.date,
            onPick = { d -> showDate = false; scope.launch { repo.dao.setVisitDate(id, d) } },
            onDismiss = { showDate = false }
        )
    }
    if (showVital) {
        VitalDialog(
            onConfirm = { type, value, time ->
                showVital = false
                scope.launch { repo.dao.insertVital(Vital(visitId = id, time = time, type = type, value = value)) }
            },
            onDismiss = { showVital = false }
        )
    }
    if (showOrder) {
        OrderDialog(
            previous = allOrders,
            onConfirm = { o -> showOrder = false; scope.launch { repo.dao.insertOrder(o.copy(visitId = id)) } },
            onDismiss = { showOrder = false }
        )
    }
    if (showShare) {
        ShareOptionsDialog(
            onShare = { hide, imgs ->
                showShare = false
                val pid = visit?.patientId
                if (pid != null) scope.launch { sharePdf(ctx, repo, pid, "V$id", hide, imgs) }
            },
            onDismiss = { showShare = false }
        )
    }
    if (showLabValue) {
        LabValueDialog(
            refs,
            onConfirm = { n, value, u ->
                showLabValue = false
                scope.launch { repo.dao.insertLabValue(LabValue(visitId = id, name = n, value = value, unit = u)) }
            },
            onDismiss = { showLabValue = false }
        )
    }
    if (showExtra) {
        TextInputDialog(
            stringResource(R.string.add_field), stringResource(R.string.field_title),
            onConfirm = { t ->
                showExtra = false
                saveVisit { cur -> cur.copy(extras = buildExtras(parseExtras(cur.extras) + (t to ""))) }
            },
            onDismiss = { showExtra = false }
        )
    }
    if (showLimb) {
        LimbPickerDialog(
            onPick = { r, sd ->
                showLimb = false
                scope.launch { repo.dao.upsertExam(Exam(visitId = id, region = r.name, side = sd)) }
            },
            onDismiss = { showLimb = false }
        )
    }
    if (showClassify) {
        ChoiceDialog(
            stringResource(R.string.classify),
            Category.values().map { it.name to stringResource(it.label) },
            onPick = { c -> showClassify = false; classifyCat = c },
            onDismiss = { showClassify = false }
        )
    }
    classifyCat?.let { cname ->
        NamePickerDialog(
            initialSide = "NONE",
            onPick = { r, vw, sd ->
                classifyCat = null
                val items = selMedia
                selected = emptySet()
                scope.launch {
                    items.forEach { m ->
                        repo.dao.updateMedia(m.copy(category = cname, region = r.name, side = sd, viewPos = vw?.name ?: ""))
                    }
                }
            },
            onDismiss = { classifyCat = null }
        )
    }
}

@Composable
fun KeywordScreen(word: String, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val visits by remember { repo.dao.allVisits() }.collectAsState(emptyList())
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val labels = remember(visits) { visitLabels(ctx, visits) }
    val hits = visits.filter { v -> v.keywords.split("|").any { it.equals(word, true) } }.sortedByDescending { it.date }
    val fmt = remember { java.text.DateFormat.getDateInstance() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#$word") },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.id }) { v ->
                val pn = patients.firstOrNull { it.id == v.patientId }?.name.orEmpty()
                Card(Modifier.fillMaxWidth().clickable { nav.push(Screen.VisitDetail(v.id)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(pn, style = MaterialTheme.typography.titleMedium)
                        Text(labels[v.id].orEmpty() + " · " + fmt.format(Date(v.date)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
