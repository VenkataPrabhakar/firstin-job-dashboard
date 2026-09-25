# Ledgers — sync contract for the daily pipeline

The morning job-search agents write one JSON file per agent into this
directory each morning, before the `daily-ingest.yml` workflow runs
(13:05 UTC). Phase 3 ships this contract, not the sync itself — the sync
automation (agents committing these files) is a separate, owner-approved
change. Until it exists, the pipeline runs on whatever ledgers are present
(an empty directory is a valid, successful run with zero records).

## File format

Each file is shaped:

```json
{
  "agent": "contract-c2c",
  "records": [
    {
      "reported": true,
      "title": "Senior Java Developer",
      "company": "Acme Corp",
      "location": "Austin, TX",
      "engagement": "C2C",
      "pay": "$75-85/hr",
      "source": "Dice",
      "url": "https://example.com/jobs/acme-java",
      "posted": "2 hours ago"
    }
  ]
}
```

Rules the extraction script enforces (`.github/scripts/extract_records.sh`):

- A record is kept only if it is an object with `reported: true` and
  non-blank `title`, `company`, `location`, and `engagement`.
- Malformed files are logged and skipped; they never fail the run.
- Duplicates (same normalized `title|company|location`) across files are
  emitted once; the backend additionally dedupes by stable posting id.

## Record schema

| Field | Required | Notes |
|---|---|---|
| `reported` | yes | `true` = agent vouches the lead is real; anything else is skipped |
| `title` | yes | job title |
| `company` | yes | hiring company or staffing firm |
| `location` | yes | e.g. `Austin, TX` or `Remote` |
| `engagement` | yes | `C2C`, `W2`, `1099`, `FULL_TIME`… (normalized by the backend) |
| `pay` | no | raw pay string, e.g. `$75-85/hr` |
| `source` | no | where the lead was found (`Dice`, `LinkedIn`…) |
| `url` | no | posting URL |
| `posted` | no | raw posted-age string, e.g. `2 hours ago` |

See `.github/fixtures/ledgers/` for annotated examples.
