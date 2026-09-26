#!/usr/bin/env bash
# Tests for publish_kafka.py. 6 cases; exits nonzero on any failure.
#
# Uses a stub confluent_kafka module on PYTHONPATH (no broker needed): the
# stub records every produce() call and the producer config, and can be told
# to fail deliveries via STUB_FAIL=1.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PUBLISH="$SCRIPT_DIR/publish_kafka.py"

pass=0
fail=0
check() { # name, shell-condition
  if eval "$2"; then echo "PASS: $1"; pass=$((pass + 1));
  else echo "FAIL: $1"; fail=$((fail + 1)); fi
}

T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

# --- stub confluent_kafka -------------------------------------------------
mkdir -p "$T/stub"
cat > "$T/stub/confluent_kafka.py" <<'EOF'
import json, os

OUT = os.environ["STUB_OUT"]

class _Msg:
    def __init__(self, topic):
        self._topic = topic
    def topic(self):
        return self._topic

class _Err:
    def __str__(self):
        return "simulated delivery failure"

class Producer:
    def __init__(self, conf):
        with open(os.path.join(OUT, "conf.json"), "w") as fh:
            json.dump(conf, fh)
    def produce(self, topic, value=None, headers=None, callback=None, **kwargs):
        rec = {
            "topic": topic,
            "value": value.decode("utf-8") if isinstance(value, (bytes, bytearray)) else value,
            "headers": {k: v for k, v in (headers or [])},
        }
        with open(os.path.join(OUT, "produces.jsonl"), "a") as fh:
            fh.write(json.dumps(rec) + "\n")
        if callback is not None:
            if os.environ.get("STUB_FAIL") == "1":
                callback(_Err(), _Msg(topic))
            else:
                callback(None, _Msg(topic))
    def poll(self, timeout):
        return 0
    def flush(self, timeout=None):
        return 0
EOF

run_publish() { # outdir, records-file
  local outdir="$1" records="$2"
  mkdir -p "$outdir"
  STUB_OUT="$outdir" PYTHONPATH="$T/stub" \
    KAFKA_BOOTSTRAP_SERVERS="redpanda.example:9092" \
    KAFKA_SASL_USERNAME="firstin" KAFKA_SASL_PASSWORD="secret" \
    RUN_ID="2026-09-26" \
    python3 "$PUBLISH" "$records" > "$outdir/stdout.log" 2> "$outdir/stderr.log"
}

# --- case 1: happy path, 2 records ----------------------------------------
printf '%s\n' '{"title":"A"}' '{"title":"B"}' > "$T/rec1.jsonl"
run_publish "$T/o1" "$T/rec1.jsonl"; rc=$?
check "happy path exits 0" "[ $rc -eq 0 ]"
check "happy path produces 3 messages" '[ "$(wc -l < "$T/o1/produces.jsonl" | tr -d " ")" = "3" ]'
check "data values preserved byte-for-byte" \
  '[ "$(sed -n 1p "$T/o1/produces.jsonl" | python3 -c "import json,sys; print(json.load(sys.stdin)[\"value\"])")" = "{\"title\":\"A\"}" ]'
check "data records carry run_id header" \
  'python3 - "$T/o1/produces.jsonl" <<EOF
import json, sys
recs = [json.loads(l) for l in open(sys.argv[1])]
assert all(r["headers"].get("run_id") == "2026-09-26" for r in recs[:2]), recs
EOF'
check "run-complete is last with both headers" \
  'python3 - "$T/o1/produces.jsonl" <<EOF
import json, sys
recs = [json.loads(l) for l in open(sys.argv[1])]
last = recs[-1]
assert last["value"] == "run-complete", last
assert last["headers"] == {"run_id": "2026-09-26", "run_complete": "true"}, last
EOF'
check "producer config is SASL_SSL/SCRAM + idempotent" \
  'python3 - "$T/o1/conf.json" <<EOF
import json, sys
c = json.load(open(sys.argv[1]))
assert c["bootstrap.servers"] == "redpanda.example:9092", c
assert c["security.protocol"] == "SASL_SSL", c
assert c["sasl.mechanism"] == "SCRAM-SHA-256", c
assert c["acks"] == "all", c
assert c["enable.idempotence"] is True, c
EOF'
check "all messages go to job-leads.raw" \
  'python3 - "$T/o1/produces.jsonl" <<EOF
import json, sys
recs = [json.loads(l) for l in open(sys.argv[1])]
assert all(r["topic"] == "job-leads.raw" for r in recs), recs
EOF'

# --- case 2: zero records -> only run-complete -----------------------------
: > "$T/rec2.jsonl"
run_publish "$T/o2" "$T/rec2.jsonl"; rc=$?
check "zero records exits 0" "[ $rc -eq 0 ]"
check "zero records sends only run-complete" \
  '[ "$(wc -l < "$T/o2/produces.jsonl" | tr -d " ")" = "1" ] && python3 - "$T/o2/produces.jsonl" <<EOF
import json, sys
r = json.loads(open(sys.argv[1]).read())
assert r["value"] == "run-complete", r
assert r["headers"] == {"run_id": "2026-09-26", "run_complete": "true"}, r
EOF'

# --- case 3: delivery failure -> nonzero exit ------------------------------
printf '%s\n' '{"title":"A"}' > "$T/rec3.jsonl"
mkdir -p "$T/o3"
STUB_OUT="$T/o3" STUB_FAIL=1 PYTHONPATH="$T/stub" \
  KAFKA_BOOTSTRAP_SERVERS="x" KAFKA_SASL_USERNAME="u" KAFKA_SASL_PASSWORD="p" \
  RUN_ID="2026-09-26" \
  python3 "$PUBLISH" "$T/rec3.jsonl" > /dev/null 2> "$T/o3/stderr.log"; rc=$?
check "delivery failure exits nonzero" "[ $rc -ne 0 ]"
check "delivery failure is reported" 'grep -q "delivery failed" "$T/o3/stderr.log"'

# --- case 4: missing env -> nonzero exit -----------------------------------
printf '%s\n' '{"title":"A"}' > "$T/rec4.jsonl"
mkdir -p "$T/o4"
STUB_OUT="$T/o4" PYTHONPATH="$T/stub" \
  KAFKA_BOOTSTRAP_SERVERS="x" KAFKA_SASL_USERNAME="u" \
  RUN_ID="2026-09-26" \
  python3 "$PUBLISH" "$T/rec4.jsonl" > /dev/null 2> "$T/o4/stderr.log"; rc=$?
check "missing KAFKA_SASL_PASSWORD exits nonzero" "[ $rc -ne 0 ]"

# --- case 5: invalid JSON line -> nonzero exit, nothing produced ------------
printf '%s\n' '{"title":"A"}' 'not json' > "$T/rec5.jsonl"
run_publish "$T/o5" "$T/rec5.jsonl"; rc=$?
check "invalid JSON exits nonzero" "[ $rc -ne 0 ]"
check "invalid JSON produces nothing" '[ ! -s "$T/o5/produces.jsonl" ]'

# --- case 6: missing records file -> nonzero exit ---------------------------
run_publish "$T/o6" "$T/does-not-exist.jsonl"; rc=$?
check "missing records file exits nonzero" "[ $rc -ne 0 ]"

echo "---"
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
