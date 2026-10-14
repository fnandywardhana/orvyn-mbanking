import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { apiRoutes } from '../src/api.ts'
import { createCustomer } from '../src/customers.ts'
import { generateHistory } from '../src/personas.ts'

const sql = connect()
const api = apiRoutes(sql)
let session = ''

const call = async (method: string, path: string, body?: object) => {
  const res = await api.request(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(session ? { Authorization: `Bearer ${session}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, body: await res.json() as any }
}

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  await sql.begin(async tx => {
    const r = await createCustomer(tx, { fullName: 'Raka Pratama', phone: '081299990000', password: 'rahasia123', persona: 'raka' })
    await generateHistory(tx, 'raka', r.savings.id, r.customer.id)
  })
})
after(() => sql.end())

test('a tester signs in, sets a PIN, and sees home with balance and recent transactions', async () => {
  const wrong = await call('POST', '/auth/password', { phone: '0812 9999 0000', password: 'salah' })
  assert.equal(wrong.status, 401)
  assert.match(wrong.body.message, /don't match/)

  const ok = await call('POST', '/auth/password', { phone: '0812 9999 0000', password: 'rahasia123' })
  assert.equal(ok.status, 200)
  assert.equal(ok.body.needsPin, true)
  session = ok.body.sessionToken

  assert.equal((await call('POST', '/pin', { pin: '123456' })).status, 400)
  assert.equal((await call('POST', '/pin', { pin: '482915' })).status, 200)

  const home = await call('GET', '/home')
  assert.equal(home.body.customer.firstName, 'Raka')
  assert.equal(home.body.accounts.length, 1)
  assert.equal(home.body.totalBalance, home.body.accounts[0].balance)
  assert.equal(home.body.recent.length, 5)

  const pin = await call('POST', '/auth/pin', { deviceToken: ok.body.deviceToken, pin: '000001' })
  assert.equal(pin.body.message, 'Wrong PIN. 4 tries left.')
})

test('transaction history pages without gaps and can be filtered', async () => {
  const home = await call('GET', '/home')
  const id = home.body.accounts[0].id
  const seen = new Set<string>()
  let next: string | null = null
  do {
    const page = await call('GET', `/accounts/${id}/transactions?limit=50${next ? `&before=${encodeURIComponent(next)}` : ''}`)
    for (const t of page.body.items) seen.add(t.id)
    next = page.body.next
  } while (next)
  const [{ n }] = await sql`select count(*)::int as n from postings where account_id = ${id}`
  assert.equal(seen.size, n)

  const incoming = await call('GET', `/accounts/${id}/transactions?direction=in&limit=100`)
  assert.ok(incoming.body.items.every((t: any) => t.amount > 0))
  const salary = await call('GET', `/accounts/${id}/transactions?q=Nusantara`)
  assert.ok(salary.body.items.length >= 5 && salary.body.items.every((t: any) => t.title === 'PT Nusantara Digital'))
})

test("a customer can't read someone else's account", async () => {
  const [other] = await sql`select id from accounts where kind = 'house' limit 1`
  assert.equal((await call('GET', `/accounts/${other.id}/transactions`)).status, 404)
})

test('a tester saves a recipient, sends money, and the recipient shows up in the list', async () => {
  const other = await sql.begin(tx => createCustomer(tx, { fullName: 'Dimas Prakoso', phone: '081299990001', password: 'rahasia123' }))
  const lookup = await call('POST', '/transfers/lookup', { bank: 'ORVYN', accountNumber: other.savings.number })
  assert.equal(lookup.body.name, 'Dimas Prakoso')
  assert.equal((await call('POST', '/recipients', { bank: 'ORVYN', accountNumber: other.savings.number, nickname: 'Dimas kantor' })).status, 200)

  const opts = await call('GET', '/transfers/options?bank=ORVYN&amount=50000')
  assert.deepEqual(opts.body.methods.map((m: any) => m.method), ['internal'])
  const sent = await call('POST', '/transfers', { bank: 'ORVYN', accountNumber: other.savings.number, amount: 50_000, method: 'internal', pin: '482915', note: 'Patungan' })
  assert.equal(sent.status, 200)
  assert.match(sent.body.reference, /^[0-9A-F]{12}$/)

  const ext = await call('POST', '/transfers', { bank: 'BCA', accountNumber: '9876543210', amount: 75_000, method: 'bifast', pin: '482915' })
  assert.equal(ext.status, 200)
  const list = await call('GET', '/recipients')
  assert.equal(list.body.saved[0].nickname, 'Dimas kantor')
  assert.ok(list.body.recent.some((r: any) => r.bank === 'BCA' && r.accountNumber === '9876543210'))
  assert.ok(!list.body.recent.some((r: any) => r.accountNumber === other.savings.number), 'saved recipients are not repeated in recent')

  const wrong = await call('POST', '/transfers', { bank: 'ORVYN', accountNumber: other.savings.number, amount: 50_000, method: 'internal', pin: '000001' })
  assert.equal(wrong.body.error, 'WRONG_PIN')
  assert.equal(wrong.body.attemptsLeft, 4)
})
