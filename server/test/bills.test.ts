import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { inquire, payBill, operatorFor } from '../src/bills.ts'
import type { TransferError } from '../src/transfers.ts'

const sql = connect()
let raka: any
const pay = (input: object) => payBill(sql, raka, { pin: '482915', idempotencyKey: crypto.randomUUID(), ...input } as any)
const rejects = (p: Promise<unknown>, code: string) => assert.rejects(p, (e: TransferError) => e.code === code)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Raka Pratama', phone: '081400000001', password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -5_000_000 }, { accountId: r.savings.id, amount: 5_000_000 }] })
  raka = { ...r.customer, savings: r.savings }
})
after(() => sql.end())

test('mobile numbers are matched to their operator by prefix', () => {
  assert.equal(operatorFor('0812 3456 7890'), 'Telkomsel')
  assert.equal(operatorFor('+62 857 1234 5678'), 'Indosat')
  assert.equal(operatorFor('0899 1234 5678'), 'Tri')
  assert.equal(operatorFor('0800 1234 5678'), null)
})

test('a PLN token costs the price plus Rp2,500 and returns a 20-digit token', async () => {
  const q = await inquire(sql, 'PLN_TOKEN', '12345678901')
  assert.ok(q.name && q.products.length === 6)
  const r = await pay({ biller: 'PLN_TOKEN', number: '12345678901', productId: '100' })
  assert.match(r.token, /^\d{20}$/)
  assert.doesNotMatch(r.token, /0123|1234|2345|3456|4567|5678|6789/, 'tokens should not look like counting')
  assert.equal(await balanceOf(sql, raka.savings.id), 5_000_000 - 102_500)
})

test("a postpaid bill can be paid once a month; the second inquiry says it's paid", async () => {
  const q = await inquire(sql, 'PLN_BILL', '512345678901')
  assert.ok(q.bill!.amount > 0)
  await pay({ biller: 'PLN_BILL', number: '512345678901' })
  await rejects(inquire(sql, 'PLN_BILL', '512345678901'), 'ALREADY_PAID')
})

test('e-wallet top-ups have a minimum and a Rp1,000 fee; bad numbers are refused', async () => {
  await rejects(pay({ biller: 'GOPAY', number: '081255556666', amount: 5_000 }), 'BAD_AMOUNT')
  const before = await balanceOf(sql, raka.savings.id)
  await pay({ biller: 'GOPAY', number: '081255556666', amount: 50_000 })
  assert.equal(await balanceOf(sql, raka.savings.id), before - 51_000)
  await rejects(inquire(sql, 'MOBILE', '0800 1111 2222'), 'BAD_NUMBER')
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})
