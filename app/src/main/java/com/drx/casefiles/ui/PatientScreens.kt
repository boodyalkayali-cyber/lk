@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drx.casefiles.R
import com.drx.casefiles.data.Admission
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Patient
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Visit
import com.drx.casefiles.data.admissionLabels
import com.drx.casefiles.data.sharePdf
import com.drx.casefiles.data.dayNumber
import com.drx.casefiles.data.visitLabels
import kotlinx.coroutines.delay
import com.drx.casefiles.Pin
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(repo: Repo, nav: Nav) {
    val scope = rememberCoroutineScope()
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val media by remember { repo.dao.allMedia() }.collectAsState(emptyList())
    val visits by remember { repo.dao.allVisits() }.collectAsState(emptyList())
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Patient?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var pinTick by remember { mutableStateOf(0) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            repo.backupTo(uri)
            Toast.makeText(ctx, ctx.getString(R.string.backup_done), Toast.LENGTH_SHORT).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }

    val thumbs = remember(media) { media.groupBy { it.patientId }.mapValues { it.value.first() } }
    val kwPatients = remember(visits, q) {
        val w = q.trim().trimStart('#').lowercase()
        if (w.isBlank()) emptySet()
        else visits.filter { v -> v.keywords.split("|").any { it.lowercase().contains(w) } }.map { it.patientId }.toSet()
    }
    val filtered = patients.filter {
        q.isBlank() || it.name.contains(q, true) || it.phone.contains(q, true) ||
            it.complaint.contains(q, true) || it.diagnosis.contains(q, true) || it.id in kwPatients
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { nav.push(Screen.RefRanges) }) { Text("🧪", fontSize = 20.sp) }
                    IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Default.Search, null) }
                    IconButton(onClick = { showSettings = true }) { Text("⚙", fontSize = 20.sp) }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quick capture: photograph first, name the patient later.
                SmallFloatingActionButton(onClick = {
                    scope.launch {
                        val pid = repo.dao.insertPatient(Patient(name = ""))
                        val vid = repo.dao.insertVisit(Visit(patientId = pid, kind = "INITIAL"))
                        nav.push(Screen.PatientDetail(pid))
                        nav.push(Screen.Camera(vid, "CLINICAL"))
                    }
                }) { Text("📷", fontSize = 18.sp) }
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                q, { q = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_patients)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp)
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_patients), textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { nav.push(Screen.PatientDetail(p.id)) },
                                    onLongClick = { toDelete = p }
                                )
                                .padding(12.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val t = thumbs[p.id]
                                if (t != null) {
                                    AsyncImage(repo.file(t.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name.ifBlank { stringResource(R.string.unnamed) }, style = MaterialTheme.typography.titleMedium)
                                val sub = p.diagnosis.ifBlank { p.complaint }
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            stringResource(R.string.new_patient), stringResource(R.string.patient_name),
            onConfirm = { name ->
                showAdd = false
                scope.launch {
                    val id = repo.dao.insertPatient(Patient(name = name))
                    repo.dao.insertVisit(Visit(patientId = id, kind = "INITIAL"))
                    nav.push(Screen.PatientDetail(id))
                }
            },
            onDismiss = { showAdd = false }
        )
    }
    if (showSettings) {
        val pinIsSet = pinTick >= 0 && Pin.isSet(ctx)
        SettingsDialog(
            pinSet = pinIsSet,
            onBackup = { showSettings = false; backupLauncher.launch("DoctorClass-backup.zip") },
            onRestore = { showSettings = false; restoreLauncher.launch(arrayOf("*/*")) },
            onPinSet = { showSettings = false; showPin = true },
            onPinClear = { Pin.clear(ctx); pinTick++; showSettings = false },
            onDismiss = { showSettings = false }
        )
    }
    if (showPin) {
        TextInputDialog(
            stringResource(R.string.pin_set), stringResource(R.string.pin_hint),
            onConfirm = { p -> Pin.set(ctx, p); pinTick++; showPin = false },
            onDismiss = { showPin = false }
        )
    }
    pendingRestore?.let { u ->
        ConfirmDialog(
            stringResource(R.string.restore_q),
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    repo.restoreFrom(u)
                    Toast.makeText(ctx, ctx.getString(R.string.done_restart), Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(1500)
                    exitProcess(0)
                }
            },
            onDismiss = { pendingRestore = null }
        )
    }
    toDelete?.let { p ->
        ConfirmDialog(
            stringResource(R.string.delete_patient_q),
            onConfirm = { toDelete = null; scope.launch { repo.deletePatient(p) } },
            onDismiss = { toDelete = null }
        )
    }
}

private data class AddTarget(val admissionId: Long?)

