#!/usr/bin/env python3
"""
Pobiera wolne zestawy symboli i przygotowuje je jako zasoby aplikacji.

Źródła (przypięte do konkretnych wersji, żeby build był powtarzalny):
  * Mulberry Symbols (CC BY-SA 4.0) – SVG + polskie nazwy (arkusz mulberry-pl.ods)
  * OpenMoji (CC BY-SA 4.0) – kolorowe SVG emoji
  * Unicode CLDR (Unicode License v3) – polskie nazwy i słowa kluczowe emoji

Wynik: app/src/main/assets/symbols/
  index.tsv     id, źródło, plik, podpis, typ słowa, domena, słowa kluczowe, emoji
  m/<id>.svg    Mulberry
  o/<hex>.svg   OpenMoji
  CREDITS.txt   autorzy i licencje

Tylko biblioteka standardowa Pythona 3.8+.
"""
import io
import json
import os
import shutil
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

MULBERRY_SHA = "fa94a93ca1b5357c0b6e0d9f8a81c426a1dfefdf"
OPENMOJI_TAG = "17.0.0"
CLDR_TAG = "49.0.0"

MULBERRY_ZIP = f"https://codeload.github.com/mulberrysymbols/mulberry-symbols/zip/{MULBERRY_SHA}"
OPENMOJI_SVG = f"https://github.com/hfg-gmuend/openmoji/releases/download/{OPENMOJI_TAG}/openmoji-svg-color.zip"
OPENMOJI_JSON = f"https://raw.githubusercontent.com/hfg-gmuend/openmoji/{OPENMOJI_TAG}/data/openmoji.json"
CLDR_BASE = f"https://raw.githubusercontent.com/unicode-org/cldr-json/{CLDR_TAG}/cldr-json"
CLDR_PL = f"{CLDR_BASE}/cldr-annotations-full/annotations/pl/annotations.json"
CLDR_PL_DERIVED = f"{CLDR_BASE}/cldr-annotations-derived-full/annotationsDerived/pl/annotations.json"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "symbols")


def fetch(url: str) -> bytes:
    print(f"  pobieram {url}", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "gadka-build"})
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


# ---------- minimalny czytnik .ods ----------

NS = {
    "table": "urn:oasis:names:tc:opendocument:xmlns:table:1.0",
    "text": "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
}
T = "{%s}" % NS["table"]


def _cell_text(cell) -> str:
    parts = []
    for p in cell.iter("{%s}p" % NS["text"]):
        parts.append("".join(p.itertext()))
    return "\n".join(parts)


def read_ods(data: bytes) -> dict:
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        root = ET.fromstring(z.read("content.xml"))
    sheets = {}
    for table in root.iter(T + "table"):
        rows = []
        for row in table.iter(T + "table-row"):
            values = []
            for cell in row:
                if cell.tag not in (T + "table-cell", T + "covered-table-cell"):
                    continue
                rep = int(cell.get(T + "number-columns-repeated", "1"))
                txt = _cell_text(cell)
                values.extend([txt] * min(rep, 64))  # puste kolumny na końcu bywają powtórzone tysiące razy
            while values and values[-1] == "":
                values.pop()
            if values:
                rows.append(values)
        if not rows:
            continue
        header = rows[0]
        sheets[table.get(T + "name")] = [
            {h: (r[i] if i < len(r) else "") for i, h in enumerate(header)} for r in rows[1:]
        ]
    return sheets


# ---------- mapowanie na klucz Fitzgeralda ----------

def mulberry_type(pos: str, domain: str) -> str:
    pos = pos.strip()
    if "pytajn" in pos:
        return "QUESTION"
    if pos.startswith("czasownik") or pos == "wyrażenie czasownikowe":
        return "VERB"
    if pos.startswith("zaimek"):
        return "PERSON"
    if pos in ("przymiotnik", "przysłówek", "wyrażenie przysłówkowe", "wyrażenie przymiotnikowe") \
            or pos.startswith("liczebnik"):
        return "DESCRIPTOR"
    if pos in ("przyimek", "wyrażenie przyimkowe"):
        return "OTHER"
    if pos in ("wykrzyknik", "zdanie") or pos.startswith("wyrażenie ("):
        return "SOCIAL"
    if domain == "Ludzie":
        return "PERSON"
    if domain == "Pytania":
        return "QUESTION"
    if domain == "Alfabet":
        return "OTHER"
    return "NOUN"


OPENMOJI_DOMAINS = {
    "smileys-emotion": "Emocje",
    "people-body": "Ludzie i gesty",
    "animals-nature": "Zwierzęta i przyroda",
    "food-drink": "Jedzenie i napoje",
    "travel-places": "Podróże i miejsca",
    "activities": "Zajęcia",
    "objects": "Przedmioty",
    "symbols": "Symbole",
    "flags": "Flagi",
    "extras-unicode": "Inne",
}


