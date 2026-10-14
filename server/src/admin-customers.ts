import { Hono } from 'hono'
import { html } from 'hono/html'
import { randomInt } from 'node:crypto'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { createCustomer, CustomerError } from './customers.ts'
import { hashPassword } from './auth.ts'
import { PERSONAS, generateHistory } from './personas.ts'
import { PRODUCTS, spendOnCard } from './cards.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { notifyJournal } from './inbox.ts'
import { generatedName } from './transfers.ts'

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
  <div class="actions"><form method="post" action="/admin/customers/dummies" class="actions" style="align-items:center">
    <input name="count" type="number" min="1" max="20" value="5" style="width:80px;height:40px" aria-label="How many">
    <button class="btn secondary" title="Made-up customers testers can send money to">Add made-up customers</button></form>
  <a class="btn" href="/admin/customers/new">New customer</a></div>
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

  // Story S1.32 AC5: made-up customers with a little money, so testers have people to send to.
  app.post('/dummies', async c => {
    const count = Math.min(20, Math.max(1, Math.floor(Number((await c.req.parseBody()).count) || 5)))
    const equity = await houseAccount(sql, 'DEMO_EQUITY')
    for (let i = 0; i < count; i++) {
      const phone = '0899' + String(randomInt(0, 1e8)).padStart(8, '0')
      await sql.begin(async tx => {
        const { savings } = await createCustomer(tx, { fullName: generatedName('ORVYN', phone), phone, password: tempPassword() + tempPassword() })
        const amount = randomInt(5, 50) * 100_000
        await postJournal(tx, { kind: 'opening', memo: { from: { name: 'ORVYN Bank' }, channel: 'Opening balance', category: 'income' }, postings: [{ accountId: equity, amount: -amount }, { accountId: savings.id, amount }] })
      }).catch(() => {}) // a rare phone clash just means one fewer
    }
    await audit(sql, c.get('officer').id, 'customer.dummies', null, null, { count })
    return c.redirect('/admin/customers')
  })

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

1. Install the app: ${PUBLIC_URL}/download/orvyn.apk
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
    const cards = await sql`select id, product, number, frozen from cards where customer_id = ${cu.id} order by kind desc, created_at`
    // Story S1.33 AC4: what the customer sees, read-only.
    const recent = await sql`
      select j.created_at, j.kind, j.memo, p.amount, exists (select 1 from journal_entries r where r.reverses_id = j.id) as reversed
      from postings p join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
      where a.customer_id = ${cu.id} and a.kind = 'savings' order by j.created_at desc limit 15`
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
<div class="tablewrap"><table><thead><tr><th colspan="3">As ${cu.full_name.split(' ')[0]} sees it: latest in History</th></tr></thead><tbody>
${recent.length === 0 ? html`<tr><td class="hint">No transactions yet.</td></tr>` : recent.map(t => {
  const amount = Number(t.amount), party = amount < 0 ? t.memo?.to : t.memo?.from
  return html`<tr><td class="mono">${t.created_at.toLocaleString('en-GB', { timeZone: 'Asia/Jakarta', dateStyle: 'short', timeStyle: 'short' })}</td>
  <td>${t.kind === 'reversal' ? 'Reversed by ORVYN Bank' : party?.name ?? t.memo?.channel}<br><span class="hint">${t.memo?.channel ?? ''}</span></td>
  <td class="num" style="${amount > 0 ? 'color:var(--success)' : ''};${t.reversed ? 'text-decoration:line-through' : ''}">${amount > 0 ? '+' : '−'}${rupiah(Math.abs(amount))}</td></tr>` })}
