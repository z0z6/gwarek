#!/usr/bin/env bash
# Szyfruje wskazane pliki do paczki .7z (AES-256, z ukrytymi nazwami plików).
# Użycie:  tools/encrypt_dist.sh wynik.7z plik1 [plik2 ...]
# Hasło można podać w zmiennej DIST_PASSWORD, inaczej skrypt o nie zapyta.
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Użycie: $0 wynik.7z plik1 [plik2 ...]" >&2
  exit 1
fi

out="$1"; shift
pw="${DIST_PASSWORD:-}"
if [ -z "$pw" ]; then
  read -r -s -p "Hasło: " pw; echo
fi

if ! command -v 7z >/dev/null 2>&1; then
  echo "Brak programu 7z. Zainstaluj p7zip-full (Linux) albo 7-Zip (Windows)." >&2
  exit 1
fi

rm -f "$out"
7z a -t7z -mx=9 -mhe=on -p"$pw" "$out" "$@"
echo "Zaszyfrowano do: $out"
