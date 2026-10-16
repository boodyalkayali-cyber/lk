package com.drx.casefiles.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.drx.casefiles.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Minimal flowing-text + image PDF writer (A4). */
private class PdfWriter {
    val doc = PdfDocument()
    val pw = 595
    val ph = 842
    val mg = 36f
    var page: PdfDocument.Page? = null
    var canvas: Canvas = Canvas()
    var y = 0f
    var n = 0
    val body = TextPaint().apply { textSize = 10.5f; isAntiAlias = true; color = Color.BLACK }
    val title = TextPaint(body).apply { textSize = 18f; isFakeBoldText = true }
    val head = TextPaint(body).apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(21, 101, 192) }
    val sub = TextPaint(body).apply { textSize = 11f; isFakeBoldText = true }
    val small = TextPaint(body).apply { textSize = 8.5f; color = Color.DKGRAY }

    init {
        newPage()
    }

    fun newPage() {
        page?.let { doc.finishPage(it) }
        n++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
        page = p
        canvas = p.canvas
        canvas.drawText("Doctor Class · $n", pw - mg - 80f, ph - 18f, small)
        y = mg
    }

    fun ensure(h: Float) {
        if (y + h > ph - mg) newPage()
    }

    fun text(s: String, p: TextPaint = body, indent: Float = 0f, gap: Float = 2f) {
        if (s.isBlank()) return
        val width = (pw - 2 * mg - indent).toInt()
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width).build()
        for (i in 0 until layout.lineCount) {
            ensure(p.fontSpacing)
            val line = s.substring(layout.getLineStart(i), layout.getLineEnd(i)).trimEnd('\n', '\r')
            canvas.drawText(line, mg + indent, y - p.ascent(), p)
            y += p.fontSpacing
        }
        y += gap
    }

    fun heading(s: String) {
        ensure(40f)
        y += 6f
        text(s, head, gap = 3f)
        canvas.drawLine(mg, y, pw - mg, y, small)
        y += 4f
    }

    fun finish(file: File) {
        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}

private fun drawMarks(c: Canvas, w: Int, h: Int, marks: List<Mark>) {
    val sw = (w / 200f).coerceAtLeast(2f)
    marks.forEach { mk ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mk.color; strokeWidth = sw; style = Paint.Style.STROKE }
        val x1 = mk.x1 * w
        val y1 = mk.y1 * h
        val x2 = mk.x2 * w
        val y2 = mk.y2 * h
        if (mk.type == "ARROW") {
            c.drawLine(x1, y1, x2, y2, p)
            val ang = atan2(y2 - y1, x2 - x1)
            val len = w / 25f
            for (da in floatArrayOf(0.5f, -0.5f)) {
                val a = ang + Math.PI.toFloat() - da
                c.drawLine(x2, y2, x2 + len * cos(a), y2 + len * sin(a), p)
            }
        } else {
            val r = hypot(x2 - x1, y2 - y1)
            if (mk.filled) {
                val f = Paint(p).apply { style = Paint.Style.FILL; alpha = 80 }
                c.drawCircle(x1, y1, r, f)
            }
            c.drawCircle(x1, y1, r, p)
        }
    }
}

private fun loadBitmap(file: File, maxW: Int, marks: List<Mark>): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    if (o.outWidth <= 0) return null
    var s = 1
    while (o.outWidth / (s * 2) >= maxW) s *= 2
    var b = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val rot = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90
            3 -> 180
            8 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
    if (rot != 0) {
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        if (r !== b) b.recycle()
        b = r
    }
    if (marks.isNotEmpty()) {
        val m = b.copy(Bitmap.Config.ARGB_8888, true)
        b.recycle()
        drawMarks(Canvas(m), m.width, m.height, marks)
        b = m
    }
    return b
}

