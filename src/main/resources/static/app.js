'use strict';

const API = 'api/v1';
const COMMODITY = 'TURMERIC';
const fmt = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const rupee = v => v == null ? '–' : '₹' + fmt.format(v);
const pct = v => v == null ? '–' : (v > 0 ? '+' : '') + fmt.format(v) + '%';
const MANDI_COLORS = ['#2f7d4f', '#7a4fb3', '#b3412f', '#2f6db3', '#8a6d3b', '#c2577a', '#4f8f8f'];
let priceChart, basisChart, status = null, tableRows = [], tablePage = 0;
const PAGE = 100;
const $ = id => document.getElementById(id);

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.detail || res.statusText);
  }
  return res.json();
}

async function allPrices(from, to) {
  const rows = [];
  for (let page = 0; page < 40; page++) {
    const p = await getJson(`${API}/prices?commodity=${COMMODITY}&from=${from}&to=${to}&size=500&page=${page}`);
    rows.push(...p.content);
    if (page + 1 >= p.totalPages) break;
  }
  return rows;
}

function isoDaysAgo(days, end) {
  const d = end ? new Date(end + 'T00:00:00Z') : new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

function series(rows, basis, contract) {
  const spot = [], nearFut = new Map(), mandis = new Map();
  const basisMonth = new Map(basis.map(p => [p.date, p.label]));
  for (const r of rows) {
    if (r.source === 'NCDEX' && r.priceType === 'SPOT' && r.market === 'Nizamabad' && r.variety === 'Farmer Polished') {
      spot.push({ x: r.date, y: r.price });
    } else if (r.source === 'NCDEX' && r.priceType === 'FUTURES' && contract !== 'near') {
      if (r.contractMonth === contract) nearFut.set(r.date, r);
    } else if (r.source === 'NCDEX' && r.priceType === 'FUTURES') {
      const chosen = basisMonth.get(r.date);
      const cur = nearFut.get(r.date);
      if (chosen ? r.contractMonth === chosen : (!cur || r.contractMonth < cur.contractMonth)) nearFut.set(r.date, r);
    } else if (r.source === 'AGMARKNET') {
      if (!mandis.has(r.market)) mandis.set(r.market, new Map());
      const byDate = mandis.get(r.market);
      byDate.set(r.date, [...(byDate.get(r.date) || []), r.price]);
    }
  }
  const byX = (a, b) => a.x.localeCompare(b.x);
  const futures = [...nearFut.values()].map(r => ({ x: r.date, y: r.price, c: r.contractMonth })).sort(byX);
  const mandiSeries = [...mandis.entries()].sort().map(([market, byDate]) => ({
    market,
    data: [...byDate.entries()].map(([x, ps]) => ({ x, y: ps.reduce((a, b) => a + b, 0) / ps.length })).sort(byX)
  }));
  return { spot: spot.sort(byX), futures, mandiSeries };
}

function timeUnit() {
  const days = (new Date($('to').value) - new Date($('from').value)) / 864e5;
  return days > 540 ? 'quarter' : days > 150 ? 'month' : 'week';
}

/** Basis for a chosen contract: futures close − latest Farmer Polished Nizamabad spot polled within 7 days. */
function clientBasis(s, contract) {
  const out = [];
  let i = 0, last = null;
  for (const f of s.futures) {
    while (i < s.spot.length && s.spot[i].x <= f.x) last = s.spot[i++];
    if (!last || (new Date(f.x) - new Date(last.x)) / 864e5 > 7) continue;
    const spread = f.y - last.y;
    out.push({ date: f.x, spreadPct: Math.round(spread / last.y * 10000) / 100, spread, flags: [], kind: 'BASIS',
      label: `TMCFGRNZM ${contract}`, comparedPrice: f.y, spotPrice: last.y, spotDate: last.x });
  }
  return out;
}

function drawPriceChart(s, contract) {
  const datasets = [
    { label: 'NCDEX spot (Nizamabad, FP)', data: s.spot, borderColor: '#d99a00', backgroundColor: '#d99a00', borderWidth: 3, stepped: 'before' },
    { label: contract === 'near' ? 'NCDEX futures (near month)' : `NCDEX futures ${contract}`, data: s.futures, borderColor: '#2b2620', backgroundColor: '#2b2620', borderWidth: 2, borderDash: [6, 3] },
    ...s.mandiSeries.map((m, i) => ({
      label: `${m.market} mandi`, data: m.data, borderColor: MANDI_COLORS[i % MANDI_COLORS.length],
      backgroundColor: MANDI_COLORS[i % MANDI_COLORS.length], borderWidth: 1.3, pointRadius: 0, hidden: i > 3
    }))
  ];
  priceChart?.destroy();
  priceChart = new Chart(document.getElementById('priceChart'), {
    type: 'line',
    data: { datasets },
    options: {
      maintainAspectRatio: false, parsing: true, pointRadius: 0, interaction: { mode: 'nearest', axis: 'x', intersect: false },
      scales: { x: { type: 'time', time: { unit: timeUnit() } }, y: { ticks: { callback: v => '₹' + fmt.format(v) } } },
      plugins: { tooltip: { callbacks: { label: c => `${c.dataset.label}: ${rupee(c.parsed.y)}` } }, legend: { labels: { boxWidth: 12 } } },
      elements: { point: { radius: 0 } }
    }
  });
}

function drawBasisChart(points, delivery) {
  const data = points.map(p => ({ x: p.date, y: p.spreadPct, p }));
  const grades = new Map();
  for (const p of delivery) {
    if (!grades.has(p.label)) grades.set(p.label, []);
    grades.get(p.label).push(p);
  }
  const lines = [...grades.entries()].flatMap(([label, ps], i) => {
    const color = MANDI_COLORS[(i + 1) % MANDI_COLORS.length];
    return [
      { type: 'line', label: `${label} adjusted`, data: ps.map(p => ({ x: p.date, y: p.adjustedSpreadPct, p })),
        borderColor: color, backgroundColor: color, borderWidth: 2, pointRadius: 2 },
      { type: 'line', label: `${label} raw`, data: ps.map(p => ({ x: p.date, y: p.spreadPct, p })),
        borderColor: color, backgroundColor: color, borderWidth: 1, borderDash: [4, 3], pointRadius: 0 }
    ];
  });
  basisChart?.destroy();
  basisChart = new Chart(document.getElementById('basisChart'), {
    type: 'bar',
    data: { datasets: [{
      label: 'Basis % of spot (FP Nizamabad)', data,
      backgroundColor: data.map(d => d.p.flags.length ? '#b3412f' : '#d99a00')
    }, ...lines] },
    options: {
      maintainAspectRatio: false,
      scales: { x: { type: 'time', time: { unit: timeUnit() } }, y: { ticks: { callback: v => v + '%' } } },
      plugins: {
        legend: { display: lines.length > 0, labels: { boxWidth: 10 } },
        tooltip: { callbacks: { label: c => {
          const p = c.raw.p;
          const adj = p.adjustedSpreadPct != null && p.kind === 'DELIVERY'
            ? [`adjusted ${pct(p.adjustedSpreadPct)} (${rupee(p.adjustedSpread)}) for ${pct(p.adjustmentPct)} premium/discount`] : [];
          return [`raw ${pct(p.spreadPct)} (${rupee(p.spread)})`, ...adj, `${p.label} · futures ${rupee(p.comparedPrice)}`,
            `spot ${rupee(p.spotPrice)} polled ${p.spotDate}`];
        } } }
      }
    }
  });
}

function renderSummary(sum) {
  const basis = sum.spreads.find(p => p.kind === 'BASIS');
  document.getElementById('spot').textContent = rupee(sum.ncdexSpot?.price);
  document.getElementById('spotMeta').textContent = sum.ncdexSpot
    ? `${sum.ncdexSpot.variety}, ${sum.ncdexSpot.market} · polled ${sum.ncdexSpot.date}` : 'no spot poll';
  document.getElementById('fut').textContent = rupee(sum.nearMonthFutures?.price);
  document.getElementById('futMeta').textContent = sum.nearMonthFutures
    ? `TMCFGRNZM ${sum.nearMonthFutures.contractMonth} · ${sum.nearMonthFutures.date}` : '';
  const basisEl = document.getElementById('basis');
  basisEl.textContent = basis ? pct(basis.spreadPct) : '–';
  basisEl.className = 'value ' + (basis && basis.flags.length ? 'neg' : '');
  document.getElementById('basisMeta').textContent = basis ? `${rupee(basis.spread)} per quintal` : 'needs spot + futures';
  const markets = new Set(sum.mandis.map(m => m.market));
  document.getElementById('mandiCount').textContent = markets.size;
  document.getElementById('mandiMeta').textContent = [...markets].join(', ') || 'set DATA_GOV_API_KEY';

  const spreadsByMarket = new Map(sum.spreads.filter(p => p.kind === 'MANDI').map(p => [p.label, p]));
  const tbody = document.querySelector('#mandiTable tbody');
  tbody.innerHTML = '';
  for (const m of sum.mandis) {
    const sp = spreadsByMarket.get(m.market);
    const tr = document.createElement('tr');
    const cls = sp ? (sp.spreadPct >= 0 ? 'pos' : 'neg') + (sp.flags.length ? ' flag' : '') : '';
    const adj = sp && sp.adjustedSpreadPct != null ? `${pct(sp.adjustedSpreadPct)} <span class="muted">(${pct(sp.adjustmentPct)})</span>` : '–';
    tr.innerHTML = `<td></td><td></td><td>${m.date}</td><td class="r">${rupee(m.price)}</td><td class="r ${cls}">${sp ? pct(sp.spreadPct) : '–'}</td><td class="r">${adj}</td>`;
    tr.children[0].textContent = m.market;
    tr.children[1].textContent = m.variety || '';
    tbody.appendChild(tr);
  }
  if (!sum.mandis.length) tbody.innerHTML = '<tr><td colspan="6" class="muted">No mandi prices yet.</td></tr>';
}

const num = v => v == null ? '–' : fmt.format(v);

function renderContracts(c) {
  const tbody = document.querySelector('#marginTable tbody');
  if (!c) {
    tbody.innerHTML = '<tr><td colspan="7" class="muted">No contract data yet (NCDEX product page not scraped).</td></tr>';
    return;
  }
  document.getElementById('productLink').href = c.productUrl;
  document.getElementById('marginDate').textContent = c.marginDate ? `· ${c.marginDate}` : '';
  tbody.innerHTML = '';
  for (const k of c.contracts) {
    const m = k.margin || {};
    const tr = document.createElement('tr');
    if (k.nearMonth) tr.className = 'near';
    const d = k.totalMarginChange;
    tr.innerHTML = `<td>${k.expiry}${k.inRollWindow ? ' <span class="muted">(rolling)</span>' : ''}</td>
      <td class="r">${k.daysToExpiry}</td><td class="r">${num(m.initial)}</td><td class="r">${num(m.extremeLoss)}</td>
      <td class="r">${num(m.additionalLong)}/${num(m.additionalShort)}</td><td class="r"><b>${num(m.total)}</b></td>
      <td class="r ${d > 0 ? 'neg' : d < 0 ? 'pos' : ''}">${d == null ? '–' : (d > 0 ? '+' : '') + num(d)}</td>`;
    tbody.appendChild(tr);
  }
}

function renderPremiumDiscount(pd, contracts) {
  const tbody = document.querySelector('#pdTable tbody');
  if (!pd) {
    tbody.innerHTML = '<tr><td colspan="3" class="muted">No premium/discount matrix yet.</td></tr>';
    return;
  }
  const near = contracts?.nearMonth;
  const rows = pd.rows.filter(r => !near || r.expiry === near);
  document.getElementById('pdAsOf').textContent = `· ${pd.asOf}${near ? ' · expiry ' + near : ''}`;
  document.getElementById('pdBasis').textContent = `Basis: ${pd.basis}. A spot quote ÷ the factor = its basis-quality equivalent.`;
  tbody.innerHTML = '';
  for (const r of (rows.length ? rows : pd.rows)) {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td></td><td class="r">${r.basis ? 'basis' : pct(r.premiumPct)}</td><td class="r">${r.divisor}</td>`;
    tr.children[0].textContent = r.location;
    tbody.appendChild(tr);
  }
}

function renderAlerts(page) {
  const ul = document.getElementById('alerts');
  ul.innerHTML = '';
  if (!page.content.length) {
    ul.innerHTML = '<li class="muted">No alerts in this window.</li>';
    return;
  }
  for (const a of page.content) {
    const li = document.createElement('li');
    const tag = document.createElement('span');
    tag.className = 'tag ' + a.flag;
    tag.textContent = a.flag.replace('_', ' ');
    li.append(tag, document.createTextNode(a.message));
    ul.appendChild(li);
  }
}

function renderStatus() {
  const empty = !status || status.rows === 0;
  $('empty').hidden = !empty;
  $('stamp').textContent = empty ? '' : `${fmt.format(status.rows)} prices from ${status.firstDate} to ${status.lastDate} · last updated `
    + new Date(status.lastUpdated).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'short' }) + ' IST';
  const sel = $('contract');
  const cur = sel.value;
  sel.innerHTML = '<option value="near">Near month</option>' + (status?.contractMonths || []).slice().reverse()
    .map(m => `<option value="${m}">${m}</option>`).join('');
  sel.value = [...sel.options].some(o => o.value === cur) ? cur : 'near';
}

function applyPreset() {
  const preset = $('preset').value;
  if (preset === 'custom' || !status?.lastDate) return;
  $('to').value = status.lastDate;
  $('from').value = preset === 'all' ? status.firstDate : isoDaysAgo(Number(preset), status.lastDate);
}

function renderTable() {
  const type = $('typeFilter').value;
  const contract = $('contract').value;
  const rows = tableRows.filter(r => (!type || r.priceType === type)
    && (contract === 'near' || r.priceType === 'SPOT' || r.contractMonth === contract));
  const pages = Math.max(1, Math.ceil(rows.length / PAGE));
  tablePage = Math.min(tablePage, pages - 1);
  $('tableCount').textContent = `· ${fmt.format(rows.length)} rows`;
  $('pageInfo').textContent = `page ${tablePage + 1} / ${pages}`;
  $('prevPage').disabled = tablePage === 0;
  $('nextPage').disabled = tablePage >= pages - 1;
  const params = new URLSearchParams({ commodity: COMMODITY, from: $('from').value, to: $('to').value });
  if (type) params.set('type', type);
  if (contract !== 'near' && type === 'FUTURES') params.set('contract', contract);
  $('exportLink').href = `${API}/prices/export.csv?${params}`;
  const tbody = document.querySelector('#priceTable tbody');
  tbody.innerHTML = '';
  for (const r of rows.slice(tablePage * PAGE, (tablePage + 1) * PAGE)) {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${r.date}</td><td>${r.priceType}</td><td></td><td></td><td></td>
      <td class="r">${num(r.price)}</td><td class="r">${num(r.minPrice)}</td><td class="r">${num(r.maxPrice)}</td>`;
    tr.children[2].textContent = r.source;
    tr.children[3].textContent = r.market;
    tr.children[4].textContent = r.priceType === 'FUTURES' ? `TMCFGRNZM ${r.contractMonth}` : (r.variety || '');
    tbody.appendChild(tr);
  }
  if (!rows.length) tbody.innerHTML = '<tr><td colspan="8" class="muted">No prices in this range.</td></tr>';
}

async function ingest() {
  const btn = $('ingestBtn');
  btn.disabled = true;
  $('ingestMsg').textContent = 'Fetching from NCDEX… this can take a minute.';
  try {
    const post = url => fetch(url, { method: 'POST' }).then(r => r.json());
    const b = await post(`${API}/ingest/backfill?from=2022-01-01`);
    const r = await post(`${API}/ingest/run`);
    $('ingestMsg').textContent = [...b.sources, ...r.sources]
      .map(s => `${s.source}: ${s.status}${s.status === 'OK' ? ` (${s.fetched} rows)` : s.error ? ` (${s.error})` : ''}`).join(' · ');
    await load(true);
  } catch (e) {
    $('ingestMsg').textContent = 'Ingest failed: ' + e.message;
  } finally {
    btn.disabled = false;
  }
}

async function load(refreshStatus = true) {
  try {
    if (refreshStatus || !status) {
      status = await getJson(`${API}/prices/status?commodity=${COMMODITY}`);
      renderStatus();
      applyPreset();
    }
    if (!$('from').value) { $('to').value = new Date().toISOString().slice(0, 10); $('from').value = isoDaysAgo(90); }
    const from = $('from').value, to = $('to').value;
    const contract = $('contract').value;
    const [rows, basis, delivery, sum, alerts, contracts, pd] = await Promise.all([
      allPrices(from, to),
      getJson(`${API}/spreads?commodity=${COMMODITY}&kind=BASIS&from=${from}&to=${to}`),
      getJson(`${API}/spreads?commodity=${COMMODITY}&kind=DELIVERY&from=${from}&to=${to}`),
      getJson(`${API}/summary?commodity=${COMMODITY}`).catch(() => null),
      getJson(`${API}/alerts?from=${from}&size=20`),
      getJson(`${API}/contracts`).catch(() => null),
      getJson(`${API}/premium-discount`).catch(() => null)
    ]);
    const s = series(rows, basis, contract);
    drawPriceChart(s, contract);
    drawBasisChart(contract === 'near' ? basis : clientBasis(s, contract), contract === 'near' ? delivery : []);
    tableRows = rows;
    tablePage = 0;
    renderTable();
    if (sum) renderSummary(sum);
    renderAlerts(alerts);
    renderContracts(contracts);
    renderPremiumDiscount(pd, contracts);
    document.getElementById('foot').textContent =
      `${rows.length} prices loaded for ${from} → ${to} · Sources: NCDEX (spot polls, futures, product page margins & premium/discount matrix), Agmarknet via data.gov.in (APMC modal prices).`;
  } catch (e) {
    document.getElementById('foot').textContent = 'Could not load data: ' + e.message;
  }
}

$('preset').addEventListener('change', () => { applyPreset(); load(false); });
for (const id of ['from', 'to']) $(id).addEventListener('change', () => { $('preset').value = 'custom'; load(false); });
$('contract').addEventListener('change', () => load(false));
$('typeFilter').addEventListener('change', () => { tablePage = 0; renderTable(); });
$('prevPage').addEventListener('click', () => { tablePage--; renderTable(); });
$('nextPage').addEventListener('click', () => { tablePage++; renderTable(); });
$('ingestBtn').addEventListener('click', ingest);
window.addEventListener('DOMContentLoaded', () => load(true));
