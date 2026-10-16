package com.drx.casefiles.ui

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.drx.casefiles.data.Media

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object RefRanges : Screen
    data class Gallery(val patientId: Long) : Screen
    data class Keyword(val word: String) : Screen
    data class PatientDetail(val id: Long) : Screen
    data class VisitDetail(val id: Long) : Screen
    data class Camera(val visitId: Long, val category: String) : Screen
    data class Viewer(val items: List<Media>, val index: Int) : Screen
    data class Compare(val a: Media, val b: Media?) : Screen
}

class Nav(private val stack: SnapshotStateList<Screen>) {
    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
