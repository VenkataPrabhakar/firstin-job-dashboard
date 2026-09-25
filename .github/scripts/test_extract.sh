#!/usr/bin/env bash
# Tests for extract_records.sh. 12 cases; exits nonzero on any failure.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXTRACT="$SCRIPT_DIR/extract_records.sh"

pass=0
fail=0
check() { # name, shell-condition
  if eval "$2"; then echo "PASS: $1"; pass=$((pass + 1));
  else echo "FAIL: $1"; fail=$((fail + 1)); fi
}

T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

write() { # filename, content
  printf '%s' "$2" > "$T/$1"
}

GOOD='{"agent":"contract-c2c","records":[
 {"reported":true,"title":"Senior Java Developer","company":"Acme Corp","location":"Austin, TX","engagement":"C2C"},
 {"reported":true,"title":"Backend Engineer","company":"Beta LLC","location":"Remote","engagement":"1099"}]}'

# 1: extracts valid records from a good ledger
write ledgers1.json "$GOOD"
mkdir -p "$T/d1" && cp "$T/ledgers1.json" "$T/d1/"
bash "$EXTRACT" "$T/d1" "$T/o1.jsonl" 2> /dev/null
check "extracts valid records" '[ "$(wc -l < "$T/o1.jsonl" | tr -d " ")" = "2" ]'

# 2: skips reported:false
write rec2.json '{"agent":"x","records":[{"reported":false,"title":"T","company":"C","location":"L","engagement":"C2C"}]}'
mkdir -p "$T/d2" && cp "$T/rec2.json" "$T/d2/"
bash "$EXTRACT" "$T/d2" "$T/o2.jsonl" 2> /dev/null
check "skips reported:false" '[ ! -s "$T/o2.jsonl" ]'

# 3: skips blank title
write rec3.json '{"agent":"x","records":[{"reported":true,"title":"  ","company":"C","location":"L","engagement":"C2C"}]}'
mkdir -p "$T/d3" && cp "$T/rec3.json" "$T/d3/"
bash "$EXTRACT" "$T/d3" "$T/o3.jsonl" 2> /dev/null
check "skips blank title" '[ ! -s "$T/o3.jsonl" ]'

# 4: skips blank company
write rec4.json '{"agent":"x","records":[{"reported":true,"title":"T","company":"","location":"L","engagement":"C2C"}]}'
mkdir -p "$T/d4" && cp "$T/rec4.json" "$T/d4/"
bash "$EXTRACT" "$T/d4" "$T/o4.jsonl" 2> /dev/null
check "skips blank company" '[ ! -s "$T/o4.jsonl" ]'

# 5: skips blank location
write rec5.json '{"agent":"x","records":[{"reported":true,"title":"T","company":"C","location":" ","engagement":"C2C"}]}'
mkdir -p "$T/d5" && cp "$T/rec5.json" "$T/d5/"
bash "$EXTRACT" "$T/d5" "$T/o5.jsonl" 2> /dev/null
check "skips blank location" '[ ! -s "$T/o5.jsonl" ]'

# 6: skips blank engagement
write rec6.json '{"agent":"x","records":[{"reported":true,"title":"T","company":"C","location":"L"}]}'
mkdir -p "$T/d6" && cp "$T/rec6.json" "$T/d6/"
bash "$EXTRACT" "$T/d6" "$T/o6.jsonl" 2> /dev/null
check "skips missing engagement" '[ ! -s "$T/o6.jsonl" ]'

# 7: skips non-object records
write rec7.json '{"agent":"x","records":["oops",42,null,{"reported":true,"title":"T","company":"C","location":"L","engagement":"W2"}]}'
mkdir -p "$T/d7" && cp "$T/rec7.json" "$T/d7/"
bash "$EXTRACT" "$T/d7" "$T/o7.jsonl" 2> /dev/null
check "skips non-object records, keeps the valid one" '[ "$(wc -l < "$T/o7.jsonl" | tr -d " ")" = "1" ]'

# 8: malformed ledger is skipped, good ledgers still processed
write bad.json '{this is not json'
mkdir -p "$T/d8" && cp "$T/bad.json" "$T/d8/" && cp "$T/ledgers1.json" "$T/d8/"
bash "$EXTRACT" "$T/d8" "$T/o8.jsonl" 2> "$T/e8.log"
check "malformed file skipped, good file processed" '[ "$(wc -l < "$T/o8.jsonl" | tr -d " ")" = "2" ] && grep -q "skipping malformed ledger" "$T/e8.log"'

# 9: ledger without a records array is skipped
write nor.json '{"agent":"x","postings":[]}'
mkdir -p "$T/d9" && cp "$T/nor.json" "$T/d9/"
bash "$EXTRACT" "$T/d9" "$T/o9.jsonl" 2> "$T/e9.log"
check "ledger without records array skipped" '[ ! -s "$T/o9.jsonl" ] && grep -q "skipping malformed ledger" "$T/e9.log"'

# 10: empty records array -> empty output, exit 0
write empty.json '{"agent":"x","records":[]}'
mkdir -p "$T/d10" && cp "$T/empty.json" "$T/d10/"
bash "$EXTRACT" "$T/d10" "$T/o10.jsonl" 2> /dev/null
check "empty records array -> empty output" '[ ! -s "$T/o10.jsonl" ]'

# 11: dedupes identical records across ledgers
mkdir -p "$T/d11" && cp "$T/ledgers1.json" "$T/d11/a.json" && cp "$T/ledgers1.json" "$T/d11/b.json"
bash "$EXTRACT" "$T/d11" "$T/o11.jsonl" 2> /dev/null
check "dedupes across ledgers" '[ "$(wc -l < "$T/o11.jsonl" | tr -d " ")" = "2" ]'

# 12: no ledger files -> empty output, exit 0
mkdir -p "$T/d12"
bash "$EXTRACT" "$T/d12" "$T/o12.jsonl" 2> /dev/null
check "no ledger files -> empty output" '[ ! -s "$T/o12.jsonl" ]'

echo "---"
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
