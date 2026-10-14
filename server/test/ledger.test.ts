import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { postJournal, reverse, balanceOf, houseAccount, checkLedger, LedgerError } from '../src/ledger.ts'

const sql = connect()

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
})
after(() => sql.end())

async function customerWithSavings(name: string, rupiah = 0) {
  const [c] = await sql`insert into customers (full_name) values (${name}) returning id`
  const [a] = await sql`
    insert into accounts (kind, customer_id, number, name, currency, floor)
    values ('savings', ${c.id}, ${String(Math.floor(Math.random() * 1e10)).padStart(10, '0')}, 'Tabungan', 'IDR', 0)
    returning id`
  if (rupiah) await postJournal(sql, { kind: 'adjustment', postings: [
    { accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -rupiah },
    { accountId: a.id, amount: rupiah },
  ] })
  return a.id as string
}

test('an internal transfer takes money from the sender and gives it to the recipient', async () => {
  const raka = await customerWithSavings('Raka', 1_000_000)
  const sinta = await customerWithSavings('Sinta')

  await postJournal(sql, { kind: 'internal_transfer', postings: [
    { accountId: raka, amount: -250_000 },
    { accountId: sinta, amount: 250_000 },
  ] })

  assert.equal(await balanceOf(sql, raka), 750_000)
  assert.equal(await balanceOf(sql, sinta), 250_000)
})

test('a journal entry that does not add up to zero is rejected and changes nothing', async () => {
  const raka = await customerWithSavings('Raka', 1_000_000)
  const sinta = await customerWithSavings('Sinta')

  await assert.rejects(
    postJournal(sql, { kind: 'internal_transfer', postings: [
      { accountId: raka, amount: -250_000 },
      { accountId: sinta, amount: 200_000 },
    ] }),
    (e: LedgerError) => e.code === 'UNBALANCED',
  )

  assert.equal(await balanceOf(sql, raka), 1_000_000)
  assert.equal(await balanceOf(sql, sinta), 0)
})

test('a savings account cannot go below zero', async () => {
  const raka = await customerWithSavings('Raka', 100_000)
  const sinta = await customerWithSavings('Sinta')

  await assert.rejects(
    postJournal(sql, { kind: 'internal_transfer', postings: [
      { accountId: raka, amount: -100_001 },
      { accountId: sinta, amount: 100_001 },
    ] }),
    (e: LedgerError) => e.code === 'INSUFFICIENT_FUNDS',
  )

  assert.equal(await balanceOf(sql, raka), 100_000)
  assert.equal(await balanceOf(sql, sinta), 0)
})

test('simultaneous transfers from one account can never overdraw it', async () => {
  const raka = await customerWithSavings('Raka', 100_000)
  const sinta = await customerWithSavings('Sinta')

  const results = await Promise.allSettled(Array.from({ length: 10 }, () =>
    postJournal(sql, { kind: 'internal_transfer', postings: [
      { accountId: raka, amount: -30_000 },
      { accountId: sinta, amount: 30_000 },
    ] })))

  assert.equal(results.filter(r => r.status === 'fulfilled').length, 3)
  assert.equal(await balanceOf(sql, raka), 10_000)
  assert.equal(await balanceOf(sql, sinta), 90_000)
})

test('posting twice with the same idempotency key moves money only once', async () => {
  const raka = await customerWithSavings('Raka', 100_000)
  const sinta = await customerWithSavings('Sinta')
  const transfer = () => postJournal(sql, { kind: 'internal_transfer', idempotencyKey: 'tap-123', postings: [
    { accountId: raka, amount: -40_000 },
    { accountId: sinta, amount: 40_000 },
  ] })

  const [a, b] = await Promise.all([transfer(), transfer()])
  const c = await transfer()

  assert.equal(a.id, b.id)
  assert.equal(a.id, c.id)
  assert.equal(await balanceOf(sql, raka), 60_000)
})

test('a reversal puts the money back, and a journal can be reversed only once', async () => {
  const raka = await customerWithSavings('Raka', 100_000)
  const sinta = await customerWithSavings('Sinta')
  const transfer = await postJournal(sql, { kind: 'internal_transfer', postings: [
    { accountId: raka, amount: -40_000 },
    { accountId: sinta, amount: 40_000 },
  ] })

  await reverse(sql, transfer.id, { reason: 'salah kirim' })

  assert.equal(await balanceOf(sql, raka), 100_000)
  assert.equal(await balanceOf(sql, sinta), 0)
  await assert.rejects(reverse(sql, transfer.id, { reason: 'lagi' }), (e: LedgerError) => e.code === 'ALREADY_REVERSED')
})

test('a reversal is refused when the recipient has already spent the money', async () => {
  const raka = await customerWithSavings('Raka', 100_000)
  const sinta = await customerWithSavings('Sinta')
  const transfer = await postJournal(sql, { kind: 'internal_transfer', postings: [
    { accountId: raka, amount: -40_000 },
    { accountId: sinta, amount: 40_000 },
  ] })
  await postJournal(sql, { kind: 'qris_payment', postings: [
    { accountId: sinta, amount: -30_000 },
    { accountId: await houseAccount(sql, 'MERCHANT_SETTLEMENT'), amount: 30_000 },
  ] })

  await assert.rejects(reverse(sql, transfer.id, { reason: 'salah kirim' }), (e: LedgerError) => e.code === 'INSUFFICIENT_FUNDS')
  assert.equal(await balanceOf(sql, sinta), 10_000)
})

test('the ledger check passes after normal activity and catches a balance that drifted', async () => {
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })

  const raka = await customerWithSavings('Raka', 100_000)
  await sql`update accounts set balance = balance + 1 where id = ${raka}` // simulate a bug that bypasses the ledger

  const result = await checkLedger(sql)
  assert.equal(result.ok, false)
  assert.match(result.problems.join('\n'), new RegExp(raka))
})

test('money stops moving through an account whose balance has drifted from its postings', async () => {
  const raka = await customerWithSavings('Raka', 100_000)
  const sinta = await customerWithSavings('Sinta')
  await sql`update accounts set balance = balance + 1 where id = ${raka}`

  await assert.rejects(postJournal(sql, { kind: 'internal_transfer', postings: [
    { accountId: raka, amount: -10_000 },
    { accountId: sinta, amount: 10_000 },
  ] }), (e: LedgerError) => e.code === 'DRIFT')
  assert.equal(await balanceOf(sql, sinta), 0)
})
