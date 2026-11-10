# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# Funkcje natywne llama.cpp (JNI szuka ich po nazwie)
-keep class org.gwarek.aac.ai.LlamaBridge { *; }
