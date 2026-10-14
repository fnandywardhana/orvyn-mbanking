import { Hono } from 'hono'
import { html } from 'hono/html'
import { randomInt } from 'node:crypto'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { createCustomer, CustomerError } from './customers.ts'
import { hashPassword } from './auth.ts'
import { PERSONAS, generateHistory } from './personas.ts'

type Env = { Variables: { officer: Officer } }

const rupiah = (n: number | string) => 'Rp' + Number(n).toLocaleString('en-US')
const TIER = { everyday: 'Everyday', prestige: 'Prestige', private: 'Private' } as Record<string, string>
const group4 = (n: string) => n.replace(/(\d{4})(?=\d)/g, '$1 ')
const tempPassword = () => 'orvyn' + String(randomInt(1000, 9999)) // letters + digits, meets the password rule
const PUBLIC_URL = process.env.PUBLIC_URL ?? 'https://orvyn.delvault.uk'

// Stories S1.32 (create from Persona Template) and S1.33 (manage customers).
export function customerRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'operator'))

  app.get('/', async c => {
    const q = c.req.query('q')?.trim() ?? ''
    const like = `%${q}%`
    const rows = await sql`
      select c.id, c.full_name, c.phone, c.tier, c.persona, c.locked, c.created_at, a.number, a.balance
      from customers c left join accounts a on a.customer_id = c.id and a.kind = 'savings'
      ${q ? sql`where c.full_name ilike ${like} or c.phone ilike ${like} or a.number ilike ${like}` : sql``}
      order by c.created_at desc limit 200`
    return c.html(shell(c.get('officer'), '/admin/customers', 'Customers', html`
<div class="toolbar">
  <form method="get"><input type="search" name="q" value="${q}" placeholder="Name, phone or account number" aria-label="Search customers"><button class="btn secondary">Search</button></form>
  <a class="btn" href="/admin/customers/new">New customer</a>
</div>
${rows.length === 0 ? html`<div class="card"><b>${q ? 'No customers match that search.' : 'No customers yet.'}</b>
  <p class="hint">${q ? 'Try a name, phone number or account number.' : 'Create one for each tester from a persona. It comes with six months of history.'}</p></div>`
  : html`<div class="tablewrap"><table>
<thead><tr><th>Name</th><th>Phone</th><th>Tier</th><th>Persona</th><th>Account</th><th class="num">Balance</th><th>Status</th></tr></thead>
<tbody>${rows.map(r => html`<tr>
  <td><a href="/admin/customers/${r.id}">${r.full_name}</a></td><td class="mono">${r.phone}</td>
  <td>${TIER[r.tier]}</td><td>${r.persona ?? '—'}</td><td class="mono">${r.number ? group4(r.number) : '—'}</td>
  <td class="num">${r.balance != null ? rupiah(r.balance) : '—'}</td>
  <td>${r.locked ? html`<span class="chip bad">Locked</span>` : html`<span class="chip ok">Active</span>`}</td></tr>`)}</tbody></table></div>`}`))
  })

  const newForm = (o: Officer, v: Record<string, string> = {}, error?: string) => shell(o, '/admin/customers', 'New customer', html`
<form method="post" class="formgrid">
${error ? html`<div class="err" role="alert">${error}</div>` : ''}
<fieldset class="personas" style="border:0;padding:0;margin:0"><legend class="eyebrow" style="margin-bottom:8px">Persona</legend>
${PERSONAS.map((p, i) => html`<label class="persona"><input type="radio" name="persona" value="${p.key}" ${(v.persona ?? (i === 0 ? p.key : '')) === p.key ? 'checked' : ''}>
<span><b>${p.fullName}</b><span class="hint">${p.summary}</span></span></label>`)}
</fieldset>
<label>Full name<input name="fullName" value="${v.fullName ?? ''}" placeholder="Leave empty to use the persona's name"></label>
<label>Phone number<input name="phone" type="tel" value="${v.phone ?? ''}" placeholder="0812 3456 7890" required></label>
<label>Starting password<input name="password" value="${v.password ?? tempPassword()}" required minlength="8"></label>
<p class="hint">Send this to the tester. They sign in with it once, then set their own PIN.</p>
<button>Create customer</button></form>`)

  app.get('/new', c => c.html(newForm(c.get('officer'))))

  app.post('/new', async c => {
    const o = c.get('officer')
    const f = Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, v]) => [k, String(v)]))
    const persona = PERSONAS.find(p => p.key === f.persona)
    if (!persona) return c.html(newForm(o, f, 'Pick a persona.'), 400)
    if (!/^(?=.*[a-zA-Z])(?=.*\d).{8,}$/.test(f.password ?? '')) return c.html(newForm(o, f, 'The starting password needs at least 8 characters, with letters and numbers.'), 400)
    try {
      const { customer, savings } = await sql.begin(async tx => {
        const r = await createCustomer(tx, { fullName: f.fullName?.trim() || persona.fullName, phone: f.phone, password: f.password, tier: persona.tier, persona: persona.key })
        await generateHistory(tx, persona.key, r.savings.id, r.customer.id)
        return r
      })
      await audit(sql, o.id, 'customer.create', customer.id, null, { name: customer.full_name, phone: customer.phone, persona: persona.key })
      const [{ balance }] = await sql`select balance from accounts where id = ${savings.id}`
      const message = `Hi ${customer.full_name.split(' ')[0]}! Here's your ORVYN demo account.

1. Install the app: ${PUBLIC_URL}/download/orvyn-0.1.0.apk
2. Open it and tap Sign in.
   Phone: ${customer.phone}
   Password: ${f.password}
3. Create your own 6-digit PIN.

ORVYN Bank is a demo. All money in it is pretend.`
      return c.html(shell(o, '/admin/customers', 'Customer created', html`<div class="formgrid">
<div class="ok-box"><b>${customer.full_name} is ready</b>
<span class="hint">Account ${group4(savings.number)} · ${TIER[customer.tier]} · ${rupiah(balance)} after six months of ${persona.fullName.split(' ')[0]}'s history</span></div>
<div class="card"><span class="eyebrow">Message for the tester</span><pre id="msg" style="margin:0;white-space:pre-wrap;font:13px/1.6 ui-monospace,Menlo,monospace">${message}</pre>
<p class="hint">This is the only time the password is shown. Copy it now.</p>
<button type="button" class="btn secondary" style="width:max-content" onclick="navigator.clipboard.writeText(document.getElementById('msg').innerText).then(()=>this.textContent='Copied')">Copy message</button></div>
<div class="actions"><a class="btn" href="/admin/customers/${customer.id}">Open customer</a><a class="btn secondary" href="/admin/customers/new">Create another</a></div></div>`))
    } catch (e) {
      if (e instanceof CustomerError) return c.html(newForm(o, f, e.message), 400)
      throw e
    }
  })

  app.get('/:id', async c => {
    const [cu] = await sql`select * from customers where id = ${c.req.param('id')}`
    if (!cu) return c.notFound()
    const accounts = await sql`select * from accounts where customer_id = ${cu.id} order by created_at`
    const [device] = await sql`select created_at from registered_devices where customer_id = ${cu.id}`
    const flash = c.req.query('done')
    return c.html(shell(c.get('officer'), '/admin/customers', cu.full_name, html`<div class="formgrid" style="max-width:720px">
${flash ? html`<div class="ok-box"><pre>${flash}</pre></div>` : ''}
<div class="cards">
<div class="card"><span class="eyebrow">Profile</span><b>${cu.phone}</b><span class="hint">${TIER[cu.tier]}${cu.persona ? ` · from the ${cu.persona[0].toUpperCase() + cu.persona.slice(1)} persona` : ''}</span>
${cu.locked ? html`<span class="chip bad">Locked</span>` : html`<span class="chip ok">Active</span>`}</div>
<div class="card"><span class="eyebrow">Phone</span><b>${device ? 'Signed in on a phone' : 'Not signed in yet'}</b>
<span class="hint">${device ? `Since ${device.created_at.toLocaleString('en-GB', { timeZone: 'Asia/Jakarta' })} WIB` : 'Waiting for the first sign-in'}</span>
<span class="hint">${cu.pin_hash ? 'PIN set' : 'No PIN yet'}</span></div></div>
<div class="tablewrap"><table><thead><tr><th>Account</th><th>Number</th><th class="num">Balance</th></tr></thead>
<tbody>${accounts.map(a => html`<tr><td>${a.name}</td><td class="mono">${a.number ? group4(a.number) : '—'}</td><td class="num">${rupiah(a.balance)}</td></tr>`)}</tbody></table></div>
<form method="post" action="/admin/customers/${cu.id}/tier" class="actions" style="align-items:end">
<label>Tier<select name="tier" style="height:40px;border-radius:10px;padding:0 10px;font:inherit">
${Object.entries(TIER).map(([k, l]) => html`<option value="${k}" ${k === cu.tier ? 'selected' : ''}>${l}</option>`)}</select></label>
<button class="btn secondary">Save tier</button></form>
<div class="actions">
${cu.locked ? html`<form method="post" action="/admin/customers/${cu.id}/unlock"><button class="btn">Unlock</button></form>`
  : html`<form method="post" action="/admin/customers/${cu.id}/lock"><button class="btn secondary">Lock</button></form>`}
<form method="post" action="/admin/customers/${cu.id}/reset-pin"><button class="btn secondary">Reset PIN</button></form>
<form method="post" action="/admin/customers/${cu.id}/reset-password"><button class="btn danger">Reset password</button></form>
</div>
<p class="hint">Resetting the PIN or password signs the customer out of their phone.</p></div>`))
  })

  const action = (path: string, run: (id: string, o: Officer) => Promise<string>) =>
    app.post(`/:id/${path}`, async c => {
      const id = c.req.param('id')
      const done = await run(id, c.get('officer'))
      return c.redirect(`/admin/customers/${id}?done=${encodeURIComponent(done)}`)
    })

  const signOutEverywhere = (id: string) => sql`delete from customer_sessions where customer_id = ${id}`

  action('lock', async (id, o) => {
    await sql`update customers set locked = true where id = ${id}`
    await signOutEverywhere(id)
    await audit(sql, o.id, 'customer.lock', id, { locked: false }, { locked: true })
    return 'Customer locked and signed out.'
  })
  action('unlock', async (id, o) => {
    await sql`update customers set locked = false, failed_attempts = 0 where id = ${id}`
    await audit(sql, o.id, 'customer.unlock', id, { locked: true }, { locked: false })
    return 'Customer unlocked. They can sign in again.'
  })
  action('reset-pin', async (id, o) => {
    await sql`update customers set pin_hash = null, failed_attempts = 0, locked = false where id = ${id}`
    await sql`delete from registered_devices where customer_id = ${id}`
    await signOutEverywhere(id)
    await audit(sql, o.id, 'customer.reset_pin', id, null, null)
    return 'PIN cleared. The customer signs in with their password and sets a new PIN.'
  })
  // Rendered directly (not redirected) so the new password never lands in a URL or browser history.
  app.post('/:id/reset-password', async c => {
    const id = c.req.param('id')
    const pw = tempPassword()
    const [cu] = await sql`update customers set password_hash = ${await hashPassword(pw)}, failed_attempts = 0, locked = false where id = ${id} returning full_name, phone`
    await sql`delete from registered_devices where customer_id = ${id}`
    await signOutEverywhere(id)
    await audit(sql, c.get('officer').id, 'customer.reset_password', id, null, null)
    return c.html(shell(c.get('officer'), '/admin/customers', 'Password reset', html`<div class="formgrid">
<div class="ok-box"><b>New starting password for ${cu.full_name}</b><pre>Phone: ${cu.phone}\nPassword: ${pw}</pre>
<span class="hint">Send it to the customer. This is the only time it's shown.</span></div>
<div class="actions"><a class="btn secondary" href="/admin/customers/${id}">Back to customer</a></div></div>`))
  })
  app.post('/:id/tier', async c => {
    const id = c.req.param('id')
    const tier = String((await c.req.parseBody()).tier)
    if (!TIER[tier]) return c.text('Unknown tier', 400)
    const [before] = await sql`select tier from customers where id = ${id}`
    await sql`update customers set tier = ${tier} where id = ${id}`
    await audit(sql, c.get('officer').id, 'customer.tier', id, { tier: before.tier }, { tier })
    return c.redirect(`/admin/customers/${id}?done=${encodeURIComponent(`Tier changed to ${TIER[tier]}.`)}`)
  })

  return app
}
