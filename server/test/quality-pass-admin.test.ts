import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { apiRoutes } from '../src/api.ts'
import { adminRoutes } from '../src/admin.ts'
import { hashPassword, loginOfficer } from '../src/auth.ts'
import { createCustomer } from '../src/customers.ts'
import { generateHistory } from '../src/personas.ts'
import { checkLedger, postJournal, houseAccount, reverse } from '../src/ledger.ts'
import { KIND_LABEL } from '../src/admin-transactions.ts'
import { parseQris } from '../src/qris.ts'
import { ensurePrices } from '../src/investments.ts'

// 0.9.0 quality pass, P1 server side: officers, banners, the admin Kind filter, pocket titles, gold in grams, My QR.

const sql = connect()
const api = apiRoutes(sql)
const admin = adminRoutes(sql)
let cookie = ''
let session = ''

const call = async (method: string, path: string, body?: object) => {
  const res = await api.request(path, { method, body: body ? JSON.stringify(body) : undefined, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session}` } })
  return { status: res.status, body: await res.json() as any }
}
const adminGet = (path: string, c = cookie) => admin.request(path, { headers: { Cookie: `orvyn_admin=${c}` } })
const adminPost = (path: string, form: Record<string, string>, c = cookie) => admin.request(path, {
  method: 'POST', body: new URLSearchParams(form).toString(), headers: { Cookie: `orvyn_admin=${c}`, 'Content-Type': 'application/x-www-form-urlencoded' },
})
const wibDay = (offset: number) => new Date(Date.now() + 7 * 3_600_000 + offset * 86_400_000).toISOString().slice(0, 10)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  await ensurePrices(sql)
  await sql`insert into officers (email, name, role, password_hash, must_change_password) values ('boss@orvyn.test', 'Boss', 'super_admin', ${await hashPassword('rahasia123')}, false)`
  const login = await loginOfficer(sql, 'boss@orvyn.test', 'rahasia123')
  assert.ok(login.ok)
  cookie = login.token
  await sql.begin(async tx => {
    const r = await createCustomer(tx, { fullName: 'Raka Pratama', phone: '081300008001', password: 'rahasia123', persona: 'raka' })
    await generateHistory(tx, 'raka', r.savings.id, r.customer.id)
  })
  session = (await call('POST', '/auth/password', { phone: '081300008001', password: 'rahasia123' })).body.sessionToken
  assert.equal((await call('POST', '/pin', { pin: '482915' })).status, 200)
})
after(() => sql.end())

test('Q-C8: a new officer\'s starting password shows once on the page, never in the URL, a log or the audit log', async () => {
  const logged: string[] = []
  const original = { log: console.log, error: console.error, warn: console.warn, info: console.info }
  for (const k of Object.keys(original) as (keyof typeof original)[]) console[k] = (...a: unknown[]) => { logged.push(a.map(String).join(' ')) }
  let res: Response
  try { res = await adminPost('/officers', { name: 'Dewi Lestari', email: 'dewi@orvyn.test', role: 'content' }) } finally { Object.assign(console, original) }
  assert.equal(res.status, 302)
  assert.equal(res.headers.get('location'), '/admin/officers', 'no query string')
  const first = await (await adminGet('/officers')).text()
  const pw = first.match(/Starting password: <span class="mono" id="pw">([^<]+)</)?.[1]
  assert.ok(pw && /^orvyn\d{6}$/.test(pw), 'shown once')
  assert.match(first, /Officer added/)
  assert.match(first, /This is the only time it's shown\. Dewi must change it at first sign-in\./)
  assert.ok(!(await (await adminGet('/officers')).text()).includes(pw), 'a reload shows it no more')
  const audits = await sql`select before::text, after::text from audit_entries`
  assert.ok(!audits.some(a => `${a.before}${a.after}`.includes(pw)))
  assert.ok(!logged.some(l => l.includes(pw)))
  const login = await loginOfficer(sql, 'dewi@orvyn.test', pw)
  assert.ok(login.ok && login.mustChangePassword, 'they sign in with it and must change it (S0.2 AC1)')
  const dup = await adminPost('/officers', { name: 'Dewi again', email: 'dewi@orvyn.test', role: 'content' })
  assert.match(await dup.text(), /That email already has an officer account\./)
})

test('Q-F8 AC5: after the migration, banners that were there keep their order as positions 1..n and show from when they were made', async () => {
  const rows = await sql`select sort, starts_at from banners order by sort, id`
  assert.deepEqual(rows.map(r => r.sort), rows.map((_, i) => i + 1))
  assert.ok(rows.every(r => r.starts_at <= new Date()))
})

test('Q-F8 AC2-4: banners get a start date and a position, can be edited with an audit entry, and the app shows live ones in order', async () => {
  const base = { body: 'One line', detail: 'More', image: '', action: 'qris', ends: wibDay(30) }
  assert.equal((await adminPost('/content/banners', { ...base, title: 'Tomorrow', starts: wibDay(1), sort: '1' })).status, 302)
  assert.equal((await adminPost('/content/banners', { ...base, title: 'First now', starts: wibDay(0), sort: '1' })).status, 302)
  const bad = await adminPost('/content/banners', { ...base, title: 'Backwards', starts: wibDay(5), ends: wibDay(2), sort: '2' })
  assert.equal(bad.status, 400)
  const badHtml = await bad.text()
  assert.match(badHtml, /Show until can&#39;t be before Show from\./)
  assert.match(badHtml, /value="Backwards"/, 'what was typed stays')
  const titles = (await call('GET', '/content')).body.banners.map((b: any) => b.title)
  assert.ok(!titles.includes('Tomorrow'), 'not before its start date')
  assert.ok(titles.includes('First now'))

  await sql`update banners set starts_at = now() - interval '1 minute' where title = 'Tomorrow'` // the day comes
  const ordered = (await call('GET', '/content')).body.banners.map((b: any) => b.title)
  assert.ok(ordered.includes('Tomorrow'))

  const [b] = await sql`select id from banners where title = 'First now'`
  const edited = await adminPost(`/content/banners/${b.id}`, { ...base, title: 'Renamed', starts: wibDay(0), sort: '99' })
  assert.equal(edited.status, 302)
  assert.match(decodeURIComponent(edited.headers.get('location') ?? ''), /Banner saved\./)
  const [a] = await sql`select before, after from audit_entries where action = 'banner.edit' and target = ${String(b.id)}`
  assert.equal(a.before.title, 'First now')
  assert.equal(a.after.title, 'Renamed')
  const last = (await call('GET', '/content')).body.banners.at(-1)
  assert.equal(last.title, 'Renamed', 'position 99 shows last')
  const page = await (await adminGet(`/content?edit=${b.id}`)).text()
  assert.match(page, /Edit banner/)
  assert.match(page, /Save changes/)
  assert.match(await (await adminGet('/content')).text(), /chip ok"[^>]*>Live/)
})

test('Q-F8 AC1: every kind in the ledger has a label in the Kind filter, and filtering shows only that kind', async () => {
  const p = await call('POST', '/pockets', { name: 'Trip', image: 'travel' })
  await call('POST', `/pockets/${p.body.id}/move`, { direction: 'in', amount: 100_000, idempotencyKey: 'qpa-p1' })
  await call('POST', '/fx/exchange', { currency: 'USD', side: 'buy', amount: 10_000, pin: '482915', idempotencyKey: 'qpa-f1' })
  await call('POST', '/deposits', { amount: 1_000_000, tenor: 1, instruction: 'payout', pin: '482915', idempotencyKey: 'qpa-d1' })
  await sql`update customers set risk_profile = 'aggressive' where phone = '081300008001'`
  await call('POST', '/invest/GOLD/buy', { amount: 100_000, pin: '482915', idempotencyKey: 'qpa-i1' })
  const kinds = (await sql`select distinct kind from journal_entries`).map(r => r.kind)
  assert.ok(kinds.length >= 8, kinds.join())
  for (const k of [...kinds, 'deposit_interest', 'bond_coupon', 'adjustment', 'reversal', 'inflow', 'opening']) assert.ok(KIND_LABEL[k], `no label for ${k}`)
  const html = await (await adminGet('/transactions?kind=pocket_move')).text()
  assert.match(html, /<optgroup label="Wealth">/)
  const rows = [...html.matchAll(/<td>([A-Z][^<]*)<\/td>\n<td>/g)].map(m => m[1])
  assert.ok(rows.length > 0 && rows.every(r => r === 'Pocket move'), rows.join())
})

test('Q-G10 (PO-20): a pocket\'s history says which way the money went, for old moves too, and the Journal Entries are untouched', async () => {
  const pocket = (await call('GET', '/pockets')).body.pockets.find((x: any) => x.name === 'Trip')
  const [savings] = await sql`select a.id from accounts a join customers c on c.id = a.customer_id where c.phone = '081300008001' and a.kind = 'savings'`
  // A move written the 0.8.1 way, before 0.9.0.
  const old = await postJournal(sql, { kind: 'pocket_move', memo: { from: { name: 'Trip' }, to: { name: 'ORVYN Everyday' }, channel: 'Pocket', category: 'pocket' },
    postings: [{ accountId: pocket.id, amount: -20_000 }, { accountId: savings.id, amount: 20_000 }] })
  const memoBefore = (await sql`select memo from journal_entries where id = ${old.id}`)[0].memo
  const side = (await call('GET', `/accounts/${pocket.id}/transactions`)).body.items
  assert.ok(side.some((t: any) => t.amount > 0 && t.title === 'Added from ORVYN Everyday'))
  assert.equal(side.find((t: any) => t.id === old.id).title, 'Moved to ORVYN Everyday')
  assert.equal((await call('GET', `/transactions/${old.id}?account=${pocket.id}`)).body.title, 'Moved to ORVYN Everyday')
  assert.equal((await call('GET', `/transactions/${old.id}`)).body.title, 'Trip', 'the savings side still names the pocket')
  assert.deepEqual((await sql`select memo from journal_entries where id = ${old.id}`)[0].memo, memoBefore)
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('Q-01-F7 (design.md 17.2): a card bill payment reads the same in the card list and the detail opened from it', async () => {
  const card = (await call('GET', '/cards')).body.cards.find((c: any) => c.kind === 'credit')
  assert.ok(card?.accountId, 'the card says which account its list is on')
  const paid = await call('POST', `/cards/${card.id}/pay`, { amount: 100_000, pin: '482915', idempotencyKey: 'qpa-f7' })
  assert.equal(paid.status, 200)
  const row = (await call('GET', `/cards/${card.id}/transactions`)).body.items.find((t: any) => t.id === paid.body.transactionId)
  assert.deepEqual([row.title, row.amount], ['Payment from your savings', 100_000])
  const detail = (await call('GET', `/transactions/${row.id}?account=${card.accountId}`)).body
  assert.deepEqual([detail.title, detail.amount, detail.subtitle], ['Payment from your savings', 100_000, 'Credit card payment'])
  const savings = (await call('GET', `/transactions/${row.id}`)).body
  assert.deepEqual([savings.title, savings.amount], [`${card.name} bill`, -100_000], 'savings side unchanged')
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('Q-F3: History filters by several kinds at once, and a reversal shows under the type it reversed', async () => {
  const [savings] = await sql`select a.id from accounts a join customers c on c.id = a.customer_id where c.phone = '081300008001' and a.kind = 'savings'`
  const [q] = await sql`select j.id from journal_entries j join postings p on p.journal_id = j.id where p.account_id = ${savings.id} and j.kind = 'qris_payment' limit 1`
  const r = await reverse(sql, q.id, { reason: 'test', channel: 'Reversal', category: 'reversal' })
  const items = (await call('GET', `/accounts/${savings.id}/transactions?kinds=qris_payment,bill_payment&limit=100`)).body.items
  assert.ok(items.length > 0 && items.every((t: any) => ['qris_payment', 'bill_payment', 'reversal'].includes(t.kind)))
  assert.ok(items.some((t: any) => t.id === r.id), 'the reversal of a QRIS payment is under QRIS')
  const transfers = (await call('GET', `/accounts/${savings.id}/transactions?kinds=internal_transfer&limit=100`)).body.items
  assert.ok(!transfers.some((t: any) => t.id === r.id))
})

test('Q-F6 AC4: gold bought in grams costs exactly grams × price rounded up, the same Rupiah as shown before the PIN', async () => {
  await sql`update settings set value = '{"buy": 1400000, "sell": 1500000}' where key = 'gold_price'`
  const before = (await sql`select coalesce(sum(quantity), 0) as q from holdings h join customers c on c.id = h.customer_id where c.phone = '081300008001' and product_id = 'GOLD'`)[0].q
  const r = await call('POST', '/invest/GOLD/buy', { grams: 0.05, pin: '482915', idempotencyKey: 'qpa-g1' })
  assert.equal(r.status, 200, JSON.stringify(r.body))
  const posts = await sql`select amount from postings where journal_id = ${r.body.transactionId} order by amount`
  assert.deepEqual(posts.map(p => Number(p.amount)), [-75_000, 75_000], 'not Rp75,001 from a float')
  const after = (await sql`select sum(quantity) as q from holdings h join customers c on c.id = h.customer_id where c.phone = '081300008001' and product_id = 'GOLD'`)[0].q
  assert.equal(Math.round((Number(after) - Number(before)) * 10_000), 500)
  assert.equal((await call('POST', '/invest/GOLD/buy', { grams: 0.03, pin: '482915', idempotencyKey: 'qpa-g2' })).body.message, 'The smallest purchase is Rp50,000.')
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('Q-F4 AC4: My QR with an amount carries the account number and that amount', async () => {
  const mine = await call('GET', '/qris/mine?amount=50000')
  const q = parseQris(mine.body.payload)
  assert.deepEqual(q, { kind: 'p2p', account: mine.body.accountNumber, amount: 50_000 })
})
