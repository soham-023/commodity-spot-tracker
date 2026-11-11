# Changelog

## Unreleased

### Added
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
