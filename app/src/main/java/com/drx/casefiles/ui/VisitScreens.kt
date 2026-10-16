@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drx.casefiles.KeyBridge
import com.drx.casefiles.R
import com.drx.casefiles.data.Category
import com.drx.casefiles.data.Diagnosis
import com.drx.casefiles.data.sideShort
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Region
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Side
import com.drx.casefiles.data.Stage
import com.drx.casefiles.data.ViewPos
import com.drx.casefiles.data.categoryText
import com.drx.casefiles.data.enumOrNull
import com.drx.casefiles.data.regionText
import com.drx.casefiles.data.stageText
import com.drx.casefiles.data.viewText
import com.drx.casefiles.data.visitLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Remembers the last tagging choices while the app is running. */
object CamPrefs {
    var view: String = ""
    var stage: String = ""
    var region: String = Region.OTHER.name
    var side: String = Side.NONE.name
}

private fun <T> nextOf(list: List<T>, cur: T): T = list[(list.indexOf(cur) + 1) % list.size]
private fun <T> prevOf(list: List<T>, cur: T): T = list[(list.indexOf(cur) - 1 + list.size) % list.size]

@Composable
fun CameraScreen(visitId: Long, startCategory: String, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val visit by remember(visitId) { repo.dao.visit(visitId) }.collectAsState(null)
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    var category by remember { mutableStateOf(enumOrNull<Category>(startCategory) ?: Category.CLINICAL) }
    var region by remember { mutableStateOf(CamPrefs.region) }
    var side by remember { mutableStateOf(CamPrefs.side) }
    var view by remember { mutableStateOf(CamPrefs.view) }
    var stage by remember { mutableStateOf(CamPrefs.stage) }
    LaunchedEffect(Unit) { CamPrefs.stage = "" }
    var healthy by remember { mutableStateOf(false) }
    var shots by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    LaunchedEffect(flash) { if (flash) { delay(90); flash = false } }

    val sides = remember { Side.values().map { it.name } }
    val stages = remember { listOf("") + Stage.values().map { it.name } }
    val cats = remember { Category.values().toList() }

    val diagText = diags.firstOrNull {
        it.patientId == visit?.patientId && it.region == region && it.side == side
    }?.text.orEmpty()

    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    fun capture() {
        val v = visit ?: return
        val name = repo.newImageName()
        val out = ImageCapture.OutputFileOptions.Builder(repo.file(name)).build()
        val snapshot = Media(
            visitId = v.id, patientId = v.patientId, path = name, category = category.name,
            region = region, side = side, viewPos = view, stage = stage, healthy = healthy
        )
        flash = true
        imageCapture.takePicture(
            out, ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    repo.appScope.launch {
                        repo.addMedia(snapshot.copy(takenAt = System.currentTimeMillis()))
                        withContext(Dispatchers.Main) { shots++ }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(ctx, exception.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    DisposableEffect(owner, granted) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider?.unbindAll()
                provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }, ContextCompat.getMainExecutor(ctx))
            KeyBridge.onVolume = { capture() }
        }
        onDispose {
            KeyBridge.onVolume = null
            provider?.unbindAll()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.camera_permission), color = Color.White)
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant)) }
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Swipe left/right: change stage. Swipe up/down: change side.
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            if (abs(dx) > abs(dy) && abs(dx) > 120f) {
                                stage = if (dx < 0) nextOf(stages, stage) else prevOf(stages, stage)
                            } else if (abs(dy) > 120f) {
                                side = nextOf(sides, side)
                                CamPrefs.side = side
                            }
                        },
                        onDrag = { change, d ->
                            change.consume()
                            dx += d.x
                            dy += d.y
                        }
                    )
                }
            )

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                Column(Modifier.statusBarsPadding().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.Default.Close, null) }
                        FilterChip(
                            diagText.isNotBlank(), { showDiag = true },
                            {
                                Text(
                                    if (diagText.isBlank()) stringResource(R.string.diagnosis_add) else diagText,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = if (diagText.isNotBlank()) {
                                {
                                    Icon(
                                        Icons.Default.Close, null,
                                        Modifier.size(18.dp).clickable {
                                            val pid = visit?.patientId
                                            val r = region
                                            val sd = side
                                            if (pid != null) repo.appScope.launch { repo.dao.clearDiagnosis(pid, r, sd) }
                                        }
                                    )
                                }
                            } else null
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(true, { category = nextOf(cats, category) }, { Text(categoryText(category.name)) })
                        FilterChip(
                            true, { showName = true },
                            {
                                Text(
                                    listOf(regionText(region), sideShort(side), viewText(view))
                                        .filter { it.isNotBlank() }.joinToString(" ")
                                )
                            }
                        )
                        FilterChip(true, { stage = nextOf(stages, stage) }, { Text(stageText(stage)) })
                        FilterChip(healthy, { healthy = !healthy }, { Text(stringResource(R.string.healthy)) })
                    }
                }
            }

            Text(
                stringResource(R.string.captured_fmt, shots),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { capture() }
            )
            if (flash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
        }
    }

    if (showName) {
        NamePickerDialog(
            initialSide = side,
            onPick = { r, v, sd ->
                region = r.name
                view = v?.name ?: ""
                side = sd
                CamPrefs.region = region
                CamPrefs.view = view
                CamPrefs.side = side
                showName = false
            },
            onDismiss = { showName = false }
        )
    }
    if (showDiag) {
        DiagnosisDialog(
            initial = diagText,
            onConfirm = { t ->
                showDiag = false
                val v = visit
                if (v != null) {
                    val r = region
                    val sd = side
                    repo.appScope.launch {
                        if (t.isBlank()) repo.dao.clearDiagnosis(v.patientId, r, sd)
                        else repo.dao.upsertDiagnosis(Diagnosis(patientId = v.patientId, region = r, side = sd, text = t))
                    }
                }
            },
            onDismiss = { showDiag = false }
        )
    }
}
