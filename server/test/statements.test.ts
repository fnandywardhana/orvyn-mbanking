import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf } from '../src/ledger.ts'
import { statement, statementMonths } from '../src/statements.ts'

const sql = connect()
let me: any
const move = async (amount: number, at: string) => postJournal(sql, { kind: amount > 0 ? 'inflow' : 'qris_payment', at: new Date(at), memo: amount > 0 ? { from: { name: 'PT Maju Jaya' } } : { to: { name: 'Kopi Senja' } },
  postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -amount }, { accountId: me.savings.id, amount }] })

before(async () => {
  await sql`drop schema public cascade`; await sql`create schema public`; await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Raka Pratama', phone: '081300000051', password: 'rahasia123' }))
  me = { ...r.customer, savings: r.savings }
  await move(1_000_000, '2026-08-20T03:00:00Z')
  await move(5_000_000, '2026-09-25T03:00:00Z')
  await move(-45_000, '2026-09-30T16:30:00Z') // 30 Sep, 23:30 WIB
  await move(-20_000, '2026-10-02T03:00:00Z')
})
after(() => sql.end())

test('statement months are full months since opening, newest first', async () => {
  assert.deepEqual(await statementMonths(sql, me.id, new Date('2026-10-15T03:00:00Z')), ['2026-09', '2026-08'])
})

test('a statement runs from the opening to the closing balance, and closes where the ledger stood', async () => {
  const s = (await statement(sql, me.id, '2026-09'))!
  assert.deepEqual([s.opening, s.moneyIn, s.moneyOut, s.closing], [1_000_000, 5_000_000, 45_000, 5_955_000])
  assert.deepEqual(s.items.map(i => i.balance), [6_000_000, 5_955_000])
  assert.equal(await balanceOf(sql, me.savings.id) + 20_000, s.closing)
  assert.equal(await statement(sql, me.id, '2026-10'), null) // month in progress
})
