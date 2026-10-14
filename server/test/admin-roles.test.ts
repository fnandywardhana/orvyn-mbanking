import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { adminRoutes } from '../src/admin.ts'
import { hashPassword, loginOfficer } from '../src/auth.ts'

const sql = connect()
const admin = adminRoutes(sql)

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  await sql`insert into officers (email, name, role, password_hash, must_change_password) values ('op@orvyn.test', 'Op', 'operator', ${await hashPassword('rahasia123')}, false)`
})
after(() => sql.end())

test('an Operator reaches customer pages but not Super Admin pages', async () => {
  const r = await loginOfficer(sql, 'op@orvyn.test', 'rahasia123')
  assert.ok(r.ok)
  const get = (path: string) => admin.request(path, { headers: { Cookie: `orvyn_admin=${r.token}` } })
  assert.equal((await get('/')).status, 200)
  assert.equal((await get('/customers')).status, 200)
  assert.equal((await get('/transactions')).status, 200)
  for (const p of ['/settings', '/officers', '/audit', '/reset', '/messages', '/content']) assert.equal((await get(p)).status, 403, p)
})
