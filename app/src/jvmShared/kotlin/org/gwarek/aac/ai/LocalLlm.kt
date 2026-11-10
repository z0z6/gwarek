package org.gwarek.aac.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.gwarek.aac.platform.FileHandle
import org.gwarek.aac.platform.loadNativeLlm
import java.io.File

enum class LlmStatus { NO_MODEL, LOADING, READY, ERROR, UNSUPPORTED }

/**
 * Lokalny model językowy (GGUF przez llama.cpp). Działa wyłącznie offline:
 * model jest plikiem wczytanym przez użytkownika do katalogu danych aplikacji.
 */
object LocalLlm {
    private const val CONTEXT = 1024
    private val mutex = Mutex()
    private var handle = 0L

    /** Ustawiane przy starcie aplikacji. */
    lateinit var modelsDir: File

    private val _status = MutableStateFlow(LlmStatus.NO_MODEL)
    val status: StateFlow<LlmStatus> = _status

    val isReady: Boolean get() = _status.value == LlmStatus.READY

    private val libraryLoaded: Boolean by lazy { loadNativeLlm() }

    fun modelFile(): File = File(modelsDir, "model.gguf")

    fun hasModel(): Boolean = modelFile().isFile

    private val threads: Int
        get() = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    suspend fun ensureLoaded(): Boolean = mutex.withLock {
        if (handle != 0L) return@withLock true
        if (!libraryLoaded) {
            _status.value = LlmStatus.UNSUPPORTED
            return@withLock false
        }
        if (!hasModel()) {
            _status.value = LlmStatus.NO_MODEL
            return@withLock false
        }
        _status.value = LlmStatus.LOADING
        val h = withContext(Dispatchers.IO) {
            LlamaBridge.nativeLoad(modelFile().absolutePath, CONTEXT, threads)
        }
        handle = h
        _status.value = if (h != 0L) LlmStatus.READY else LlmStatus.ERROR
        h != 0L
    }

    /** Odpowiedź modelu albo null (brak modelu, błąd). Wywołania są kolejkowane. */
    suspend fun generate(system: String, user: String, maxTokens: Int, temperature: Float = 0f): String? {
        if (!ensureLoaded()) return null
        return mutex.withLock {
            if (handle == 0L) return@withLock null
            withContext(Dispatchers.Default) {
                LlamaBridge.nativeGenerate(handle, system, user, maxTokens, temperature)
                    ?.toString(Charsets.UTF_8)
                    ?.trim()
            }
        }
    }

    private suspend fun unload() = mutex.withLock {
        if (handle != 0L) {
            LlamaBridge.nativeFree(handle)
            handle = 0L
        }
        _status.value = if (libraryLoaded) LlmStatus.NO_MODEL else LlmStatus.UNSUPPORTED
    }

    /** Kopiuje plik GGUF wybrany przez użytkownika. Zwraca komunikat błędu albo null. */
    suspend fun importModel(source: FileHandle, onProgress: (Float) -> Unit): String? {
        unload()
        return withContext(Dispatchers.IO) {
            val dir = modelsDir.apply { mkdirs() }
            val size = source.size
            if (size > 0 && dir.usableSpace < size + 200L * 1024 * 1024) {
                return@withContext "Za mało miejsca na urządzeniu"
            }
            val tmp = File(dir, "import.tmp")
            try {
                source.openInput().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                            if (size > 0) onProgress((total.toDouble() / size).toFloat().coerceIn(0f, 1f))
                        }
                    }
                }
                val magic = tmp.inputStream().use { s ->
                    val b = ByteArray(4)
                    s.read(b)
                    String(b, Charsets.US_ASCII)
                }
                if (magic != "GGUF") {
                    tmp.delete()
                    return@withContext "To nie jest plik modelu GGUF"
                }
                val target = modelFile()
                target.delete()
                if (!tmp.renameTo(target)) return@withContext "Nie udało się zapisać modelu"
                null
            } catch (e: Exception) {
                tmp.delete()
                "Błąd kopiowania: ${e.message}"
            }
        }
    }

    suspend fun removeModel() {
        unload()
        withContext(Dispatchers.IO) { modelFile().delete() }
    }
}
