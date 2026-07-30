#!/usr/bin/env bash
set -euo pipefail

apk_path="${1:-}"
if [[ -z "$apk_path" || ! -f "$apk_path" ]]; then
  echo "Aufruf: $0 <internal-beta.apk>"
  exit 2
fi

apk_strings="$(unzip -p "$apk_path" | strings)"

if grep -Eqi '10\.0\.2\.2|127\.0\.0\.1|localhost' <<< "$apk_strings"; then
  echo "Die Beta-APK enthält eine lokale Adresse."
  exit 1
fi

if grep -Eqi '(sk-[A-Za-z0-9_-]{20,}|Bearer [A-Za-z0-9._-]{20,})' <<< "$apk_strings"; then
  echo "Die Beta-APK enthält ein mögliches eingebettetes Token."
  exit 1
fi

echo "Keine lokale Adresse oder offensichtliches Token in der Beta-APK gefunden."