suspend fun buildCasePdf(
    ctx: Context,
    repo: Repo,
    patientId: Long,
    scope: String,
    hideIdentity: Boolean,
    withImages: Boolean
): File = withContext(Dispatchers.IO) {
    val dao = repo.dao
    val patient = dao.patient(patientId).first() ?: error("no patient")
    val allVisits = dao.visits(patientId).first()
    val admissions = dao.admissions(patientId).first()
    val media = dao.mediaOfPatient(patientId)
    val diags = dao.allDiagnoses().first().filter { it.patientId == patientId }
    val marks = dao.allMarks().first()
    val labVals = dao.allLabValues().first()
    val orders = dao.allOrders().first()
    val labels = visitLabels(ctx, allVisits)
    val admLabels = admissionLabels(ctx, admissions)
    val fmt = DateFormat.getDateInstance()
    fun d(t: Long) = fmt.format(Date(t))
    fun s(id: Int) = ctx.getString(id)

    val visits = when {
        scope.startsWith("V") -> allVisits.filter { it.id == scope.drop(1).toLongOrNull() }
        scope.startsWith("A") -> {
            val aid = scope.drop(1).toLongOrNull()
            val adm = admissions.firstOrNull { it.id == aid }
            allVisits.filter { it.admissionId == aid || it.id == adm?.examVisitId }
        }
        else -> allVisits
    }.sortedBy { it.date }

    fun region(n: String) = enumOrNull<Region>(n)?.let { ctx.getString(it.label) } ?: n
    fun view(n: String) = enumOrNull<ViewPos>(n)?.let { ctx.getString(it.label) } ?: ""
    fun stage(n: String) = enumOrNull<Stage>(n)?.let { ctx.getString(it.label) } ?: n
    fun cat(n: String) = enumOrNull<Category>(n)?.let { ctx.getString(it.label) } ?: n
    fun diagOf(m: Media) = diags.firstOrNull { it.region == m.region && it.side == m.side }?.text

    val w = PdfWriter()
    w.text("Doctor Class", w.title, gap = 4f)
    w.text(d(System.currentTimeMillis()), w.small, gap = 6f)

    // ---- patient
    if (!hideIdentity) {
        w.text(patient.name.ifBlank { s(R.string.unnamed) }, w.sub)
        w.text(listOf(patient.age, patient.residence, patient.phone).filter { it.isNotBlank() }.joinToString(" · "))
    } else if (patient.age.isNotBlank()) {
        w.text(s(R.string.age) + ": " + patient.age, w.sub)
    }
    fun line(label: String, value: String) { if (value.isNotBlank()) w.text("$label: $value") }
    line(s(R.string.complaint), patient.complaint)
    line(s(R.string.delay_info), patient.delay)
    line(s(R.string.mechanism), patient.mechanism)
    line(s(R.string.diagnosis), patient.diagnosis)
    line(s(R.string.description), patient.description)
    val derived = media.mapNotNull { diagOf(it) }.distinct()
    if (derived.isNotEmpty()) line(s(R.string.dx_from_images), derived.joinToString(" · "))

    var lastAdm: Long? = null
    var first = true
    visits.forEach { v ->
        if (v.admissionId != lastAdm || first) {
            val adm = admissions.firstOrNull { it.id == v.admissionId }
            if (adm != null) {
                w.heading(admLabels[adm.id].orEmpty() + " · " + d(adm.admitDate) +
                    (if (adm.discharged) " → ✔ " + (adm.dischargeDate?.let { d(it) } ?: "") else ""))
                line(s(R.string.admission_reason), adm.reason)
                if (adm.surgeryNeeded) w.text(s(if (adm.surgeryDone) R.string.surgery_done else R.string.surgery_prep))
                line(s(R.string.pmh), adm.pmh)
                line(s(R.string.psh), adm.psh)
                line(s(R.string.drugs), adm.drugs)
                line(s(R.string.allergy), adm.allergy)
                line(s(R.string.family), adm.family)
            }
            lastAdm = v.admissionId
            first = false
        }
        w.heading(labels[v.id].orEmpty() + " · " + d(v.date) + " · " + ctx.getString(R.string.day_fmt, dayNumber(v, allVisits)))
        if (v.keywords.isNotBlank()) w.text(v.keywords.split("|").filter { it.isNotBlank() }.joinToString(" ") { "#$it" }, w.small)
        enumOrNull<GeneralCondition>(v.generalCondition)?.let { line(s(R.string.sec_general), ctx.getString(it.label)) }
        enumOrNull<VitalsState>(v.vitalsStable)?.let { line(s(R.string.sec_vitals), ctx.getString(it.label)) }
        enumOrNull<Fill>(v.catheter)?.let { line(s(R.string.catheter), ctx.getString(it.label)) }
        enumOrNull<Fill>(v.drain)?.let { line(s(R.string.drain), ctx.getString(it.label)) }
        dao.vitals(v.id).first().forEach { r ->
            val t = enumOrNull<VitalType>(r.type)?.let { ctx.getString(it.label) } ?: r.type
            w.text("${r.time} · $t · ${r.value}", indent = 8f, gap = 0f)
        }
        dao.exams(v.id).first().forEach { e ->
            val parts = mutableListOf<String>()
            e.support.split(",").filter { it.isNotBlank() }.forEach { n -> enumOrNull<SupportItem>(n)?.let { parts += ctx.getString(it.label) } }
            e.clinical.split(",").filter { it.isNotBlank() }.forEach { n -> enumOrNull<ClinicalSign>(n)?.let { parts += ctx.getString(it.label) } }
            enumOrNull<VascularState>(e.vascular)?.let { parts += s(R.string.exam_vascular) + ": " + ctx.getString(it.label) }
            enumOrNull<NeuroState>(e.neuro)?.let { parts += s(R.string.exam_neuro) + ": " + ctx.getString(it.label) }
            if (e.note.isNotBlank()) parts += e.note
            w.text(limbName(ctx, e.region, e.side) + " — " + parts.joinToString(" · "), w.sub)
        }
        line(s(R.string.sec_gross), v.gross)
        val needs = listOfNotNull(
            if (v.needBlood) s(R.string.need_blood) else null,
            if (v.needPlasma) s(R.string.need_plasma) else null,
            if (v.needConsult) s(R.string.need_consult) else null
        )
        if (needs.isNotEmpty()) w.text(needs.joinToString(" · "))
        line(s(R.string.needs_hint), v.needsNote)
        orders.filter { it.visitId == v.id }.forEach { o ->
            val k = enumOrNull<OrderKind>(o.kind)?.let { ctx.getString(it.label) } ?: ""
            w.text("$k ${o.name} ${o.dose} ${if (o.qty.isNotBlank()) "x " + o.qty else ""} ${if (o.perDay.isNotBlank()) o.perDay + "/d" else ""} ${o.time}".replace(Regex(" +"), " "), indent = 8f, gap = 0f)
        }
        labVals.filter { it.visitId == v.id }.forEach { lv -> w.text("${lv.name}: ${lv.value} ${lv.unit}", indent = 8f, gap = 0f) }
        line(s(R.string.sec_lab_text), v.labText)
        line(s(R.string.notes), v.notes)
        v.extras.split("\u0002").filter { it.isNotBlank() }.forEach {
            val i = it.indexOf('\u0001')
            if (i > 0) line(it.substring(0, i), it.substring(i + 1))
        }

        if (withImages) {
            val vm = media.filter { it.visitId == v.id && !it.isVideo }
            Category.values().forEach { c ->
                val list = vm.filter { it.category == c.name }
                if (list.isEmpty()) return@forEach
                w.ensure(30f)
                w.text(cat(c.name), w.sub, gap = 4f)
                val cellW = (w.pw - 2 * w.mg - 10f) / 2f
                list.chunked(2).forEach { row ->
                    val bmps = row.map { m -> loadBitmap(repo.file(m.path), 900, marks.filter { it.mediaId == m.id }) }
                    val sizes = bmps.map { b ->
                        if (b == null) 0f to 0f else {
                            val sc = min(cellW / b.width, 480f / b.height)
                            b.width * sc to b.height * sc
                        }
                    }
                    val rowH = (sizes.maxOfOrNull { it.second } ?: 0f) + 34f
                    w.ensure(rowH)
                    row.forEachIndexed { i, m ->
                        val x = w.mg + i * (cellW + 10f)
                        bmps[i]?.let { b ->
                            w.canvas.drawBitmap(b, null, RectF(x, w.y, x + sizes[i].first, w.y + sizes[i].second), null)
                            b.recycle()
                        }
                        val cap = listOf(
                            region(m.region) + " " + sideShort(m.side) + " " + view(m.viewPos),
                            if (m.stage.isNotBlank()) stage(m.stage) else "",
                            if (m.healthy) s(R.string.healthy_short) else "",
                            diagOf(m).orEmpty(),
                            m.caption
                        ).filter { it.isNotBlank() }.joinToString(" · ").trim()
                        val layout = StaticLayout.Builder.obtain(cap, 0, cap.length, w.small, cellW.toInt()).build()
                        var cy = w.y + sizes[i].second + 2f
                        for (li in 0 until minOf(layout.lineCount, 3)) {
                            w.canvas.drawText(cap.substring(layout.getLineStart(li), layout.getLineEnd(li)).trimEnd('\n', '\r'), x, cy - w.small.ascent(), w.small)
                            cy += w.small.fontSpacing
                        }
                    }
                    w.y += rowH
                }
            }
        }
    }

    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val base = if (hideIdentity) "case" else patient.name.ifBlank { "case" }.replace(Regex("[^\\p{L}\\p{N}]+"), "_")
    val file = File(dir, "DoctorClass_$base.pdf")
    w.finish(file)
    file
}

private fun limbName(ctx: Context, region: String, side: String): String {
    val r = enumOrNull<Region>(region)?.let { ctx.getString(it.label) } ?: region
    return (r + " " + sideShort(side)).trim()
}

/** Builds the PDF and opens the system share sheet, so the receiver needs no app. */
suspend fun sharePdf(ctx: Context, repo: Repo, patientId: Long, scope: String, hideIdentity: Boolean, withImages: Boolean) {
    Toast.makeText(ctx, ctx.getString(R.string.preparing_pdf), Toast.LENGTH_SHORT).show()
    val file = buildCasePdf(ctx, repo, patientId, scope, hideIdentity, withImages)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
