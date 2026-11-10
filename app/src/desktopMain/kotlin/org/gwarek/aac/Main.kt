package org.gwarek.aac

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.gwarek.aac.ai.LocalLlm
import org.gwarek.aac.data.SettingsStore
import org.gwarek.aac.platform.AppAssets
import org.gwarek.aac.platform.AppDeps
import org.gwarek.aac.platform.DesktopSpeech
import org.gwarek.aac.platform.DesktopWindow
import org.gwarek.aac.platform.createDesktopDatabase
import org.gwarek.aac.platform.desktopDataDir
import org.gwarek.aac.platform.desktopResourcesDir
import org.gwarek.aac.ui.BoardScreen
import org.gwarek.aac.ui.MainViewModel
import org.gwarek.aac.ui.theme.GwarekTheme
import java.awt.Dimension
import java.io.File

fun main() {
    val dataDir = desktopDataDir()
    val resources = desktopResourcesDir()
    AppAssets.opener = { path -> resources?.let { File(it, path) }?.takeIf { it.isFile }?.inputStream() }
    LocalLlm.modelsDir = File(dataDir, "models")

    val deps = AppDeps(
        db = createDesktopDatabase(File(dataDir, "gwarek.db")),
        speech = DesktopSpeech(),
        settingsStore = SettingsStore(File(dataDir, "settings.properties")),
        dataDir = dataDir,
    )
    val icon = BitmapPainter(useResource("icon.png") { loadImageBitmap(it) })

    application {
        val vm = remember { MainViewModel(deps) }
        val fullscreen by DesktopWindow.fullscreen.collectAsState()
        val state = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1280.dp, 800.dp))
        LaunchedEffect(fullscreen) {
            state.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Maximized
        }
        Window(
            onCloseRequest = {
                deps.speech.shutdown()
                exitApplication()
            },
            state = state,
            title = "Gwarek",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.key == Key.F11 && e.type == KeyEventType.KeyDown) {
                    DesktopWindow.fullscreen.value = !DesktopWindow.fullscreen.value
                    true
                } else {
                    false
                }
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(800, 560) }
            GwarekTheme { BoardScreen(vm) }
        }
    }
}
