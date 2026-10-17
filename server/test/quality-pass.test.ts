import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { apiRoutes } from '../src/api.ts'
import { adminRoutes } from '../src/admin.ts'
import { hashPassword, loginOfficer } from '../src/auth.ts'
import { createCustomer } from '../src/customers.ts'
import { generateHistory } from '../src/personas.ts'
import { checkLedger } from '../src/ledger.ts'
import { sendOtp, verifyOtp, submitRegistration } from '../src/registrations.ts'

// 0.9.0 quality pass, server side (stories.md "Definisi selesai" item 1).

const sql = connect()
const api = apiRoutes(sql)
const admin = adminRoutes(sql)

const call = async (method: string, path: string, body?: object, session?: string) => {
  const res = await api.request(path, {
    method, body: body ? JSON.stringify(body) : undefined,
    headers: { 'Content-Type': 'application/json', ...(session ? { Authorization: `Bearer ${session}` } : {}) },
  })
  return { status: res.status, body: await res.json() as any }
}
const details = { fullName: 'Nadia Putri', birthDate: '1999-01-01', address: 'Jl. Melati 5, Jakarta', occupation: 'Employee', incomeRange: 'Rp5–10 million', purpose: 'Savings' }

let officerCookie = ''
const post = (path: string, form: Record<string, string>) => admin.request(path, {
  method: 'POST', body: new URLSearchParams(form).toString(),
  headers: { Cookie: `orvyn_admin=${officerCookie}`, 'Content-Type': 'application/x-www-form-urlencoded' },
})

/** A customer signed in on a phone with PIN 482915. */
async function tester(phone: string, persona?: string) {
  await sql.begin(async tx => {
    const r = await createCustomer(tx, { fullName: 'Raka Pratama', phone, password: 'rahasia123', persona })
    if (persona) await generateHistory(tx, persona, r.savings.id, r.customer.id)
  })
  const s = await call('POST', '/auth/password', { phone, password: 'rahasia123' })
  assert.equal((await call('POST', '/pin', { pin: '482915' }, s.body.sessionToken)).status, 200)
  return s.body as { sessionToken: string, deviceToken: string }
}
const failedAttempts = async (phone: string) => (await sql`select failed_attempts, locked from customers where phone = ${phone}`)[0]

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  await sql`insert into officers (email, name, role, password_hash, must_change_password) values ('boss@orvyn.test', 'Boss', 'super_admin', ${await hashPassword('rahasia123')}, false)`
  const login = await loginOfficer(sql, 'boss@orvyn.test', 'rahasia123')
  assert.ok(login.ok)
  officerCookie = login.token
})
after(() => sql.end())

test('Q-C1: Change PIN checks the current PIN first and counts wrong tries with unlock and payments; the 5th locks', async () => {
  const me = await tester('081300009001')
  const check = (pin: string) => call('POST', '/me/pin/check', { pin }, me.sessionToken)
  const wrong = await check('000001')
  assert.equal(wrong.body.error, 'WRONG_PIN')
  assert.equal(wrong.body.message, "That's not your current PIN. 4 tries left.")
  assert.equal(wrong.body.attemptsLeft, 4)
  assert.equal((await check('000001')).body.message, "That's not your current PIN. 3 tries left.")
  assert.equal((await failedAttempts('081300009001')).failed_attempts, 2)
  // Shared with the unlock screen (AC2): the next wrong PIN there leaves 2.
  assert.equal((await call('POST', '/auth/pin', { deviceToken: me.deviceToken, pin: '000001' })).body.message, 'Wrong PIN. 2 tries left.')
  const again = (await call('POST', '/auth/pin', { deviceToken: me.deviceToken, pin: '482915' })).body.sessionToken
  assert.equal((await failedAttempts('081300009001')).failed_attempts, 0, 'a right PIN resets the count (AC4)')
  for (const left of [4, 3, 2]) assert.equal((await call('POST', '/me/pin/check', { pin: '000001' }, again)).body.attemptsLeft, left)
  assert.equal((await call('POST', '/me/pin/check', { pin: '000001' }, again)).body.message, "That's not your current PIN. 1 try left.")
  const fifth = await call('POST', '/me/pin/check', { pin: '000001' }, again)
  assert.equal(fifth.body.error, 'LOCKED')
  assert.equal(fifth.body.message, 'Your account is locked. Ask the ORVYN team to unlock it.')
  assert.equal((await failedAttempts('081300009001')).locked, true)
  assert.equal((await call('GET', '/home', undefined, again)).body.error, 'signed_out', 'locking ends the session')
})

