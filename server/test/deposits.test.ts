import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { addMonths, interest, depositQuote, openDeposit, listDeposits, breakDeposit, runMaturities, setInstruction, matureDeposit } from '../src/deposits.ts'
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

// Hotfix 0.9.1, H91-3 (Q-02-F4): no zero postings, and one bad deposit never holds up the others.
const postingsOf = async (kind: string, depositId: string) => (await sql`
  select p.account_id, p.amount from postings p join journal_entries j on j.id = p.journal_id
  where j.kind = ${kind} and j.id in (select journal_id from postings where account_id = ${depositId}) order by p.amount`)
  .map(p => Number(p.amount))
const setRates = (bp: number) => sql`update settings set value = ${sql.json({ '1': bp, '3': bp, '6': bp, '12': bp })} where key = 'deposit_rates'`
/** Opens a Rp1,000,000 deposit and returns its id. */
const open = async (instruction: string, tenor = 1) => {
  const j = await openDeposit(sql, me, { amount: 1_000_000, tenor, instruction, pin: '482915', idempotencyKey: key() })
  return (await sql`select account_id from postings where journal_id = ${j.id} and amount > 0`)[0].account_id as string
}

test('H91-3 AC1-2: paying out with Rp0 interest writes one deposit_close with two postings', async () => {
  const id = await open('payout')
  const before = await balanceOf(sql, me.savings.id)
  const r = await matureDeposit(sql, id) // "Mature now" on the day it was opened
  assert.equal(r!.net, 0)
  assert.deepEqual(await postingsOf('deposit_close', id), [-1_000_000, 1_000_000])
  assert.equal(await balanceOf(sql, me.savings.id), before + 1_000_000)
  assert.equal((await listDeposits(sql, me.id)).find(d => d.id === id), undefined)
  const [m] = await sql`select title from inbox_messages where customer_id = ${me.id} order by created_at desc limit 1`
  assert.equal(m.title, 'Deposit paid out')
  assert.equal((await sql`select count(*)::int as n from postings where amount = 0`)[0].n, 0)
  assert.ok((await checkLedger(sql)).ok)
})

test('H91-3 AC3: interest whose tax rounds to Rp0 skips only the tax posting', async () => {
  await setRates(10) // 0.10% a year: Rp1,000,000 for one day earns Rp2 gross, Rp0 tax
  try {
    const id = await open('payout')
    const [d] = await sql`select opened_on from time_deposits where account_id = ${id}`
    const tomorrow = new Date(d.opened_on.getTime() + 86400_000 + 3600_000)
    assert.equal((await matureDeposit(sql, id, tomorrow))!.net, 2)
    assert.deepEqual(await postingsOf('deposit_close', id), [-1_000_000, -2, 1_000_002])

    const roll = await open('rollover')
    await matureDeposit(sql, roll, tomorrow)
    assert.deepEqual(await postingsOf('deposit_interest', roll), [-2, 2])
    assert.ok((await checkLedger(sql)).ok)
  } finally { await sql`update settings set value = ${sql.json({ '1': 300, '3': 350, '6': 400, '12': 450 })} where key = 'deposit_rates'` }
})

test('H91-3 AC4: renewing with Rp0 interest writes no journal entry', async () => {
  const id = await open('rollover')
  const entries = (await sql`select count(*)::int as n from journal_entries`)[0].n
  await matureDeposit(sql, id)
  assert.equal((await sql`select count(*)::int as n from journal_entries`)[0].n, entries)
  assert.ok((await listDeposits(sql, me.id)).find(d => d.id === id))
  const [m] = await sql`select title from inbox_messages where customer_id = ${me.id} order by created_at desc limit 1`
  assert.equal(m.title, 'Deposit renewed')
  await setInstruction(sql, me.id, id, 'payout'); await breakDeposit(sql, me, id, '482915')
})

test('H91-3 AC5-6: the maturity job runs each deposit on its own, and 0% pays out with two postings', async (t) => {
  await setRates(0)
  try {
    const bad = await open('payout')
    const good = await open('payout')
    const [d] = await sql`select maturity_date from time_deposits where account_id = ${good}`
    const due = new Date(d.maturity_date.getTime() + 3600_000)
    await sql`create function fail_one() returns trigger language plpgsql as $$ begin
      if new.account_id = ${sql.unsafe(`'${bad}'`)} then raise exception 'test failure'; end if; return new; end $$`
    await sql`create trigger fail_one before insert on postings for each row execute function fail_one()`
    const errors = t.mock.method(console, 'error', () => {})
    await runMaturities(sql, due)
    await sql`drop trigger fail_one on postings`; await sql`drop function fail_one()`
    assert.equal(errors.mock.callCount(), 1)
    assert.ok(String(errors.mock.calls[0].arguments.join(' ')).includes(bad))
    assert.deepEqual(await postingsOf('deposit_close', good), [-1_000_000, 1_000_000])
    assert.ok((await listDeposits(sql, me.id)).find(x => x.id === bad), 'the failed deposit stays open')
    assert.deepEqual(await postingsOf('deposit_close', bad), [])

    await runMaturities(sql, due) // next round tries again
    assert.deepEqual(await postingsOf('deposit_close', bad), [-1_000_000, 1_000_000])
    assert.ok((await checkLedger(sql)).ok)
  } finally { await sql`update settings set value = ${sql.json({ '1': 300, '3': 350, '6': 400, '12': 450 })} where key = 'deposit_rates'` }
})
