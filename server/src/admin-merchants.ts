import { Hono } from 'hono'
import { html, raw } from 'hono/html'
import QRCode from 'qrcode'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { buildQris } from './qris.ts'

type Env = { Variables: { officer: Officer } }

const CATEGORIES: Record<string, { label: string; mcc: string }> = {
  food: { label: 'Food and drink', mcc: '5812' }, groceries: { label: 'Groceries', mcc: '5411' }, shopping: { label: 'Shopping', mcc: '5651' },
  health: { label: 'Health', mcc: '5912' }, transport: { label: 'Transport and parking', mcc: '7523' }, leisure: { label: 'Leisure', mcc: '7997' },
}

// Story S1.17: made-up QRIS merchants and their codes.
export function merchantRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'operator'))

  app.get('/', async c => {
    const rows = await sql`
      select m.*, (select count(*)::int from journal_entries j where j.memo->>'merchantId' = m.id::text) as payments
      from merchants m order by m.active desc, m.name`
    return c.html(shell(c.get('officer'), '/admin/merchants', 'Merchants', html`
<div class="toolbar"><p class="hint" style="margin:0">Testers scan these codes with ORVYN. Open one to show it full screen or download it.</p>
<a class="btn" href="/admin/merchants/new">New merchant</a></div>
<div class="tablewrap"><table><thead><tr><th>Name</th><th>City</th><th>Category</th><th class="num">Payments</th><th>Status</th></tr></thead>
<tbody>${rows.map(m => html`<tr><td><a href="/admin/merchants/${m.id}">${m.name}</a></td><td>${m.city}</td>
<td>${CATEGORIES[m.category]?.label ?? m.category}</td><td class="num">${m.payments}</td>
<td>${m.active ? html`<span class="chip ok">Active</span>` : html`<span class="chip bad">Paused</span>`}</td></tr>`)}</tbody></table></div>`))
  })

  const form = (o: Officer, error?: string) => shell(o, '/admin/merchants', 'New merchant', html`<form method="post" class="formgrid">
${error ? html`<div class="err" role="alert">${error}</div>` : ''}
<label>Name<input name="name" maxlength="25" required placeholder="Kedai Kopi Lima"></label>
<p class="hint">Up to 25 characters. This is what customers see after scanning.</p>
<label>City<input name="city" maxlength="15" required placeholder="Jakarta Selatan"></label>
<label>Category<select name="category" style="height:48px;border-radius:10px;padding:0 10px;font:inherit">
${Object.entries(CATEGORIES).map(([k, v]) => html`<option value="${k}">${v.label}</option>`)}</select></label>
<button>Create merchant</button></form>`)

  app.get('/new', c => c.html(form(c.get('officer'))))
  app.post('/new', async c => {
    const f = await c.req.parseBody()
    const name = String(f.name ?? '').trim(), city = String(f.city ?? '').trim(), category = String(f.category ?? '')
    if (!name || !city || !CATEGORIES[category]) return c.html(form(c.get('officer'), 'Fill in the name, city and category.'), 400)
    const code = (name.replace(/[^A-Za-z]/g, '').slice(0, 3).toUpperCase().padEnd(3, 'X')) + String(Date.now()).slice(-5)
    const [m] = await sql`insert into merchants (code, name, city, category, mcc) values (${code}, ${name.slice(0, 25)}, ${city.slice(0, 15)}, ${category}, ${CATEGORIES[category].mcc}) returning id`
    await audit(sql, c.get('officer').id, 'merchant.create', m.id, null, { name, city, category })
    return c.redirect(`/admin/merchants/${m.id}`)
  })

  app.get('/:id', async c => {
    const [m] = await sql`select * from merchants where id = ${c.req.param('id')}`
    if (!m) return c.notFound()
    const amount = Math.floor(Number(c.req.query('amount') ?? 0)) || undefined
    const payload = buildQris({ kind: 'merchant', code: m.code, name: m.name, city: m.city, mcc: m.mcc, amount })
    const svg = await QRCode.toString(payload, { type: 'svg', margin: 2, errorCorrectionLevel: 'M', color: { dark: '#0A0B10', light: '#FFFFFF' } })
    const png = await QRCode.toDataURL(payload, { margin: 2, width: 1024, errorCorrectionLevel: 'M' })
    return c.html(shell(c.get('officer'), '/admin/merchants', m.name, html`<div class="formgrid" style="max-width:720px">
<div class="cards">
<div class="card" style="align-items:center;justify-items:center">
  <div id="qr" style="width:280px;max-width:100%;background:#fff;border-radius:14px;padding:8px">${raw(svg)}</div>
  <b>${amount ? `Dynamic QR · Rp${amount.toLocaleString('en-US')}` : 'Static QR · customer types the amount'}</b>
  <div class="actions"><button type="button" class="btn" onclick="document.getElementById('qr').requestFullscreen()">Show full screen</button>
  <a class="btn secondary" download="${m.code}${amount ? '-' + amount : ''}.png" href="${png}">Download PNG</a></div>
</div>
<div class="card"><span class="eyebrow">Merchant</span><b>${m.name}</b><span class="hint">${m.city} · ${CATEGORIES[m.category]?.label ?? m.category}</span>
<form method="get" style="display:grid;gap:8px;margin-top:8px"><label>Amount for a dynamic QR<input name="amount" type="number" min="1000" step="1" value="${amount ?? ''}" placeholder="38000"></label>
<button class="btn secondary">Make dynamic QR</button>${amount ? html`<a href="/admin/merchants/${m.id}" class="hint">Back to the static QR</a>` : ''}</form>
<form method="post" action="/admin/merchants/${m.id}/toggle" style="margin-top:8px"><button class="btn ${m.active ? 'danger' : ''}">${m.active ? 'Pause merchant' : 'Resume merchant'}</button></form>
</div></div>
<style>#qr svg{width:100%;height:auto;display:block}#qr:fullscreen{display:grid;place-items:center;background:#fff}#qr:fullscreen svg{width:min(90vw,90vh)}</style></div>`))
  })

  app.post('/:id/toggle', async c => {
    const [m] = await sql`update merchants set active = not active where id = ${c.req.param('id')} returning active`
    await audit(sql, c.get('officer').id, m.active ? 'merchant.resume' : 'merchant.pause', c.req.param('id'), { active: !m.active }, { active: m.active })
    return c.redirect(`/admin/merchants/${c.req.param('id')}`)
  })

  return app
}
