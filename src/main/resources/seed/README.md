# Seed data: real NCDEX turmeric prices

Loaded by `SeedDataLoader` on the default profile when `price_snapshot` is empty (`SPOT_SEED_ENABLED=false` to skip).
All values were downloaded from ncdex.com on **2026-11-12** and are unmodified apart from removing thousands
separators and converting dates to ISO. Nothing here is synthetic.

| File | Source | Range | Rows |
|---|---|---|---|
| `ncdex-turmeric-futures.csv` | Futures Prices (`/markets/futureprices` → `POST /futureprices/get_data`), TMCFGRNZM, all 34 listed expiries | 2022-01-03 → 2026-11-11 | 4,121 |
| `ncdex-turmeric-spot.csv` | Polled spot (`POST /spotprices/price_graph`) for TMCFGRNZM, TMCFUPNZM, TMCRJPSGL (only ~30 days back are served) | 2026-10-08 → 2026-11-11 | 56 |
| `ncdex-turmeric-spot.csv` | `UndrlygPric` column of the daily final bhavcopy (`/markets/bhavcopy`) for TMCFGRNZM (Nizamabad, Farmer Polished) | 2025-11-10 → 2026-11-09 | 176 |

Notes
- Prices are ₹/quintal; futures volume and open interest are in MT (kept in the CSV, not yet stored in the DB).
- Futures rows with a zero close are skipped on load. `low`/`high` become the snapshot range.
- The bhavcopy underlying is the exchange's last polled spot, so it repeats on days NCDEX did not poll
  (e.g. 2026-11-04 → 2026-11-10 all read 24,954.10). Where a polled value exists for a date it wins.
- About 4 weekdays in the range returned no bhavcopy (exchange holidays).
- Regenerate: run the backfill endpoint against a live instance and export `/api/v1/prices/export.csv`, or the
  curl script described in the main README.
