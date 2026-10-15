import { randomBytes, randomInt } from 'node:crypto'
import type { TransactionSql } from 'postgres'
import type { Sql } from './db.ts'
import { hashPassword, verifyPassword } from './auth.ts'
import { issueCard } from './cards.ts'
import { notify } from './inbox.ts'

const MAX_ATTEMPTS = 5
export const CUSTOMER_IDLE_MINUTES = 5 // story S1.6: signed out after 5 idle minutes

export type NewCustomer = {
  fullName: string
  phone: string
  password: string
  tier?: 'everyday' | 'prestige' | 'private'
  persona?: string
}

export class CustomerError extends Error {
  code: 'PHONE_TAKEN' | 'WEAK_PIN' | 'BAD_PHONE'
  constructor(code: CustomerError['code'], message: string) {
    super(message)
    this.code = code
  }
}

/** Indonesian mobile numbers, stored as 08xxxxxxxxxx (10–13 digits). Accepts +62 / 62 prefixes and spaces. */
export function normalizePhone(input: string) {
  const digits = input.replace(/\D/g, '').replace(/^62/, '0')
  if (!/^08\d{8,11}$/.test(digits)) throw new CustomerError('BAD_PHONE', 'Enter an Indonesian mobile number, like 0812 3456 7890.')
  return digits
}

/** Six digits that are not all the same and not a straight run (123456, 654321). */
export function isAcceptablePin(pin: string) {
  if (!/^\d{6}$/.test(pin)) return false
  if (/^(\d)\1{5}$/.test(pin)) return false
  const d = [...pin].map(Number)
  const steps = new Set(d.slice(1).map((x, i) => x - d[i]))
  return !(steps.size === 1 && (steps.has(1) || steps.has(-1)))
}

/** Creates the Customer with a Savings Account. Runs inside the caller's transaction. */
export async function createCustomer(tx: TransactionSql, c: NewCustomer) {
  const phone = normalizePhone(c.phone)
  const [taken] = await tx`select 1 from customers where phone = ${phone}`
  if (taken) throw new CustomerError('PHONE_TAKEN', 'This phone number already has an ORVYN account.')
  const [customer] = await tx`
    insert into customers (full_name, phone, password_hash, tier, persona)
    values (${c.fullName.trim()}, ${phone}, ${await hashPassword(c.password)}, ${c.tier ?? 'everyday'}, ${c.persona ?? null})
    returning *`
  const savings = await openSavings(tx, customer.id)
  await issueCard(tx, customer.id, 'debit', { savingsId: savings.id }) // story S1.4: every customer gets a Debit Card
  return { customer, savings }
}

async function openSavings(tx: TransactionSql, customerId: string) {
  // 10-digit numbers starting with 40; retry on the rare collision.
  for (;;) {
    const number = '40' + String(randomInt(0, 1e8)).padStart(8, '0')
    const [a] = await tx`
      insert into accounts (kind, customer_id, number, name, currency, floor)
      values ('savings', ${customerId}, ${number}, 'ORVYN Everyday', 'IDR', 0)
      on conflict (number) do nothing returning *`
    if (a) return a
  }
}

type SignIn = { ok: true; deviceToken?: string; sessionToken: string; needsPin: boolean } | { ok: false; reason: 'invalid' | 'locked' }

async function failAttempt(sql: Sql, customerId: string): Promise<SignIn> {
  const [c] = await sql`
    update customers set failed_attempts = failed_attempts + 1, locked = failed_attempts + 1 >= ${MAX_ATTEMPTS}
    where id = ${customerId} returning locked`
  return { ok: false, reason: c.locked ? 'locked' : 'invalid' }
}

async function openSession(sql: Sql, customerId: string) {
  const token = randomBytes(32).toString('base64url')
  await sql`update customers set failed_attempts = 0 where id = ${customerId}`
  await sql`insert into customer_sessions (token, customer_id) values (${token}, ${customerId})`
  return token
}

/** First sign-in on a phone. Makes it the Registered Device, signing out any previous phone (S1.5). */
export async function signInWithPassword(sql: Sql, phoneInput: string, password: string): Promise<SignIn> {
  let phone: string
  try { phone = normalizePhone(phoneInput) } catch { return { ok: false, reason: 'invalid' } }
  const [c] = await sql`select * from customers where phone = ${phone}`
  if (!c || !c.password_hash) return { ok: false, reason: 'invalid' }
  if (c.locked) return { ok: false, reason: 'locked' }
  if (!(await verifyPassword(password, c.password_hash))) return failAttempt(sql, c.id)

  const deviceToken = randomBytes(32).toString('base64url')
  const [previous] = await sql`select 1 from registered_devices where customer_id = ${c.id}`
  if (previous) await notify(sql, c.id, 'Signed in on a new phone', 'ORVYN is now set up on another phone, so your previous phone was signed out. If this wasn\'t you, contact the ORVYN team.')
  await sql`delete from customer_sessions where customer_id = ${c.id}`
  await sql`
    insert into registered_devices (customer_id, device_token) values (${c.id}, ${deviceToken})
    on conflict (customer_id) do update set device_token = excluded.device_token, push_token = null, created_at = now()`
  return { ok: true, deviceToken, sessionToken: await openSession(sql, c.id), needsPin: !c.pin_hash }
}

async function customerForDevice(sql: Sql, deviceToken: string) {
  const [c] = await sql`select c.* from registered_devices d join customers c on c.id = d.customer_id where d.device_token = ${deviceToken}`
  return c
}

/** Quick sign-in on the Registered Device with the 6-digit PIN (S1.6). */
export async function signInWithPin(sql: Sql, deviceToken: string, pin: string): Promise<SignIn> {
  const c = await customerForDevice(sql, deviceToken)
  if (!c || !c.pin_hash) return { ok: false, reason: 'invalid' }
  if (c.locked) return { ok: false, reason: 'locked' }
  if (!(await verifyPassword(pin, c.pin_hash))) return failAttempt(sql, c.id)
  return { ok: true, sessionToken: await openSession(sql, c.id), needsPin: false }
}

/** Biometric sign-in: the phone already checked the fingerprint; the device token proves which phone it is. */
export async function signInWithDevice(sql: Sql, deviceToken: string): Promise<SignIn> {
  const c = await customerForDevice(sql, deviceToken)
  if (!c || !c.pin_hash) return { ok: false, reason: 'invalid' }
  if (c.locked) return { ok: false, reason: 'locked' }
  return { ok: true, sessionToken: await openSession(sql, c.id), needsPin: false }
}

export async function setPin(sql: Sql, customerId: string, pin: string) {
  if (!isAcceptablePin(pin)) throw new CustomerError('WEAK_PIN', "Choose 6 digits that aren't all the same or in a row.")
  await sql`update customers set pin_hash = ${await hashPassword(pin)} where id = ${customerId}`
}

/** The signed-in Customer, or null if the session is unknown, idle too long, or the customer is locked. */
export async function customerForSession(sql: Sql, token: string | undefined) {
  if (!token) return null
  const [c] = await sql`
    update customer_sessions s set last_seen = now()
    from customers c
    where s.token = ${token} and c.id = s.customer_id and not c.locked
      and s.last_seen > now() - ${CUSTOMER_IDLE_MINUTES + ' minutes'}::interval
    returning c.*`
  return c ?? null
}

export async function signOut(sql: Sql, token: string) {
  await sql`delete from customer_sessions where token = ${token}`
}
