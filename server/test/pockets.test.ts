import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { createPocket, listPockets, movePocket, updatePocket, closePocket, runAutoSave, PocketError } from '../src/pockets.ts'

const sql = connect()
let raka: any
const key = () => crypto.randomUUID()
const rejects = (p: Promise<unknown>, code: string) => assert.rejects(p, (e: PocketError) => e.code === code)
const inbox = async () => (await sql`select title from inbox_messages where customer_id = ${raka.id} order by created_at`).map(r => r.title)
const fund = async (accountId: string, kind: string, amount: number, memo: object = {}) =>
  postJournal(sql, { kind, memo, postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -amount }, { accountId, amount }] })
const spend = async (amount: number) =>
  postJournal(sql, { kind: 'qris_payment', memo: { category: 'food' }, postings: [{ accountId: raka.savings.id, amount: -amount }, { accountId: await houseAccount(sql, 'MERCHANT_SETTLEMENT'), amount }] })

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Raka Pratama', phone: '081300000011', password: 'rahasia123' }))
  raka = { ...r.customer, savings: r.savings }
  await fund(raka.savings.id, 'adjustment', 2_000_000)
})
after(() => sql.end())

test('a pocket holds the customer\'s own money: in and out with no fee, never more than there is', async () => {
  const id = await createPocket(sql, raka.id, { name: 'Bali trip', image: 'travel', target: 1_000_000, targetDate: '2027-06-01' })
  await movePocket(sql, raka.id, id, 'in', 300_000, key())
  assert.equal(await balanceOf(sql, id), 300_000)
  assert.equal(await balanceOf(sql, raka.savings.id), 1_700_000)
  await movePocket(sql, raka.id, id, 'out', 100_000, key())
  assert.equal(await balanceOf(sql, id), 200_000)
  await rejects(movePocket(sql, raka.id, id, 'out', 200_001, key()), 'INSUFFICIENT_FUNDS')
  await rejects(movePocket(sql, raka.id, id, 'in', 5_000_000, key()), 'INSUFFICIENT_FUNDS')
  assert.deepEqual((await listPockets(sql, raka.id)).map(p => [p.name, p.balance, p.target]), [['Bali trip', 200_000, 1_000_000]])
  assert.ok((await checkLedger(sql)).ok)
})

test('reaching the target is celebrated once, and again only after dropping below it', async () => {
  const [p] = await listPockets(sql, raka.id)
  assert.equal((await movePocket(sql, raka.id, p.id, 'in', 800_000, key())).reached, true)
  assert.equal((await movePocket(sql, raka.id, p.id, 'in', 10_000, key())).reached, false)
  assert.equal((await inbox()).filter(t => t === 'Goal reached').length, 1)
  await movePocket(sql, raka.id, p.id, 'out', 500_000, key())
  assert.equal((await movePocket(sql, raka.id, p.id, 'in', 500_000, key())).reached, true)
})

test('only ten pockets, and closing one sends its money home first', async () => {
  for (let i = 0; i < 9; i++) await createPocket(sql, raka.id, { name: `Pocket ${i}`, image: 'gift' })
  await rejects(createPocket(sql, raka.id, { name: 'One too many', image: 'gift' }), 'TOO_MANY')
  const [bali] = await listPockets(sql, raka.id)
  const before = await balanceOf(sql, raka.savings.id)
  await closePocket(sql, raka.id, bali.id, key())
  assert.equal(await balanceOf(sql, raka.savings.id), before + bali.balance)
  assert.equal((await listPockets(sql, raka.id)).length, 9)
  await rejects(movePocket(sql, raka.id, bali.id, 'in', 1000, key()), 'NOT_FOUND')
})

test('round-up saves the change from each payment made after the rule was set, once', async () => {
  const [p] = await listPockets(sql, raka.id)
  await spend(12_500) // before the rule: ignored
  await updatePocket(sql, raka.id, p.id, { name: p.name, image: p.image, rule: { type: 'roundup' } })
  await spend(23_000)
  await spend(40_000) // already round
  await runAutoSave(sql)
  await runAutoSave(sql)
  assert.equal(await balanceOf(sql, p.id), 7_000)
})

test('pay-day saving takes a share of salary; a skipped save is told once and not retried', async () => {
  const [, p] = await listPockets(sql, raka.id)
  await updatePocket(sql, raka.id, p.id, { name: p.name, image: p.image, rule: { type: 'payday', percent: 10 } })
  await fund(raka.savings.id, 'inflow', 5_000_000, { category: 'income', channel: 'Salary' })
  await fund(raka.savings.id, 'inflow', 100_000, { category: 'transfer' }) // not salary
  await runAutoSave(sql)
  assert.equal(await balanceOf(sql, p.id), 500_000)

  const [, , q] = await listPockets(sql, raka.id)
  await updatePocket(sql, raka.id, q.id, { name: q.name, image: q.image, rule: { type: 'payday', amount: 999_000_000 } })
  await fund(raka.savings.id, 'inflow', 1_000_000, { category: 'income' })
  await runAutoSave(sql)
  await runAutoSave(sql)
  assert.equal(await balanceOf(sql, q.id), 0)
  assert.equal((await inbox()).filter(t => t === 'Auto-save skipped').length, 1)
})

test('weekly saving runs on its day only, once', async () => {
  const [, , , p] = await listPockets(sql, raka.id)
  await updatePocket(sql, raka.id, p.id, { name: p.name, image: p.image, rule: { type: 'weekly', day: 3, amount: 50_000 } })
  await runAutoSave(sql, new Date('2026-10-13T03:00:00Z')) // Tuesday in Jakarta
  assert.equal(await balanceOf(sql, p.id), 0)
  await runAutoSave(sql, new Date('2026-10-14T03:00:00Z')) // Wednesday
  await runAutoSave(sql, new Date('2026-10-14T09:00:00Z'))
  assert.equal(await balanceOf(sql, p.id), 50_000)
  assert.ok((await checkLedger(sql)).ok)
})
