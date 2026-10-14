import { scrypt, randomBytes, timingSafeEqual } from 'node:crypto'
import { promisify } from 'node:util'
import type { Sql } from './db.ts'

const scryptAsync = promisify(scrypt) as (pw: string, salt: Buffer, len: number) => Promise<Buffer>
const MAX_ATTEMPTS = 5
const LOCK_MINUTES = 15
export const IDLE_MINUTES = 30

export async function hashPassword(password: string) {
  const salt = randomBytes(16)
  return `scrypt$${salt.toString('base64')}$${(await scryptAsync(password, salt, 64)).toString('base64')}`
}

export async function verifyPassword(password: string, stored: string) {
  const [, salt, hash] = stored.split('$')
  const actual = await scryptAsync(password, Buffer.from(salt, 'base64'), 64)
  return timingSafeEqual(actual, Buffer.from(hash, 'base64'))
}

export function isStrongPassword(pw: string) {
  return pw.length >= 8 && /[a-zA-Z]/.test(pw) && /\d/.test(pw)
}

type LoginResult = { ok: true; token: string; mustChangePassword: boolean } | { ok: false; reason: 'invalid' | 'locked' }

export async function loginOfficer(sql: Sql, email: string, password: string): Promise<LoginResult> {
  const [o] = await sql`select * from officers where email = ${email.trim().toLowerCase()} and active`
  if (!o) return { ok: false, reason: 'invalid' }
  if (o.locked_until && o.locked_until > new Date()) return { ok: false, reason: 'locked' }
  if (!(await verifyPassword(password, o.password_hash))) {
    const [u] = await sql`
      update officers set failed_attempts = failed_attempts + 1,
        locked_until = case when failed_attempts + 1 >= ${MAX_ATTEMPTS} then now() + ${LOCK_MINUTES + ' minutes'}::interval end
      where id = ${o.id} returning locked_until`
    return { ok: false, reason: u.locked_until ? 'locked' : 'invalid' }
  }
  await sql`update officers set failed_attempts = 0, locked_until = null where id = ${o.id}`
  const token = randomBytes(32).toString('base64url')
  await sql`insert into officer_sessions (token, officer_id) values (${token}, ${o.id})`
  return { ok: true, token, mustChangePassword: o.must_change_password }
}

// Returns the signed-in officer, or null if the session is unknown, idle too long, or the officer was deactivated.
export async function officerForSession(sql: Sql, token: string | undefined) {
  if (!token) return null
  const [o] = await sql`
    update officer_sessions s set last_seen = now()
    from officers o
    where s.token = ${token} and o.id = s.officer_id and o.active
      and s.last_seen > now() - ${IDLE_MINUTES + ' minutes'}::interval
    returning o.id, o.email, o.name, o.role, o.must_change_password`
  return o ?? null
}

export async function logoutOfficer(sql: Sql, token: string) {
  await sql`delete from officer_sessions where token = ${token}`
}

export async function changeOfficerPassword(sql: Sql, officerId: string, password: string) {
  await sql`update officers set password_hash = ${await hashPassword(password)}, must_change_password = false where id = ${officerId}`
}

// First start: create the Super Admin from env. The password must be changed on first sign-in.
export async function seedSuperAdmin(sql: Sql, email?: string, password?: string) {
  const [{ count }] = await sql`select count(*)::int from officers`
  if (count > 0 || !email || !password) return
  await sql`
    insert into officers (email, name, role, password_hash)
    values (${email.toLowerCase()}, 'Super Admin', 'super_admin', ${await hashPassword(password)})`
}