</tbody></table></div>
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
<p class="hint">Resetting the PIN or password signs the customer out of their phone.</p>
<form method="post" action="/admin/customers/${cu.id}/money" class="card" style="gap:12px">
<span class="eyebrow">Add or remove money</span>
<label>What happens<select name="type" style="height:44px;border-radius:10px;padding:0 10px;font:inherit">
<option value="inflow">Money arrives from someone (looks real to the customer)</option>
<option value="add">Balance adjustment: add</option>
<option value="remove">Balance adjustment: remove</option></select></label>
<label>Amount<input name="amount" type="number" min="1000" required placeholder="5000000"></label>
<label>From (for money arriving)<input name="from" list="senders" placeholder="PT Maju Jaya"><datalist id="senders">
<option value="PT Maju Jaya"><option value="PT Nusantara Digital"><option value="Dimas Prakoso"><option value="Ibu Sri Wahyuni"></datalist></label>
<label>Their bank (for money arriving)<input name="bank" list="banks" placeholder="BCA"><datalist id="banks"><option value="BCA"><option value="Mandiri"><option value="BRI"><option value="BNI"></datalist></label>
<label>Kind of money (for money arriving)<select name="channel" style="height:44px;border-radius:10px;padding:0 10px;font:inherit">
<option value="Salary">Salary</option><option value="BI-FAST">Transfer</option></select></label>
<label>Reason (required for adjustments; the customer sees it)<input name="reason" maxlength="80" placeholder="Bonus for testing"></label>
<button class="btn" style="width:max-content">Move money</button></form>
<form method="post" action="/admin/customers/${cu.id}/card-purchase" class="card" style="gap:12px">
<span class="eyebrow">Simulate a card purchase</span>
<label>Card<select name="cardId" style="height:44px;border-radius:10px;padding:0 10px;font:inherit">
${cards.map(k => html`<option value="${k.id}">${PRODUCTS[k.product as keyof typeof PRODUCTS].name} ··${k.number.slice(-4)}${k.frozen ? ' (frozen)' : ''}</option>`)}</select></label>
<label>Merchant<input name="merchant" required value="Toko Online Nusantara"></label>
<label>Amount<input name="amount" type="number" min="1000" required value="250000"></label>
<label>How<select name="channel" style="height:44px;border-radius:10px;padding:0 10px;font:inherit">
<option value="in_store">In store</option><option value="online">Online</option><option value="contactless">Contactless</option><option value="overseas">Overseas</option></select></label>
<button class="btn" style="width:max-content">Run purchase</button>
<p class="hint">It follows the card's freeze, switches, daily limit and available credit, just like a real purchase.</p></form></div>`))
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
  // Story S1.34: officer money moves, always through the ledger and always audited.
  app.post('/:id/money', async c => {
    const id = c.req.param('id')
    const f = Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, v]) => [k, String(v).trim()]))
    const amount = Math.floor(Number(f.amount))
    const back = (msg: string) => c.redirect(`/admin/customers/${id}?done=${encodeURIComponent(msg)}`)
    if (!(amount >= 1000)) return back('Enter an amount of at least Rp1,000.')
    const [savings] = await sql`select id from accounts where customer_id = ${id} and kind = 'savings'`
    let memo: object, postings
    if (f.type === 'inflow') {
      const from = f.from || 'PT Maju Jaya'
      memo = { from: { name: from, bank: f.bank || 'BCA' }, channel: f.channel === 'Salary' ? 'Salary' : `BI-FAST · ${f.bank || 'BCA'}`, category: f.channel === 'Salary' ? 'income' : 'transfer' }
      postings = [{ accountId: await houseAccount(sql, 'EXTERNAL_CLEARING'), amount: -amount }, { accountId: savings.id, amount }]
    } else {
      if (!f.reason) return back('Adjustments need a reason. The customer sees it in their history.')
      const sign = f.type === 'remove' ? -1 : 1
      memo = { from: { name: 'ORVYN Bank' }, to: { name: 'ORVYN Bank' }, channel: 'Balance adjustment', category: 'adjustment', note: f.reason }
      postings = [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -sign * amount }, { accountId: savings.id, amount: sign * amount }]
    }
    try {
      const j = await postJournal(sql, { kind: f.type === 'inflow' ? 'inflow' : 'adjustment', memo, postings })
      await notifyJournal(sql, j.id)
      await audit(sql, c.get('officer').id, `money.${f.type}`, id, null, { amount, ...memo })
      return back(`Done: ${f.type === 'remove' ? 'removed' : 'added'} Rp${amount.toLocaleString('en-US')}.`)
    } catch (e) {
      if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') return back("Not done: that would take the balance below zero.")
      throw e
    }
  })

  app.post('/:id/card-purchase', async c => {
    const id = c.req.param('id')
    const f = await c.req.parseBody()
    const [card] = await sql`select id from cards where id = ${String(f.cardId)} and customer_id = ${id}`
    if (!card) return c.text('Unknown card', 400)
    const r = await spendOnCard(sql, { cardId: card.id, merchant: String(f.merchant).slice(0, 40), amount: Math.floor(Number(f.amount)), channel: String(f.channel) as any })
    await audit(sql, c.get('officer').id, 'card.simulate_purchase', card.id, null, { merchant: f.merchant, amount: Number(f.amount), channel: f.channel, ...r })
    const done = r.approved ? `Approved: Rp${Number(f.amount).toLocaleString('en-US')} at ${f.merchant}.` : `Declined: ${r.reason}.`
    return c.redirect(`/admin/customers/${id}?done=${encodeURIComponent(done)}`)
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
