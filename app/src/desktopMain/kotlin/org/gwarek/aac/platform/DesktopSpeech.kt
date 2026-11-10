package org.gwarek.aac.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.gwarek.aac.tts.SpeechEngine
import org.gwarek.aac.tts.TtsStatus
import java.io.BufferedWriter
import java.io.File
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Mowa na Windowsie: wbudowany syntezator SAPI (System.Speech) z polskim głosem
 * (np. „Microsoft Paulina”), działający offline. Jeden proces PowerShell działa w tle
 * i odbiera polecenia, więc odczyt zaczyna się bez opóźnienia.
 * Poza Windowsem używany jest espeak-ng, jeśli jest zainstalowany.
 */
class DesktopSpeech : SpeechEngine {
    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    override val status: StateFlow<TtsStatus> = _status

    override var rate: Float = 1.0f
        set(value) {
            field = value
            send("RATE ${sapiRate(value)}")
        }

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val espeak = Executors.newSingleThreadExecutor()
    private var espeakProcess: Process? = null

    init {
        if (isWindows) startSapi() else checkEspeak()
    }

    private fun sapiRate(r: Float) = ((r - 1f) * 10f).roundToInt().coerceIn(-10, 10)

    private fun startSapi() {
        try {
            val script = File.createTempFile("gwarek-mowa", ".ps1").apply {
                deleteOnExit()
                writeText(SCRIPT, Charsets.UTF_8)
            }
            val p = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                "-File", script.absolutePath,
            ).redirectErrorStream(true).start()
            process = p
            writer = p.outputStream.bufferedWriter(Charsets.US_ASCII)
            thread(isDaemon = true, name = "gwarek-mowa") {
                p.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        when (line.trim()) {
                            "STATUS READY" -> _status.value = TtsStatus.READY
                            "STATUS MISSING" -> _status.value = TtsStatus.MISSING_LANGUAGE
                        }
                    }
                }
                if (_status.value == TtsStatus.INITIALIZING) _status.value = TtsStatus.ERROR
            }
        } catch (e: Exception) {
            _status.value = TtsStatus.ERROR
        }
    }

    private fun checkEspeak() {
        _status.value = try {
            val p = ProcessBuilder("espeak-ng", "--version").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            if (p.waitFor() == 0) TtsStatus.READY else TtsStatus.ERROR
        } catch (e: Exception) {
            TtsStatus.ERROR
        }
    }

    @Synchronized
    private fun send(command: String) {
        val w = writer ?: return
        try {
            w.write(command)
            w.newLine()
            w.flush()
        } catch (e: Exception) {
            _status.value = TtsStatus.ERROR
        }
    }

    override fun speak(text: String) {
        val t = text.replace('\n', ' ').replace('\r', ' ').trim()
        if (t.isEmpty()) return
        if (isWindows) {
            send("RATE ${sapiRate(rate)}")
            // Tekst w Base64 (UTF-8) – polskie znaki przechodzą bez względu na stronę kodową konsoli.
            send("SAY " + Base64.getEncoder().encodeToString(t.toByteArray(Charsets.UTF_8)))
        } else if (_status.value == TtsStatus.READY) {
            espeakProcess?.destroy()
            espeak.execute {
                espeakProcess = runCatching {
                    ProcessBuilder("espeak-ng", "-v", "pl", "-s", (175 * rate).roundToInt().toString(), t).start()
                }.getOrNull()
            }
        }
    }

    override fun recheck() {
        if (isWindows) send("CHECK") else checkEspeak()
    }

    override fun shutdown() {
        runCatching { writer?.close() }
        process?.destroy()
        espeakProcess?.destroy()
        espeak.shutdownNow()
    }

    private companion object {
        val SCRIPT = """
            Add-Type -AssemblyName System.Speech
            ${'$'}s = New-Object System.Speech.Synthesis.SpeechSynthesizer
            ${'$'}s.SetOutputToDefaultAudioDevice()
            function Select-Polish {
                ${'$'}v = ${'$'}s.GetInstalledVoices() | Where-Object { ${'$'}_.Enabled -and ${'$'}_.VoiceInfo.Culture.Name -eq 'pl-PL' } | Select-Object -First 1
                if (${'$'}v) { ${'$'}s.SelectVoice(${'$'}v.VoiceInfo.Name); [Console]::Out.WriteLine('STATUS READY') }
                else { [Console]::Out.WriteLine('STATUS MISSING') }
                [Console]::Out.Flush()
            }
            Select-Polish
            while (${'$'}true) {
                ${'$'}line = [Console]::In.ReadLine()
                if (${'$'}line -eq ${'$'}null) { break }
                if (${'$'}line.StartsWith('RATE ')) { ${'$'}s.Rate = [int]${'$'}line.Substring(5) }
                elseif (${'$'}line.StartsWith('SAY ')) {
                    ${'$'}text = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(${'$'}line.Substring(4)))
                    ${'$'}s.SpeakAsyncCancelAll()
                    [void]${'$'}s.SpeakAsync(${'$'}text)
                }
                elseif (${'$'}line -eq 'CHECK') { Select-Polish }
            }
        """.trimIndent()
    }
}
