package com.drx.casefiles.data

import android.content.Context
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class Repo(private val ctx: Context, val dao: CaseDao) {
    /** Survives screen changes; used for writes that must not be cancelled. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dir = File(ctx.filesDir, "media").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    fun newImageName(): String = "${UUID.randomUUID()}.jpg"

    suspend fun addMedia(m: Media) {
        dao.insertMedia(m)
        dao.touchPatient(m.patientId, System.currentTimeMillis())
    }

    suspend fun importImages(uris: List<Uri>, template: Media) = withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            val video = ctx.contentResolver.getType(uri)?.startsWith("video") == true
            val name = if (video) "${UUID.randomUUID()}.mp4" else newImageName()
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                file(name).outputStream().use { out -> input.copyTo(out) }
            }
            if (file(name).exists()) {
                addMedia(template.copy(id = 0, path = name, isVideo = video, takenAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun deleteMedia(items: List<Media>) = withContext(Dispatchers.IO) {
        dao.deleteMediaByIds(items.map { it.id })
        items.forEach { file(it.path).delete() }
    }

    suspend fun deleteVisit(v: Visit) = withContext(Dispatchers.IO) {
        dao.mediaOfVisit(v.id).forEach { file(it.path).delete() }
        dao.deleteVisit(v)
    }

    suspend fun deletePatient(p: Patient) = withContext(Dispatchers.IO) {
        dao.mediaOfPatient(p.id).forEach { file(it.path).delete() }
        dao.deletePatient(p)
    }

    /** One zip file: the database plus every image and video. */
    suspend fun backupTo(uri: Uri) = withContext(Dispatchers.IO) {
        AppDb.get(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("cases.db"))
                ctx.getDatabasePath("cases.db").inputStream().use { it.copyTo(z) }
                z.closeEntry()
                dir.listFiles()?.forEach { f ->
                    z.putNextEntry(ZipEntry("media/" + f.name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    /** Replaces all current data. The app must be reopened afterwards. */
    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        AppDb.get(ctx).close()
        val dbFile = ctx.getDatabasePath("cases.db")
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = File(e.name).name
                    if (e.name == "cases.db") dbFile.outputStream().use { z.copyTo(it) }
                    else if (e.name.startsWith("media/") && n.isNotBlank()) file(n).outputStream().use { z.copyTo(it) }
                    z.closeEntry()
                    e = z.nextEntry
                }
            }
        }
    }

    suspend fun seedRefs() {
        if (dao.refCount() > 0) return
        listOf(
            Triple("WBC", "x10³/µL", 4.0 to 11.0), Triple("Hb", "g/dL", 12.0 to 17.5), Triple("Hct", "%", 36.0 to 50.0),
            Triple("PLT", "x10³/µL", 150.0 to 450.0), Triple("Na", "mmol/L", 135.0 to 145.0), Triple("K", "mmol/L", 3.5 to 5.0),
            Triple("Cl", "mmol/L", 98.0 to 107.0), Triple("Ca", "mg/dL", 8.5 to 10.5), Triple("Mg", "mg/dL", 1.7 to 2.2),
            Triple("Phosphate", "mg/dL", 2.5 to 4.5), Triple("Urea", "mg/dL", 15.0 to 45.0), Triple("BUN", "mg/dL", 7.0 to 20.0),
            Triple("Creatinine", "mg/dL", 0.6 to 1.3), Triple("Glucose (fasting)", "mg/dL", 70.0 to 100.0), Triple("HbA1c", "%", 4.0 to 5.6),
            Triple("AST", "U/L", 10.0 to 40.0), Triple("ALT", "U/L", 7.0 to 56.0), Triple("ALP", "U/L", 44.0 to 147.0),
            Triple("Bilirubin (total)", "mg/dL", 0.1 to 1.2), Triple("Albumin", "g/dL", 3.5 to 5.0), Triple("CRP", "mg/L", 0.0 to 5.0),
            Triple("ESR", "mm/h", 0.0 to 20.0), Triple("INR", "", 0.8 to 1.2), Triple("PT", "s", 11.0 to 13.5),
            Triple("aPTT", "s", 25.0 to 35.0), Triple("Uric acid", "mg/dL", 3.5 to 7.2), Triple("CK", "U/L", 30.0 to 200.0),
            Triple("Lactate", "mmol/L", 0.5 to 2.2), Triple("Vitamin D", "ng/mL", 30.0 to 100.0), Triple("pH", "", 7.35 to 7.45),
            Triple("PaCO2", "mmHg", 35.0 to 45.0), Triple("HCO3", "mmol/L", 22.0 to 28.0)
        ).forEach { (n, u, r) -> dao.insertRef(RefRange(name = n, unit = u, low = r.first, high = r.second)) }
    }
}
