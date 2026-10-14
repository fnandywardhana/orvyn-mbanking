import { randomBytes, randomInt } from 'node:crypto'
import type { Sql } from './db.ts'
import { hashPassword, verifyPassword, isStrongPassword } from './auth.ts'
import { normalizePhone, isAcceptablePin, createCustomer, CustomerError } from './customers.ts'
import { notify } from './inbox.ts'

export class RegistrationError extends Error {
  code: string
  constructor(code: string, message: string) { super(message); this.code = code }
}

/** Story S1.1 AC1: a simulated OTP. The demo hands the code back so the app can show it as a notification. */
export async function sendOtp(sql: Sql, phoneInput: string) {
  let phone: string
  try { phone = normalizePhone(phoneInput) } catch (e) { throw new RegistrationError('BAD_PHONE', (e as Error).message) }
  const [taken] = await sql`select 1 from customers where phone = ${phone}`
  if (taken) throw new RegistrationError('PHONE_TAKEN', 'This phone number already has an ORVYN account. Sign in instead.')
  const code = String(randomInt(0, 1_000_000)).padStart(6, '0')
  await sql`insert into otp_codes (phone, code_hash, expires_at) values (${phone}, ${await hashPassword(code)}, now() + interval '5 minutes')
    on conflict (phone) do update set code_hash = excluded.code_hash, expires_at = excluded.expires_at, attempts = 0`
  return { phone, code }
}

export async function verifyOtp(sql: Sql, phoneInput: string, code: string) {
  const phone = normalizePhone(phoneInput)
  const [o] = await sql`update otp_codes set attempts = attempts + 1 where phone = ${phone} returning *`
  if (!o || o.expires_at < new Date() || o.attempts > 5) throw new RegistrationError('OTP_EXPIRED', 'That code has expired. Ask for a new one.')
  if (!(await verifyPassword(code, o.code_hash))) throw new RegistrationError('OTP_WRONG', "That code doesn't match. Check the notification and try again.")
  await sql`delete from otp_codes where phone = ${phone}`
  const token = randomBytes(24).toString('base64url')
  await sql`insert into registrations (token, phone, verified) values (${token}, ${phone}, true)`
  return token
}

export type Details = { fullName: string; nik: string; birthDate: string; address: string; occupation: string; incomeRange: string; purpose: string }

/** S1.1 AC2–AC4 and S1.3. Everything is checked here even though the app checks too. */
export async function submitRegistration(sql: Sql, token: string, d: Details, password: string, pin: string) {
  const [r] = await sql`select * from registrations where token = ${token} and verified`
  if (!r) throw new RegistrationError('NOT_FOUND', 'Start again from your phone number.')
  if (r.status !== 'draft') throw new RegistrationError('ALREADY_SENT', 'You already sent this application.')
  const problems: string[] = []
  if (!d.fullName?.trim() || d.fullName.trim().length < 3) problems.push('Enter your full name as on your KTP.')
  if (!/^\d{16}$/.test(d.nik ?? '')) problems.push('NIK has 16 digits.')
  const birth = new Date(d.birthDate)
  const age = (Date.now() - birth.getTime()) / (365.25 * 86_400_000)
  if (isNaN(age) || age < 17 || age > 100) problems.push('You need to be at least 17 to open an account.')
  if (!d.address?.trim()) problems.push('Enter your address.')
  if (!d.occupation || !d.incomeRange || !d.purpose) problems.push('Answer the questions about your work and why you want an account.')
  if (!isStrongPassword(password)) problems.push('Use a password with at least 8 characters, with letters and numbers.')
  if (!isAcceptablePin(pin)) problems.push("Choose a PIN that isn't all the same digit or in a row.")
  if (problems.length) throw new RegistrationError('INVALID', problems.join(' '))
  const [nikTaken] = await sql`select 1 from customers where nik = ${d.nik} union all select 1 from registrations where nik = ${d.nik} and status = 'pending'`
  if (nikTaken) throw new RegistrationError('NIK_TAKEN', 'This NIK is already registered with ORVYN.')
  await sql`update registrations set full_name = ${d.fullName.trim()}, nik = ${d.nik}, birth_date = ${d.birthDate}, address = ${d.address.trim()},
    occupation = ${d.occupation}, income_range = ${d.incomeRange}, purpose = ${d.purpose},
    password_hash = ${await hashPassword(password)}, pin_hash = ${await hashPassword(pin)}, status = 'pending', submitted_at = now()
    where id = ${r.id}`
}

export async function registrationStatus(sql: Sql, token: string) {
  const [r] = await sql`select status, reason, phone from registrations where token = ${token}`
  if (!r) throw new RegistrationError('NOT_FOUND', 'Start again from your phone number.')
  return { status: r.status as string, reason: r.reason as string | null, phone: r.phone as string }
}

/** Story S1.4: approval creates the Customer, Savings Account and Debit Card, keeping the password and PIN they chose. */
export async function approveRegistration(sql: Sql, id: string, tier: 'everyday' | 'prestige' | 'private') {
  const [r] = await sql`select * from registrations where id = ${id} and status = 'pending'`
  if (!r) throw new RegistrationError('NOT_FOUND', 'This application is no longer waiting.')
  const { customer } = await sql.begin(async tx => {
    const made = await createCustomer(tx, { fullName: r.full_name, phone: r.phone, password: randomBytes(12).toString('hex'), tier })
    await tx`update customers set password_hash = ${r.password_hash}, pin_hash = ${r.pin_hash}, nik = ${r.nik}, birth_date = ${r.birth_date},
      address = ${r.address}, occupation = ${r.occupation}, income_range = ${r.income_range}, purpose = ${r.purpose} where id = ${made.customer.id}`
    await tx`update registrations set status = 'approved', customer_id = ${made.customer.id} where id = ${id}`
    return made
  }).catch(e => { if (e instanceof CustomerError) throw new RegistrationError(e.code, e.message); throw e })
  await notify(sql, customer.id, 'Your account is ready', 'Welcome to ORVYN. Your account and debit card are open. Sign in with your phone number and the password you chose.')
  return customer
}

export async function rejectRegistration(sql: Sql, id: string, reason: string) {
  await sql`update registrations set status = 'rejected', reason = ${reason} where id = ${id} and status = 'pending'`
}
