#!/usr/bin/env bash
# Phase 3: extract valid job-lead records from agent ledger files.
#
# Usage: bash extract_records.sh <ledgers-dir> <output-jsonl>
#
# Each ledger file is shaped {agent, records[]}. Only object records with
# reported:true and non-blank title/company/location/engagement are kept.
# Malformed files and invalid records are logged to stderr and skipped —
# one bad ledger never kills the run.
#
# Output is one JSON object per line. Duplicates (same normalized
# title+company+location) across ledgers are emitted once; the backend also
# dedupes by stable posting id, so this is defense in depth.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <ledgers-dir> <output-jsonl>" >&2
  exit 2
fi

LEDGERS_DIR="$1"
OUT="$2"

if [ ! -d "$LEDGERS_DIR" ]; then
  echo "ledgers dir not found: $LEDGERS_DIR" >&2
  exit 1
fi

command -v jq > /dev/null || { echo "jq is required" >&2; exit 1; }

VALID_FILTER='
  .records[]
  | select(type == "object")
  | select(.reported == true)
  | select((.title // "" | tostring) | test("\\S"))
  | select((.company // "" | tostring) | test("\\S"))
  | select((.location // "" | tostring) | test("\\S"))
  | select((.engagement // "" | tostring) | test("\\S"))
'
KEY='[(.title | tostring | ascii_downcase | gsub("^\\s+|\\s+$"; "")),
     (.company | tostring | ascii_downcase | gsub("^\\s+|\\s+$"; "")),
     (.location | tostring | ascii_downcase | gsub("^\\s+|\\s+$"; ""))] | join("|")'

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

shopt -s nullglob
files=("$LEDGERS_DIR"/*.json)
if [ "${#files[@]}" -eq 0 ]; then
  echo "no ledger files in $LEDGERS_DIR - emitting empty output" >&2
  : > "$OUT"
  exit 0
fi

total_valid=0
for f in "${files[@]}"; do
  if ! jq -e 'type == "object" and has("records") and (.records | type == "array")' \
      "$f" > /dev/null 2>&1; then
    echo "skipping malformed ledger (no records array): $f" >&2
    continue
  fi
  valid=$(jq -c "$VALID_FILTER" "$f" | tee -a "$TMP" | wc -l | tr -d ' ')
  echo "ledger $f: $valid valid records kept" >&2
  total_valid=$((total_valid + valid))
done

if [ "$total_valid" -eq 0 ]; then
  : > "$OUT"
  echo "no valid records extracted - empty output" >&2
  exit 0
fi

# Dedupe across ledgers by normalized title|company|location, keeping the
# first occurrence.
jq -s -c "unique_by($KEY) | .[]" "$TMP" > "$OUT"
echo "extracted $(wc -l < "$OUT" | tr -d ' ') unique records -> $OUT" >&2
