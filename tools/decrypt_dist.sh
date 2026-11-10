#!/usr/bin/env bash
# Odszyfrowuje paczkę .7z zaszyfrowaną hasłem.
# Użycie:  tools/decrypt_dist.sh paczka.7z [katalog_docelowy]
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Użycie: $0 paczka.7z [katalog_docelowy]" >&2
  exit 1
fi

src="$1"; dest="${2:-.}"
pw="${DIST_PASSWORD:-}"
if [ -z "$pw" ]; then
  read -r -s -p "Hasło: " pw; echo
fi

if ! command -v 7z >/dev/null 2>&1; then
  echo "Brak programu 7z. Zainstaluj p7zip-full (Linux) albo 7-Zip (Windows)." >&2
  exit 1
fi

7z x -p"$pw" -o"$dest" "$src"
echo "Odszyfrowano do: $dest"
