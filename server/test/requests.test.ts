import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { createRequest, createSplit, listRequests, listSplits, payRequest, declineRequest, cancelRequest, equalShares } from '../src/requests.ts'
import { TransferError, MINIMUM } from '../src/transfers.ts'
import { apiRoutes } from '../src/api.ts'

const sql = connect()
let raka: any, sinta: any, dimas: any
const code = (c: string) => (e: TransferError) => e.code === c

async function customer(name: string, phone: string, rupiah: number) {
  const r = await sql.begin(tx => createCustomer(tx, { fullName: name, phone, password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  if (rupiah) await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -rupiah }, { accountId: r.savings.id, amount: rupiah }] })
  return { ...r.customer, tier: 'everyday', savings: r.savings }
}
const titles = async (who: any) => (await sql`select title from inbox_messages where customer_id = ${who.id}`).map(r => r.title)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  raka = await customer('Raka Pratama', '081300000031', 1_000_000)
  sinta = await customer('Sinta Maharani', '081300000032', 1_000_000)
  dimas = await customer('Dimas Prakoso', '081300000033', 50_000)
})
after(() => sql.end())

test('asking for money reaches the payer, who pays it once with a PIN', async () => {
  await assert.rejects(createRequest(sql, raka, { accountNumber: raka.savings.number, amount: 50_000 }), code('OWN_ACCOUNT'))
  const id = await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 75_000, note: 'Lunch' })
  assert.ok((await titles(sinta)).includes('Money request'))
  const [incoming] = await listRequests(sql, sinta.id)
  assert.deepEqual([incoming.from, incoming.mine, incoming.status], ['Raka Pratama', false, 'open'])

  await assert.rejects(payRequest(sql, raka, id, '482915'), code('NOT_FOUND')) // only the payer can pay
  await assert.rejects(payRequest(sql, sinta, id, '000000'), code('WRONG_PIN'))
  await payRequest(sql, sinta, id, '482915')
  await assert.rejects(payRequest(sql, sinta, id, '482915'), code('ALREADY_PAID'))
  assert.equal(await balanceOf(sql, raka.savings.id), 1_075_000)
  assert.equal(await balanceOf(sql, sinta.savings.id), 925_000)
  assert.ok((await titles(raka)).includes('Request paid'))
})

test('a payer can decline, a requester can cancel, and old requests expire', async () => {
  const a = await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 })
  await declineRequest(sql, sinta, a)
  assert.ok((await titles(raka)).includes('Request declined'))
  const b = await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 })
  await cancelRequest(sql, raka.id, b)
  await assert.rejects(payRequest(sql, sinta, b, '482915'), code('ALREADY_PAID'))
  const c = await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 })
  await sql`update money_requests set expires_at = now() - interval '1 minute' where id = ${c}`
  assert.equal((await listRequests(sql, raka.id)).find(r => r.id === c)!.status, 'expired')
  await assert.rejects(payRequest(sql, sinta, c, '482915'), code('ALREADY_PAID'))
})

test('a split sends one request per friend and tracks who paid', async () => {
  assert.equal(equalShares(100_000, 2), 33_333)
  await assert.rejects(createSplit(sql, raka, { total: 50_000, shares: [{ accountNumber: sinta.savings.number, amount: 40_000 }, { accountNumber: dimas.savings.number, amount: 40_000 }] }), code('BAD_AMOUNT'))
  await createSplit(sql, raka, { total: 100_000, note: 'Bakmi', shares: [{ accountNumber: sinta.savings.number, amount: 33_333 }, { accountNumber: dimas.savings.number, amount: 33_333 }] })
  const [split] = await listSplits(sql, raka.id)
  assert.deepEqual(split.people.map(p => [p.name, p.status]), [['Sinta Maharani', 'open'], ['Dimas Prakoso', 'open']])
  await payRequest(sql, sinta, split.people[0].requestId, '482915')
  await declineRequest(sql, dimas, split.people[1].requestId)
  assert.deepEqual((await listSplits(sql, raka.id))[0].people.map(p => p.status), ['paid', 'declined'])
  assert.ok((await checkLedger(sql)).ok)
})

test('twenty requests a day at most', async () => {
  await sql`delete from money_requests`
  for (let i = 0; i < 20; i++) await createRequest(sql, dimas, { accountNumber: raka.savings.number, amount: 10_000 })
  await assert.rejects(createRequest(sql, dimas, { accountNumber: raka.savings.number, amount: 10_000 }), code('OVER_LIMIT'))
})

// Hotfix 0.9.1, H91-1 (Q-02-F2): every request can be paid. One minimum, the Transfer minimum, checked only when a request is made.
const rejects = (p: Promise<unknown>, message: string) => assert.rejects(p, (e: TransferError) => e.code === 'BAD_AMOUNT' && e.message === message)

test('H91-1 AC5: requests, split totals and shares under the minimum are refused with no request and no inbox', async () => {
  await sql`delete from money_requests`; await sql`delete from inbox_messages`
  await rejects(createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 9_999 }), 'Ask for at least Rp10,000.')
  await rejects(createSplit(sql, raka, { total: 9_999, shares: [{ accountNumber: sinta.savings.number, amount: 5_000 }] }), 'The smallest bill you can split is Rp10,000.')
  await rejects(createSplit(sql, raka, { total: 30_000, shares: [{ accountNumber: sinta.savings.number, amount: 9_999 }] }), 'The smallest share is Rp10,000.')
  assert.equal((await sql`select count(*)::int as n from money_requests`)[0].n, 0)
  assert.deepEqual(await titles(sinta), [])
  await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 })
  await createSplit(sql, raka, { total: 10_000, shares: [{ accountNumber: sinta.savings.number, amount: 10_000 }] })
  assert.equal((await sql`select count(*)::int as n from money_requests`)[0].n, 2)
})

test('H91-1 AC6: a Rp10,000 request pays as one internal transfer, even after the minimum goes up', async () => {
  const id = await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 })
  const later = await createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 })
  const before = await balanceOf(sql, raka.savings.id)
  const j = await payRequest(sql, sinta, id, '482915')
  assert.equal(j.kind, 'internal_transfer')
  assert.deepEqual((await sql`select amount from postings where journal_id = ${j.id} order by amount`).map(p => Number(p.amount)), [-10_000, 10_000])
  assert.equal((await listRequests(sql, raka.id)).find(r => r.id === id)!.status, 'paid')

  MINIMUM.transfer = 15_000
  try {
    await rejects(createRequest(sql, raka, { accountNumber: sinta.savings.number, amount: 10_000 }), 'Ask for at least Rp15,000.')
    const api = apiRoutes(sql)
    assert.equal((await (await api.request('/config')).json() as any).minTransfer, 15_000)
    await payRequest(sql, sinta, later, '482915') // H-9: made under the old minimum, still payable at its own amount
    assert.equal(await balanceOf(sql, raka.savings.id), before + 20_000)
  } finally { MINIMUM.transfer = 10_000 }
  assert.ok((await checkLedger(sql)).ok)
})
