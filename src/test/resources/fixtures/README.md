# Test fixtures

| File | Provenance |
| --- | --- |
| `ncdex-live-spot-turmeric.html` | **Recorded** from `https://www.ncdex.com/markets/livespot` (turmeric products) on 2026-11-11 IST, trimmed to the price table. |
| `ncdex-live-quotes.html` | **Recorded** from `https://www.ncdex.com/market-watch/live_quotes` on 2026-11-11 IST, trimmed to the price table. |
| `datagov-mandi-turmeric.json` | **Synthetic.** Follows the exact response schema of data.gov.in resource `9ef84268-d588-465a-a308-a864a43d0070` (field ids, `dd/MM/yyyy` dates, numeric *or* string prices), but the prices are illustrative because the API could not be reached from the build sandbox. Replace it with a real recording once you have a key: `curl "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070?api-key=$DATA_GOV_API_KEY&format=json&limit=1000&filters[commodity]=Turmeric"`. |
