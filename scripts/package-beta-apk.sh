#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_apk="$project_root/app/build/outputs/apk/internalBeta/app-internalBeta.apk"
version="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$project_root/app/build.gradle.kts" | head -1)"
commit="${GITHUB_SHA:-$(git -C "$project_root" rev-parse HEAD)}"
short_commit="${commit:0:12}"
artifact_directory="$project_root/artifacts"
artifact_name="baseline-nutrition-${version}-beta-${short_commit}.apk"

if [[ ! -f "$source_apk" ]]; then
  echo "Beta-APK fehlt: $source_apk"
  exit 1
fi

mkdir -p "$artifact_directory"
cp "$source_apk" "$artifact_directory/$artifact_name"
printf '%s\n' "$artifact_directory/$artifact_name"

