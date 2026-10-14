import { Hono } from 'hono'
import type { Sql } from './db.ts'
import {
  signInWithPassword, signInWithPin, signInWithDevice, setPin, customerForSession, signOut, CustomerError,
} from './customers.ts'

type Customer = { id: string; full_name: string; tier: string; pin_hash: string | null }
type Env = { Variables: { customer: Customer; token: string } }

const SIGN_IN_ERRORS = {
  invalid: "That phone number and password don't match.",
  locked: 'Your account is locked after too many wrong tries. Ask the ORVYN team to unlock it.',
}

// One customer-side view of a journal entry (CONTEXT.md: Transaction).
const txColumns = (sql: Sql) => sql`
  j.id, j.kind, j.created_at as at, p.amount, j.memo, j.reverses_id,
  exists (select 1 from journal_entries r where r.reverses_id = j.id) as reversed`

function toTransaction(r: any) {
  const amount = Number(r.amount)
  const m = r.memo ?? {}
  const party = amount < 0 ? m.to : m.from
  return {
    id: r.id,
    at: r.at,
    amount,
    title: r.kind === 'reversal' ? `Reversal: ${party?.name ?? m.channel}` : (party?.name ?? m.channel ?? 'Transaction'),
    subtitle: m.channel ?? null,
    category: m.category ?? null,
    note: m.note ?? null,
    fee: m.fee ?? 0,
    kind: r.kind,
    status: r.reversed ? 'reversed' : 'success',
    reversalOf: r.reverses_id,
  }
}

export function apiRoutes(sql: Sql) {
  const app = new Hono<Env>()

  app.post('/auth/password', async c => {
    const b = await c.req.json().catch(() => ({}))
    const r = await signInWithPassword(sql, String(b.phone ?? ''), String(b.password ?? ''))
    if (!r.ok) return c.json({ error: r.reason, message: SIGN_IN_ERRORS[r.reason] }, 401)
    return c.json({ deviceToken: r.deviceToken, sessionToken: r.sessionToken, needsPin: r.needsPin })
  })

  app.post('/auth/pin', async c => {
    const b = await c.req.json().catch(() => ({}))
    const r = await signInWithPin(sql, String(b.deviceToken ?? ''), String(b.pin ?? ''))
    if (!r.ok) {
      const [left] = r.reason === 'invalid'
        ? await sql`select 5 - c.failed_attempts as n from registered_devices d join customers c on c.id = d.customer_id where d.device_token = ${String(b.deviceToken ?? '')}`
        : [{ n: 0 }]
      return c.json({ error: r.reason, attemptsLeft: left?.n ?? null,
        message: r.reason === 'locked' ? SIGN_IN_ERRORS.locked : `Wrong PIN. ${left?.n ?? 0} ${left?.n === 1 ? 'try' : 'tries'} left.` }, 401)
    }
    return c.json({ sessionToken: r.sessionToken })
  })

  app.post('/auth/device', async c => {
    const b = await c.req.json().catch(() => ({}))
    const r = await signInWithDevice(sql, String(b.deviceToken ?? ''))
    if (!r.ok) return c.json({ error: r.reason, message: r.reason === 'locked' ? SIGN_IN_ERRORS.locked : 'Sign in with your password again.' }, 401)
    return c.json({ sessionToken: r.sessionToken })
  })

  // Everything below needs a signed-in customer.
  app.use('*', async (c, next) => {
    const token = c.req.header('Authorization')?.replace(/^Bearer /, '')
    const customer = await customerForSession(sql, token) as Customer | null
    if (!customer) return c.json({ error: 'signed_out', message: 'You were signed out. Sign in again to continue.' }, 401)
    c.set('customer', customer)
    c.set('token', token!)
    await next()
  })

  app.post('/auth/signout', async c => {
    await signOut(sql, c.get('token'))
    return c.json({ ok: true })
  })

  app.post('/pin', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      await setPin(sql, c.get('customer').id, String(b.pin ?? ''))
    } catch (e) {
      if (e instanceof CustomerError) return c.json({ error: e.code, message: e.message }, 400)
      throw e
    }
    return c.json({ ok: true })
  })

  app.get('/home', async c => {
    const me = c.get('customer')
    const accounts = await sql`
      select id, kind, name, number, balance from accounts where customer_id = ${me.id} order by created_at`
    const ids = accounts.map(a => a.id)
    const recent = ids.length ? await sql`
      select ${txColumns(sql)} from postings p join journal_entries j on j.id = p.journal_id
      where p.account_id in ${sql(ids)} order by j.created_at desc, j.id desc limit 5` : []
    return c.json({
      customer: { name: me.full_name, firstName: me.full_name.split(' ')[0], tier: me.tier },
      totalBalance: accounts.reduce((s, a) => s + Number(a.balance), 0),
      accounts: accounts.map(a => ({ id: a.id, kind: a.kind, name: a.name, number: a.number, balance: Number(a.balance) })),
      recent: recent.map(toTransaction),
    })
  })

  // Story S1.10: newest first, keyset paging, filters for dates, direction, kind and text.
  app.get('/accounts/:id/transactions', async c => {
    const me = c.get('customer')
    const [account] = await sql`select id from accounts where id = ${c.req.param('id')} and customer_id = ${me.id}`
    if (!account) return c.json({ error: 'not_found', message: "We couldn't find that account." }, 404)
    const q = c.req.query()
    const limit = Math.min(Number(q.limit) || 30, 100)
    const [beforeAt, beforeId] = (q.before ?? '').split('|')
    const text = q.q?.trim()
    const digits = text?.replace(/\D/g, '')
    const rows = await sql`
      select ${txColumns(sql)} from postings p join journal_entries j on j.id = p.journal_id
      where p.account_id = ${account.id}
        ${beforeAt ? sql`and (j.created_at, j.id) < (${new Date(beforeAt)}, ${beforeId})` : sql``}
        ${q.from ? sql`and j.created_at >= ${new Date(q.from)}` : sql``}
        ${q.to ? sql`and j.created_at < ${new Date(q.to)}` : sql``}
        ${q.direction === 'in' ? sql`and p.amount > 0` : q.direction === 'out' ? sql`and p.amount < 0` : sql``}
        ${q.kind ? sql`and j.kind = ${q.kind}` : sql``}
        ${text ? sql`and (j.memo::text ilike ${'%' + text + '%'} ${digits ? sql`or abs(p.amount)::text = ${digits}` : sql``})` : sql``}
      order by j.created_at desc, j.id desc limit ${limit + 1}`
    const items = rows.slice(0, limit).map(toTransaction)
    const last = rows.length > limit ? rows[limit - 1] : null
    return c.json({ items, next: last ? `${last.at.toISOString()}|${last.id}` : null })
  })

  app.get('/transactions/:id', async c => {
    const me = c.get('customer')
    const [r] = await sql`
      select ${txColumns(sql)}, a.number as account_number from postings p
      join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
      where j.id = ${c.req.param('id')} and a.customer_id = ${me.id} limit 1`
    if (!r) return c.json({ error: 'not_found', message: "We couldn't find that transaction." }, 404)
    return c.json({ ...toTransaction(r), accountNumber: r.account_number, reference: r.id.replace(/-/g, '').slice(0, 12).toUpperCase() })
  })

  return app
}
