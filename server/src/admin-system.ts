import { Hono } from 'hono'
import { html } from 'hono/html'
import { randomInt } from 'node:crypto'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { hashPassword, verifyPassword } from './auth.ts'
import { BANKS } from './transfers.ts'
import { getCookie } from 'hono/cookie'

type Env = { Variables: { officer: Officer } }
const sel = 'style="height:40px;border-radius:10px;padding:0 10px;font:inherit"'
const form = async (c: any) => Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, x]) => [k, String(x).trim()])) as Record<string, string>
const FEATURES: [string, string][] = [['transfer', 'Transfers'], ['qris', 'QRIS'], ['bills', 'Pay and buy'], ['cards', 'Cards'], ['register', 'Open an account'],
  ['pockets', 'Pockets'], ['scheduled', 'Scheduled transfers'], ['requests', 'Request money and split bill'], ['insights', 'Insights and budgets'], ['vyna', 'Vyna, the assistant'], ['statements', 'E-statements'],
  ['fx', 'Foreign currency'], ['deposits', 'Time deposits'], ['invest', 'Investments'], ['passkeys', 'Passkey sign-in']]
const ROLES = { super_admin: 'Super Admin', operator: 'Operator', content: 'Content' } as Record<string, string>

// Stories S1.37 (settings) and S1.38 (officers, audit log, reset demo). Super Admin only.
export function systemRoutes(sql: Sql) {
  const app = new Hono<Env>()
  // Mounted at the admin root, so guard only these paths (a '*' guard would lock Operators out of everything).
  for (const path of ['/settings', '/settings/*', '/officers', '/officers/*', '/audit', '/reset']) app.use(path, requireRole('super_admin'))
  const setting = async (key: string) => (await sql`select value from settings where key = ${key}`)[0]?.value
  const save = async (c: any, key: string, value: object) => {
    const before = await setting(key)
    await sql`update settings set value = ${sql.json(value)} where key = ${key}`
    await audit(sql, c.get('officer').id, `settings.${key}`, key, before, value)
  }
  const flash = (c: any, path: string, msg: string) => c.redirect(`${path}?done=${encodeURIComponent(msg)}`)

  app.get('/settings', async c => {
    const limits = await setting('daily_limit'), fees = await setting('transfer_fees'), features = await setting('features'), maint = await setting('maintenance'), vyna = await setting('vyna')
    const directory = await sql`select * from external_directory order by bank, account_number`
    const fx = await sql`select currency, buy, sell from fx_rates order by currency`
    const rms = await sql`select r.*, (select count(*)::int from customers c where c.rm_id = r.id) as customers from relationship_managers r order by name`
    const deposit = await setting('deposit_rates'), spread = await setting('fx_prestige_spread'), gold = await setting('gold_price')
    const done = c.req.query('done')
    return c.html(shell(c.get('officer'), '/admin/settings', 'Settings', html`<div class="formgrid" style="max-width:760px">
${done ? html`<div class="ok-box"><pre>${done}</pre></div>` : ''}
<form method="post" action="/admin/settings/maintenance" class="card" style="gap:10px">
<span class="eyebrow">Maintenance mode</span>
<p class="hint">While it's on, the app shows this message and refuses every payment.</p>
<label>Message<input name="message" value="${maint.message}" maxlength="160"></label>
<div class="actions"><button class="btn ${maint.on ? 'secondary' : 'danger'}" name="on" value="${maint.on ? 'off' : 'on'}">${maint.on ? 'Turn maintenance off' : 'Turn maintenance on'}</button>
${maint.on ? html`<span class="chip bad">On now</span>` : html`<span class="chip ok">Off</span>`}</div></form>

<form method="post" action="/admin/settings/features" class="card" style="gap:10px">
<span class="eyebrow">Features in the app</span>
${FEATURES.map(([k, l]) => html`<label style="display:flex;gap:10px;align-items:center;font-weight:400"><input type="checkbox" name="${k}" ${features[k] ? 'checked' : ''} style="height:20px;width:20px">${l}</label>`)}
<p class="hint">Switched-off features disappear from the app on its next refresh. No update needed.</p>
<button class="btn" style="width:max-content">Save features</button></form>

<form method="post" action="/admin/settings/limits" class="card" style="gap:10px">
<span class="eyebrow">Daily limit per tier (Rp)</span>
${['everyday', 'prestige', 'private'].map(t => html`<label>${t[0].toUpperCase() + t.slice(1)}<input name="${t}" type="number" min="100000" value="${limits[t]}"></label>`)}
<span class="eyebrow" style="margin-top:8px">Transfer fees (Rp)</span>
<label>BI-FAST fee<input name="bifast" type="number" min="0" value="${fees.bifast}"></label>
<label>BI-FAST largest amount<input name="bifast_max" type="number" min="1" value="${fees.bifast_max}"></label>
<label>RTGS fee<input name="rtgs" type="number" min="0" value="${fees.rtgs}"></label>
<label>RTGS smallest amount<input name="rtgs_min" type="number" min="1" value="${fees.rtgs_min}"></label>
<button class="btn" style="width:max-content">Save limits and fees</button></form>

<div class="card" style="gap:10px"><span class="eyebrow">Relationship Managers</span>
${rms.map(r => html`<form method="post" action="/admin/settings/rm/delete" class="actions" style="align-items:center">
<div style="flex:1"><b>${r.name}</b><br><span class="hint">${r.title} · ${r.phone} · ${r.email} · ${r.customers} ${r.customers === 1 ? 'customer' : 'customers'}</span></div>
<input type="hidden" name="id" value="${r.id}"><button class="btn secondary" ${r.customers ? 'disabled title="Assign their customers to someone else first"' : ''}>Remove</button></form>`)}
<form method="post" action="/admin/settings/rm" style="display:grid;gap:10px">
<label>Name<input name="name" required maxlength="40"></label><label>Title<input name="title" maxlength="40" value="Relationship Manager"></label>
<label>Phone<input name="phone" required maxlength="20" value="+62 21 0000 "></label><label>Email<input name="email" type="email" required maxlength="60"></label>
<p class="hint">Use numbers with a 0000 block so a tester's call can't reach a real person. Customers see a monogram, not a photo.</p>
<button class="btn" style="width:max-content">Add Relationship Manager</button></form></div>

<form method="post" action="/admin/settings/fx" class="card" style="gap:10px">
<span class="eyebrow">Exchange rates (Rupiah per unit)</span>
<p class="hint">Buy: what ORVYN pays when a customer sells. Sell: what a customer pays. Prestige and Private customers pay a narrower spread.</p>
${fx.map(r => html`<div class="actions" style="align-items:end"><b style="width:48px">${r.currency}</b>
<label style="flex:1">Buy<input name="${r.currency}_buy" type="number" step="0.0001" min="0.0001" value="${Number(r.buy)}"></label>
<label style="flex:1">Sell<input name="${r.currency}_sell" type="number" step="0.0001" min="0.0001" value="${Number(r.sell)}"></label></div>`)}
<label>Prestige and Private pay this share of the spread<input name="factor" type="number" step="0.05" min="0" max="1" value="${spread?.factor ?? 0.5}"></label>
<button class="btn" style="width:max-content">Save rates</button></form>

<form method="post" action="/admin/settings/gold" class="card" style="gap:10px">
<span class="eyebrow">Gold price (Rupiah per gram)</span>
<label>Buy back (what customers get when they sell)<input name="buy" type="number" min="1" value="${gold.buy}"></label>
<label>Sell (what customers pay)<input name="sell" type="number" min="1" value="${gold.sell}"></label>
<button class="btn" style="width:max-content">Save gold price</button></form>

<form method="post" action="/admin/settings/deposits" class="card" style="gap:10px">
<span class="eyebrow">Time deposit rates (% a year)</span>
${['1', '3', '6', '12'].map(t => html`<label>${t} ${t === '1' ? 'month' : 'months'}<input name="t${t}" type="number" step="0.05" min="0" max="20" value="${Number(deposit[t]) / 100}"></label>`)}
<p class="hint">New deposits and renewals use these. Open deposits keep their rate.</p>
<button class="btn" style="width:max-content">Save deposit rates</button></form>

<form method="post" action="/admin/settings/vyna" class="card" style="gap:10px">
<span class="eyebrow">Vyna</span>
<label>Questions per customer per day<input name="daily_limit" type="number" min="0" max="500" value="${vyna?.daily_limit ?? 30}"></label>
<p class="hint">Keeps the Claude API bill small. Usage and cost are on the dashboard. Switch Vyna off entirely under Features.</p>
<button class="btn" style="width:max-content">Save Vyna limit</button></form>

<div class="card" style="gap:10px"><span class="eyebrow">Accounts at other banks</span>
<p class="hint">Any number gets a made-up name automatically. Add one here to choose the name a tester sees.</p>
${directory.map(d => html`<form method="post" action="/admin/settings/directory/delete" class="actions" style="align-items:center">
<input type="hidden" name="bank" value="${d.bank}"><input type="hidden" name="number" value="${d.account_number}">
<span style="flex:1">${d.bank} · <span class="mono">${d.account_number}</span> · ${d.name}</span><button class="btn secondary">Remove</button></form>`)}
<form method="post" action="/admin/settings/directory" class="actions" style="align-items:end">
<label>Bank<select name="bank" ${sel}>${BANKS.filter(b => b.code !== 'ORVYN').map(b => html`<option value="${b.code}">${b.name}</option>`)}</select></label>
<label>Account number<input name="number" required pattern="[0-9]{10,16}"></label>
<label>Name<input name="name" required maxlength="40"></label>
<button class="btn">Add</button></form></div></div>`))
  })

  app.post('/settings/maintenance', async c => {
    const v = await form(c)
    await save(c, 'maintenance', { on: v.on === 'on', message: v.message || 'ORVYN is getting an update. Payments are paused for a few minutes.' })
    return flash(c, '/admin/settings', v.on === 'on' ? 'Maintenance is on. Payments are paused.' : 'Maintenance is off. Payments work again.')
  })
  app.post('/settings/features', async c => {
    const v = await form(c)
    await save(c, 'features', Object.fromEntries(FEATURES.map(([k]) => [k, v[k] === 'on'])))
    return flash(c, '/admin/settings', 'Features saved.')
  })
  app.post('/settings/limits', async c => {
    const v = await form(c)
    const n = (k: string) => Math.max(0, Math.floor(Number(v[k])))
    await save(c, 'daily_limit', { everyday: n('everyday'), prestige: n('prestige'), private: n('private') })
    await save(c, 'transfer_fees', { bifast: n('bifast'), bifast_max: n('bifast_max'), rtgs: n('rtgs'), rtgs_min: n('rtgs_min') })
    return flash(c, '/admin/settings', 'Limits and fees saved.')
  })
  app.post('/settings/fx', async c => {
    const v = await form(c)
    const old = await sql`select currency, buy, sell from fx_rates`
    for (const r of old) {
      const buy = Number(v[`${r.currency}_buy`]), sell = Number(v[`${r.currency}_sell`])
      if (!(buy > 0 && sell >= buy)) return flash(c, '/admin/settings', `${r.currency}: the sell rate must be at least the buy rate.`)
    }
    for (const r of old) await sql`update fx_rates set buy = ${Number(v[`${r.currency}_buy`])}, sell = ${Number(v[`${r.currency}_sell`])}, updated_at = now() where currency = ${r.currency}`
    await audit(sql, c.get('officer').id, 'settings.fx_rates', null, old.map(r => ({ ...r })), (await sql`select currency, buy, sell from fx_rates`).map(r => ({ ...r })))
    await save(c, 'fx_prestige_spread', { factor: Math.min(1, Math.max(0, Number(v.factor) || 0)) })
    return flash(c, '/admin/settings', 'Exchange rates saved.')
  })
  app.post('/settings/rm', async c => {
    const v = await form(c)
    if (!v.name || !v.phone || !v.email) return flash(c, '/admin/settings', 'Name, phone and email are needed.')
    await sql`insert into relationship_managers (name, title, phone, email) values (${v.name}, ${v.title || 'Relationship Manager'}, ${v.phone}, ${v.email})`
    await audit(sql, c.get('officer').id, 'rm.add', null, null, { name: v.name })
    return flash(c, '/admin/settings', 'Relationship Manager added.')
  })
  app.post('/settings/rm/delete', async c => {
    const v = await form(c)
    const [r] = await sql`delete from relationship_managers r where id = ${v.id} and not exists (select 1 from customers c where c.rm_id = r.id) returning name`
    if (r) await audit(sql, c.get('officer').id, 'rm.remove', v.id, { name: r.name }, null)
    return flash(c, '/admin/settings', r ? 'Relationship Manager removed.' : 'Assign their customers to someone else first.')
  })
  app.post('/settings/gold', async c => {
    const v = await form(c)
    const buy = Math.floor(Number(v.buy)), sell = Math.floor(Number(v.sell))
    if (!(buy > 0 && sell >= buy)) return flash(c, '/admin/settings', 'The sell price must be at least the buy-back price.')
    await save(c, 'gold_price', { buy, sell })
    return flash(c, '/admin/settings', 'Gold price saved.')
  })
  app.post('/settings/deposits', async c => {
    const v = await form(c)
    const bp = (k: string) => Math.round(Math.min(20, Math.max(0, Number(v[k]) || 0)) * 100)
    await save(c, 'deposit_rates', { '1': bp('t1'), '3': bp('t3'), '6': bp('t6'), '12': bp('t12') })
    return flash(c, '/admin/settings', 'Deposit rates saved.')
  })
  app.post('/settings/vyna', async c => {
    const v = await form(c)
    await save(c, 'vyna', { daily_limit: Math.min(500, Math.max(0, Math.floor(Number(v.daily_limit) || 0))) })
    return flash(c, '/admin/settings', 'Vyna limit saved.')
  })
  app.post('/settings/directory', async c => {
    const v = await form(c)
    if (!/^\d{10,16}$/.test(v.number)) return flash(c, '/admin/settings', 'Account numbers have 10 to 16 digits.')
    await sql`insert into external_directory (bank, account_number, name) values (${v.bank}, ${v.number}, ${v.name}) on conflict (bank, account_number) do update set name = excluded.name`
    await audit(sql, c.get('officer').id, 'directory.set', `${v.bank}:${v.number}`, null, { name: v.name })
    return flash(c, '/admin/settings', 'Saved.')
  })
  app.post('/settings/directory/delete', async c => {
    const v = await form(c)
    await sql`delete from external_directory where bank = ${v.bank} and account_number = ${v.number}`
    await audit(sql, c.get('officer').id, 'directory.remove', `${v.bank}:${v.number}`, null, null)
    return c.redirect('/admin/settings')
  })

  // Officers (S1.38 AC1). Q-C8: a new officer's starting password waits in memory for the officer who made it, keyed by
  // their session, and is shown once on the next page. It never goes into a URL, a log or the audit log.
  // ponytail: in memory, so a server restart between the two requests loses it (the officer just resets it from the list).
  const added = new Map<string, { name: string, email: string, password: string }>()
  app.get('/officers', async c => officersPage(c))
  const officersPage = async (c: any, error?: string) => {
    const rows = await sql`select id, email, name, role, active, created_at from officers order by created_at`
    const done = c.req.query('done')
    const token = getCookie(c, 'orvyn_admin') ?? ''
    const fresh = added.get(token)
    added.delete(token)
    return c.html(shell(c.get('officer'), '/admin/officers', 'Officers', html`<div class="formgrid" style="max-width:760px">
${done ? html`<div class="ok-box"><pre>${done}</pre></div>` : ''}
${error ? html`<div class="err-box" role="alert">${error}</div>` : ''}
${fresh ? html`<div class="ok-box" role="status"><b>Officer added</b>
<span>Name: ${fresh.name}</span><span>Email: ${fresh.email}</span>
<span>Starting password: <span class="mono" id="pw">${fresh.password}</span></span>
<button type="button" class="btn secondary" style="width:max-content" onclick="navigator.clipboard.writeText(document.getElementById('pw').innerText).then(()=>{this.textContent='Copied';setTimeout(()=>this.textContent='Copy password',2000)})">Copy password</button>
<span class="hint">This is the only time it's shown. ${fresh.name.split(' ')[0]} must change it at first sign-in.</span></div>` : ''}
<div class="tablewrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr></thead><tbody>
${rows.map(o => html`<tr><td>${o.name}</td><td>${o.email}</td>
<td><form method="post" action="/admin/officers/${o.id}/role" style="display:flex;gap:6px"><select name="role" ${sel}>${Object.entries(ROLES).map(([k, l]) => html`<option value="${k}" ${o.role === k ? 'selected' : ''}>${l}</option>`)}</select><button class="btn secondary">Save</button></form></td>
<td>${o.active ? html`<span class="chip ok">Active</span>` : html`<span class="chip bad">Off</span>`}</td>
<td>${o.id === c.get('officer').id ? html`<span class="hint">You</span>` : html`<form method="post" action="/admin/officers/${o.id}/toggle"><button class="btn secondary">${o.active ? 'Turn off' : 'Turn on'}</button></form>`}</td></tr>`)}
</tbody></table></div>
<form method="post" action="/admin/officers" class="card" style="gap:10px"><span class="eyebrow">Add an officer</span>
<label>Name<input name="name" required></label><label>Email<input name="email" type="email" required></label>
<label>Role<select name="role" ${sel}>${Object.entries(ROLES).map(([k, l]) => html`<option value="${k}">${l}</option>`)}</select></label>
<button class="btn" style="width:max-content">Add officer</button>
<p class="hint">They get a starting password, shown once, and must change it when they first sign in.</p></form></div>`))
  }
  app.post('/officers', async c => {
    const v = await form(c)
    const pw = 'orvyn' + String(randomInt(100000, 999999))
    try {
      const [o] = await sql`insert into officers (email, name, role, password_hash) values (${v.email.toLowerCase()}, ${v.name}, ${ROLES[v.role] ? v.role : 'operator'}, ${await hashPassword(pw)}) returning id`
      await audit(sql, c.get('officer').id, 'officer.add', o.id, null, { email: v.email, role: v.role })
      added.set(getCookie(c, 'orvyn_admin') ?? '', { name: v.name, email: v.email.toLowerCase(), password: pw })
      return c.redirect('/admin/officers')
    } catch { return officersPage(c, 'That email already has an officer account.') }
  })
  app.post('/officers/:id/role', async c => {
    const role = (await form(c)).role
    if (!ROLES[role]) return c.redirect('/admin/officers')
    const [b] = await sql`select role from officers where id = ${c.req.param('id')}`
    await sql`update officers set role = ${role} where id = ${c.req.param('id')}`
    await audit(sql, c.get('officer').id, 'officer.role', c.req.param('id'), { role: b.role }, { role })
    return flash(c, '/admin/officers', 'Role saved.')
  })
  app.post('/officers/:id/toggle', async c => {
    if (c.req.param('id') === c.get('officer').id) return c.redirect('/admin/officers')
    const [o] = await sql`update officers set active = not active where id = ${c.req.param('id')} returning active`
    if (!o.active) await sql`delete from officer_sessions where officer_id = ${c.req.param('id')}`
    await audit(sql, c.get('officer').id, o.active ? 'officer.on' : 'officer.off', c.req.param('id'), null, null)
    return c.redirect('/admin/officers')
  })

  // Audit log (S1.38 AC2–AC3): read-only, filterable.
  app.get('/audit', async c => {
    const q = c.req.query()
    const rows = await sql`
      select a.*, o.name from audit_entries a join officers o on o.id = a.officer_id
      where true ${q.officer ? sql`and a.officer_id = ${q.officer}` : sql``} ${q.action ? sql`and a.action ilike ${q.action + '%'}` : sql``}
        ${q.from ? sql`and a.created_at >= ${new Date(`${q.from}T00:00:00+07:00`)}` : sql``}
      order by a.id desc limit 200`
    const officers = await sql`select id, name from officers order by name`
    return c.html(shell(c.get('officer'), '/admin/audit', 'Audit log', html`
<form method="get" class="toolbar" style="justify-content:flex-start">
<select name="officer" ${sel}><option value="">All officers</option>${officers.map(o => html`<option value="${o.id}" ${q.officer === o.id ? 'selected' : ''}>${o.name}</option>`)}</select>
<input name="action" placeholder="Action, e.g. money" value="${q.action ?? ''}"><input type="date" name="from" value="${q.from ?? ''}" aria-label="From">
<button class="btn secondary">Filter</button></form>
<div class="tablewrap"><table><thead><tr><th>When (WIB)</th><th>Officer</th><th>Action</th><th>Before</th><th>After</th></tr></thead><tbody>
${rows.map(r => html`<tr><td class="mono">${r.created_at.toLocaleString('en-GB', { timeZone: 'Asia/Jakarta', dateStyle: 'short', timeStyle: 'short' })}</td>
<td>${r.name}</td><td>${r.action}</td><td class="mono" style="white-space:normal;max-width:240px;font-size:12px">${r.before ? JSON.stringify(r.before) : ''}</td>
<td class="mono" style="white-space:normal;max-width:320px;font-size:12px">${r.after ? JSON.stringify(r.after) : ''}</td></tr>`)}
</tbody></table></div>`))
  })

  // Reset demo (S1.38 AC4–AC5): typed RESET plus the officer's password. Officers, audit log, settings and content stay.
  app.get('/reset', async c => {
    const done = c.req.query('done')
    return c.html(shell(c.get('officer'), '/admin/reset', 'Reset demo', html`<form method="post" class="formgrid">
${done ? html`<div class="ok-box"><pre>${done}</pre></div>` : ''}
<div class="card"><b>This deletes every customer, account, card, transaction, application and inbox message.</b>
<p class="hint">Officers, the audit log, settings, merchants, banners and help articles stay. Testers will need new accounts. This can't be undone.</p></div>
<label>Type RESET<input name="confirm" required pattern="RESET" autocomplete="off"></label>
<label>Your password<input name="password" type="password" required autocomplete="current-password"></label>
<button class="btn danger" style="width:max-content">Reset the demo</button></form>`))
  })
  app.post('/reset', async c => {
    const v = await form(c)
    const [me] = await sql`select password_hash from officers where id = ${c.get('officer').id}`
    if (v.confirm !== 'RESET' || !(await verifyPassword(v.password, me.password_hash))) return flash(c, '/admin/reset', 'Not reset: type RESET and your own password.')
    const counts = await sql.begin(async tx => {
      const [{ n }] = await tx`select count(*)::int as n from customers`
      await tx`delete from saved_bill_numbers`; await tx`delete from bill_payments`; await tx`delete from inbox_messages`; await tx`delete from announcement_seen`
      await tx`delete from saved_recipients`; await tx`delete from registrations`; await tx`delete from otp_codes`; await tx`delete from cards`
      await tx`delete from customer_sessions`; await tx`delete from registered_devices`
      // Phase 2 and 3 tables that point at customers, accounts or journal entries
      await tx`delete from autosave_runs`; await tx`delete from pockets`; await tx`delete from scheduled_transfers`
      await tx`delete from money_requests`; await tx`delete from splits`; await tx`delete from category_overrides`
      await tx`delete from budgets`; await tx`delete from budget_alerts`; await tx`delete from vyna_usage`
      await tx`delete from time_deposits`; await tx`delete from holdings`; await tx`delete from bond_coupons`
      await tx`delete from passkeys` // Q-C3: the public halves; the copies left in phones' password managers stop working
      await tx`delete from postings`; await tx`update journal_entries set reverses_id = null`; await tx`delete from journal_entries`
      await tx`delete from accounts where kind <> 'house' or code like 'CLOSED_%'`; await tx`update accounts set balance = 0`; await tx`delete from customers`
      return n
    })
    await audit(sql, c.get('officer').id, 'demo.reset', null, { customers: counts }, null)
    return flash(c, '/admin/reset', `Demo reset. ${counts} customers removed. Create new testers from Customers.`)
  })
  return app
}
