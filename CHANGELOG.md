# Changelog

## Unreleased

### Added
- **Real NCDEX data out of the box.** `src/main/resources/seed/` ships 4,121 daily TMCFGRNZM futures rows
  (34 expiries, 2022-01-03 → 2026-11-11) and 232 spot rows (Farmer Polished from the bhavcopy underlying since
  2025-11-10, plus polls for all three centres since 2026-10-08), fetched from ncdex.com on 2026-11-12.
  `SeedDataLoader` loads them on the default profile when the price table is empty (`SPOT_SEED_ENABLED`).
- `NcdexFuturesHistorySource` and `NcdexSpotHistorySource` (session cookie + CSRF handshake) and
  `POST /api/v1/ingest/backfill?from=&to=`.
- Startup ingest on a background thread: backfill the last `SPOT_STARTUP_BACKFILL_DAYS`, then run the live
  sources (`SPOT_INGEST_ON_STARTUP`).
- `GET /api/v1/prices/status` and `GET /api/v1/prices/export.csv`.
- Dashboard: defaults to the full stored range, date pickers and presets, contract selector (near month or any
  expiry) driving the spot-vs-futures and basis charts, paged table of every price with CSV download,
  last-updated stamp, and an empty state with an *Ingest from NCDEX* button.

### Fixed
- Delivery-adjusted spreads applied today's premium/discount matrix to the whole history window, so a matrix
  revision silently rewrote past adjusted spreads. Each day now uses the matrix published on or before it
  (`DeliveryMatrixHistory`); days before the first stored matrix fall back to the earliest one.
- The LTP and change in the NCDEX product page header were parsed and then thrown away. They are now stored
  per market date in `product_quote` (Flyway `V4`, last scrape of the day wins) and returned as `lastTraded`
  by `GET /api/v1/contracts`.
- The default profile showed an empty dashboard until the first scheduled run.
- `DemoProfileIntegrationTest` used the system clock, so its near-month assertions would break after the
  18 Dec 2026 expiry; it now runs on a fixed `Clock`.
- **NCDEX product page source** (`https://www.ncdex.com/products/TMCFGRNZM`): contract calendar, daily
  margins per expiry and the premium/discount matrix, stored in `contract_expiry`, `daily_margin` and
  `premium_discount` (Flyway `V3`), upserted per date/expiry/location. Runs before the price sources in
  every ingestion. Toggle with `SPOT_NCDEX_PRODUCT_ENABLED`.
- **Delivery-adjusted spreads.** Each spot quote is converted to basis-quality equivalent
  (`price / (1 + premium% / 100)`: Rajapore Sangli ÷ 1.04, Unpolished Nizamabad ÷ 0.96, Salem at Erode ÷ 1.02)
  before it is compared. Spread points now carry `adjustmentPct`, `adjustedSpread` and `adjustedSpreadPct`
  alongside the raw values, and `WIDE_SPREAD` is judged on the adjusted value.
- New spread kind `DELIVERY`: near-month futures vs each NCDEX-polled deliverable grade.
- **Margin alert** `MARGIN_CHANGE` when near-month total margin moves more than `SPOT_MARGIN_ALERT_POINTS`
  (default 2) percentage points from the previous margin date.
- `GET /api/v1/contracts` and `GET /api/v1/premium-discount`.
- Dashboard: daily margin table, premium/discount matrix for the near month, adjusted vs raw delivery-basis
  lines, an adjusted column for mandis and a link to the NCDEX product page.

### Changed
- The near-month contract comes from the scraped expiry calendar and rolls `SPOT_ROLL_DAYS` (default 5)
  calendar days before expiry, instead of always taking the earliest contract quoted. Contracts the calendar
  has never seen keep the old behaviour.
- Alerts use their own `AlertKind` (`BASIS`, `DELIVERY`, `MANDI`, `MARGIN`) and `AlertFlag`
  (`WIDE_SPREAD`, `SHARP_MOVE`, `MARGIN_CHANGE`). Stored values are unchanged for existing alerts.
- For `WIDE_SPREAD` alerts, `spreadPct` is the delivery-adjusted spread when one exists; for `MARGIN`
  alerts it is the TM change in points.

## 0.1.0 — 2026-11-11

- First release: NCDEX spot and futures, Agmarknet mandi prices, basis and mandi spreads, deduplicated
  alerts (log, Telegram, webhook), REST API, Chart.js dashboard, demo profile, Docker and CI.
