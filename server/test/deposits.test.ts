import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { addMonths, interest, depositQuote, openDeposit, listDeposits, breakDeposit, runMaturities, setInstruction } from '../src/deposits.ts'
import { TransferError } from '../src/transfers.ts'

const sql = connect()
let me: any
const key = () => crypto.randomUUID()

before(async () => {
  await sql`drop schema public cascade`; await sql`create schema public`; await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Sinta Maharani', phone: '081300000071', password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  me = { ...r.customer, tier: 'everyday', savings: r.savings }
  await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -100_000_000 }, { accountId: r.savings.id, amount: 100_000_000 }] })
})
after(() => sql.end())

test('maturity dates and interest after tax', () => {
  assert.equal(addMonths('2026-01-31', 1), '2026-02-28')
  assert.equal(addMonths('2026-10-15', 12), '2027-10-15')
  assert.deepEqual(interest(10_000_000, 450, '2026-10-15', '2027-10-15'), { gross: 450_000, tax: 90_000, net: 360_000 })
})

test('opening a deposit moves the money and quotes the interest first', async () => {
  await assert.rejects(depositQuote(sql, 500_000, 3), (e: TransferError) => e.code === 'BAD_AMOUNT')
  const q = await depositQuote(sql, 10_000_000, 12)
  assert.equal(q.rateBp, 450)
  await openDeposit(sql, me, { amount: 10_000_000, tenor: 12, instruction: 'payout', pin: '482915', idempotencyKey: key() })
  await openDeposit(sql, me, { amount: 20_000_000, tenor: 1, instruction: 'rollover', pin: '482915', idempotencyKey: key() })
  assert.equal(await balanceOf(sql, me.savings.id), 70_000_000)
  assert.deepEqual((await listDeposits(sql, me.id)).map(d => [d.tenor, d.principal]), [[1, 20_000_000], [12, 10_000_000]])
})

test('at maturity a rollover renews with interest and a payout goes home with interest', async () => {
  const [one, twelve] = await listDeposits(sql, me.id)
  await runMaturities(sql, new Date(`${one.maturityDate}T01:00:00Z`))
  const renewed = (await listDeposits(sql, me.id)).find(d => d.id === one.id)!
  const i = interest(20_000_000, 300, one.openedOn, one.maturityDate)
  assert.equal(renewed.principal, 20_000_000 + i.net)
  assert.equal(renewed.maturityDate, addMonths(one.maturityDate, 1))

  await runMaturities(sql, new Date(`${twelve.maturityDate}T01:00:00Z`))
  assert.equal((await listDeposits(sql, me.id)).find(d => d.id === twelve.id), undefined)
  assert.equal(await balanceOf(sql, me.savings.id), 70_000_000 + 10_000_000 + interest(10_000_000, 450, twelve.openedOn, twelve.maturityDate).net)
  assert.ok((await checkLedger(sql)).ok)
})

test('withdrawing early returns only the principal', async () => {
  const [d] = await listDeposits(sql, me.id)
  await setInstruction(sql, me.id, d.id, 'payout')
  const before = await balanceOf(sql, me.savings.id)
  await breakDeposit(sql, me, d.id, '482915')
  assert.equal(await balanceOf(sql, me.savings.id), before + d.principal)
  assert.equal((await listDeposits(sql, me.id)).length, 0)
  assert.ok((await checkLedger(sql)).ok)
})
