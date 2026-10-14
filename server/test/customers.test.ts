import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { generateHistory, PERSONAS } from '../src/personas.ts'
import { checkLedger, balanceOf } from '../src/ledger.ts'
import { createCustomer, signInWithPassword, signInWithPin, signInWithDevice, setPin, customerForSession, isAcceptablePin, normalizePhone } from '../src/customers.ts'

const sql = connect()

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
})
after(() => sql.end())

const newCustomer = (phone: string) => sql.begin(tx => createCustomer(tx, { fullName: 'Raka Pratama', phone, password: 'rahasia123' }))

test('phone numbers are stored in one form', () => {
  assert.equal(normalizePhone('+62 812-3456-7890'), '081234567890')
  assert.throws(() => normalizePhone('12345'))
})

test('PINs that are easy to guess are refused', () => {
  for (const bad of ['111111', '123456', '654321', '12345', 'abcdef']) assert.equal(isAcceptablePin(bad), false, bad)
  for (const ok of ['482915', '112233', '135791']) assert.equal(isAcceptablePin(ok), true, ok)
})

test('a new customer gets a 10-digit savings account starting with 40', async () => {
  const { savings } = await newCustomer('0811000001')
  assert.match(savings.number, /^40\d{8}$/)
})

test('first sign-in asks for a PIN; afterwards the PIN or the device alone signs in', async () => {
  await newCustomer('0811000002')
  const first = await signInWithPassword(sql, '0811000002', 'rahasia123')
  assert.ok(first.ok && first.needsPin && first.deviceToken)
  const me = await customerForSession(sql, first.sessionToken)
  await setPin(sql, me.id, '482915')

  assert.equal((await signInWithPin(sql, first.deviceToken, '482915')).ok, true)
  assert.equal((await signInWithDevice(sql, first.deviceToken)).ok, true)
})

test('signing in on a second phone signs the first phone out', async () => {
  await newCustomer('0811000003')
  const phoneA = await signInWithPassword(sql, '0811000003', 'rahasia123')
  assert.ok(phoneA.ok)
  const phoneB = await signInWithPassword(sql, '0811000003', 'rahasia123')
  assert.ok(phoneB.ok)

  assert.equal(await customerForSession(sql, phoneA.sessionToken), null)
  assert.equal((await signInWithDevice(sql, phoneA.deviceToken!)).ok, false)
})

test('five wrong PINs lock the customer until an officer unlocks them', async () => {
  await newCustomer('0811000004')
  const s = await signInWithPassword(sql, '0811000004', 'rahasia123')
  assert.ok(s.ok)
  await setPin(sql, (await customerForSession(sql, s.sessionToken)).id, '482915')

  for (let i = 0; i < 4; i++) assert.deepEqual(await signInWithPin(sql, s.deviceToken!, '000001'), { ok: false, reason: 'invalid' })
  assert.deepEqual(await signInWithPin(sql, s.deviceToken!, '000001'), { ok: false, reason: 'locked' })
  assert.deepEqual(await signInWithPin(sql, s.deviceToken!, '482915'), { ok: false, reason: 'locked' })
  assert.equal((await signInWithPassword(sql, '0811000004', 'rahasia123')).ok, false)
})

test('a customer session ends after 5 idle minutes', async () => {
  await newCustomer('0811000005')
  const s = await signInWithPassword(sql, '0811000005', 'rahasia123')
  assert.ok(s.ok)
  await sql`update customer_sessions set last_seen = now() - interval '6 minutes' where token = ${s.sessionToken}`
  assert.equal(await customerForSession(sql, s.sessionToken), null)
})

test('each persona gets six months of history that leaves the ledger balanced', async () => {
  for (const [i, p] of PERSONAS.entries()) {
    const { savings } = await sql.begin(async tx => {
      const r = await createCustomer(tx, { fullName: p.fullName, phone: `08120000000${i}`, password: 'rahasia123', tier: p.tier, persona: p.key })
      await generateHistory(tx, p.key, r.savings.id, r.customer.id)
      return r
    })
    const [{ n, first }] = await sql`select count(*)::int as n, min(j.created_at) as first from postings p join journal_entries j on j.id = p.journal_id where p.account_id = ${savings.id}`
    assert.ok(n > 40, `${p.key} has ${n} transactions`) // Hendra transacts least: about 50–70
    assert.ok((Date.now() - first.getTime()) / 86_400_000 > 180, `${p.key} history starts ${first}`)
    assert.ok(await balanceOf(sql, savings.id) > 0)
  }
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('persona history happens in the daytime, Western Indonesia Time', async () => {
  const [{ bad }] = await sql`
    select count(*)::int as bad from journal_entries
    where kind <> 'opening' and extract(hour from created_at at time zone 'Asia/Jakarta') not between 7 and 20`
  assert.equal(bad, 0)
})