def openmoji_type(group: str, subgroup: str) -> str:
    if group == "smileys-emotion":
        return "DESCRIPTOR"
    if group == "people-body":
        if subgroup.startswith("hand") or subgroup == "hands":
            return "SOCIAL"
        if subgroup in ("person-activity", "person-sport", "person-resting"):
            return "VERB"
        if subgroup.startswith("person") or subgroup == "family":
            return "PERSON"
        return "NOUN"
    if group in ("symbols", "flags", "extras-unicode"):
        return "OTHER"
    return "NOUN"


def clean(s: str) -> str:
    return " ".join(s.replace("\t", " ").split())


def main() -> None:
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, "m"))
    os.makedirs(os.path.join(OUT, "o"))
    rows = []

    # --- Mulberry ---
    print("Mulberry Symbols…")
    mz = zipfile.ZipFile(io.BytesIO(fetch(MULBERRY_ZIP)))
    prefix = mz.namelist()[0].split("/")[0] + "/"
    sheet = read_ods(mz.read(prefix + "scripts/data/mulberry-pl.ods"))["symbols"]
    info = {}
    import csv
    for r in csv.DictReader(io.StringIO(mz.read(prefix + "scripts/data/symbol-info.csv").decode("utf-8"))):
        info[r["symbol-en"]] = r
    for r in sheet:
        en = r["symbol-en"]
        try:
            svg = mz.read(f"{prefix}EN/{en}.svg")
        except KeyError:
            print(f"  brak pliku {en}.svg – pomijam", file=sys.stderr)
            continue
        sid = r["symbol-id"]
        with open(os.path.join(OUT, "m", f"{sid}.svg"), "wb") as f:
            f.write(svg)
        domain = clean(r["domain-pl"])
        keywords = " ".join(filter(None, [
            clean(r["subcategory-pl"]),
            en.replace("_", " "),
            info.get(en, {}).get("tags", ""),
        ]))
        rows.append([
            f"m{sid}", "mulberry", f"m/{sid}.svg", clean(r["symbol-pl"]),
            mulberry_type(r["pos-pl"], domain), domain, clean(keywords), "",
        ])
    print(f"  {len(rows)} symboli")

    # --- OpenMoji + CLDR ---
    print("OpenMoji + CLDR…")
    ann = json.loads(fetch(CLDR_PL_DERIVED))["annotationsDerived"]["annotations"]
    ann.update(json.loads(fetch(CLDR_PL))["annotations"]["annotations"])
    meta = json.loads(fetch(OPENMOJI_JSON))
    oz = zipfile.ZipFile(io.BytesIO(fetch(OPENMOJI_SVG)))
    names = set(oz.namelist())
    count = 0
    for e in meta:
        if e.get("skintone") or e["group"] not in OPENMOJI_DOMAINS:
            continue
        emoji = e["emoji"]
        a = ann.get(emoji) or ann.get(emoji.replace("\ufe0f", ""))
        if not a or "tts" not in a:
            continue  # bez polskiej nazwy – pomijamy
        hexcode = e["hexcode"]
        if f"{hexcode}.svg" not in names:
            continue
        with open(os.path.join(OUT, "o", f"{hexcode}.svg"), "wb") as f:
            f.write(oz.read(f"{hexcode}.svg"))
        rows.append([
            f"o{hexcode}", "openmoji", f"o/{hexcode}.svg", clean(a["tts"][0]),
            openmoji_type(e["group"], e.get("subgroups", "")), OPENMOJI_DOMAINS[e["group"]],
            clean(" ".join(a.get("default", [])) + " " + e.get("annotation", "")), emoji,
        ])
        count += 1
    print(f"  {count} symboli")

    with open(os.path.join(OUT, "index.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("id\tsource\tfile\tlabel\ttype\tdomain\tkeywords\temoji\n")
        for r in rows:
            f.write("\t".join(r) + "\n")

    with open(os.path.join(OUT, "CREDITS.txt"), "w", encoding="utf-8") as f:
        f.write(CREDITS)
    print(f"Gotowe: {len(rows)} symboli w {OUT}")


CREDITS = f"""Mulberry Symbols
Copyright 2018–2026 Steve Lee i współtwórcy. Polskie nazwy i taksonomia: współtwórcy projektu Mulberry Symbols.
Licencja: Creative Commons Uznanie autorstwa – Na tych samych warunkach 4.0 (CC BY-SA 4.0)
https://mulberrysymbols.org · https://creativecommons.org/licenses/by-sa/4.0/deed.pl
Wersja: {MULBERRY_SHA[:10]}

OpenMoji
Projekt otwartych emoji dla designerów, deweloperów i wszystkich zainteresowanych.
Licencja: CC BY-SA 4.0 · https://openmoji.org
Wersja: {OPENMOJI_TAG}

Polskie nazwy emoji: Unicode CLDR {CLDR_TAG}
Copyright © 1991–2025 Unicode, Inc. Licencja: Unicode License v3 · https://www.unicode.org/license.txt

Piktogramy pochodzące z tych zestawów (także zmodyfikowane w aplikacji) są udostępniane na licencji CC BY-SA 4.0.
"""

if __name__ == "__main__":
    main()
