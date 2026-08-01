#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

# Scan only tracked text files. Placeholders such as `${{ secrets.NAME }}` are
# intentionally allowed; concrete credentials and private keys are not.
pattern='(sk-[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}|AIza[0-9A-Za-z_-]{30,}|-----BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY-----)'
if git grep -n -I -E "$pattern" -- . >/dev/null 2>&1; then
  echo "Ein möglicher Zugangsschlüssel wurde in versionierten Dateien gefunden."
  git grep -n -I -E "$pattern" -- .
  exit 1
fi

echo "Keine offensichtlichen Zugangsschlüssel oder privaten Schlüssel gefunden."
