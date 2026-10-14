import { Hono } from 'hono'
import type { Sql } from './db.ts'
import {
  signInWithPassword, signInWithPin, signInWithDevice, setPin, customerForSession, signOut, CustomerError,
} from './customers.ts'
import { BANKS, lookupRecipient, transferOptions, sendTransfer, payMerchant, TransferError } from './transfers.ts'
import { parseQris, buildQris } from './qris.ts'
import { inquire, payBill } from './bills.ts'
import { listCards, updateCard, cardDetails, payCardBill } from './cards.ts'
import { hashPassword, verifyPassword, isStrongPassword } from './auth.ts'
import { isAcceptablePin } from './customers.ts'
import { sendOtp, verifyOtp, submitRegistration, registrationStatus, RegistrationError } from './registrations.ts'

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
    title: r.kind === 'reversal' ? 'Reversed by ORVYN Bank' : (party?.name ?? m.channel ?? 'Transaction'),
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

  // App switches, read before sign-in too (story S1.37)
  app.get('/config', async c => {
    const rows = await sql`select key, value from settings where key in ('features', 'maintenance')`
    const v = Object.fromEntries(rows.map(r => [r.key, r.value]))
    return c.json({ features: v.features, maintenance: v.maintenance })
  })

  // Opening an account (stories S1.1–S1.3) happens before there is a customer.
  const regFail = (c: any, e: unknown) => {
    if (e instanceof RegistrationError) return c.json({ error: e.code, message: e.message }, 400)
    throw e
  }
  app.post('/register/otp', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { const r = await sendOtp(sql, String(b.phone ?? '')); return c.json({ phone: r.phone, demoCode: r.code }) } catch (e) { return regFail(c, e) }
  })
  app.post('/register/verify', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json({ token: await verifyOtp(sql, String(b.phone ?? ''), String(b.code ?? '')) } ) } catch (e) { return regFail(c, e) }
  })
  app.post('/register/submit', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      await submitRegistration(sql, String(b.token ?? ''), b.details ?? {}, String(b.password ?? ''), String(b.pin ?? ''))
      return c.json({ status: 'pending' })
    } catch (e) { return regFail(c, e) }
  })
  app.get('/register/status', async c => {
    try { return c.json(await registrationStatus(sql, c.req.query('token') ?? '')) } catch (e) { return regFail(c, e) }
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
      select id, kind, name, number, balance from accounts where customer_id = ${me.id} and kind in ('savings', 'pocket') order by created_at`
    const ids = accounts.map(a => a.id)
    const recent = ids.length ? await sql`
      select ${txColumns(sql)} from postings p join journal_entries j on j.id = p.journal_id
      where p.account_id in ${sql(ids)} order by j.created_at desc, j.id desc limit 5` : []
    return c.json({
      customer: { name: me.full_name, firstName: me.full_name.split(' ')[0], tier: me.tier },
      unread: (await sql`select count(*)::int as n from inbox_messages where customer_id = ${me.id} and read_at is null`)[0].n,
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

  // Transfers (stories S1.12–S1.14)
  const fail = (c: any, e: unknown) => {
    if (e instanceof TransferError) return c.json({ error: e.code, message: e.message, ...e.extra }, e.code === 'NOT_FOUND' ? 404 : 400)
    throw e
  }

  app.get('/banks', c => c.json({ banks: BANKS }))

  app.post('/transfers/lookup', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json(await lookupRecipient(sql, c.get('customer').id, String(b.bank ?? ''), String(b.accountNumber ?? ''))) } catch (e) { return fail(c, e) }
  })

  app.get('/transfers/options', async c => {
    const me = c.get('customer')
    const [acct] = await sql`select balance from accounts where customer_id = ${me.id} and kind = 'savings'`
    const opts = await transferOptions(sql, me, c.req.query('bank') === 'ORVYN', Number(c.req.query('amount') ?? 0))
    return c.json({ ...opts, balance: Number(acct.balance) })
  })

  app.post('/transfers', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await sendTransfer(sql, c.get('customer') as any, {
        bank: String(b.bank ?? ''), accountNumber: String(b.accountNumber ?? ''), amount: Number(b.amount),
        method: b.method, note: b.note, pin: String(b.pin ?? ''), idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()),
      })
      return c.json({ transactionId: j.id, reference: j.id.replace(/-/g, '').slice(0, 12).toUpperCase() })
    } catch (e) { return fail(c, e) }
  })

  // Saved Recipients first, then people paid recently who aren't saved yet.
  app.get('/recipients', async c => {
    const me = c.get('customer')
    const saved = await sql`select id, bank, account_number, name, nickname from saved_recipients where customer_id = ${me.id} order by coalesce(nickname, name)`
    const recent = await sql`
      select distinct on (j.memo->'to'->>'account') j.memo->'to'->>'account' as account_number, j.memo->'to'->>'name' as name,
        case when j.kind = 'internal_transfer' then 'ORVYN' else coalesce((select code from (values ${sql(BANKS.map(b => [b.code, b.name]))}) v(code, name) where v.name = j.memo->'to'->>'bank'), j.memo->'to'->>'bank') end as bank,
        j.created_at
      from postings p join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
      where a.customer_id = ${me.id} and p.amount < 0 and j.kind in ('internal_transfer', 'external_transfer') and j.memo->'to'->>'account' is not null
      order by j.memo->'to'->>'account', j.created_at desc`
    const savedKeys = new Set(saved.map(r => r.bank + r.account_number))
    const bankName = (code: string) => BANKS.find(b => b.code === code)?.name ?? code
    return c.json({
      saved: saved.map(r => ({ id: r.id, bank: r.bank, bankName: bankName(r.bank), accountNumber: r.account_number, name: r.name, nickname: r.nickname })),
      recent: recent.filter(r => !savedKeys.has(r.bank + r.account_number)).sort((a, b) => b.created_at - a.created_at).slice(0, 8)
        .map(r => ({ bank: r.bank, bankName: bankName(r.bank), accountNumber: r.account_number, name: r.name })),
    })
  })

  app.post('/recipients', async c => {
    const me = c.get('customer')
    const b = await c.req.json().catch(() => ({}))
    try {
      const r = await lookupRecipient(sql, me.id, String(b.bank ?? ''), String(b.accountNumber ?? ''))
      const nickname = String(b.nickname ?? '').trim().slice(0, 30) || null
      const [row] = await sql`
        insert into saved_recipients (customer_id, bank, account_number, name, nickname) values (${me.id}, ${r.bank}, ${r.accountNumber}, ${r.name}, ${nickname})
        on conflict (customer_id, bank, account_number) do update set nickname = excluded.nickname returning id`
      return c.json({ id: row.id })
    } catch (e) { return fail(c, e) }
  })

  app.post('/recipients/:id/nickname', async c => {
    const b = await c.req.json().catch(() => ({}))
    const nickname = String(b.nickname ?? '').trim().slice(0, 30) || null
    await sql`update saved_recipients set nickname = ${nickname} where id = ${c.req.param('id')} and customer_id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  app.delete('/recipients/:id', async c => {
    await sql`delete from saved_recipients where id = ${c.req.param('id')} and customer_id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  // QRIS (stories S1.15, S1.16)
  app.post('/qris/scan', async c => {
    const b = await c.req.json().catch(() => ({}))
    let q
    try { q = parseQris(String(b.payload ?? '')) } catch (e) { return c.json({ error: 'NOT_QRIS', message: (e as Error).message }, 400) }
    if (q.kind === 'p2p') {
      try {
        return c.json({ type: 'p2p', amount: q.amount, recipient: await lookupRecipient(sql, c.get('customer').id, 'ORVYN', q.account) })
      } catch (e) { return fail(c, e) }
    }
    const [m] = await sql`select id, name, city, category from merchants where code = ${q.code} and active`
    if (!m) return c.json({ error: 'NOT_FOUND', message: "This merchant isn't taking ORVYN payments right now." }, 404)
    return c.json({ type: 'merchant', amount: q.amount, merchant: m })
  })

  app.post('/qris/pay', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await payMerchant(sql, c.get('customer') as any, { merchantId: String(b.merchantId ?? ''), amount: Number(b.amount), pin: String(b.pin ?? ''), idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()) })
      return c.json({ transactionId: j.id, reference: j.id.replace(/-/g, '').slice(0, 12).toUpperCase() })
    } catch (e) { return fail(c, e) }
  })

  app.get('/qris/mine', async c => {
    const me = c.get('customer')
    const [a] = await sql`select number from accounts where customer_id = ${me.id} and kind = 'savings'`
    const amount = Math.floor(Number(c.req.query('amount') ?? 0)) || undefined
    return c.json({ payload: buildQris({ kind: 'p2p', account: a.number, name: me.full_name, amount }), name: me.full_name, accountNumber: a.number })
  })

  // Bill Payments (stories S1.18–S1.21)
  app.get('/billers', async c => {
    const billers = await sql`select code, name, kind, fee from billers order by sort`
    const saved = await sql`select biller, number, name from saved_bill_numbers where customer_id = ${c.get('customer').id} order by last_used desc`
    return c.json({ billers: billers.map(b => ({ ...b, fee: Number(b.fee), saved: saved.filter(s => s.biller === b.code).slice(0, 5) })) })
  })

  app.post('/bills/inquiry', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json(await inquire(sql, String(b.biller ?? ''), String(b.number ?? ''))) } catch (e) { return fail(c, e) }
  })

  app.post('/bills/pay', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const r = await payBill(sql, c.get('customer') as any, {
        biller: String(b.biller ?? ''), number: String(b.number ?? ''), productId: b.productId, amount: b.amount,
        pin: String(b.pin ?? ''), idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()),
      })
      return c.json({ reference: r.journal.id.replace(/-/g, '').slice(0, 12).toUpperCase(), token: r.token, amount: r.amount, fee: r.fee, label: r.label })
    } catch (e) { return fail(c, e) }
  })

  // Cards (stories S1.23–S1.25)
  app.get('/cards', async c => c.json({ cards: await listCards(sql, c.get('customer').id) }))

  app.post('/cards/:id/settings', async c => { // POST, not PATCH: Android's HttpURLConnection has no PATCH
    const b = await c.req.json().catch(() => ({}))
    const pick = (k: string) => typeof b[k] === 'boolean' ? b[k] : undefined
    try {
      await updateCard(sql, c.get('customer').id, c.req.param('id'), {
        frozen: pick('frozen'), online: pick('online'), overseas: pick('overseas'), contactless: pick('contactless'),
        dailyLimit: b.dailyLimit === undefined ? undefined : Number(b.dailyLimit),
      })
      return c.json({ cards: await listCards(sql, c.get('customer').id) })
    } catch (e) { return fail(c, e) }
  })

  app.post('/cards/:id/details', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json(await cardDetails(sql, c.get('customer').id, c.req.param('id'), String(b.pin ?? ''))) } catch (e) { return fail(c, e) }
  })

  app.get('/cards/:id/transactions', async c => {
    const me = c.get('customer')
    const [card] = await sql`select id, account_id from cards where id = ${c.req.param('id')} and customer_id = ${me.id}`
    if (!card) return c.json({ error: 'not_found', message: "We couldn't find that card." }, 404)
    // This card's purchases and bill payments only (a debit card shares its account with everything else).
    const rows = await sql`
      select ${txColumns(sql)} from postings p join journal_entries j on j.id = p.journal_id
      where p.account_id = ${card.account_id} and j.memo->>'cardId' = ${card.id}::text
      order by j.created_at desc, j.id desc limit 50`
    return c.json({ items: rows.map(toTransaction) })
  })

  app.post('/cards/:id/pay', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await payCardBill(sql, c.get('customer').id, c.req.param('id'), Math.floor(Number(b.amount)), String(b.pin ?? ''), String(b.idempotencyKey ?? crypto.randomUUID()))
      return c.json({ reference: j.id.replace(/-/g, '').slice(0, 12).toUpperCase() })
    } catch (e) { return fail(c, e) }
  })

  // Inbox and announcements (stories S1.27, S1.28)
  app.get('/inbox', async c => {
    const rows = await sql`select id, title, body, link, read_at, created_at from inbox_messages where customer_id = ${c.get('customer').id} order by id desc limit 100`
    return c.json({ items: rows.map(r => ({ id: String(r.id), title: r.title, body: r.body, link: r.link, read: !!r.read_at, at: r.created_at })) })
  })

  app.post('/inbox/read', async c => {
    await sql`update inbox_messages set read_at = now() where customer_id = ${c.get('customer').id} and read_at is null`
    return c.json({ ok: true })
  })

  app.get('/announcement', async c => {
    const [a] = await sql`
      select id, title, body from announcements a where ends_at > now()
        and not exists (select 1 from announcement_seen s where s.announcement_id = a.id and s.customer_id = ${c.get('customer').id})
      order by id desc limit 1`
    return c.json({ announcement: a ? { id: String(a.id), title: a.title, body: a.body } : null })
  })

  app.post('/announcement/:id/seen', async c => {
    await sql`insert into announcement_seen (announcement_id, customer_id) values (${c.req.param('id')}, ${c.get('customer').id}) on conflict do nothing`
    return c.json({ ok: true })
  })

  // Content and profile (stories S1.8, S1.30, S1.31)
  app.get('/content', async c => {
    const banners = await sql`select id, title, body, detail, style, image, action from banners where starts_at <= now() and ends_at > now() order by sort, id`
    const faqs = await sql`select id, category, question, answer from faqs order by sort, id`
    return c.json({ banners: banners.map(b => ({ ...b, id: String(b.id) })), faqs: faqs.map(f => ({ ...f, id: String(f.id) })) })
  })

  app.post('/me/pin', async c => {
    const b = await c.req.json().catch(() => ({}))
    const [me] = await sql`select pin_hash from customers where id = ${c.get('customer').id}`
    if (!me.pin_hash || !(await verifyPassword(String(b.oldPin ?? ''), me.pin_hash))) return c.json({ error: 'WRONG_PIN', message: "That's not your current PIN." }, 400)
    if (!isAcceptablePin(String(b.newPin ?? ''))) return c.json({ error: 'WEAK_PIN', message: "Choose 6 digits that aren't all the same or in a row." }, 400)
    await sql`update customers set pin_hash = ${await hashPassword(String(b.newPin))} where id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  app.post('/me/password', async c => {
    const b = await c.req.json().catch(() => ({}))
    const [me] = await sql`select password_hash from customers where id = ${c.get('customer').id}`
    if (!(await verifyPassword(String(b.oldPassword ?? ''), me.password_hash))) return c.json({ error: 'WRONG_PASSWORD', message: "That's not your current password." }, 400)
    if (!isStrongPassword(String(b.newPassword ?? ''))) return c.json({ error: 'WEAK_PASSWORD', message: 'Use at least 8 characters, with both letters and numbers.' }, 400)
    await sql`update customers set password_hash = ${await hashPassword(String(b.newPassword))} where id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  return app
}
