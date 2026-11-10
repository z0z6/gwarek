package org.gwarek.aac.platform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/*
 * Elementy wspólne dla wszystkich platform. Funkcje zależne od systemu (wybór plików,
 * mowa, obrazki SVG, druk…) są zdefiniowane z identycznymi sygnaturami w
 * src/androidMain/.../platform/Platform.kt i src/desktopMain/.../platform/Platform.kt.
 */

/** Plik wskazany przez użytkownika (Android: Uri, komputer: zwykły plik). */
interface FileHandle {
    val name: String
    /** Rozmiar w bajtach albo -1, gdy nieznany. */
    val size: Long
    fun openInput(): InputStream
    fun openOutput(): OutputStream
}

/** Dostęp do plików dołączonych do aplikacji (biblioteka symboli). */
object AppAssets {
    @Volatile
    var opener: (String) -> InputStream? = { null }

    fun open(path: String): InputStream? = runCatching { opener(path) }.getOrNull()
    fun readBytes(path: String): ByteArray? = open(path)?.use { it.readBytes() }
    fun exists(path: String): Boolean = open(path)?.use { true } ?: false
}

/** Wspólne zależności aplikacji, tworzone przy starcie przez część platformową. */
class AppDeps(
    val db: org.gwarek.aac.data.AppDatabase,
    val speech: org.gwarek.aac.tts.SpeechEngine,
    val settingsStore: org.gwarek.aac.data.SettingsStore,
    /** Katalog danych aplikacji: własne obrazki, model AI, ustawienia. */
    val dataDir: File,
)

/** Krótkie komunikaty u dołu ekranu – działają tak samo na każdej platformie. */
object Toasts {
    val message = MutableStateFlow<String?>(null)
    fun show(text: String) {
        message.value = text
    }
}

@Composable
fun ToastOverlay() {
    val text by Toasts.message.collectAsState()
    LaunchedEffect(text) {
        if (text != null) {
            delay(3000)
            Toasts.message.value = null
        }
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xEE323232), contentColor = Color.White) {
                Text(text ?: "", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}
