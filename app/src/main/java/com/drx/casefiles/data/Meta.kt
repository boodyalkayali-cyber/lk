package com.drx.casefiles.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drx.casefiles.R as AppR

enum class Category(@StringRes val label: Int) {
    CLINICAL(AppR.string.cat_clinical),
    XRAY(AppR.string.cat_xray),
    MRI(AppR.string.cat_mri),
    CT(AppR.string.cat_ct),
    ECHO(AppR.string.cat_echo),
    LAB(AppR.string.cat_lab),
    HISTO(AppR.string.cat_histo),
    RX(AppR.string.cat_rx),
    CONSULT(AppR.string.cat_consult),
    REPORT(AppR.string.cat_report)
}

enum class Side(@StringRes val label: Int) {
    NONE(AppR.string.side_none),
    L(AppR.string.side_left),
    R(AppR.string.side_right)
}

enum class Region(@StringRes val label: Int) {
    SHOULDER(AppR.string.r_shoulder), ARM(AppR.string.r_arm), ELBOW(AppR.string.r_elbow),
    FOREARM(AppR.string.r_forearm), WRIST(AppR.string.r_wrist), HAND(AppR.string.r_hand),
    FINGER(AppR.string.r_finger), TOE(AppR.string.r_toe), PELVIS(AppR.string.r_pelvis), HIP(AppR.string.r_hip), THIGH(AppR.string.r_thigh),
    KNEE(AppR.string.r_knee), LEG(AppR.string.r_leg), ANKLE(AppR.string.r_ankle),
    FOOT(AppR.string.r_foot), SPINE(AppR.string.r_spine), CHEST(AppR.string.r_chest), OTHER(AppR.string.r_other)
}

enum class Stage(@StringRes val label: Int) {
    BEFORE_REDUCTION(AppR.string.st_before_reduction),
    AFTER_REDUCTION(AppR.string.st_after_reduction),
    AFTER_CAST(AppR.string.st_after_cast),
    PRE_OP(AppR.string.st_pre_op),
    POST_OP(AppR.string.st_post_op),
    CONTROL_FIXATION(AppR.string.st_control_fixation)
}

enum class ViewPos(@StringRes val label: Int) {
    AP(AppR.string.v_ap), LATERAL(AppR.string.v_lateral), OBLIQUE(AppR.string.v_oblique),
    PA(AppR.string.v_pa), MORTISE(AppR.string.v_mortise), GRASHEY(AppR.string.v_grashey),
    YVIEW(AppR.string.v_yview), AXILLARY(AppR.string.v_axillary), ROBERTS(AppR.string.v_roberts),
    SKYLINE(AppR.string.v_skyline), ANTERIOR(AppR.string.v_anterior), POSTERIOR(AppR.string.v_posterior),
    MEDIAL(AppR.string.v_medial), AXIAL(AppR.string.v_axial), STRESS(AppR.string.v_stress)
}

inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

fun sideShort(side: String): String = when (side) {
    "L" -> "L"
    "R" -> "R"
    else -> ""
}

@Composable
fun stageText(stage: String): String {
    val s = enumOrNull<Stage>(stage)
    return when {
        s != null -> stringResource(s.label)
        stage.isBlank() -> stringResource(AppR.string.stage_none)
        else -> stage
    }
}

@Composable
fun regionText(region: String): String {
    val r = enumOrNull<Region>(region)
    return if (r != null) stringResource(r.label) else ""
}

@Composable
fun viewText(viewPos: String): String {
    val v = enumOrNull<ViewPos>(viewPos)
    return if (v != null) stringResource(v.label) else ""
}

@Composable
fun categoryText(category: String): String {
    val c = enumOrNull<Category>(category)
    return if (c != null) stringResource(c.label) else category
}

/** Label for every visit; clinic follow-ups are numbered per patient by date. */
fun visitLabels(ctx: Context, visits: List<Visit>): Map<Long, String> {
    val out = HashMap<Long, String>()
    visits.groupBy { it.patientId }.values.forEach { list ->
        var n = 0
        list.sortedBy { it.date }.forEach { v ->
            out[v.id] = when (v.kind) {
                "INITIAL" -> ctx.getString(AppR.string.visit_initial)
                "ADMIT" -> ctx.getString(AppR.string.visit_admit)
                "AM" -> ctx.getString(AppR.string.visit_am)
                "PM" -> ctx.getString(AppR.string.visit_pm)
                "DISCHARGE" -> ctx.getString(AppR.string.visit_discharge)
                "CUSTOM" -> v.title.ifBlank { ctx.getString(AppR.string.visit_followup) }
                else -> {
                    n++
                    ctx.getString(AppR.string.visit_followup_fmt, n)
                }
            }
        }
    }
    return out
}

