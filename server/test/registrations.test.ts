import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { sendOtp, verifyOtp, submitRegistration, registrationStatus, approveRegistration, rejectRegistration } from '../src/registrations.ts'
import { signInWithPassword, signInWithPin } from '../src/customers.ts'
import { listCards } from '../src/cards.ts'

const sql = connect()
const details = { fullName: 'Nadia Putri', nik: '3175014101990001', birthDate: '1999-01-01', address: 'Jl. Melati 5, Jakarta', occupation: 'Employee', incomeRange: 'Rp5–10 million', purpose: 'Savings' }

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
})
after(() => sql.end())

test('a new customer verifies their phone, applies, gets approved, and signs in with the password and PIN they chose', async () => {
  const { code } = await sendOtp(sql, '0813 7777 0001')
  await assert.rejects(verifyOtp(sql, '081377770001', code === '000000' ? '111111' : '000000'), /doesn't match/)
  const token = await verifyOtp(sql, '081377770001', code)
  await assert.rejects(submitRegistration(sql, token, { ...details, nik: '123' }, 'rahasia123', '482915'), /16 digits/)
  await assert.rejects(submitRegistration(sql, token, { ...details, birthDate: '2015-01-01' }, 'rahasia123', '482915'), /at least 17/)
  await submitRegistration(sql, token, details, 'rahasia123', '482915')
  assert.equal((await registrationStatus(sql, token)).status, 'pending')

  const [r] = await sql`select id from registrations where token = ${token}`
  const customer = await approveRegistration(sql, r.id, 'everyday')
  assert.equal((await registrationStatus(sql, token)).status, 'approved')
  assert.equal((await listCards(sql, customer.id))[0].kind, 'debit')
  const s = await signInWithPassword(sql, '081377770001', 'rahasia123')
  assert.ok(s.ok && !s.needsPin)
  assert.equal((await signInWithPin(sql, s.deviceToken!, '482915')).ok, true)
})

test('a rejected application shows the reason; a used phone number cannot apply again', async () => {
  const { code } = await sendOtp(sql, '081377770002')
  const token = await verifyOtp(sql, '081377770002', code)
  await submitRegistration(sql, token, { ...details, nik: '3175014101990002' }, 'rahasia123', '482915')
  const [r] = await sql`select id from registrations where token = ${token}`
  await rejectRegistration(sql, r.id, 'The NIK and name do not match.')
  assert.deepEqual(await registrationStatus(sql, token), { status: 'rejected', reason: 'The NIK and name do not match.', phone: '081377770002' })
  await assert.rejects(sendOtp(sql, '081377770001'), /already has an ORVYN account/)
})
