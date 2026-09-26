#!/usr/bin/env python3
"""Phase 4: publish extracted job-lead records to Redpanda via the Kafka protocol.

Reads records.jsonl (one JSON object per line, produced by extract_records.sh)
and publishes each record to the ``job-leads.raw`` topic with a ``run_id``
header, then sends the run-complete control record (value ``run-complete``,
headers ``run_id`` + ``run_complete=true``). Zero-record runs still send the
control record so the consumer can mark the run COMPLETED.

The wire format replicates the old Upstash REST flow byte-for-byte; the
Spring consumer (JobLeadListener) is unchanged.

Configuration (environment):
  KAFKA_BOOTSTRAP_SERVERS  Redpanda bootstrap, e.g. host:9092
  KAFKA_SASL_USERNAME / KAFKA_SASL_PASSWORD  SCRAM-SHA-256 credentials
  RUN_ID  UTC date (YYYY-MM-DD), stamped on the run_id header

Usage:
  python3 publish_kafka.py records.jsonl

Exit status: 0 when every message was delivered, non-zero otherwise.
"""

import json
import os
import sys

from confluent_kafka import Producer

TOPIC = "job-leads.raw"
RUN_COMPLETE_VALUE = "run-complete"
# Bounded so a dead broker fails the workflow step in about a minute
# instead of hanging until the job timeout.
FLUSH_TIMEOUT_S = 60


def fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def main():
    bootstrap = os.environ.get("KAFKA_BOOTSTRAP_SERVERS")
    username = os.environ.get("KAFKA_SASL_USERNAME")
    password = os.environ.get("KAFKA_SASL_PASSWORD")
    run_id = os.environ.get("RUN_ID")
    for name, value in (
        ("KAFKA_BOOTSTRAP_SERVERS", bootstrap),
        ("KAFKA_SASL_USERNAME", username),
        ("KAFKA_SASL_PASSWORD", password),
        ("RUN_ID", run_id),
    ):
        if not value:
            fail(f"{name} is not set")

    if len(sys.argv) != 2:
        fail(f"usage: {sys.argv[0]} records.jsonl")
    records_path = sys.argv[1]
    if not os.path.isfile(records_path):
        fail(f"records file not found: {records_path}")

    lines = []
    with open(records_path, "rb") as fh:
        for lineno, raw in enumerate(fh, start=1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                json.loads(raw)
            except json.JSONDecodeError as exc:
                fail(f"{records_path}:{lineno}: invalid JSON: {exc}")
            lines.append(raw)

    producer = Producer(
        {
            "bootstrap.servers": bootstrap,
            "security.protocol": "SASL_SSL",
            "sasl.mechanism": "SCRAM-SHA-256",
            "sasl.username": username,
            "sasl.password": password,
            # Defense in depth (the consumer dedupes anyway): no duplicates
            # on retry, and the broker confirms every write.
            "acks": "all",
            "enable.idempotence": True,
        }
    )

    failures = []

    def on_delivery(err, msg):
        if err is not None:
            failures.append(f"delivery failed for {msg.topic()}: {err}")

    run_headers = [("run_id", run_id)]
    for raw in lines:
        producer.produce(TOPIC, value=raw, headers=run_headers, callback=on_delivery)
        producer.poll(0)
    # Flush before the control record: on the single-partition topic the
    # run-complete marker must be consumed after every data record.
    remaining = producer.flush(FLUSH_TIMEOUT_S)
    if remaining:
        failures.append(f"{remaining} data message(s) undelivered after flush")
    if failures:
        fail("; ".join(failures))

    producer.produce(
        TOPIC,
        value=RUN_COMPLETE_VALUE.encode("utf-8"),
        headers=[("run_id", run_id), ("run_complete", "true")],
        callback=on_delivery,
    )
    remaining = producer.flush(FLUSH_TIMEOUT_S)
    if remaining:
        failures.append(f"{remaining} control message(s) undelivered after flush")
    if failures:
        fail("; ".join(failures))

    print(f"published {len(lines)} records + run-complete for run {run_id}")


if __name__ == "__main__":
    main()
