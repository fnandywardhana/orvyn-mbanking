import { Hono } from 'hono'
import { html } from 'hono/html'
import { randomInt } from 'node:crypto'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { hashPassword, verifyPassword } from './auth.ts'
import { BANKS } from './transfers.ts'

type Env = { Variables: { officer: Officer } }
const sel = 'style="height:40px;border-radius:10px;padding:0 10px;font:inherit"'
const form = async (c: any) => Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, x]) => [k, String(x).trim()])) as Record<string, string>
const FEATURES: [string, string][] = [['transfer', 'Transfers'], ['qris', 'QRIS'], ['bills', 'Pay and buy'], ['cards', 'Cards'], ['register', 'Open an account']]
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
    const limits = await setting('daily_limit'), fees = await setting('transfer_fees'), features = await setting('features'), maint = await setting('maintenance')
    const directory = await sql`select * from external_directory order by bank, account_number`
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

  // Officers (S1.38 AC1)
  app.get('/officers', async c => {
    const rows = await sql`select id, email, name, role, active, created_at from officers order by created_at`
    const done = c.req.query('done')
    return c.html(shell(c.get('officer'), '/admin/officers', 'Officers', html`<div class="formgrid" style="max-width:760px">
${done ? html`<div class="ok-box"><pre>${done}</pre></div>` : ''}
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
  })
  app.post('/officers', async c => {
    const v = await form(c)
    const pw = 'orvyn' + String(randomInt(100000, 999999))
    try {
      const [o] = await sql`insert into officers (email, name, role, password_hash) values (${v.email.toLowerCase()}, ${v.name}, ${ROLES[v.role] ? v.role : 'operator'}, ${await hashPassword(pw)}) returning id`
      await audit(sql, c.get('officer').id, 'officer.add', o.id, null, { email: v.email, role: v.role })
      return flash(c, '/admin/officers', `Added ${v.name}.\nEmail: ${v.email}\nStarting password: ${pw}\nThis is the only time it's shown.`)
    } catch { return flash(c, '/admin/officers', 'That email already has an officer account.') }
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
      await tx`delete from postings`; await tx`update journal_entries set reverses_id = null`; await tx`delete from journal_entries`
      await tx`delete from accounts where kind <> 'house'`; await tx`update accounts set balance = 0`; await tx`delete from customers`
      return n
    })
    await audit(sql, c.get('officer').id, 'demo.reset', null, { customers: counts }, null)
    return flash(c, '/admin/reset', `Demo reset. ${counts} customers removed. Create new testers from Customers.`)
  })
  return app
}
