# Gadka

Otwartoźródłowy komunikator AAC (komunikacja wspomagająca i alternatywna) na Androida, projektowany przede wszystkim pod tablety. Działa w pełni offline.

## Stan: etap 3

- tablica piktogramów z kategoriami (ok. 100 słów startowych)
- biblioteka ok. 5 400 wolnych symboli z polskimi nazwami i wyszukiwarką (także bez polskich znaków: „zolw” znajdzie „żółw”)
- pasek zdania: budowanie wypowiedzi, odczyt całości, cofnij, wyczyść
- synteza mowy offline przez systemowy silnik TTS (polski głos)
- kolory kafelków wg zmodyfikowanego klucza Fitzgeralda
- tryb edycji chroniony przytrzymaniem kłódki: dodawanie, edycja i usuwanie piktogramów oraz kategorii
- kreator piktogramów: zdjęcie z aparatu lub galerii z kadrowaniem, łączenie dwóch symboli, rysowanie palcem, znak przekreślenia
- druk kart na A4 (4/6/8 cm, podpis, kolory, linie cięcia) lub zapis do PDF; symbole drukują się wektorowo
- ustawienia: liczba kolumn, tempo mowy, czytanie po dotknięciu

## Plan

1. ✅ Szkielet, TTS, edycja tablic, CI
2. ✅ Baza piktogramów z wyszukiwarką po polskich słowach
3. ✅ Kreator piktogramów (zdjęcie, rysowanie, łączenie) i druk do PDF
4. Lokalne agenty AI: przewidywanie kolejnego słowa, odmiana zdań po polsku, generowanie tablic tematycznych
5. Kopie zapasowe, import/eksport (Open Board Format), dostępność

## Budowanie

GitHub Actions buduje APK przy każdym pushu do `main` (zakładka **Actions → ostatni przebieg → Artifacts → gadka-apk**).

Lokalnie (JDK 17, Android SDK, Gradle 8.9, Python 3):

```
gradle assembleRelease
```

Przy pierwszym buildzie Gradle uruchamia `tools/fetch_symbols.py`, który pobiera zestawy symboli (przypięte wersje) do `app/src/main/assets/symbols/`. Ten katalog nie trafia do repo. Aby pobrać go od nowa, usuń katalog.

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

## Licencje

Wszystko jest bezpłatne i wolne, również do zastosowań komercyjnych.

| Element | Licencja |
|---|---|
| Kod aplikacji | GNU GPL v3 |
| [Mulberry Symbols](https://mulberrysymbols.org) (Steve Lee i współtwórcy, wraz z polskimi nazwami) | CC BY-SA 4.0 |
| [OpenMoji](https://openmoji.org) | CC BY-SA 4.0 |
| Polskie nazwy emoji: Unicode CLDR | Unicode License v3 |

Warunki CC BY-SA spełnia sama aplikacja: autorzy są wymienieni w Ustawienia → Licencje i autorzy symboli. Piktogramy przerobione z tych zestawów pozostają na CC BY-SA 4.0.
