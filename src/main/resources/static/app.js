'use strict';

const API = 'api/v1';
const COMMODITY = 'TURMERIC';
const fmt = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const rupee = v => v == null ? '–' : '₹' + fmt.format(v);
const pct = v => v == null ? '–' : (v > 0 ? '+' : '') + fmt.format(v) + '%';
const MANDI_COLORS = ['#2f7d4f', '#7a4fb3', '#b3412f', '#2f6db3', '#8a6d3b', '#c2577a', '#4f8f8f'];
let priceChart, basisChart;

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.detail || res.statusText);
  }
  return res.json();
}

async function allPrices(from) {
  const rows = [];
  for (let page = 0; page < 20; page++) {
    const p = await getJson(`${API}/prices?commodity=${COMMODITY}&from=${from}&size=500&page=${page}`);
    rows.push(...p.content);
    if (page + 1 >= p.totalPages) break;
  }
  return rows;
}

function isoDaysAgo(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

function series(rows) {
  const spot = [], nearFut = new Map(), mandis = new Map();
  for (const r of rows) {
    if (r.source === 'NCDEX' && r.priceType === 'SPOT' && r.market === 'Nizamabad' && r.variety === 'Farmer Polished') {
      spot.push({ x: r.date, y: r.price });
    } else if (r.source === 'NCDEX' && r.priceType === 'FUTURES') {
      const cur = nearFut.get(r.date);
      if (!cur || r.contractMonth < cur.contractMonth) nearFut.set(r.date, r);
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

function drawPriceChart(s) {
  const datasets = [
    { label: 'NCDEX spot (Nizamabad, FP)', data: s.spot, borderColor: '#d99a00', backgroundColor: '#d99a00', borderWidth: 3, stepped: 'before' },
    { label: 'NCDEX futures (near month)', data: s.futures, borderColor: '#2b2620', backgroundColor: '#2b2620', borderWidth: 2, borderDash: [6, 3] },
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
      scales: { x: { type: 'time', time: { unit: 'week' } }, y: { ticks: { callback: v => '₹' + fmt.format(v) } } },
      plugins: { tooltip: { callbacks: { label: c => `${c.dataset.label}: ${rupee(c.parsed.y)}` } }, legend: { labels: { boxWidth: 12 } } },
      elements: { point: { radius: 0 } }
    }
  });
}

function drawBasisChart(points) {
  const data = points.map(p => ({ x: p.date, y: p.spreadPct, p }));
  basisChart?.destroy();
  basisChart = new Chart(document.getElementById('basisChart'), {
    type: 'bar',
    data: { datasets: [{
      label: 'Basis % of spot', data,
      backgroundColor: data.map(d => d.p.flags.length ? '#b3412f' : '#d99a00')
    }] },
    options: {
      maintainAspectRatio: false,
      scales: { x: { type: 'time', time: { unit: 'week' } }, y: { ticks: { callback: v => v + '%' } } },
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: c => {
          const p = c.raw.p;
          return [`${pct(p.spreadPct)} (${rupee(p.spread)})`, `${p.label} futures ${rupee(p.comparedPrice)}`,
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
    tr.innerHTML = `<td></td><td></td><td>${m.date}</td><td class="r">${rupee(m.price)}</td><td class="r ${cls}">${sp ? pct(sp.spreadPct) : '–'}</td>`;
    tr.children[0].textContent = m.market;
    tr.children[1].textContent = m.variety || '';
    tbody.appendChild(tr);
  }
  if (!sum.mandis.length) tbody.innerHTML = '<tr><td colspan="5" class="muted">No mandi prices yet.</td></tr>';
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

async function load() {
  const days = Number(document.getElementById('window').value);
  const from = isoDaysAgo(days);
  try {
    const [rows, basis, sum, alerts] = await Promise.all([
      allPrices(from),
      getJson(`${API}/spreads?commodity=${COMMODITY}&kind=BASIS&from=${from}`),
      getJson(`${API}/summary?commodity=${COMMODITY}`).catch(() => null),
      getJson(`${API}/alerts?from=${from}&size=20`)
    ]);
    drawPriceChart(series(rows));
    drawBasisChart(basis);
    if (sum) renderSummary(sum);
    renderAlerts(alerts);
    document.getElementById('foot').textContent =
      `${rows.length} prices loaded · last data ${sum ? sum.asOf : 'n/a'} · Sources: NCDEX (spot polls & futures), Agmarknet via data.gov.in (APMC modal prices).`;
  } catch (e) {
    document.getElementById('foot').textContent = 'Could not load data: ' + e.message;
  }
}

document.getElementById('window').addEventListener('change', load);
window.addEventListener('DOMContentLoaded', load);
