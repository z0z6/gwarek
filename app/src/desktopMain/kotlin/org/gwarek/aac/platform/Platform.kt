package org.gwarek.aac.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.gwarek.aac.data.AppDatabase
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.print.PrintOptions
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/*
 * Implementacja funkcji platformowych dla komputera (Windows). Wersja dla Androida ma
 * dokładnie te same nazwy i sygnatury (src/androidMain/.../platform/Platform.kt).
 */

val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("windows")

val platformName: String = if (isWindows) "Windows" else "komputer"

/** Katalog danych: %APPDATA%\Gwarek na Windowsie, ~/.gwarek gdzie indziej. */
fun desktopDataDir(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (isWindows && !appData.isNullOrBlank()) File(appData, "Gwarek")
    else File(System.getProperty("user.home"), ".gwarek")
    return dir.apply { mkdirs() }
}

/** Katalog z zasobami aplikacji (biblioteka symboli, biblioteka AI). */
fun desktopResourcesDir(): File? {
    System.getProperty("compose.application.resources.dir")?.let { p ->
        File(p).takeIf { it.isDirectory }?.let { return it }
    }
    // uruchomienie z repozytorium (gradle run) bez spakowanych zasobów
    return listOf(File("generated-assets/common"), File("app/generated-assets/common")).firstOrNull { it.isDirectory }
}

/** Stan okna: tryb pełnoekranowy („kiosk”), przełączany też klawiszem F11. */
object DesktopWindow {
    val fullscreen = MutableStateFlow(false)
}

// ---------------- baza danych ----------------

fun createDesktopDatabase(file: File): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

suspend fun <R> AppDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }

// ---------------- pliki ----------------

private class DesktopFileHandle(private val file: File) : FileHandle {
    override val name: String = file.name
    override val size: Long get() = if (file.isFile) file.length() else -1L
    override fun openInput(): InputStream = file.inputStream()
    override fun openOutput(): OutputStream {
        file.parentFile?.mkdirs()
        return file.outputStream()
    }
}

private fun showFileDialog(title: String, mode: Int, suggestedName: String? = null): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    if (suggestedName != null) dialog.file = suggestedName
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

@Composable
fun rememberOpenFile(onResult: (FileHandle?) -> Unit): (mimeTypes: List<String>) -> Unit {
    val callback by rememberUpdatedState(onResult)
    return remember<(List<String>) -> Unit> {
        { _ ->
            val f = showFileDialog("Wybierz plik", FileDialog.LOAD)
            callback(f?.takeIf { it.isFile }?.let { DesktopFileHandle(it) })
        }
    }
}

@Composable
fun rememberSaveFile(mimeType: String, onResult: (FileHandle?) -> Unit): (suggestedName: String) -> Unit {
    val callback by rememberUpdatedState(onResult)
    return remember<(String) -> Unit> {
        { suggested ->
            val f = showFileDialog("Zapisz plik", FileDialog.SAVE, suggested)
            callback(f?.let { DesktopFileHandle(it) })
        }
    }
}

// ---------------- obrazki ----------------

fun loadFileBitmap(path: String): ImageBitmap? =
    runCatching { Image.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap() }.getOrNull()

/** Zapisuje obrazek z importu (SVG lub grafika rastrowa) jako PNG; zwraca ścieżkę. */
fun saveImageAsPng(bytes: ByteArray, isSvg: Boolean, dir: File): String? {
    val png: ByteArray = try {
        if (isSvg) renderSvgToPng(bytes, 512) else Image.makeFromEncoded(bytes).encodeToData(EncodedImageFormat.PNG)?.bytes
    } catch (e: Exception) {
        null
    } ?: return null
    dir.mkdirs()
    val file = File(dir, "${UUID.randomUUID()}.png")
    file.writeBytes(png)
    return file.absolutePath
}

private fun renderSvgToPng(bytes: ByteArray, size: Int): ByteArray? {
    val dom = SVGDOM(Data.makeFromBytes(bytes))
    val root = dom.root ?: return null
    val box = root.viewBox
    val w = box?.width ?: root.width.value
    val h = box?.height ?: root.height.value
    if (w <= 0f || h <= 0f) return null
    val surface = Surface.makeRasterN32Premul(size, size)
    val scale = minOf(size / w, size / h)
    val canvas = surface.canvas
    canvas.translate((size - w * scale) / 2f, (size - h * scale) / 2f)
    canvas.scale(scale, scale)
    dom.setContainerSize(w, h)
    dom.render(canvas)
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
}

// ---------------- kreator, druk, mowa, blokada ----------------

// Kreator i druk na komputerze dojdą w kolejnym etapie.
val creatorAvailable: Boolean = false

@Composable
fun CreatorDialog(initial: Pictogram, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    LaunchedEffect(Unit) { onDismiss() }
}

val printingAvailable: Boolean = false

suspend fun writeCardsPdf(sections: List<Pair<String?, List<Pictogram>>>, opt: PrintOptions, out: File) {
    throw UnsupportedOperationException("Druk nie jest jeszcze dostępny na komputerze")
}

@Composable
fun rememberPrintPdf(): (File, String) -> Unit = remember<(File, String) -> Unit> { { _, _ -> } }

@Composable
fun rememberOpenVoiceSettings(): () -> Unit = remember<() -> Unit> {
    {
        if (isWindows) {
            runCatching { ProcessBuilder("cmd", "/c", "start", "ms-settings:speech").start() }
                .onFailure { Toasts.show("Otwórz Ustawienia → Czas i język → Mowa") }
        } else {
            Toasts.show("Zainstaluj syntezator espeak-ng z polskim głosem")
        }
    }
}

val pinScreenLabel: String = "Pełny ekran (tryb kiosku)"
val pinScreenHint: String =
    "Okno zajmie cały ekran bez ramki. Aby wyjść, naciśnij F11. Pełną blokadę komputera " +
        "(tylko Gwarek, bez dostępu do pulpitu) zapewnia funkcja Windows „Dostęp przypisany”."

@Composable
fun rememberPinScreen(): (() -> Unit)? = remember<() -> Unit> { { DesktopWindow.fullscreen.value = true } }

// ---------------- AI ----------------

/** Biblioteka llama.cpp dla komputera (gwarek_llm.dll) – budowana w kolejnym etapie. */
fun loadNativeLlm(): Boolean {
    val dir = desktopResourcesDir() ?: return false
    val name = System.mapLibraryName("gwarek_llm")
    val lib = File(dir, name)
    if (!lib.isFile) return false
    return try {
        System.load(lib.absolutePath)
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }
}
