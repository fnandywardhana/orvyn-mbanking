import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { ratesFor, quote, exchange, listFx, closeFx, toRupiah } from '../src/fx.ts'
import { TransferError } from '../src/transfers.ts'

const sql = connect()
let raka: any, hendra: any
const key = () => crypto.randomUUID()
const code = (c: string) => (e: TransferError) => e.code === c

async function customer(name: string, phone: string, tier: string, rupiah: number) {
  const r = await sql.begin(tx => createCustomer(tx, { fullName: name, phone, password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  await sql`update customers set tier = ${tier} where id = ${r.customer.id}`
  await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -rupiah }, { accountId: r.savings.id, amount: rupiah }] })
  return { ...r.customer, tier, savings: r.savings }
}

before(async () => {
  await sql`drop schema public cascade`; await sql`create schema public`; await migrate(sql)
  raka = await customer('Raka Pratama', '081300000061', 'everyday', 5_000_000)
  hendra = await customer('Hendra Wijaya', '081300000062', 'prestige', 50_000_000)
})
after(() => sql.end())

test('rupiah amounts are exact and always rounded in the bank\'s favour', () => {
  assert.equal(toRupiah(10_000, 2, 16420, 'buy'), 1_642_000) // USD 100.00
  assert.equal(toRupiah(1, 2, 16420, 'buy'), 165)              // 164.2 rounds up when buying
  assert.equal(toRupiah(1, 2, 16180, 'sell'), 161)             // 161.8 rounds down when selling
  assert.equal(toRupiah(10_000, 0, 108.9, 'buy'), 1_089_000)    // JPY 10,000
})

test('Prestige gets half the spread around the same middle rate', async () => {
  const usd = (await ratesFor(sql, 'everyday')).find(r => r.currency === 'USD')!
  const vip = (await ratesFor(sql, 'prestige')).find(r => r.currency === 'USD')!
  assert.deepEqual([usd.buy, usd.sell, usd.special], [16180, 16420, false])
  assert.deepEqual([vip.buy, vip.sell, vip.special], [16240, 16360, true])
})

test('buying and selling USD moves both currencies in one balanced entry', async () => {
  await assert.rejects(quote(sql, raka, 'USD', 'buy', 500), code('BAD_AMOUNT')) // USD 5 is under Rp100,000
  await assert.rejects(exchange(sql, raka, { currency: 'USD', side: 'buy', amount: 10_000, pin: '000000', idempotencyKey: key() }), code('WRONG_PIN'))
  await exchange(sql, raka, { currency: 'USD', side: 'buy', amount: 10_000, pin: '482915', idempotencyKey: key() })
  assert.equal(await balanceOf(sql, raka.savings.id), 5_000_000 - 1_642_000)
  const [usd] = await listFx(sql, raka)
  assert.deepEqual([usd.currency, usd.balance, usd.idrValue], ['USD', 10_000, 1_618_000])
  await exchange(sql, raka, { currency: 'USD', side: 'sell', amount: 4_000, pin: '482915', idempotencyKey: key() })
  assert.equal((await listFx(sql, raka))[0].balance, 6_000)
  assert.equal(await balanceOf(sql, raka.savings.id), 5_000_000 - 1_642_000 + 647_200)
  await assert.rejects(exchange(sql, raka, { currency: 'USD', side: 'sell', amount: 7_000, pin: '482915', idempotencyKey: key() }), code('INSUFFICIENT_FUNDS'))
  assert.ok((await checkLedger(sql)).ok)
})

test('an FX account closes only when empty', async () => {
  await assert.rejects(closeFx(sql, raka.id, 'USD'), code('BAD_AMOUNT'))
  await exchange(sql, raka, { currency: 'USD', side: 'sell', amount: 6_000, pin: '482915', idempotencyKey: key() })
  await closeFx(sql, raka.id, 'USD')
  assert.equal((await listFx(sql, raka)).length, 0)
  await exchange(sql, hendra, { currency: 'JPY', side: 'buy', amount: 100_000, pin: '482915', idempotencyKey: key() })
  assert.equal((await listFx(sql, hendra))[0].balance, 100_000)
  assert.ok((await checkLedger(sql)).ok)
})
