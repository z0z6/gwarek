import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
}

val appVersion = "0.6.0"

/*
 * Układ źródeł:
 *   src/jvmShared  – wspólny kod aplikacji (interfejs, baza, symbole, AI, kopie, OBF),
 *                    kompilowany osobno dla Androida i dla komputera (oba to JVM),
 *   src/androidMain – część tylko dla Androida (mowa, kreator, druk, wybór plików…),
 *   src/desktopMain – część tylko dla Windowsa/komputera.
 * Obie części platformowe definiują te same funkcje z pakietu org.gwarek.aac.platform.
 */
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("androidx.room:room-runtime:2.7.1")
        }
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation("androidx.core:core-ktx:1.16.0")
                implementation("androidx.activity:activity-compose:1.10.1")
                implementation("androidx.exifinterface:exifinterface:1.3.7")
                implementation("com.caverock:androidsvg-aar:1.4")
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("androidx.sqlite:sqlite-bundled:2.5.1")
                implementation("org.json:json:20240303")
            }
        }
    }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.1")
    add("kspDesktop", "androidx.room:room-compiler:2.7.1")
}

ksp {
    arg("room.generateKotlin", "true")
}

// Podpisywanie: jeśli CI poda keystore przez zmienne środowiskowe, używamy go.
// W przeciwnym razie release podpisuje się kluczem debug (tylko do testów).
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "org.gwarek.aac"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.gwarek.aac"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = appVersion

        // Lokalny model AI (llama.cpp) budujemy tylko dla 64-bitowego ARM – to praktycznie wszystkie tablety.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Biblioteka symboli generowana przez tools/fetch_symbols.py
    sourceSets["main"].assets.srcDir("generated-assets/common")

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.gwarek.aac.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Gwarek"
            packageVersion = appVersion
            description = "Gwarek – komunikator AAC"
            vendor = "Gwarek"
            // Biblioteka symboli trafia do katalogu zasobów aplikacji (common/symbols)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("generated-assets"))
            includeAllModules = true
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "Gwarek"
                upgradeUuid = "6f3c2b1e-4d5a-4f7e-9b8c-1a2d3e4f5a6b"
                iconFile.set(project.file("src/desktopMain/icon.ico"))
            }
        }
    }
}

// Biblioteka symboli (Mulberry + OpenMoji) nie jest trzymana w repo – pobiera ją skrypt
// przy pierwszym buildzie. Żeby pobrać ponownie, usuń app/generated-assets.
val symbolsIndex = file("generated-assets/common/symbols/index.tsv")
val fetchSymbols by tasks.registering(Exec::class) {
    description = "Pobiera wolne zestawy symboli i przygotowuje zasoby"
    workingDir = rootDir
    val python = if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
    commandLine(python, "tools/fetch_symbols.py")
    onlyIf { !symbolsIndex.exists() }
}
tasks.matching { it.name == "preBuild" || it.name == "prepareAppResources" || it.name == "desktopProcessResources" }
    .configureEach { dependsOn(fetchSymbols) }
