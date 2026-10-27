# Ingestion Service

## Purpose

Ingestion loads the legacy `hubs-global.csv` file, converts CSV rows into
`Hub` objects, normalizes values, removes duplicate hub records and exposes
the cleaned dataset through REST.

This is the first stage of the LogisticsConnect integration flow.

## Source dataset

The supplied CSV contains four columns:

- `hub_id`
- `Province`
- `sorting_center`
- `active`

The current dataset includes casing differences, whitespace variations,
duplicate sorting-centre records, different boolean spellings and placeholder
values.

The supplied CSV does not include date or numeric columns.

## Cleaning rules

- Hub IDs are trimmed and normalized to uppercase.
- Province names use canonical casing and recognize known KwaZulu-Natal
  spelling variations.
- Text is trimmed and repeated whitespace is collapsed.
- Known placeholders such as `N/A`, `TBD`, `unknown`, `-` and `NaN` become
  `null`.
- Boolean values such as `Y`, `yes`, `1` and `true` become `true`.
- Boolean values such as `N`, `no`, `0` and `false` become `false`.
- Unknown boolean values become `null`.
- Records without a usable hub ID are excluded from the returned dataset.
- Duplicates are resolved using normalized province and sorting-centre names.
  The smallest normalized hub ID wins the canonical-record selection.

Incomplete identity fields are not used to guess whether two records describe
the same place.

## Endpoint

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Returns plain-text `OK`. |
| GET | `/hubs` | Returns the cleaned hub dataset as a JSON array. |

Default port: `7050`.

## Build and test

From the repository root:

```bash
mvn -f ingestion-service/pom.xml package
mvn -f ingestion-service/pom.xml test
```

Check the endpoints while the service is running:

```bash
curl http://localhost:7050/health
curl http://localhost:7050/hubs
```

## Limitations

The hub dataset is held in memory after the service loads the CSV. The
current dataset has no date or numeric fields, so date normalization and
numeric-column validation do not apply to its current schema.