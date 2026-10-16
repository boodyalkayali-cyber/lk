package com.drx.casefiles

import android.app.Application
import android.content.Context
import android.view.WindowManager
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.drx.casefiles.ui.LockScreen
import java.security.MessageDigest
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.drx.casefiles.data.AppDb
import com.drx.casefiles.data.Repo
import com.drx.casefiles.ui.CameraScreen
import com.drx.casefiles.ui.CompareScreen
import com.drx.casefiles.ui.HomeScreen
import com.drx.casefiles.ui.Nav
import com.drx.casefiles.ui.PatientScreen
import com.drx.casefiles.ui.Screen
import com.drx.casefiles.ui.SearchScreen
import com.drx.casefiles.ui.ViewerScreen
import com.drx.casefiles.ui.VisitScreen

class App : Application() {
    lateinit var repo: Repo

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this, AppDb.get(this).dao())
        Coil.setImageLoader(ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build())
    }
}

/** Optional PIN lock; a set PIN also blocks screenshots. */
object Pin {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dc", Context.MODE_PRIVATE)
    private fun hash(p: String): String =
        MessageDigest.getInstance("SHA-256").digest(("dc" + p).toByteArray()).joinToString("") { "%02x".format(it) }
    fun isSet(ctx: Context) = prefs(ctx).contains("pin")
    fun set(ctx: Context, p: String) = prefs(ctx).edit().putString("pin", hash(p)).apply()
    fun clear(ctx: Context) = prefs(ctx).edit().remove("pin").apply()
    fun check(ctx: Context, p: String) = prefs(ctx).getString("pin", null) == hash(p)
}

/** Lets the camera screen use the volume keys as a shutter. */
object KeyBridge {
    var onVolume: (() -> Unit)? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Pin.isSet(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val repo = (application as App).repo
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    var unlocked by remember { mutableStateOf(!Pin.isSet(this@MainActivity)) }
                    LaunchedEffect(Unit) { repo.seedRefs() }
                    if (unlocked) AppRoot(repo)
                    else LockScreen { p ->
                        val ok = Pin.check(this@MainActivity, p)
                        if (ok) unlocked = true
                        ok
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val handler = KeyBridge.onVolume
        if (handler != null && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event?.repeatCount == 0) handler()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun AppRoot(repo: Repo) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nav = remember { Nav(stack) }
    BackHandler(enabled = stack.size > 1) { nav.pop() }
    val s = stack.last()
    key(stack.size, s) {
        when (s) {
            is Screen.Home -> HomeScreen(repo, nav)
            is Screen.Search -> SearchScreen(repo, nav)
            is Screen.RefRanges -> com.drx.casefiles.ui.RefScreen(repo, nav)
            is Screen.Gallery -> com.drx.casefiles.ui.GalleryScreen(s.patientId, repo, nav)
            is Screen.Keyword -> com.drx.casefiles.ui.KeywordScreen(s.word, repo, nav)
            is Screen.PatientDetail -> PatientScreen(s.id, repo, nav)
            is Screen.VisitDetail -> VisitScreen(s.id, repo, nav)
            is Screen.Camera -> CameraScreen(s.visitId, s.category, repo, nav)
            is Screen.Viewer -> ViewerScreen(s.items, s.index, repo, nav)
            is Screen.Compare -> CompareScreen(s.a, s.b, repo, nav)
        }
    }
}
