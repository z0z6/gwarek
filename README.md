# Gadka

Otwartoźródłowy komunikator AAC (komunikacja wspomagająca i alternatywna) na Androida, projektowany przede wszystkim pod tablety. Działa w pełni offline.

## Stan: etap 1

- tablica piktogramów z kategoriami (ok. 100 słów startowych, na razie jako emoji)
- pasek zdania: budowanie wypowiedzi, odczyt całości, cofnij, wyczyść
- synteza mowy offline przez systemowy silnik TTS (polski głos)
- kolory kafelków wg zmodyfikowanego klucza Fitzgeralda
- tryb edycji chroniony przytrzymaniem kłódki: dodawanie, edycja i usuwanie piktogramów oraz kategorii
- ustawienia: liczba kolumn, tempo mowy, czytanie po dotknięciu

## Plan

1. ✅ Szkielet, TTS, edycja tablic, CI
2. Baza piktogramów z wyszukiwarką po polskich słowach
3. Kreator piktogramów (zdjęcie, rysowanie, łączenie) i druk do PDF
4. Lokalne agenty AI: przewidywanie kolejnego słowa, odmiana zdań po polsku, generowanie tablic tematycznych
5. Kopie zapasowe, import/eksport (Open Board Format), dostępność

## Budowanie

GitHub Actions buduje APK przy każdym pushu do `main` (zakładka **Actions → ostatni przebieg → Artifacts → gadka-apk**).

Lokalnie (JDK 17, Android SDK, Gradle 8.9):

```
gradle assembleRelease
```

Jeśli wolisz wrapper: `gradle wrapper --gradle-version 8.9`, a potem `./gradlew assembleRelease`.

### Stały klucz podpisu (ważne!)

Bez własnego klucza każdy build z CI jest podpisany innym, losowym kluczem debug, więc nowej wersji nie da się zainstalować na starą bez odinstalowania, a to kasuje tablice użytkownika. Raz wygeneruj klucz:

```
keytool -genkeypair -v -keystore gadka.jks -alias gadka -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 gadka.jks > gadka.jks.b64
```

i dodaj w **Settings → Secrets and variables → Actions**:

| Secret | Wartość |
|---|---|
| `KEYSTORE_BASE64` | zawartość `gadka.jks.b64` |
| `SIGNING_STORE_PASSWORD` | hasło keystore |
| `SIGNING_KEY_ALIAS` | `gadka` |
| `SIGNING_KEY_PASSWORD` | hasło klucza |

Plik `gadka.jks` trzymaj bezpiecznie poza repozytorium. Jego utrata oznacza, że aktualizacje przestaną się instalować.

## Głos

Aplikacja korzysta z systemowego TTS. Jeśli brakuje polskiego głosu, pokaże przycisk instalacji. Po pobraniu głos działa bez internetu.

## Licencja

Kod: proponowana GPL-3.0 (dodaj plik LICENSE przez GitHub). Licencja zestawu piktogramów zostanie opisana w etapie 2.
