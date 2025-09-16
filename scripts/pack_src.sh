# /Users/elkes/AndroidStudioProjects/kosherjava-compute-engine/scripts/pack_src.sh
#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------------------
# Pack a lean source snapshot (Kotlin + Gradle only) into ./scripts/
# Usage:
#   ./scripts/pack_src.sh [name.zip] [--with-git] [--with-profiles]
#
# Defaults:
#   name.zip       -> halacha-engine_sources_YYYYMMDD_HHMM.zip
#   --with-git     -> exclude .git by default (include only if flag set)
#   --with-profiles-> exclude profiles JSON by default (include if flag set)
# ------------------------------------------------------------------------------

WITH_GIT="false"
WITH_PROFILES="false"
OUT_NAME="halacha-engine_sources_$(date +%Y%m%d_%H%M).zip"

for arg in "$@"; do
  case "$arg" in
    --with-git) WITH_GIT="true" ;;
    --with-profiles) WITH_PROFILES="true" ;;
    *.zip) OUT_NAME="$(basename "$arg")" ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$ROOT/scripts"
mkdir -p "$SCRIPTS_DIR"
OUT="$SCRIPTS_DIR/$OUT_NAME"

cd "$ROOT"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# ------------------------------------------------------------------------------
# MANIFEST (git info if available)
# ------------------------------------------------------------------------------
MANIFEST="$STAGE/MANIFEST.txt"
{
  echo "Halacha Engine Lean Source Snapshot"
  echo "Packed at: $(date -Iseconds)"
  if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Git branch: $(git rev-parse --abbrev-ref HEAD || echo N/A)"
    echo "Git commit: $(git rev-parse --short HEAD || echo N/A)"
    echo "Git status (short):"
    git status --porcelain || true
  else
    echo "Git: not a repo (or git not available)"
  fi
} > "$MANIFEST"

# ------------------------------------------------------------------------------
# Build rsync include/exclude lists to keep the archive tiny
# Keep only:
#   - Kotlin sources (*.kt), Gradle Kotlin scripts (*.kts)
#   - Gradle wrapper (gradlew, gradlew.bat, gradle/wrapper/**)
#   - gradle.properties, README.md
#   - scripts/*.sh
#   - (optional) profiles JSON files if --with-profiles
# ------------------------------------------------------------------------------
INCLUDES=(
  "--include=/gradlew"
  "--include=/gradlew.bat"
  "--include=/gradle/wrapper/***"
  "--include=/gradle.properties"
  "--include=/README.md"
  "--include=/scripts/***.sh"
  "--include=/***.kt"
  "--include=/***.kts"
)
if [[ "$WITH_PROFILES" == "true" ]]; then
  INCLUDES+=("--include=/profiles/src/main/resources/profiles/***.json")
fi

EXCLUDES=( "--exclude=/**" )  # start with everything excluded

# Copy to staging with sparse include list
rsync -a --prune-empty-dirs "${INCLUDES[@]}" "${EXCLUDES[@]}" ./ "$STAGE/repo"

# ------------------------------------------------------------------------------
# Zip it (MANIFEST.txt at root + repo/ subtree)
# ------------------------------------------------------------------------------
cd "$STAGE"
zip -r "$OUT" MANIFEST.txt repo > /dev/null

# Show size
BYTES=$(stat -f%z "$OUT" 2>/dev/null || stat -c%s "$OUT")
echo "Packed sources -> $OUT (${BYTES} bytes)"
echo "Options: with-git=$WITH_GIT with-profiles=$WITH_PROFILES"

# Optional: append .git if requested (done last; may inflate size a lot)
if [[ "$WITH_GIT" == "true" ]]; then
  cd "$ROOT"
  zip -r "$OUT" .git > /dev/null
  echo "Appended .git into $OUT"
fi