test('Q-C1 AC6: the 0.8.1 Change PIN call with a wrong current PIN counts too; a right one changes it', async () => {
  const me = await tester('081300009002')
  const r = await call('POST', '/me/pin', { oldPin: '000001', newPin: '135791' }, me.sessionToken)
  assert.equal(r.body.error, 'WRONG_PIN')
  assert.equal(r.body.message, "That's not your current PIN. 4 tries left.")
  assert.equal((await failedAttempts('081300009002')).failed_attempts, 1)
  assert.equal((await call('POST', '/me/pin/check', { pin: '482915' }, me.sessionToken)).status, 200)
  assert.equal((await call('POST', '/me/pin', { oldPin: '482915', newPin: '111111' }, me.sessionToken)).body.error, 'WEAK_PIN')
  assert.equal((await call('POST', '/me/pin', { oldPin: '482915', newPin: '135791' }, me.sessionToken)).status, 200)
  assert.ok((await call('POST', '/auth/pin', { deviceToken: me.deviceToken, pin: '135791' })).body.sessionToken)
})

test('Q-C2: a wrong current password counts toward the same 5 and locks on the 5th', async () => {
  const me = await tester('081300009003')
  const pw = (old: string, session = me.sessionToken) => call('POST', '/me/password', { oldPassword: old, newPassword: 'baru12345' }, session)
  const r = await pw('salah123')
  assert.equal(r.body.error, 'WRONG_PASSWORD')
  assert.equal(r.body.message, "That's not your current password. 4 tries left.")
  assert.equal((await failedAttempts('081300009003')).failed_attempts, 1)
  assert.equal((await call('POST', '/me/password', { oldPassword: 'rahasia123', newPassword: 'short' }, me.sessionToken)).body.error, 'WEAK_PASSWORD')
  assert.equal((await failedAttempts('081300009003')).failed_attempts, 0, 'the right current password resets the count (AC3)')
  for (let i = 0; i < 4; i++) await pw('salah123')
  const fifth = await pw('salah123')
  assert.equal(fifth.body.error, 'LOCKED')
  assert.equal((await failedAttempts('081300009003')).locked, true)
})

test('Q-B5 AC4 and design 13: card PIN checks say how many tries are left and count like the rest', async () => {
  const me = await tester('081300009004', 'raka')
  const cards = (await call('GET', '/cards', undefined, me.sessionToken)).body.cards
  const r = await call('POST', `/cards/${cards[0].id}/details`, { pin: '000001' }, me.sessionToken)
  assert.equal(r.body.message, 'Wrong PIN. 4 tries left.')
  const credit = cards.find((c: any) => c.kind === 'credit')
  const pay = await call('POST', `/cards/${credit.id}/pay`, { amount: 10_000, pin: '000001' }, me.sessionToken)
  assert.equal(pay.body.message, 'Wrong PIN. 3 tries left.')
})

test('Q-B9: the locked screen can ask whether this phone is still locked, without a PIN, and learns nothing else', async () => {
  const me = await tester('081300009005')
  assert.deepEqual((await call('POST', '/auth/status', { deviceToken: me.deviceToken })).body, { locked: false, firstName: 'Raka' })
  await sql`update customers set locked = true where phone = '081300009005'` // locked by an officer (PO-7)
  assert.equal((await call('POST', '/auth/status', { deviceToken: me.deviceToken })).body.locked, true)
  assert.equal((await call('POST', '/auth/pin', { deviceToken: me.deviceToken, pin: '482915' })).body.message, 'Your account is locked. Ask the ORVYN team to unlock it.')
  await sql`update customers set locked = false, failed_attempts = 0 where phone = '081300009005'`
  assert.equal((await call('POST', '/auth/status', { deviceToken: me.deviceToken })).body.locked, false)
  assert.equal((await call('POST', '/auth/status', { deviceToken: 'not-a-phone' })).status, 401)
})

