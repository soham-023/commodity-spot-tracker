# Test fixtures

| File | Provenance |
| --- | --- |
| `ncdex-live-spot-turmeric.html` | **Recorded** from `https://www.ncdex.com/markets/livespot` (turmeric products) on 2026-11-11 IST, trimmed to the price table. |
| `ncdex-live-quotes.html` | **Recorded** from `https://www.ncdex.com/market-watch/live_quotes` on 2026-11-11 IST, trimmed to the price table. |
| `ncdex-product-turmeric.html` | **Recorded** from `https://www.ncdex.com/products/TMCFGRNZM` on 2026-11-11 IST (margin date 11-Nov-26, Morning file), trimmed to the LTP header, the expiry selectors, the *Daily margin information* table and the *Premium Discount Matrix* table. A copy is in `src/main/resources/demo/` for the demo profile. |
| `datagov-mandi-turmeric.json` | **Synthetic.** Follows the exact response schema of data.gov.in resource `9ef84268-d588-465a-a308-a864a43d0070` (field ids, `dd/MM/yyyy` dates, numeric *or* string prices), but the prices are illustrative because the API could not be reached from the build sandbox. Replace it with a real recording once you have a key: `curl "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070?api-key=$DATA_GOV_API_KEY&format=json&limit=1000&filters[commodity]=Turmeric"`. |

## History endpoints (recorded 2026-11-12)

- `ncdex-futureprices-expiries.json`: `POST /futureprices/filters` (select=symbol, value=TMCFGRNZM, dependent=expiry_date).
- `ncdex-futureprices-TMCFGRNZM-18-Dec-2026.json`: first 5 rows of `POST /futureprices/get_data` for the Dec-2026 contract.
- `ncdex-spot-price-graph-TMCFGRNZM.json`: `POST /spotprices/price_graph` (ftype=1, 2026-09-01 → 2026-11-12).