/** "Admission 1", "Admission 2"... numbered per patient by date. */
fun admissionLabels(ctx: Context, admissions: List<Admission>): Map<Long, String> {
    val out = HashMap<Long, String>()
    admissions.groupBy { it.patientId }.values.forEach { list ->
        list.sortedBy { it.admitDate }.forEachIndexed { i, a ->
            out[a.id] = ctx.getString(AppR.string.admission_fmt, i + 1)
        }
    }
    return out
}

enum class GeneralCondition(@StringRes val label: Int) {
    GOOD(AppR.string.gc_good), FAIR(AppR.string.gc_fair), POOR(AppR.string.gc_poor)
}

enum class VitalsState(@StringRes val label: Int) {
    STABLE(AppR.string.vs_stable), UNSTABLE(AppR.string.vs_unstable)
}

enum class VitalType(@StringRes val label: Int) {
    BP(AppR.string.vt_bp), PULSE(AppR.string.vt_pulse), SPO2(AppR.string.vt_spo2),
    TEMP(AppR.string.vt_temp), RESP(AppR.string.vt_resp),
    URINE(AppR.string.vt_urine), DRAIN(AppR.string.vt_drain)
}

enum class ClinicalSign(@StringRes val label: Int) {
    PAIN(AppR.string.cl_pain), TENDER(AppR.string.cl_tender), LIMITED(AppR.string.cl_limited),
    SWELLING(AppR.string.cl_swelling), ABRASION(AppR.string.cl_abrasion), BRUISE(AppR.string.cl_bruise), DEFORMITY(AppR.string.cl_deformity),
    WOUND(AppR.string.cl_wound), COMPARTMENT(AppR.string.cl_compartment)
}

enum class VascularState(@StringRes val label: Int) {
    NORMAL(AppR.string.va_normal), MODERATE(AppR.string.va_moderate),
    WEAK(AppR.string.va_weak), NOT_PALPABLE(AppR.string.va_not_palpable), ABSENT(AppR.string.va_absent)
}

enum class NeuroState(@StringRes val label: Int) {
    NORMAL(AppR.string.ne_normal), ABNORMAL(AppR.string.ne_abnormal), ABSENT(AppR.string.ne_absent),
    UNCOOPERATIVE(AppR.string.ne_uncoop), PARALYSIS_OLD(AppR.string.ne_paralysis)
}

enum class Fill(@StringRes val label: Int) {
    ABSENT(AppR.string.fl_absent), EMPTY(AppR.string.fl_empty), HALF(AppR.string.fl_half), FULL(AppR.string.fl_full)
}

enum class SupportItem(@StringRes val label: Int) {
    BACK_SPLINT(AppR.string.sp_back_splint), CAST(AppR.string.sp_cast), ABOVE_KNEE(AppR.string.sp_above_knee),
    BELOW_KNEE(AppR.string.sp_below_knee), PILLOW(AppR.string.sp_pillow), TRACTION(AppR.string.sp_traction),
    EXFIX(AppR.string.sp_exfix), SLING(AppR.string.sp_sling)
}

enum class OrderKind(@StringRes val label: Int) {
    DRUG(AppR.string.ord_drug), BLOOD(AppR.string.ord_blood),
    PLASMA(AppR.string.ord_plasma), OTHER(AppR.string.ord_other)
}

/** 0 = upper limb, 1 = lower limb, 2 = spine / other. */
fun limbGroup(region: String): Int = when (region) {
    "SHOULDER", "ARM", "ELBOW", "FOREARM", "WRIST", "HAND", "FINGER" -> 0
    "PELVIS", "HIP", "THIGH", "KNEE", "LEG", "ANKLE", "FOOT", "TOE" -> 1
    else -> 2
}

@Composable
fun limbText(region: String, side: String): String {
    val g = when (limbGroup(region)) {
        0 -> stringResource(AppR.string.limb_upper)
        1 -> stringResource(AppR.string.limb_lower)
        else -> stringResource(AppR.string.limb_other)
    }
    return (g + " " + sideShort(side)).trim()
}

/** Days since the first visit of the same patient. */
fun dayNumber(visit: Visit, patientVisits: List<Visit>): Int {
    val start = patientVisits.firstOrNull { it.kind == "INITIAL" }?.date
        ?: patientVisits.minOfOrNull { it.date } ?: visit.date
    return ((visit.date - start) / 86_400_000L).toInt().coerceAtLeast(0)
}