test('Q-D6: Forgot PIN sets a new PIN without the old one only within 10 minutes of a password sign-in, and the phone stays registered', async () => {
  const me = await tester('081300009006')
  // Unlocking with the PIN gives a session that can't skip the old PIN.
  const pinSession = (await call('POST', '/auth/pin', { deviceToken: me.deviceToken, pin: '482915' })).body.sessionToken
  const refused = await call('POST', '/pin', { pin: '246813' }, pinSession)
  assert.equal(refused.body.error, 'PIN_TIMEOUT')
  assert.equal(refused.body.message, 'That took too long. Sign in with your password again.')

  const [{ n: before }] = await sql`select count(*)::int as n from inbox_messages i join customers c on c.id = i.customer_id where c.phone = '081300009006'`
  const pw = await call('POST', '/auth/password', { phone: '081300009006', password: 'rahasia123', deviceToken: me.deviceToken })
  assert.equal(pw.body.deviceToken, me.deviceToken, 'same phone, same Registered Device')
  const [{ n: after }] = await sql`select count(*)::int as n from inbox_messages i join customers c on c.id = i.customer_id where c.phone = '081300009006'`
  assert.equal(after, before, 'no "signed in on a new phone" message')
  assert.equal((await call('POST', '/pin', { pin: '123456' }, pw.body.sessionToken)).body.error, 'WEAK_PIN')
  await sql`update customer_sessions set password_at = now() - interval '11 minutes' where token = ${pw.body.sessionToken}`
  assert.equal((await call('POST', '/pin', { pin: '246813' }, pw.body.sessionToken)).body.error, 'PIN_TIMEOUT')
  await sql`update customer_sessions set password_at = now() - interval '9 minutes' where token = ${pw.body.sessionToken}`
  assert.equal((await call('POST', '/pin', { pin: '246813' }, pw.body.sessionToken)).status, 200)
  assert.ok((await call('POST', '/auth/pin', { deviceToken: me.deviceToken, pin: '246813' })).body.sessionToken)
})

test('Q-D2: the NIK check needs an OTP-verified sign-up, says only free or taken, allows 10 checks, and never logs the NIK', async () => {
  // A customer and a waiting application already hold these NIKs.
  await sql`update customers set nik = '3175000000000001' where phone = '081300009001'`
  const pending = await verifyOtp(sql, '081377790001', (await sendOtp(sql, '081377790001')).code)
  await submitRegistration(sql, pending, { ...details, nik: '3175000000000002' }, 'rahasia123', '482915')

  assert.equal((await call('POST', '/register/nik', { token: 'made-up', nik: '3175000000000009' })).body.error, 'NOT_FOUND')
  const token = await verifyOtp(sql, '081377790002', (await sendOtp(sql, '081377790002')).code)
  const logged: string[] = []
  const original = { log: console.log, error: console.error, warn: console.warn, info: console.info }
  for (const k of Object.keys(original) as (keyof typeof original)[]) console[k] = (...a: unknown[]) => { logged.push(a.map(String).join(' ')) }
  try {
    const free = await call('POST', '/register/nik', { token, nik: '3175000000000009' })
    assert.deepEqual(free.body, { available: true })
    const customer = await call('POST', '/register/nik', { token, nik: '3175000000000001' })
    assert.equal(customer.body.error, 'NIK_TAKEN')
    assert.equal(customer.body.message, 'This NIK is already registered with ORVYN.')
    assert.deepEqual(Object.keys(customer.body).sort(), ['error', 'message'], 'nothing about the other person')
    assert.equal((await call('POST', '/register/nik', { token, nik: '3175000000000002' })).body.error, 'NIK_TAKEN')
    for (let i = 4; i <= 10; i++) assert.equal((await call('POST', '/register/nik', { token, nik: '3175000000000009' })).status, 200)
    const eleventh = await call('POST', '/register/nik', { token, nik: '3175000000000009' })
    assert.equal(eleventh.body.error, 'TOO_MANY_CHECKS')
    assert.equal(eleventh.body.message, 'Too many NIK checks for this sign-up. Start again from your phone number.')
  } finally { Object.assign(console, original) }
  assert.ok(!logged.some(l => l.includes('317500000000')), 'no NIK in the server log')
  // AC6: the server still refuses a taken NIK when the application is sent.
  await assert.rejects(submitRegistration(sql, token, { ...details, nik: '3175000000000001' }, 'rahasia123', '482915'), /already registered/)
})

