#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
resources_root="$project_root/app/src/main/res"
reference="$resources_root/values/strings.xml"

extract_keys() {
  grep -o 'name="[^"]*"' "$1" | sed 's/name="//; s/"$//' | sort -u
}

temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

extract_keys "$reference" > "$temporary_directory/reference"

for locale in de ru; do
  translation="$resources_root/values-$locale/strings.xml"
  extract_keys "$translation" > "$temporary_directory/$locale"

  missing="$(comm -23 "$temporary_directory/reference" "$temporary_directory/$locale")"
  extra="$(comm -13 "$temporary_directory/reference" "$temporary_directory/$locale")"

  if [[ -n "$missing" || -n "$extra" ]]; then
    echo "Ressourcenschlüssel für '$locale' stimmen nicht mit values/strings.xml überein."
    [[ -n "$missing" ]] && echo "Fehlend: $missing"
    [[ -n "$extra" ]] && echo "Zusätzlich: $extra"
    exit 1
  fi
done

echo "Deutsche und russische Ressourcenschlüssel sind vollständig."

