import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { createSchedule, listSchedules, updateSchedule, runScheduledTransfers, nextAfter } from '../src/scheduled.ts'
import { TransferError } from '../src/transfers.ts'

const sql = connect()
let raka: any, sinta: any
const at = (iso: string) => new Date(iso) // UTC; Jakarta is +7

async function customer(name: string, phone: string, rupiah: number) {
  const r = await sql.begin(tx => createCustomer(tx, { fullName: name, phone, password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  if (rupiah) await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -rupiah }, { accountId: r.savings.id, amount: rupiah }] })
  return { ...r.customer, savings: r.savings }
}
const tomorrow = () => new Date(Date.now() + 7 * 3600_000 + 86400_000).toISOString().slice(0, 10)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  raka = await customer('Raka Pratama', '081300000021', 3_000_000)
  sinta = await customer('Sinta Maharani', '081300000022', 0)
})
after(() => sql.end())

test('next dates: weekly keeps its weekday, monthly on the 31st falls back to the last day', () => {
  assert.equal(nextAfter('weekly', 3, '2026-10-14'), '2026-10-21') // Wed → next Wed
  assert.equal(nextAfter('monthly', 31, '2026-10-31'), '2026-11-30')
  assert.equal(nextAfter('monthly', 31, '2027-01-31'), '2027-02-28')
  assert.equal(nextAfter('monthly', 15, '2026-10-14'), '2026-10-15')
  assert.equal(nextAfter('once', 1, '2026-10-14'), null)
})

test('making a schedule checks the PIN once and refuses past dates', async () => {
  const base = { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 250_000, method: 'internal' as const, repeat: 'monthly' as const }
  await assert.rejects(createSchedule(sql, raka, { ...base, firstDate: tomorrow(), pin: '000000' }), (e: TransferError) => e.code === 'WRONG_PIN')
  await assert.rejects(createSchedule(sql, raka, { ...base, firstDate: '2020-01-01', pin: '482915' }), (e: TransferError) => e.code === 'BAD_AMOUNT')
  const id = await createSchedule(sql, raka, { ...base, firstDate: tomorrow(), pin: '482915', note: 'Rent' })
  const [s] = await listSchedules(sql, raka.id)
  assert.equal(s.id, id)
  assert.equal(s.name, 'Sinta Maharani')
  assert.equal(s.nextRun, tomorrow())
})

test('a due schedule sends at 07:00 WIB once without a PIN, then moves to its next date', async () => {
  await sql`update scheduled_transfers set next_run = '2026-10-14', day = 14`
  await runScheduledTransfers(sql, at('2026-10-13T23:30:00Z')) // 06:30 WIB: too early
  assert.equal(await balanceOf(sql, sinta.savings.id), 0)
  await runScheduledTransfers(sql, at('2026-10-14T00:05:00Z')) // 07:05 WIB
  await runScheduledTransfers(sql, at('2026-10-14T00:06:00Z'))
  assert.equal(await balanceOf(sql, sinta.savings.id), 250_000)
  const [s] = await listSchedules(sql, raka.id)
  assert.equal(s.nextRun, '2026-11-14')
  assert.equal(s.lastResult.ok, true)
})

test('a server that was down catches up once, not once per missed day', async () => {
  await sql`update scheduled_transfers set next_run = '2026-09-14'`
  await runScheduledTransfers(sql, at('2026-10-20T02:00:00Z'))
  await runScheduledTransfers(sql, at('2026-10-20T02:01:00Z'))
  assert.equal(await balanceOf(sql, sinta.savings.id), 500_000)
  assert.equal((await listSchedules(sql, raka.id))[0].nextRun, '2026-11-14')
})

test('a failed run is told to the customer, not retried that day, and paused or deleted schedules stay quiet', async () => {
  const [s] = await listSchedules(sql, raka.id)
  await sql`update scheduled_transfers set amount = 999000000, next_run = '2026-10-21' where id = ${s.id}`
  await runScheduledTransfers(sql, at('2026-10-21T01:00:00Z'))
  await runScheduledTransfers(sql, at('2026-10-21T02:00:00Z'))
  const [failed] = await listSchedules(sql, raka.id)
  assert.equal(failed.lastResult.ok, false)
  assert.equal((await sql`select count(*)::int as n from inbox_messages where customer_id = ${raka.id} and title like 'Scheduled%'`)[0].n, 1)

  await sql`update scheduled_transfers set amount = 10000, next_run = '2026-10-22' where id = ${s.id}`
  await updateSchedule(sql, raka.id, s.id, { paused: true })
  await runScheduledTransfers(sql, at('2026-10-22T01:00:00Z'))
  await updateSchedule(sql, raka.id, s.id, { delete: true })
  assert.equal((await listSchedules(sql, raka.id)).length, 0)
  assert.equal(await balanceOf(sql, sinta.savings.id), 500_000)
  assert.ok((await checkLedger(sql)).ok)
})
