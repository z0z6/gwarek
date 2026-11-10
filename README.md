# Gwarek

Otwartoźródłowy komunikator AAC (komunikacja wspomagająca i alternatywna) na Androida i Windowsa, projektowany pod tablety i komputery. Działa w pełni offline.

## Stan: etap 5 (plan ukończony)

- tablica piktogramów z kategoriami (ok. 100 słów startowych)
- biblioteka ok. 5 400 wolnych symboli z polskimi nazwami i wyszukiwarką (także bez polskich znaków: „zolw” znajdzie „żółw”)
- pasek zdania: budowanie wypowiedzi, odczyt całości, cofnij, wyczyść
- synteza mowy offline przez systemowy silnik TTS (polski głos)
- kolory kafelków wg zmodyfikowanego klucza Fitzgeralda
- tryb edycji chroniony przytrzymaniem kłódki: dodawanie, edycja i usuwanie piktogramów oraz kategorii
- kreator piktogramów: zdjęcie z aparatu lub galerii z kadrowaniem, łączenie dwóch symboli, rysowanie palcem, znak przekreślenia
- druk kart na A4 (4/6/8 cm, podpis, kolory, linie cięcia) lub zapis do PDF; symbole drukują się wektorowo
- podpowiedzi kolejnych piktogramów uczące się z wyborów użytkownika (bez AI, natychmiastowe)
- asystent AI działający w pełni offline (llama.cpp + model GGUF wczytany z pliku): układanie poprawnych zdań po polsku przed odczytaniem, generowanie tablic tematycznych
- kopia zapasowa całości do jednego pliku .zip i przywracanie
- import i eksport tablic w otwartym formacie Open Board Format (.obf/.obz), zgodnym z innymi aplikacjami AAC
- blokada: PIN do trybu edycji, przypinanie ekranu (dziecko nie wyjdzie z aplikacji)
- dostępność: ukrywanie i powiększanie podpisów, grube obramowania, ignorowanie przypadkowych podwójnych dotknięć
- ustawienia: liczba kolumn, tempo mowy, czytanie po dotknięciu

## Plan

1. ✅ Szkielet, TTS, edycja tablic, CI
2. ✅ Baza piktogramów z wyszukiwarką po polskich słowach
3. ✅ Kreator piktogramów (zdjęcie, rysowanie, łączenie) i druk do PDF
4. ✅ Lokalne agenty AI: przewidywanie kolejnego słowa, odmiana zdań po polsku, generowanie tablic tematycznych
5. ✅ Kopie zapasowe, import/eksport (Open Board Format), blokada, dostępność

## Windows (w przygotowaniu)

Projekt jest wieloplatformowy (Kotlin + Compose Multiplatform). Wspólny kod jest w `app/src/jvmShared`, część tylko dla Androida w `app/src/androidMain`, a dla Windowsa w `app/src/desktopMain`.

Na Windowsie działają już: tablice, mowa (wbudowany syntezator SAPI z polskim głosem), biblioteka symboli, edycja, podpowiedzi, kopie zapasowe, Open Board Format, PIN i tryb pełnoekranowy (F11). Kreator piktogramów, druk i asystent AI dojdą w kolejnym etapie.

Polski głos na Windowsie: Ustawienia → Czas i język → Mowa → Dodaj głosy → Polski. Dane aplikacji są w `%APPDATA%\Gwarek`.

Lokalne uruchomienie wersji na komputer: `gradle :app:run`. Instalator: `gradle :app:packageMsi` (na Windowsie).

## Budowanie

GitHub Actions przy każdym pushu do `main` buduje APK (artefakt **gwarek-apk**) i instalator Windows (artefakt **gwarek-windows-msi**). Znajdziesz je w zakładce Actions → ostatni przebieg → Artifacts.

Lokalnie (JDK 17, Android SDK, Gradle 8.9, Python 3):

```
gradle :app:assembleRelease
```

Przy pierwszym buildzie Gradle uruchamia `tools/fetch_symbols.py`, który pobiera zestawy symboli (przypięte wersje) do `app/generated-assets/common/symbols/`. Ten katalog nie trafia do repo. Aby pobrać go od nowa, usuń katalog.

Jeśli wolisz wrapper: `gradle wrapper --gradle-version 8.9`, a potem `./gradlew assembleRelease`.

### Stały klucz podpisu (ważne!)

Bez własnego klucza każdy build z CI jest podpisany innym, losowym kluczem debug, więc nowej wersji nie da się zainstalować na starą bez odinstalowania, a to kasuje tablice użytkownika. Raz wygeneruj klucz:

```
keytool -genkeypair -v -keystore gwarek.jks -alias gwarek -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 gwarek.jks > gwarek.jks.b64
```

i dodaj w **Settings → Secrets and variables → Actions**:

| Secret | Wartość |
|---|---|
| `KEYSTORE_BASE64` | zawartość `gwarek.jks.b64` |
| `SIGNING_STORE_PASSWORD` | hasło keystore |
| `SIGNING_KEY_ALIAS` | `gwarek` |
| `SIGNING_KEY_PASSWORD` | hasło klucza |

Plik `gwarek.jks` trzymaj bezpiecznie poza repozytorium. Jego utrata oznacza, że aktualizacje przestaną się instalować.

## Asystent AI

Aplikacja nie ma uprawnienia do internetu, więc model trzeba dostarczyć jako plik. Zalecany jest **Bielik-1.5B-v3.0-Instruct** (SpeakLeash, Apache 2.0) w formacie GGUF, wariant Q4_K_M (ok. 1 GB), do pobrania z Hugging Face (repozytorium `speakleash/Bielik-1.5B-v3.0-Instruct-GGUF`). Plik kopiujesz na tablet, a potem wybierasz Ustawienia → Asystent AI → Wczytaj model z pliku. Działa każdy model czatu GGUF obsługiwany przez llama.cpp, ale najlepiej po polsku radzi sobie Bielik.

Silnik: [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT), przypięty do wydania b12000, kompilowany w CI dla arm64-v8a.

## Głos

Aplikacja korzysta z systemowego TTS. Jeśli brakuje polskiego głosu, pokaże przycisk instalacji. Po pobraniu głos działa bez internetu.

## Licencje

Wszystko jest bezpłatne i wolne, również do zastosowań komercyjnych.

| Element | Licencja |
|---|---|
| Kod aplikacji | GNU GPL v3 |
| llama.cpp | MIT |
| [Mulberry Symbols](https://mulberrysymbols.org) (Steve Lee i współtwórcy, wraz z polskimi nazwami) | CC BY-SA 4.0 |
| [OpenMoji](https://openmoji.org) | CC BY-SA 4.0 |
| Polskie nazwy emoji: Unicode CLDR | Unicode License v3 |

Warunki CC BY-SA spełnia sama aplikacja: autorzy są wymienieni w Ustawienia → Licencje i autorzy symboli. Piktogramy przerobione z tych zestawów pozostają na CC BY-SA 4.0.
