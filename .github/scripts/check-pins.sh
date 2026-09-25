#!/usr/bin/env bash
# Phase 0 process gate: every `uses:` line in .github/workflows must reference
# a 40-hex-char commit SHA, never a mutable tag. Lives in its own file so the
# check cannot flag its own documentation.
set -euo pipefail

sha='[0-9a-f]{40}'
bad=$(grep -R "uses:" .github/workflows | grep -vE "^[[:space:]]*#" | grep -vE "@${sha}" || true)

if [ -n "$bad" ]; then
  echo "ERROR: unpinned action found:"
  echo "$bad"
  exit 1
fi
echo "all actions SHA-pinned"
