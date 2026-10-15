import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { setRiskProfile, ensurePrices, listProducts, priceHistory, buy, sell, listHoldings, runBonds, totalAssets } from '../src/investments.ts'
import { TransferError } from '../src/transfers.ts'

const sql = connect()
let me: any
const key = () => crypto.randomUUID()
const code = (c: string) => (e: TransferError) => e.code === c

before(async () => {
  await sql`drop schema public cascade`; await sql`create schema public`; await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Hendra Wijaya', phone: '081300000081', password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  me = { ...r.customer, tier: 'prestige', savings: r.savings }
  await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -100_000_000 }, { accountId: r.savings.id, amount: 100_000_000 }] })
  await ensurePrices(sql)
})
after(() => sql.end())

test('the quiz sets a profile, and prices exist for every day of the past year, the same each run', async () => {
  assert.equal(await setRiskProfile(sql, me.id, [0, 1, 1, 1, 1]), 'conservative')
  await assert.rejects(setRiskProfile(sql, me.id, [0, 1]), code('BAD_AMOUNT'))
  const year = await priceHistory(sql, 'ORV-EQ', 365)
  assert.ok(year.length >= 364)
  await sql`delete from prices where day > current_date - 10`
  const before = (await priceHistory(sql, 'ORV-EQ', 30)).length
  await ensurePrices(sql)
  const again = await priceHistory(sql, 'ORV-EQ', 365)
  assert.ok(again.length > before)
  assert.deepEqual(again.slice(0, 300), year.slice(0, 300))
  const mm = await priceHistory(sql, 'ORV-MM', 365)
  assert.ok(Math.abs(mm.at(-1)!.price / mm[0].price - 1.045) < 0.01) // money market: about its 4.5% drift, no drama
})

test('funds: buying above the profile needs a confirmation; units come from today\'s NAV; selling part keeps the cost fair', async () => {
  await assert.rejects(buy(sql, me, { productId: 'ORV-EQ', amount: 1_000_000, pin: '482915', idempotencyKey: key() }), code('BAD_METHOD'))
  await buy(sql, me, { productId: 'ORV-EQ', amount: 1_000_000, pin: '482915', acknowledgeRisk: true, idempotencyKey: key() })
  const nav = (await listProducts(sql, me)).products.find(p => p.id === 'ORV-EQ')!.nav!
  const [h] = await listHoldings(sql, me.id)
  assert.ok(Math.abs(h.quantity - 1_000_000 / nav) < 0.001)
  assert.ok(Math.abs(h.value - 1_000_000) <= 1)
  await sell(sql, me, { productId: 'ORV-EQ', quantity: h.quantity / 2, pin: '482915', idempotencyKey: key() })
  const [half] = await listHoldings(sql, me.id)
  assert.equal(half.cost, 500_000)
  await assert.rejects(sell(sql, me, { productId: 'ORV-EQ', quantity: h.quantity, pin: '482915', idempotencyKey: key() }), code('INSUFFICIENT_FUNDS'))
  await sell(sql, me, { productId: 'ORV-EQ', pin: '482915', idempotencyKey: key() })
  assert.equal((await listHoldings(sql, me.id)).length, 0)
  assert.ok(Math.abs(await balanceOf(sql, me.savings.id) - 100_000_000) <= 2)
  assert.ok((await checkLedger(sql)).ok)
})

test('bonds: steps of Rp1,000,000, a coupon once a month after tax, the face value back at maturity', async () => {
  await assert.rejects(buy(sql, me, { productId: 'ORI-D01', amount: 1_500_000, pin: '482915', idempotencyKey: key() }), code('BAD_AMOUNT'))
  await buy(sql, me, { productId: 'ORI-D01', amount: 12_000_000, pin: '482915', idempotencyKey: key() })
  await assert.rejects(sell(sql, me, { productId: 'ORI-D01', pin: '482915', idempotencyKey: key() }), code('BAD_METHOD'))
  const start = await balanceOf(sql, me.savings.id)
  await runBonds(sql, new Date('2026-11-10T02:00:00Z'))
  await runBonds(sql, new Date('2026-11-11T02:00:00Z'))
  const coupon = Math.floor(12_000_000 * 640 / 10_000 / 12)
  assert.equal(await balanceOf(sql, me.savings.id), start + coupon - Math.floor(coupon * 0.1))
  await sql`update products set maturity = '2026-11-12' where id = 'ORI-D01'`
  await runBonds(sql, new Date('2026-11-12T02:00:00Z'))
  assert.equal(await balanceOf(sql, me.savings.id), start + coupon - Math.floor(coupon * 0.1) + 12_000_000)
  assert.equal((await listHoldings(sql, me.id)).length, 0)
  assert.ok((await checkLedger(sql)).ok)
})

test('gold by rupiah or grams, valued at the buy-back price; Total Assets adds it all up', async () => {
  await assert.rejects(buy(sql, me, { productId: 'GOLD', amount: 10_000, pin: '482915', idempotencyKey: key() }), code('BAD_AMOUNT'))
  await buy(sql, me, { productId: 'GOLD', grams: 2, pin: '482915', idempotencyKey: key() })
  const [g] = await listHoldings(sql, me.id)
  assert.deepEqual([g.quantity, g.cost, g.value], [2, 3_924_000, 3_710_000])
  const t = await totalAssets(sql, me)
  assert.equal(t.parts.find(p => p.key === 'gold')!.value, 3_710_000)
  assert.equal(t.total, await balanceOf(sql, me.savings.id) + 3_710_000)
  assert.ok((await checkLedger(sql)).ok)
})
