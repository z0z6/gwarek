package org.gadka.aac.ai

/** Funkcje natywne z app/src/main/cpp/gadka_llm.cpp. Bibliotekę ładuje [LocalLlm]. */
internal object LlamaBridge {
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeGenerate(handle: Long, system: String, user: String, maxTokens: Int, temperature: Float): ByteArray?
    external fun nativeFree(handle: Long)
}
