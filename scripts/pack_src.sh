#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------------------
# Pack a lean-but-useful source snapshot for development into ./scripts/
#
# Usage:
#   ./scripts/pack_src.sh [name.zip] [--with-git] [--with-profiles] [--root /path/to/root]
#
# Defaults:
#   name.zip         -> kosherjava-compute-engine_sources_YYYYMMDD_HHMM.zip
#   --with-git       -> exclude .git by default (include only if flag set)
#   --with-profiles  -> include profile JSONs even if they live outside src/resources
#   --root           -> project root (defaults to script/..)
# ------------------------------------------------------------------------------

WITH_GIT="false"
WITH_PROFILES="false"
OUT_NAME="kosherjava-compute-engine_sources_$(date +%Y%m%d_%H%M).zip"
EXPLICIT_ROOT=""

while (( "$#" )); do
  case "$1" in
    --with-git)       WITH_GIT="true"; shift ;;
    --with-profiles)  WITH_PROFILES="true"; shift ;;
    --root)           EXPLICIT_ROOT="${2-}"; shift 2 ;;
    *.zip)            OUT_NAME="$(basename "$1")"; shift ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

# Resolve ROOT
if [[ -n "$EXPLICIT_ROOT" ]]; then
  ROOT="$(cd "$EXPLICIT_ROOT" && pwd)"
else
  ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi

SCRIPTS_DIR="$ROOT/scripts"
mkdir -p "$SCRIPTS_DIR"
OUT="$SCRIPTS_DIR/$OUT_NAME"

cd "$ROOT"

# Tools sanity
command -v rsync >/dev/null 2>&1 || { echo "rsync is required"; exit 1; }
command -v zip   >/dev/null 2>&1 || { echo "zip is required"; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# ------------------------------------------------------------------------------
# MANIFEST
# ------------------------------------------------------------------------------
MANIFEST="$STAGE/MANIFEST.txt"
{
  echo "KosherJava Compute Engine - Source Snapshot"
  echo "Packed at: $(date -Iseconds)"
  echo "Root: $ROOT"
  if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Git branch: $(git -C "$ROOT" rev-parse --abbrev-ref HEAD || echo N/A)"
    echo "Git commit: $(git -C "$ROOT" rev-parse --short HEAD || echo N/A)"
    echo "Git status (short):"
    git -C "$ROOT" status --porcelain || true
  else
    echo "Git: not a repo (or git not available)"
  fi
} > "$MANIFEST"

# ------------------------------------------------------------------------------
# rsync filters:
#   - include dirs first (*/) so rsync can descend
#   - include Kotlin/Gradle and helpful dev/config/resource files
#   - exclude everything else
# Notes:
#   Use '**' globs (rsync filter syntax) – NO '***'
#   Avoid leading '/' in patterns so they match anywhere below ROOT
# ------------------------------------------------------------------------------
INCLUDE_RULES=(
  "--include=*/"

  # Kotlin sources
  "--include=**/*.kt"

  # Gradle scripts & wrapper
  "--include=gradlew"
  "--include=gradlew.bat"
  "--include=gradle/wrapper/**"
  "--include=**/*.gradle"
  "--include=**/*.gradle.kts"
  "--include=**/settings.gradle"
  "--include=**/settings.gradle.kts"
  "--include=**/build.gradle"
  "--include=**/build.gradle.kts"
  "--include=**/gradle.properties"

  # Properties & configs commonly useful
  "--include=**/*.properties"
  "--include=.editorconfig"
  "--include=.gitignore"
  "--include=.gitattributes"
  "--include=.github/**"

  # Scripts (bash/sh)
  "--include=**/*.sh"

  # Docs
  "--include=**/README"
  "--include=**/README.*"
  "--include=**/LICENSE*"
  "--include=**/*.md"

  # Common resources helpful for dev (profiles, config, etc.)
  "--include=**/*.json"
  "--include=**/*.yaml"
  "--include=**/*.yml"
  "--include=**/*.xml"
  "--include=**/*.txt"
)

if [[ "$WITH_PROFILES" == "true" ]]; then
  # Be explicit about the known profiles path too (harmless if already covered)
  INCLUDE_RULES+=("--include=profiles/**")
fi

EXCLUDE_RULES=(
  # Build outputs and typical noise
  "--exclude=**/build/**"
  "--exclude=**/.gradle/**"
  "--exclude=**/.idea/**"
  "--exclude=**/.DS_Store"
  "--exclude=**/*.iml"
  "--exclude=**/.venv/**"
  "--exclude=**/__pycache__/**"
  "--exclude=**/*.zip"

  # Finally, exclude everything else not matched above
  "--exclude=*"
)

# Stage selected files under $STAGE/repo
rsync -a --prune-empty-dirs \
  "${INCLUDE_RULES[@]}" \
  "${EXCLUDE_RULES[@]}" \
  "./" "$STAGE/repo/"

# ------------------------------------------------------------------------------
# Zip (MANIFEST + repo/)
# ------------------------------------------------------------------------------
cd "$STAGE"
zip -r "$OUT" MANIFEST.txt repo > /dev/null

# Optional: append .git (can inflate size a lot)
if [[ "$WITH_GIT" == "true" ]]; then
  ( cd "$ROOT" && zip -r "$OUT" .git > /dev/null )
  APPENDED_GIT="yes"
else
  APPENDED_GIT="no"
fi

# Show size
BYTES=$(stat -f%z "$OUT" 2>/dev/null || stat -c%s "$OUT")
echo "Packed sources -> $OUT (${BYTES} bytes)"
echo "Options: with-git=$APPENDED_GIT with-profiles=$WITH_PROFILES"