@Composable
fun PatientScreen(id: Long, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbPatient by remember(id) { repo.dao.patient(id) }.collectAsState(null)
    val visits by remember(id) { repo.dao.visits(id) }.collectAsState(emptyList())
    val media by remember(id) { repo.dao.mediaOfPatientFlow(id) }.collectAsState(emptyList())
    val admissions by remember(id) { repo.dao.admissions(id) }.collectAsState(emptyList())
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())
    val allOrders by remember { repo.dao.allOrders() }.collectAsState(emptyList())

    var f by remember { mutableStateOf<Patient?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<AddTarget?>(null) }
    var visitToDelete by remember { mutableStateOf<Visit?>(null) }
    var admToDelete by remember { mutableStateOf<Admission?>(null) }
    var dateTarget by remember { mutableStateOf<Pair<Admission, Boolean>?>(null) }
    var reasonTarget by remember { mutableStateOf<Admission?>(null) }
    var showNewAdmission by remember { mutableStateOf(false) }
    var showScopePick by remember { mutableStateOf(false) }
    var shareScope by remember { mutableStateOf<String?>(null) }
    var histOpen by remember { mutableStateOf(setOf<Long>()) }
    fun saveAdm(aid: Long, f: (Admission) -> Admission) {
        repo.appScope.launch {
            val cur = repo.dao.admissionOnce(aid) ?: return@launch
            repo.dao.updateAdmission(f(cur))
        }
    }

    LaunchedEffect(dbPatient) { if (f == null && dbPatient != null) f = dbPatient }
    LaunchedEffect(f) {
        if (dirty) {
            delay(600)
            f?.let { repo.dao.updatePatient(it.copy(updatedAt = System.currentTimeMillis())) }
            dirty = false
        }
    }
    val latest by rememberUpdatedState(f)
    val latestDirty by rememberUpdatedState(dirty)
    DisposableEffect(Unit) {
        onDispose {
            if (latestDirty) latest?.let { repo.appScope.launch { repo.dao.updatePatient(it) } }
        }
    }
    fun edit(n: Patient) {
        f = n
        dirty = true
    }

    val labels = remember(visits) { visitLabels(ctx, visits) }
    val admLabels = remember(admissions) { admissionLabels(ctx, admissions) }
    val dateFmt = remember { DateFormat.getDateInstance() }
    val initialVisit = visits.firstOrNull { it.kind == "INITIAL" }
    val lastVisit = visits.maxByOrNull { it.date }
    fun diagFor(m: Media): String? =
        diags.firstOrNull { it.patientId == m.patientId && it.region == m.region && it.side == m.side }?.text
    val openAdmission = admissions.filter { !it.discharged }.maxByOrNull { it.admitDate }

    @Composable
    fun RenderVisit(v: Visit, indent: Boolean) {
        val vm: List<Media> = media.filter { it.visitId == v.id }
        Box(Modifier.padding(start = if (indent) 16.dp else 0.dp)) {
            VisitBox(
                v = v,
                label = labels[v.id].orEmpty(),
                day = stringResource(R.string.day_fmt, dayNumber(v, visits)),
                date = dateFmt.format(Date(v.date)),
                thumbs = vm.takeLast(4).reversed(),
                repo = repo,
                onClick = { nav.push(Screen.VisitDetail(v.id)) },
                onLong = { if (v.kind != "INITIAL") visitToDelete = v }
            )
        }
    }

    @Composable
    fun RxBox(vs: List<Visit>, modifier: Modifier) {
        val ids = vs.map { it.id }
        val os = allOrders.filter { it.visitId in ids }
        val rx = media.count { it.visitId in ids && it.category == "RX" }
        Card(
            modifier.clickable { vs.firstOrNull()?.let { nav.push(Screen.VisitDetail(it.id)) } },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sec_rx), style = MaterialTheme.typography.labelLarge)
                os.take(6).forEach { Text("• " + it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (os.size > 6) Text("+" + (os.size - 6), style = MaterialTheme.typography.bodySmall)
                if (rx > 0) Text("📷 " + rx, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((f?.name.orEmpty()).ifBlank { stringResource(R.string.unnamed) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { showScopePick = true }) { Text("📤", fontSize = 20.sp) }
                    IconButton(onClick = { nav.push(Screen.Gallery(id)) }) { Text("🖼", fontSize = 20.sp) }
                }
            )
        }
    ) { pad ->
        val p = f
        if (p == null) {
            Box(Modifier.padding(pad).fillMaxSize())
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(stringResource(R.string.patient_name), p.name, Modifier.weight(2f)) { edit(p.copy(name = it)) }
                        FormField(stringResource(R.string.age), p.age, Modifier.weight(1f)) { edit(p.copy(age = it)) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(stringResource(R.string.residence), p.residence, Modifier.weight(1f)) { edit(p.copy(residence = it)) }
                        FormField(stringResource(R.string.phone), p.phone, Modifier.weight(1f)) { edit(p.copy(phone = it)) }
                    }
                }
                item { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                item { FormField(stringResource(R.string.complaint), p.complaint) { edit(p.copy(complaint = it)) } }
                item { FormField(stringResource(R.string.delay_info), p.delay) { edit(p.copy(delay = it)) } }
                item { FormField(stringResource(R.string.mechanism), p.mechanism) { edit(p.copy(mechanism = it)) } }
                item { FormField(stringResource(R.string.diagnosis), p.diagnosis) { edit(p.copy(diagnosis = it)) } }
                item(key = "dx_chips") {
                    val derived = media.mapNotNull { diagFor(it) }.distinct()
                    if (derived.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.dx_from_images), style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                derived.forEach { d ->
                                    FilterChip(p.diagnosis.contains(d), {
                                        if (!p.diagnosis.contains(d)) edit(p.copy(diagnosis = (p.diagnosis + " " + d).trim()))
                                    }, { Text(d, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                                }
                            }
                        }
                    }
                }
                item { FormField(stringResource(R.string.description), p.description, last = true) { edit(p.copy(description = it)) } }

                // The two most important boxes: first day and latest visit.
                if (initialVisit != null) {
                    item(key = "first") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.first_day_card), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            RenderVisit(initialVisit, false)
                        }
                    }
                }
                if (lastVisit != null && lastVisit.id != initialVisit?.id) {
                    item(key = "last") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.last_visit_card), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            RenderVisit(lastVisit, false)
                        }
                    }
                }

                // Admissions: each one is a parent folder holding its visits.
                item(key = "adm_header") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.admissions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showNewAdmission = true }) { Icon(Icons.Default.Add, null) }
                    }
                }
                admissions.forEach { a ->
                    item(key = "adm" + a.id) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(admLabels[a.id].orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { addTarget = AddTarget(a.id) }) { Icon(Icons.Default.Add, null) }
                                    IconButton(onClick = { admToDelete = a }) { Icon(Icons.Default.Delete, null) }
                                }
                                if (a.surgeryNeeded) {
                                    Text(
                                        stringResource(if (a.surgeryDone) R.string.surgery_done else R.string.surgery_prep),
                                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                a.examVisitId?.let { ev ->
                                    Text(
                                        stringResource(R.string.visit_admit) + ": " + labels[ev].orEmpty(),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { nav.push(Screen.VisitDetail(ev)) }.padding(vertical = 4.dp)
                                    )
                                }
                                val dxs = media.filter { m -> visits.any { it.id == m.visitId && it.admissionId == a.id } }.mapNotNull { diagFor(it) }.distinct()
                                if (dxs.isNotEmpty()) {
                                    Text(dxs.joinToString(" · "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row {
                                    Text(
                                        stringResource(R.string.admit_date) + ": " + dateFmt.format(Date(a.admitDate)),
                                        modifier = Modifier.weight(1f).clickable { dateTarget = a to true }.padding(vertical = 6.dp)
                                    )
                                    Text(
                                        (if (a.discharged) "✔ " else "") + stringResource(R.string.discharge_date) + ": " +
                                            (a.dischargeDate?.let { dateFmt.format(Date(it)) } ?: stringResource(R.string.not_set)),
                                        modifier = Modifier.weight(1f).clickable { dateTarget = a to false }.padding(vertical = 6.dp)
                                    )
                                }
                                Text(
                                    a.reason.ifBlank { stringResource(R.string.admission_reason) },
                                    modifier = Modifier.fillMaxWidth().clickable { reasonTarget = a }.padding(vertical = 4.dp)
                                )
                                val hOpen = a.id in histOpen
                                Text(
                                    (if (hOpen) "▾ " else "▸ ") + stringResource(R.string.admission_history),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.fillMaxWidth().clickable { histOpen = if (hOpen) histOpen - a.id else histOpen + a.id }.padding(vertical = 6.dp)
                                )
                                if (hOpen) {
                                    DebouncedField(stringResource(R.string.pmh), a.pmh) { t -> saveAdm(a.id) { it.copy(pmh = t) } }
                                    DebouncedField(stringResource(R.string.psh), a.psh) { t -> saveAdm(a.id) { it.copy(psh = t) } }
                                    DebouncedField(stringResource(R.string.drugs), a.drugs) { t -> saveAdm(a.id) { it.copy(drugs = t) } }
                                    DebouncedField(stringResource(R.string.allergy), a.allergy) { t -> saveAdm(a.id) { it.copy(allergy = t) } }
                                    DebouncedField(stringResource(R.string.family), a.family, last = true) { t -> saveAdm(a.id) { it.copy(family = t) } }
                                }
                            }
                        }
                    }
                    val dayGroups = visits.filter { it.admissionId == a.id }.groupBy { it.date / 86_400_000L }.values.toList()
                    items(dayGroups, key = { "day" + it.first().id }) { g ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(6.dp)) { g.forEach { RenderVisit(it, true) } }
                            RxBox(g, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }

                val standalone = visits.filter { it.admissionId == null }
                item(key = "visits_header") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.standalone_visits), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { addTarget = AddTarget(null) }) { Icon(Icons.Default.Add, null) }
                    }
                }
                items(standalone, key = { "v" + it.id }) { v ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(3f)) { RenderVisit(v, false) }
                        RxBox(listOf(v), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }

    addTarget?.let { t ->
        val opts = if (t.admissionId != null) {
            listOf("AM" to stringResource(R.string.visit_am), "PM" to stringResource(R.string.visit_pm))
        } else {
            listOf("CLINIC" to stringResource(R.string.visit_type_clinic))
        }
        ChoiceDialog(
            title = stringResource(R.string.add_visit),
            options = opts,
            customHint = stringResource(R.string.visit_name_hint),
            onPick = { key ->
                addTarget = null
                scope.launch {
                    // A follow-up outside the admission closes the open admission (discharged, no date).
                    if (t.admissionId == null && openAdmission != null) repo.dao.updateAdmission(openAdmission.copy(discharged = true))
                    val v = if (key == "AM" || key == "PM" || key == "CLINIC") {
                        Visit(patientId = id, admissionId = t.admissionId, kind = key)
                    } else {
                        Visit(patientId = id, admissionId = t.admissionId, kind = "CUSTOM", title = key)
                    }
                    val vid = repo.dao.insertVisit(v)
                    nav.push(Screen.VisitDetail(vid))
                }
            },
            onDismiss = { addTarget = null }
        )
    }
    if (showScopePick) {
        val opts = buildList {
            add("ALL" to stringResource(R.string.share_whole))
            admissions.forEach { add("A" + it.id to admLabels[it.id].orEmpty()) }
            visits.forEach { add("V" + it.id to labels[it.id].orEmpty()) }
        }
        ChoiceDialog(
            stringResource(R.string.share_pdf), opts,
            onPick = { k -> showScopePick = false; shareScope = k },
            onDismiss = { showScopePick = false }
        )
    }
    shareScope?.let { key ->
        ShareOptionsDialog(
            onShare = { hide, imgs ->
                shareScope = null
                scope.launch { sharePdf(ctx, repo, id, key, hide, imgs) }
            },
            onDismiss = { shareScope = null }
        )
    }
    visitToDelete?.let { v ->
        ConfirmDialog(
            stringResource(R.string.delete_visit_q),
            onConfirm = { visitToDelete = null; scope.launch { repo.deleteVisit(v) } },
            onDismiss = { visitToDelete = null }
        )
    }
    admToDelete?.let { a ->
        ConfirmDialog(
            stringResource(R.string.delete_admission_q),
            onConfirm = { admToDelete = null; scope.launch { repo.dao.deleteAdmission(a) } },
            onDismiss = { admToDelete = null }
        )
    }
    dateTarget?.let { (a, isAdmit) ->
        DatePickDialog(
            initial = if (isAdmit) a.admitDate else a.dischargeDate,
            onPick = { d ->
                dateTarget = null
                scope.launch {
                    repo.dao.updateAdmission(if (isAdmit) a.copy(admitDate = d) else a.copy(dischargeDate = d))
                }
            },
            onDismiss = { dateTarget = null }
        )
    }
    reasonTarget?.let { a ->
        TextInputDialog(
            stringResource(R.string.admission_reason), stringResource(R.string.admission_reason),
            onConfirm = { r -> reasonTarget = null; scope.launch { repo.dao.updateAdmission(a.copy(reason = r)) } },
            onDismiss = { reasonTarget = null },
            initial = a.reason
        )
    }
    if (showNewAdmission) {
        TextInputDialog(
            stringResource(R.string.new_admission), stringResource(R.string.admission_reason),
            onConfirm = { r ->
                showNewAdmission = false
                scope.launch {
                    // The last visit before admission serves as the admission exam; no new visit is created.
                    val examId = visits.filter { it.admissionId == null }.maxByOrNull { it.date }?.id
                    repo.dao.insertAdmission(Admission(patientId = id, reason = r, examVisitId = examId))
                }
            },
            onDismiss = { showNewAdmission = false }
        )
    }
}

@Composable
private fun VisitBox(
    v: Visit,
    label: String,
    day: String,
    date: String,
    thumbs: List<Media>,
    repo: Repo,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$day · $date", style = MaterialTheme.typography.bodySmall)
            }
            if (v.notes.isNotBlank()) {
                Text(v.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (v.keywords.isNotBlank()) {
                Text(
                    v.keywords.split("|").filter { it.isNotBlank() }.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (thumbs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    thumbs.forEach { m ->
                        AsyncImage(
                            repo.file(m.path), null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
    }
}
