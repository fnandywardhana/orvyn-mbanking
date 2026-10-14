import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { postJournal, houseAccount, balanceOf, checkLedger } from '../src/ledger.ts'
import { sendTransfer, lookupRecipient, transferOptions, generatedName, TransferError } from '../src/transfers.ts'

const sql = connect()
let raka: any, sinta: any

async function customer(name: string, phone: string, rupiah: number) {
  const r = await sql.begin(tx => createCustomer(tx, { fullName: name, phone, password: 'rahasia123' }))
  await setPin(sql, r.customer.id, '482915')
  if (rupiah) await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -rupiah }, { accountId: r.savings.id, amount: rupiah }] })
  return { ...r.customer, savings: r.savings }
}
const send = (who: any, input: object) => sendTransfer(sql, who, { pin: '482915', idempotencyKey: crypto.randomUUID(), ...input } as any)
const rejects = (p: Promise<unknown>, code: string) => assert.rejects(p, (e: TransferError) => e.code === code)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  raka = await customer('Raka Pratama', '081300000001', 5_000_000)
  sinta = await customer('Sinta Maharani', '081300000002', 0)
})
after(() => sql.end())

test('looking up an ORVYN account shows the holder; your own account and unknown numbers are refused', async () => {
  assert.equal((await lookupRecipient(sql, raka.id, 'ORVYN', sinta.savings.number)).name, 'Sinta Maharani')
  await rejects(lookupRecipient(sql, raka.id, 'ORVYN', raka.savings.number), 'OWN_ACCOUNT')
  await rejects(lookupRecipient(sql, raka.id, 'ORVYN', '4099999999'), 'NOT_FOUND')
})

test('other banks always show the same made-up name for the same number', async () => {
  const a = await lookupRecipient(sql, raka.id, 'BCA', '1234567890')
  assert.equal(a.name, generatedName('BCA', '1234567890'))
  assert.equal((await lookupRecipient(sql, raka.id, 'BCA', '1234567890')).name, a.name)
  await rejects(lookupRecipient(sql, raka.id, 'BCA', '123'), 'BAD_NUMBER')
})

test('an internal transfer moves money from sender to recipient with no fee', async () => {
  await send(raka, { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 250_000, method: 'internal', note: 'Makan siang' })
  assert.equal(await balanceOf(sql, raka.savings.id), 4_750_000)
  assert.equal(await balanceOf(sql, sinta.savings.id), 250_000)
})

test('BI-FAST costs Rp2,500 and only takes money from the sender', async () => {
  await send(raka, { bank: 'BCA', accountNumber: '1234567890', amount: 100_000, method: 'bifast' })
  assert.equal(await balanceOf(sql, raka.savings.id), 4_750_000 - 102_500)
})

test('RTGS is offered only above Rp100,000,000, and BI-FAST only up to Rp250,000,000', async () => {
  const m = async (amount: number) => (await transferOptions(sql, { id: raka.id, tier: 'everyday' }, false, amount)).methods.map(x => x.method)
  assert.deepEqual(await m(100_000_000), ['bifast'])
  assert.deepEqual(await m(150_000_000), ['bifast', 'rtgs'])
  assert.deepEqual(await m(300_000_000), ['rtgs'])
})

test('a wrong PIN is refused and counts toward the lock', async () => {
  await rejects(send(raka, { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 10_000, method: 'internal', pin: '000001' }), 'WRONG_PIN')
  assert.equal(await balanceOf(sql, sinta.savings.id), 250_000)
})

test('not enough money is refused without moving anything', async () => {
  await rejects(send(sinta, { bank: 'ORVYN', accountNumber: raka.savings.number, amount: 300_000, method: 'internal' }), 'INSUFFICIENT_FUNDS')
  assert.equal(await balanceOf(sql, sinta.savings.id), 250_000)
})

test('the daily limit counts transfers already sent today, not fees', async () => {
  await sql`update settings set value = '{"everyday": 500000, "prestige": 1000000000, "private": 5000000000}' where key = 'daily_limit'`
  // 250,000 internal + 100,000 BI-FAST already sent today = 350,000
  await rejects(send(raka, { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 150_001, method: 'internal' }), 'OVER_LIMIT')
  await send(raka, { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 150_000, method: 'internal' })
  await sql`update settings set value = '{"everyday": 100000000, "prestige": 1000000000, "private": 5000000000}' where key = 'daily_limit'`
})

test('a double tap with the same key sends once', async () => {
  const before = await balanceOf(sql, raka.savings.id)
  const input = { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 20_000, method: 'internal', idempotencyKey: 'tap-1' }
  const [a, b] = await Promise.all([send(raka, input), send(raka, input)])
  assert.equal(a.id, b.id)
  assert.equal(await balanceOf(sql, raka.savings.id), before - 20_000)
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('both sides of an internal transfer get one inbox message each, even after a double tap', async () => {
  const input = { bank: 'ORVYN', accountNumber: sinta.savings.number, amount: 15_000, method: 'internal', idempotencyKey: 'inbox-1' }
  await Promise.all([send(raka, input), send(raka, input)])
  const msgs = await sql`select customer_id, title, body from inbox_messages where link like 'tx:%' and body like 'Rp15,000%'`
  assert.deepEqual(msgs.map(m => m.title).sort(), ['Money in', 'Money out'])
  assert.match(msgs.find(m => m.title === 'Money in')!.body, /from Raka Pratama/)
})
