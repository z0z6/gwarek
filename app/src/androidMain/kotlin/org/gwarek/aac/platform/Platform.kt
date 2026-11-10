package org.gwarek.aac.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import org.gwarek.aac.creator.PictogramCreatorDialog
import org.gwarek.aac.data.AppDatabase
import org.gwarek.aac.data.Pictogram
import org.gwarek.aac.print.PdfBuilder
import org.gwarek.aac.print.PrintOptions
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max

/*
 * Implementacja funkcji platformowych dla Androida. Wersja dla komputera ma
 * dokładnie te same nazwy i sygnatury (src/desktopMain/.../platform/Platform.kt).
 */

/** Kontekst aplikacji ustawiany w MainActivity. */
@SuppressLint("StaticFieldLeak")
object AndroidApp {
    lateinit var context: Context
}

const val platformName: String = "Android"

// ---------------- baza danych ----------------

/** v2: tabela przejść do podpowiedzi kolejnego piktogramu. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transitions` (`fromId` INTEGER NOT NULL, `toId` INTEGER NOT NULL, " +
                "`count` INTEGER NOT NULL, PRIMARY KEY(`fromId`, `toId`))"
        )
    }
}

fun createDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "gwarek.db")
        .addMigrations(MIGRATION_1_2)
        .build()

suspend fun <R> AppDatabase.transaction(block: suspend () -> R): R = withTransaction { block() }

// ---------------- pliki ----------------

private class UriFileHandle(private val context: Context, private val uri: Uri) : FileHandle {
    override val name: String = uri.lastPathSegment ?: "plik"
    override val size: Long
        get() = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }

    override fun openInput(): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("Nie można otworzyć pliku")

    override fun openOutput(): OutputStream =
        context.contentResolver.openOutputStream(uri) ?: error("Nie można zapisać pliku")
}

/** Zwraca funkcję otwierającą systemowe okno wyboru pliku. */
@Composable
fun rememberOpenFile(onResult: (FileHandle?) -> Unit): (mimeTypes: List<String>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onResult(uri?.let { UriFileHandle(context, it) })
    }
    return { mimeTypes -> launcher.launch(mimeTypes.toTypedArray()) }
}

/** Zwraca funkcję otwierającą systemowe okno zapisu pliku. */
@Composable
fun rememberSaveFile(mimeType: String, onResult: (FileHandle?) -> Unit): (suggestedName: String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        onResult(uri?.let { UriFileHandle(context, it) })
    }
    return { name -> launcher.launch(name) }
}

// ---------------- obrazki ----------------

fun loadFileBitmap(path: String): ImageBitmap? =
    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()

/** Zapisuje obrazek z importu (SVG lub grafika rastrowa) jako PNG; zwraca ścieżkę. */
fun saveImageAsPng(bytes: ByteArray, isSvg: Boolean, dir: File): String? {
    val bitmap = if (isSvg) {
        org.gwarek.aac.symbols.SvgRenderer.renderBytes(bytes, 512)
    } else {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { b ->
            val m = max(b.width, b.height)
            if (m <= 768) b else {
                val s = 768f / m
                Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
            }
        }
    } ?: return null
    dir.mkdirs()
    val file = File(dir, "${UUID.randomUUID()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file.absolutePath
}

// ---------------- kreator, druk, mowa, blokada ----------------

const val creatorAvailable: Boolean = true

@Composable
fun CreatorDialog(initial: Pictogram, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    PictogramCreatorDialog(initial = initial, onDismiss = onDismiss, onDone = onDone)
}

const val printingAvailable: Boolean = true

suspend fun writeCardsPdf(sections: List<Pair<String?, List<Pictogram>>>, opt: PrintOptions, out: File) {
    PdfBuilder(AndroidApp.context).build(sections, opt, out)
}

@Composable
fun rememberPrintPdf(): (File, String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { file, name -> org.gwarek.aac.print.printPdf(context, file, name) } }
}

@Composable
fun rememberOpenVoiceSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            try {
                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (e: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                } catch (e2: ActivityNotFoundException) {
                    Toasts.show("Otwórz Ustawienia → Zamiana tekstu na mowę")
                }
            }
        }
    }
}

const val pinScreenLabel: String = "Przypnij ekran (blokada wyjścia z aplikacji)"
const val pinScreenHint: String =
    "Po przypięciu nie da się wyjść do innych aplikacji. Aby odpiąć, przytrzymaj jednocześnie " +
        "przyciski Wstecz i Przegląd (albo gest opisany przez system)."

@Composable
fun rememberPinScreen(): (() -> Unit)? {
    val activity = LocalContext.current as? Activity ?: return null
    return remember(activity) { { activity.startLockTask() } }
}

// ---------------- AI ----------------

fun loadNativeLlm(): Boolean = try {
    System.loadLibrary("gwarek_llm")
    true
} catch (e: UnsatisfiedLinkError) {
    false
}