test('Q-C7: the server refuses a rejection without a reason, even only spaces, and the application stays pending', async () => {
  const token = await verifyOtp(sql, '081377790003', (await sendOtp(sql, '081377790003')).code)
  await submitRegistration(sql, token, { ...details, nik: '3175000000000003' }, 'rahasia123', '482915')
  const [r] = await sql`select id from registrations where token = ${token}`
  for (const reason of ['', '   ']) {
    const res = await post(`/applications/${r.id}/reject`, { reason })
    assert.equal(res.status, 400)
    assert.match(await res.text(), /Write a reason first\. The applicant sees it in the app\./)
    assert.equal((await sql`select status from registrations where id = ${r.id}`)[0].status, 'pending')
  }
  assert.equal((await post(`/applications/${r.id}/reject`, { reason: 'The NIK and name do not match.' })).status, 302)
  assert.deepEqual({ ...(await sql`select status, reason from registrations where id = ${r.id}`)[0] }, { status: 'rejected', reason: 'The NIK and name do not match.' })
})

test('Q-F2 and Q-B2: payments hand back the server time for the success screen; an early withdrawal hands back its amount', async () => {
  const me = await tester('081300009007', 'raka')
  const other = (await sql`select number from accounts a join customers c on c.id = a.customer_id where c.phone = '081300009006' and a.kind = 'savings'`)[0].number
  const t = await call('POST', '/transfers', { bank: 'ORVYN', accountNumber: other, amount: 10_000, method: 'internal', pin: '482915', idempotencyKey: 'qp-t1' }, me.sessionToken)
  assert.ok(t.body.transactionId && t.body.reference && !isNaN(Date.parse(t.body.at)))
  const [j] = await sql`select created_at from journal_entries where id = ${t.body.transactionId}`
  assert.equal(new Date(t.body.at).getTime(), j.created_at.getTime(), 'the same time as the transaction detail')

  const d = await call('POST', '/deposits', { amount: 1_000_000, tenor: 3, instruction: 'payout', pin: '482915', idempotencyKey: 'qp-d1' }, me.sessionToken)
  assert.equal(d.status, 200)
  const [dep] = (await call('GET', '/deposits', undefined, me.sessionToken)).body.deposits
  const savings = async () => (await call('GET', '/home', undefined, me.sessionToken)).body.accounts[0].balance
  const before = await savings()
  const w = await call('POST', `/deposits/${dep.id}/withdraw`, { pin: '482915' }, me.sessionToken)
  assert.equal(w.body.amount, 1_000_000)
  assert.ok(w.body.reference && w.body.at)
  assert.equal(await savings(), before + 1_000_000)
  const posts = await sql`select amount from postings where journal_id = ${w.body.transactionId} order by amount`
  assert.deepEqual(posts.map(p => Number(p.amount)), [-1_000_000, 1_000_000], 'principal only, no interest posting')
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('Q-C3: Reset demo works with a passkey and empties every table that points at customers, including tables added later', async () => {
  // Fill every customer table: real flows where cheap, a plain row where not.
  const me = await tester('081300009008', 'hendra')
  const s = me.sessionToken
  const [cu] = await sql`select id from customers where phone = '081300009008'`
  const other = (await sql`select number from accounts a join customers c on c.id = a.customer_id where c.phone = '081300009007' and a.kind = 'savings'`)[0].number
  await call('POST', '/recipients', { bank: 'BCA', accountNumber: '9876543210', nickname: 'Rent' }, s)
  await call('POST', '/bills/pay', { biller: 'PLN_TOKEN', number: '12345678901', productId: '20', pin: '482915', idempotencyKey: 'qp-b1' }, s)
  await call('POST', '/scheduled', { bank: 'BCA', accountNumber: '9876543210', amount: 50_000, method: 'bifast', repeat: 'monthly', firstDate: '2099-01-01', pin: '482915' }, s)
  await call('POST', '/splits', { total: 100_000, note: 'Dinner', shares: [{ accountNumber: other, amount: 50_000 }] }, s)
  await call('POST', '/budgets', { category: 'food', limit: 1_000_000 }, s)
  await call('POST', '/deposits', { amount: 1_000_000, tenor: 1, instruction: 'payout', pin: '482915', idempotencyKey: 'qp-d2' }, s)
  const [j] = await sql`select p.journal_id from postings p join accounts a on a.id = p.account_id where a.customer_id = ${cu.id} limit 1`
  await sql`insert into category_overrides (customer_id, journal_id, category) values (${cu.id}, ${j.journal_id}, 'other') on conflict do nothing`
  await sql`insert into budget_alerts (customer_id, category, month, threshold) values (${cu.id}, 'food', '2026-10', 80) on conflict do nothing`
  await sql`insert into vyna_usage (customer_id, day) values (${cu.id}, current_date) on conflict do nothing`
  await sql`insert into holdings (customer_id, product_id, quantity, cost) values (${cu.id}, 'GOLD', 0.5, 750000) on conflict do nothing`
  await sql`insert into bond_coupons (customer_id, product_id, month) select ${cu.id}, id, '2026-10' from products where kind = 'bond' limit 1`
  await sql`insert into passkeys (id, customer_id, public_key) values ('cred-1', ${cu.id}, '\\x00')`
  const [a] = await sql`insert into announcements (title, body, ends_at) values ('Hi', 'News', now() + interval '1 day') returning id`
  await sql`insert into announcement_seen (announcement_id, customer_id) values (${a.id}, ${cu.id})`
  await sql`update registrations set customer_id = ${cu.id} where id = (select id from registrations limit 1)`

  // Every table with a foreign key to customers, and the rows in it that point at one (house accounts have none).
  const tables = await sql`
    select c.conrelid::regclass::text as name, string_agg(a.attname || ' is not null', ' or ') as pointing
    from pg_constraint c join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any(c.conkey)
    where c.contype = 'f' and c.confrelid = 'customers'::regclass group by 1 order by 1`
  const rows = async (t: { name: string, pointing: string }) => (await sql.unsafe(`select count(*)::int as n from ${t.name} where ${t.pointing}`))[0].n
  assert.ok(tables.length >= 21, `${tables.length} tables point at customers`)
  for (const t of tables) assert.ok(await rows(t as any) > 0, `${t.name} needs a row in this test before the reset (new table? add it here and to Reset demo)`)
  const [{ officers }] = await sql`select count(*)::int as officers from officers`
  const [{ audits }] = await sql`select count(*)::int as audits from audit_entries`

  const res = await post('/reset', { confirm: 'RESET', password: 'rahasia123' })
  assert.equal(res.status, 302)
  assert.match(decodeURIComponent(res.headers.get('location') ?? ''), /Demo reset\. \d+ customers removed\./)
  for (const t of tables) assert.equal(await rows(t as any), 0, `${t.name} has no customer rows after the reset`)
  assert.equal((await sql`select count(*)::int as n from customers`)[0].n, 0)
  assert.equal((await sql`select count(*)::int as n from passkeys`)[0].n, 0)
  assert.equal((await sql`select count(*)::int as n from officers`)[0].n, officers)
  assert.equal((await sql`select count(*)::int as n from audit_entries`)[0].n, audits + 1)
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})
