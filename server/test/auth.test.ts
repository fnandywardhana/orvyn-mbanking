import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { seedSuperAdmin, loginOfficer, officerForSession } from '../src/auth.ts'

const sql = connect()

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  await seedSuperAdmin(sql, 'Admin@Orvyn.test', 'awal12345')
})
after(() => sql.end())

test('the first super admin must change the starting password', async () => {
  const r = await loginOfficer(sql, 'admin@orvyn.test', 'awal12345')
  assert.equal(r.ok && r.mustChangePassword, true)
})

test('five wrong passwords lock the officer out, even with the right password afterwards', async () => {
  for (let i = 0; i < 4; i++) assert.deepEqual(await loginOfficer(sql, 'admin@orvyn.test', 'salah'), { ok: false, reason: 'invalid' })
  assert.deepEqual(await loginOfficer(sql, 'admin@orvyn.test', 'salah'), { ok: false, reason: 'locked' })
  assert.deepEqual(await loginOfficer(sql, 'admin@orvyn.test', 'awal12345'), { ok: false, reason: 'locked' })
  await sql`update officers set locked_until = null, failed_attempts = 0`
})

test('a session ends after 30 idle minutes', async () => {
  const r = await loginOfficer(sql, 'admin@orvyn.test', 'awal12345')
  assert.ok(r.ok)
  assert.equal((await officerForSession(sql, r.token))?.email, 'admin@orvyn.test')
  await sql`update officer_sessions set last_seen = now() - interval '31 minutes'`
  assert.equal(await officerForSession(sql, r.token), null)
})
