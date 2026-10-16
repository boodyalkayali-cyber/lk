package com.drx.casefiles.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val age: String = "",
    val residence: String = "",
    val phone: String = "",
    val complaint: String = "",
    /** How long after the injury the patient presented. */
    val delay: String = "",
    /** Mechanism of injury, e.g. fall from 3 m. */
    val mechanism: String = "",
    val diagnosis: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** One hospital stay. A patient can have several, each with its own reason. */
@Entity(
    tableName = "admissions",
    foreignKeys = [ForeignKey(entity = Patient::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patientId")]
)
data class Admission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val admitDate: Long = System.currentTimeMillis(),
    val dischargeDate: Long? = null,
    val reason: String = "",
    /** History belongs to the admission, not to the patient file. */
    val pmh: String = "",
    val psh: String = "",
    val drugs: String = "",
    val allergy: String = "",
    val family: String = "",
    val surgeryNeeded: Boolean = false,
    val surgeryDone: Boolean = false,
    /** Discharged; the date is optional. */
    val discharged: Boolean = false,
    /** The last visit before admission serves as the admission exam. */
    val examVisitId: Long? = null
)

/**
 * One encounter. kind: INITIAL (first day / history), ADMIT (admission exam), AM / PM (ward rounds),
 * CLINIC (outpatient follow-up, numbered by date), DISCHARGE, CUSTOM (uses title).
 * admissionId == null means the visit is outside any admission.
 */
@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(entity = Patient::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Admission::class, parentColumns = ["id"], childColumns = ["admissionId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("patientId"), Index("admissionId")]
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val admissionId: Long? = null,
    val kind: String = "CLINIC",
    val title: String = "",
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val generalCondition: String = "",
    val vitalsStable: String = "",
    val gross: String = "",
    /** Key points / hashtags, separated by '|'. */
    val keywords: String = "",
    /** Free text for lab and histology results. */
    val labText: String = "",
    /** Urinary catheter and drain fullness (Fill names). */
    val catheter: String = "",
    val drain: String = "",
    val needBlood: Boolean = false,
    val needPlasma: Boolean = false,
    val needConsult: Boolean = false,
    val needsNote: String = "",
    /** Custom titled notes: title\u0001text pairs separated by \u0002. */
    val extras: String = ""
)

/** Examination of one limb in one visit; created automatically when radiology of that limb is added. */
@Entity(
    tableName = "exams",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["visitId", "region", "side"], unique = true)]
)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val region: String,
    val side: String,
    /** ClinicalSign names separated by ','. */
    val clinical: String = "",
    val vascular: String = "",
    val neuro: String = "",
    /** SupportItem names separated by ',' (splint, cast, pillow...). */
    val support: String = "",
    val note: String = ""
)

@Entity(
    tableName = "vitals",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class Vital(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val time: String,
    val type: String,
    val value: String
)

/** Drug, blood bag, plasma... with dose, quantity, times per day and time. */
@Entity(
    tableName = "orders",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class MedOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val kind: String = "DRUG",
    val name: String,
    val dose: String = "",
    val qty: String = "",
    val perDay: String = "",
    val time: String = "",
    val given: Boolean = false
)

@Entity(
    tableName = "media",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId"), Index("patientId")]
)
data class Media(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val patientId: Long,
    val path: String,
    val category: String = "CLINICAL",
    val region: String = "OTHER",
    val side: String = "NONE",
    val viewPos: String = "",
    val stage: String = "",
    val healthy: Boolean = false,
    val caption: String = "",
    val takenAt: Long = System.currentTimeMillis(),
    val isVideo: Boolean = false
)

/**
 * The diagnosis belongs to (patient, region, side), so it automatically shows on every
 * view (AP, lateral, mortise...) of that body part, and search never mixes in other regions.
 */
@Entity(
    tableName = "diagnoses",
    foreignKeys = [ForeignKey(entity = Patient::class, parentColumns = ["id"], childColumns = ["patientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["patientId", "region", "side"], unique = true)]
)
data class Diagnosis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val region: String,
    val side: String,
    val text: String
)

/** Circle / arrow drawn on an image. Coordinates are 0..1 relative to the image itself. */
@Entity(
    tableName = "marks",
    foreignKeys = [ForeignKey(entity = Media::class, parentColumns = ["id"], childColumns = ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId")]
)
data class Mark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val type: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val filled: Boolean
)

/** Reference (normal) range of a lab test; editable because ranges differ between labs. */
@Entity(tableName = "ref_ranges")
data class RefRange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "",
    val low: Double,
    val high: Double
)

@Entity(
    tableName = "lab_values",
    foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("visitId")]
)
data class LabValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val name: String,
    val value: String,
    val unit: String = ""
)
