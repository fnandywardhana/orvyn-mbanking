import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { adminRoutes } from '../src/admin.ts'
import { hashPassword, loginOfficer } from '../src/auth.ts'

const sql = connect()
const admin = adminRoutes(sql)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  await sql`insert into officers (email, name, role, password_hash, must_change_password) values ('op@orvyn.test', 'Op', 'operator', ${await hashPassword('rahasia123')}, false)`
})
after(() => sql.end())

test('an Operator reaches customer pages but not Super Admin pages', async () => {
  const r = await loginOfficer(sql, 'op@orvyn.test', 'rahasia123')
  assert.ok(r.ok)
  const get = (path: string) => admin.request(path, { headers: { Cookie: `orvyn_admin=${r.token}` } })
  assert.equal((await get('/')).status, 200)
  assert.equal((await get('/customers')).status, 200)
  assert.equal((await get('/transactions')).status, 200)
  for (const p of ['/settings', '/officers', '/audit', '/reset', '/messages', '/content']) assert.equal((await get(p)).status, 403, p)
})

test('reset demo clears customers and everything that hangs off them, and the ledger stays balanced', async () => {
  const { createCustomer, setPin } = await import('../src/customers.ts')
  const { generateHistory } = await import('../src/personas.ts')
  const { buy } = await import('../src/investments.ts')
  const { openDeposit } = await import('../src/deposits.ts')
  const { exchange } = await import('../src/fx.ts')
  const { checkLedger } = await import('../src/ledger.ts')
  const { ensurePrices } = await import('../src/investments.ts')
  await ensurePrices(sql)
  const r = await sql.begin(async tx => { const r = await createCustomer(tx, { fullName: 'Hendra Wijaya', phone: '081300000091', password: 'rahasia123', persona: 'hendra' }); await generateHistory(tx, 'hendra', r.savings.id, r.customer.id); return r })
  await setPin(sql, r.customer.id, '482915')
  const me = { id: r.customer.id, tier: 'prestige' }
  await sql`update customers set risk_profile = 'aggressive' where id = ${me.id}`
  await buy(sql, me, { productId: 'GOLD', amount: 1_000_000, pin: '482915', idempotencyKey: 'g1' })
  await openDeposit(sql, me, { amount: 5_000_000, tenor: 3, instruction: 'payout', pin: '482915', idempotencyKey: 'd1' })
  await exchange(sql, me, { currency: 'USD', side: 'buy', amount: 50_000, pin: '482915', idempotencyKey: 'f1' })

  await sql`insert into officers (email, name, role, password_hash, must_change_password) values ('boss@orvyn.test', 'Boss', 'super_admin', ${await hashPassword('rahasia123')}, false)`
  const login = await loginOfficer(sql, 'boss@orvyn.test', 'rahasia123')
  const res = await admin.request('/reset', { method: 'POST', headers: { Cookie: `orvyn_admin=${login.token}`, 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'confirm=RESET&password=rahasia123' })
  assert.equal(res.status, 302)
  assert.equal((await sql`select count(*)::int as n from customers`)[0].n, 0)
  assert.equal((await sql`select count(*)::int as n from holdings`)[0].n, 0)
  assert.ok((await checkLedger(sql)).ok)
})
