import { Hono } from 'hono'
import { streamSSE } from 'hono/streaming'
import type { Sql } from './db.ts'
import {
  signInWithPassword, signInWithPin, signInWithDevice, setPin, customerForSession, signOut, CustomerError, deviceStatus, countWrongTry, triesLeft,
} from './customers.ts'
import { BANKS, lookupRecipient, transferOptions, sendTransfer, payMerchant, TransferError, checkPin, LOCKED_MESSAGE } from './transfers.ts'
import { parseQris, buildQris } from './qris.ts'
import { ratesFor, listFx, openFx, closeFx, quote, exchange } from './fx.ts'
import { depositRates, depositQuote, listDeposits, openDeposit, breakDeposit, setInstruction } from './deposits.ts'
import { QUIZ, setRiskProfile, listProducts, priceHistory, listHoldings, buy as buyProduct, sell as sellProduct, totalAssets } from './investments.ts'
import { registrationOptions, finishRegistration, signInOptions, signInWithPasskey, listPasskeys, deletePasskey, PasskeyError } from './passkeys.ts'
import { statement, statementMonths } from './statements.ts'
import { askVyna, vynaAllowance, vynaAvailable } from './vyna.ts'
import { insights, insightCard, setBudget, setCategory, categoryOf, SPEND_KINDS, CATEGORIES } from './insights.ts'
import { createRequest, createSplit, listRequests, listSplits, getRequest, payRequest, declineRequest, cancelRequest } from './requests.ts'
import { createSchedule, listSchedules, updateSchedule } from './scheduled.ts'
import { listPockets, createPocket, updatePocket, movePocket, closePocket, PocketError, POCKET_IMAGES } from './pockets.ts'
import { inquire, payBill } from './bills.ts'
import { listCards, updateCard, cardDetails, payCardBill } from './cards.ts'
import { hashPassword, verifyPassword, isStrongPassword } from './auth.ts'
import { isAcceptablePin } from './customers.ts'
import { sendOtp, verifyOtp, submitRegistration, registrationStatus, checkNik, RegistrationError } from './registrations.ts'

type Customer = { id: string; full_name: string; tier: string; pin_hash: string | null }
type Env = { Variables: { customer: Customer; token: string } }

const SIGN_IN_ERRORS = {
  invalid: "That phone number and password don't match.",
  locked: LOCKED_MESSAGE,
}

const ref = (id: string) => id.replace(/-/g, '').slice(0, 12).toUpperCase()
/** What a success screen needs from a Journal Entry: its id for the receipt, the short reference, and the server's time (Q-F2). */
const receipt = (j: any) => ({ transactionId: j.id as string, reference: ref(j.id), at: j.created_at })

// One customer-side view of a journal entry (CONTEXT.md: Transaction).
const txColumns = (sql: Sql) => sql`
  j.id, j.kind, j.created_at as at, p.amount, j.memo, j.reverses_id, ${categoryOf(sql)} as spend_category,
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
    spendCategory: amount < 0 && SPEND_KINDS.includes(r.kind) ? r.spend_category : null, // S2.8: one of the seven, for spending only
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
    const r = await signInWithPassword(sql, String(b.phone ?? ''), String(b.password ?? ''), b.deviceToken ? String(b.deviceToken) : undefined)
    if (!r.ok) return c.json({ error: r.reason, message: SIGN_IN_ERRORS[r.reason] }, 401)
    return c.json({ deviceToken: r.deviceToken, sessionToken: r.sessionToken, needsPin: r.needsPin })
  })

  // Story S3.12: passkey sign-in, before any session exists
  app.post('/auth/passkey/options', async c => c.json(await signInOptions()))
  app.post('/auth/passkey', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const r = await signInWithPasskey(sql, b)
      if (!r.ok) return c.json({ error: r.reason, message: SIGN_IN_ERRORS[r.reason] }, 401)
      return c.json({ deviceToken: r.deviceToken, sessionToken: r.sessionToken, needsPin: r.needsPin })
    } catch (e) {
      if (e instanceof PasskeyError) return c.json({ error: 'PASSKEY', message: e.message }, 401)
      throw e
    }
  })

  app.post('/auth/pin', async c => {
    const b = await c.req.json().catch(() => ({}))
    const r = await signInWithPin(sql, String(b.deviceToken ?? ''), String(b.pin ?? ''))
    if (!r.ok) {
      const [left] = r.reason === 'invalid'
        ? await sql`select 5 - c.failed_attempts as n from registered_devices d join customers c on c.id = d.customer_id where d.device_token = ${String(b.deviceToken ?? '')}`
        : [{ n: 0 }]
      return c.json({ error: r.reason, attemptsLeft: left?.n ?? null,
        message: r.reason === 'locked' ? SIGN_IN_ERRORS.locked : `Wrong PIN. ${triesLeft(left?.n ?? 0)}` }, 401)
    }
    return c.json({ sessionToken: r.sessionToken })
  })

  app.post('/auth/device', async c => {
    const b = await c.req.json().catch(() => ({}))
    const r = await signInWithDevice(sql, String(b.deviceToken ?? ''))
    if (!r.ok) return c.json({ error: r.reason, message: r.reason === 'locked' ? SIGN_IN_ERRORS.locked : 'Sign in with your password again.' }, 401)
    return c.json({ sessionToken: r.sessionToken })
  })

  // Q-B9: the locked screen's "Try again" asks whether this phone's customer is still locked, without a PIN.
  app.post('/auth/status', async c => {
    const r = await deviceStatus(sql, String((await c.req.json().catch(() => ({}))).deviceToken ?? ''))
    return r ? c.json(r) : c.json({ error: 'invalid', message: 'Sign in with your password again.' }, 401)
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
  // Q-D2 (PO-2): is this NIK free? Only for a phone that passed the OTP, yes or no, 10 checks per sign-up. The NIK is never logged.
  app.post('/register/nik', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json(await checkNik(sql, String(b.token ?? ''), String(b.nik ?? ''))) } catch (e) { return regFail(c, e) }
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
    // forget: the customer signed out from Profile, so this phone is no longer theirs and gets no more pushes
    if ((await c.req.json().catch(() => ({}))).forget) await sql`delete from registered_devices where customer_id = ${c.get('customer').id}`
    await signOut(sql, c.get('token'))
    return c.json({ ok: true })
  })

  app.post('/me/push', async c => {
    const token = String((await c.req.json().catch(() => ({}))).token ?? '').slice(0, 4096)
    await sql`update registered_devices set push_token = ${token || null} where customer_id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  app.post('/pin', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      await setPin(sql, c.get('customer').id, String(b.pin ?? ''), c.get('token'))
    } catch (e) {
      if (e instanceof CustomerError) return c.json({ error: e.code, message: e.message }, 400)
      throw e
    }
    return c.json({ ok: true })
  })

  app.get('/home', async c => {
    const me = c.get('customer')
    const accounts = await sql`
      select id, kind, name, number, balance, created_at from accounts where customer_id = ${me.id} and kind in ('savings', 'pocket') order by created_at`
    const ids = accounts.filter(a => a.kind === 'savings').map(a => a.id) // a move to a pocket would otherwise show twice
    const recent = ids.length ? await sql`
      select ${txColumns(sql)} from postings p join journal_entries j on j.id = p.journal_id
      where p.account_id in ${sql(ids)} order by j.created_at desc, j.id desc limit 5` : []
    return c.json({
      customer: { name: me.full_name, firstName: me.full_name.split(' ')[0], tier: me.tier, phone: (me as any).phone }, // phone: S1.20 "My number", from the server, never the SIM
      unread: (await sql`select count(*)::int as n from inbox_messages where customer_id = ${me.id} and read_at is null`)[0].n,
      totalBalance: accounts.reduce((s, a) => s + Number(a.balance), 0),
      accounts: accounts.filter(a => a.kind === 'savings').map(a => ({ id: a.id, kind: a.kind, name: a.name, number: a.number, balance: Number(a.balance), openedOn: a.created_at })),
      pockets: await listPockets(sql, me.id),
      // S3.11: only Prestige and Private customers have a Relationship Manager
      relationshipManager: me.tier === 'everyday' || !me.rm_id ? null
        : (await sql`select name, title, phone, email from relationship_managers where id = ${me.rm_id}`)[0] ?? null,
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
    // Q-F3: History's Type filter sends several kinds; a reversal shows under the type it reversed.
    const kinds = (q.kinds ?? '').split(',').map(k => k.trim()).filter(Boolean).slice(0, 20)
    const rows = await sql`
      select ${txColumns(sql)} from postings p join journal_entries j on j.id = p.journal_id
      where p.account_id = ${account.id}
        ${beforeAt ? sql`and (j.created_at, j.id) < (${new Date(beforeAt)}, ${beforeId})` : sql``}
        ${q.from ? sql`and j.created_at >= ${new Date(q.from)}` : sql``}
        ${q.to ? sql`and j.created_at < ${new Date(q.to)}` : sql``}
        ${q.direction === 'in' ? sql`and p.amount > 0` : q.direction === 'out' ? sql`and p.amount < 0` : sql``}
        ${q.kind ? sql`and j.kind = ${q.kind}` : sql``}
        ${kinds.length ? sql`and (j.kind in ${sql(kinds)} or (j.kind = 'reversal' and (select o.kind from journal_entries o where o.id = j.reverses_id) in ${sql(kinds)}))` : sql``}
        ${q.category ? sql`and p.amount < 0 and j.kind in ${sql(SPEND_KINDS)} and ${categoryOf(sql)} = ${q.category}` : sql``}
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
      where j.id = ${c.req.param('id')} and a.customer_id = ${me.id}
        ${c.req.query('account') ? sql`and a.id = ${c.req.query('account')!}` : sql``}
      order by a.kind = 'savings' desc limit 1`
    if (!r) return c.json({ error: 'not_found', message: "We couldn't find that transaction." }, 404)
    return c.json({ ...toTransaction(r), accountNumber: r.account_number, reference: r.id.replace(/-/g, '').slice(0, 12).toUpperCase() })
  })

  // Vyna (stories S2.12–S2.16). Answers stream as server-sent events: text, action, error, done.
  app.get('/vyna', async c => c.json({ available: vynaAvailable(), ...(await vynaAllowance(sql, c.get('customer').id)) }))
  app.post('/vyna/chat', async c => {
    const b = await c.req.json().catch(() => ({}))
    const me = c.get('customer') as any
    return streamSSE(c, async stream => {
      await askVyna(sql, me, Array.isArray(b.messages) ? b.messages : [], async e => {
        await stream.writeSSE({ event: e.type, data: JSON.stringify(e.type === 'text' ? { text: e.text } : e.type === 'action' ? e.action : { message: e.message }) })
      })
      await stream.writeSSE({ event: 'done', data: '{}' })
    })
  })

  // Passkeys (story S3.12)
  app.get('/passkeys', async c => c.json({ passkeys: await listPasskeys(sql, c.get('customer').id) }))
  app.post('/passkeys/options', async c => c.json(await registrationOptions(sql, c.get('customer') as any)))
  app.post('/passkeys', async c => {
    try { await finishRegistration(sql, c.get('customer').id, await c.req.json().catch(() => ({}))); return c.json({ ok: true }) }
    catch (e) { if (e instanceof PasskeyError) return c.json({ error: 'PASSKEY', message: e.message }, 400); throw e }
  })
  app.post('/passkeys/:id/delete', async c => { await deletePasskey(sql, c.get('customer').id, c.req.param('id')); return c.json({ ok: true }) })

  // FX (stories S3.1–S3.2)
  app.get('/fx', async c => {
    const me = c.get('customer') as any
    return c.json({ rates: await ratesFor(sql, me.tier), accounts: await listFx(sql, me) })
  })
  app.post('/fx/quote', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json(await quote(sql, c.get('customer') as any, String(b.currency), b.side === 'sell' ? 'sell' : 'buy', Number(b.amount))) } catch (e) { return fail(c, e) }
  })
  app.post('/fx/exchange', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await exchange(sql, c.get('customer') as any, { currency: String(b.currency), side: b.side === 'sell' ? 'sell' : 'buy', amount: Number(b.amount), pin: String(b.pin ?? ''), idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()) })
      return c.json(receipt(j))
    } catch (e) { return fail(c, e) }
  })
  app.post('/fx/:currency/open', async c => { try { await openFx(sql, c.get('customer').id, c.req.param('currency')); return c.json({ ok: true }) } catch (e) { return fail(c, e) } })
  app.post('/fx/:currency/close', async c => { try { await closeFx(sql, c.get('customer').id, c.req.param('currency')); return c.json({ ok: true }) } catch (e) { return fail(c, e) } })

  // Time Deposits (stories S3.3–S3.4)
  app.get('/deposits', async c => c.json({ rates: await depositRates(sql), deposits: await listDeposits(sql, c.get('customer').id) }))
  app.post('/deposits/quote', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json(await depositQuote(sql, b.amount, Number(b.tenor))) } catch (e) { return fail(c, e) }
  })
  app.post('/deposits', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await openDeposit(sql, c.get('customer') as any, { amount: b.amount, tenor: Number(b.tenor), instruction: String(b.instruction), pin: String(b.pin ?? ''), idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()) })
      return c.json(receipt(j))
    } catch (e) { return fail(c, e) }
  })
  app.post('/deposits/:id/instruction', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { await setInstruction(sql, c.get('customer').id, c.req.param('id'), String(b.instruction)); return c.json({ ok: true }) } catch (e) { return fail(c, e) }
  })
  app.post('/deposits/:id/withdraw', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { const j = await breakDeposit(sql, c.get('customer') as any, c.req.param('id'), String(b.pin ?? '')); return c.json({ ok: true, ...receipt(j), amount: j.amount }) } catch (e) { return fail(c, e) }
  })

  // Investments and Total Assets (stories S3.5–S3.9)
  app.get('/invest', async c => {
    const me = c.get('customer') as any
    return c.json({ ...(await listProducts(sql, me)), holdings: await listHoldings(sql, me.id), quiz: QUIZ })
  })
  app.get('/invest/:id/prices', async c => c.json({ prices: await priceHistory(sql, c.req.param('id'), Number(c.req.query('days') ?? 30)) }))
  app.post('/invest/profile', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json({ profile: await setRiskProfile(sql, c.get('customer').id, Array.isArray(b.answers) ? b.answers.map(Number) : []) }) } catch (e) { return fail(c, e) }
  })
  app.post('/invest/:id/buy', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await buyProduct(sql, c.get('customer') as any, { productId: c.req.param('id'), amount: b.amount, grams: b.grams, pin: String(b.pin ?? ''), acknowledgeRisk: b.acknowledgeRisk === true, idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()) })
      return c.json(receipt(j))
    } catch (e) { return fail(c, e) }
  })
  app.post('/invest/:id/sell', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await sellProduct(sql, c.get('customer') as any, { productId: c.req.param('id'), quantity: b.quantity ?? undefined, pin: String(b.pin ?? ''), idempotencyKey: String(b.idempotencyKey ?? crypto.randomUUID()) })
      return c.json(receipt(j))
    } catch (e) { return fail(c, e) }
  })
  app.get('/wealth', async c => c.json(await totalAssets(sql, c.get('customer') as any)))

  // E-statements (story S2.17)
  app.get('/statements', async c => c.json({ months: await statementMonths(sql, c.get('customer').id) }))
  app.get('/statements/:month', async c => {
    const s = await statement(sql, c.get('customer').id, c.req.param('month'))
    return s ? c.json(s) : c.json({ error: 'not_found', message: 'There is no statement for that month.' }, 404)
  })

  // Insights and budgets (stories S2.8–S2.11)
  app.get('/insights', async c => c.json(await insights(sql, c.get('customer').id)))
  app.get('/insights/card', async c => c.json({ card: await insightCard(sql, c.get('customer').id) }))
  app.post('/budgets', async c => {
    const b = await c.req.json().catch(() => ({}))
    if (!CATEGORIES.includes(b.category)) return c.json({ error: 'BAD_INPUT', message: 'Pick a category.' }, 400)
    await setBudget(sql, c.get('customer').id, b.category, b.limit == null ? null : Number(b.limit))
    return c.json({ ok: true })
  })
  app.post('/transactions/:id/category', async c => {
    const b = await c.req.json().catch(() => ({}))
    if (!(await setCategory(sql, c.get('customer').id, c.req.param('id'), String(b.category ?? ''))))
      return c.json({ error: 'not_found', message: "We couldn't change that transaction." }, 404)
    return c.json({ ok: true })
  })

  // Pockets (stories S2.1–S2.3)
  const pocketFail = (c: any, e: unknown) => {
    if (e instanceof PocketError) return c.json({ error: e.code, message: e.message }, e.code === 'NOT_FOUND' ? 404 : 400)
    throw e
  }
  app.get('/pockets', async c => c.json({ pockets: await listPockets(sql, c.get('customer').id), images: POCKET_IMAGES }))
  app.post('/pockets', async c => {
    try { return c.json({ id: await createPocket(sql, c.get('customer').id, await c.req.json().catch(() => ({}))) }) } catch (e) { return pocketFail(c, e) }
  })
  app.post('/pockets/:id', async c => {
    try { await updatePocket(sql, c.get('customer').id, c.req.param('id'), await c.req.json().catch(() => ({}))); return c.json({ ok: true }) } catch (e) { return pocketFail(c, e) }
  })
  app.post('/pockets/:id/move', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const r = await movePocket(sql, c.get('customer').id, c.req.param('id'), b.direction === 'out' ? 'out' : 'in', b.amount, String(b.idempotencyKey ?? crypto.randomUUID()))
      return c.json(r)
    } catch (e) { return pocketFail(c, e) }
  })
  app.post('/pockets/:id/close', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { await closePocket(sql, c.get('customer').id, c.req.param('id'), String(b.idempotencyKey ?? crypto.randomUUID())); return c.json({ ok: true }) } catch (e) { return pocketFail(c, e) }
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
      return c.json(receipt(j))
    } catch (e) { return fail(c, e) }
  })

  // Scheduled Transfers (stories S2.4–S2.5)
  app.get('/scheduled', async c => c.json({ scheduled: await listSchedules(sql, c.get('customer').id) }))
  app.post('/scheduled', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const id = await createSchedule(sql, c.get('customer') as any, {
        bank: String(b.bank ?? ''), accountNumber: String(b.accountNumber ?? ''), amount: Number(b.amount), method: b.method,
        note: b.note, repeat: b.repeat, firstDate: String(b.firstDate ?? ''), pin: String(b.pin ?? ''),
      })
      const [s] = await sql`select created_at from scheduled_transfers where id = ${id}`
      return c.json({ id, createdAt: s.created_at })
    } catch (e) { return fail(c, e) }
  })
  app.post('/scheduled/:id', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { await updateSchedule(sql, c.get('customer').id, c.req.param('id'), { paused: typeof b.paused === 'boolean' ? b.paused : undefined, delete: b.delete === true }); return c.json({ ok: true }) }
    catch (e) { return fail(c, e) }
  })

  // Money Requests and Split Bills (stories S2.6–S2.7)
  app.get('/requests', async c => {
    const me = c.get('customer')
    return c.json({ requests: await listRequests(sql, me.id), splits: await listSplits(sql, me.id) })
  })
  app.get('/requests/:id', async c => { try { return c.json(await getRequest(sql, c.get('customer').id, c.req.param('id'))) } catch (e) { return fail(c, e) } })
  app.post('/requests', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json({ id: await createRequest(sql, c.get('customer') as any, { accountNumber: String(b.accountNumber ?? ''), amount: b.amount, note: b.note }), createdAt: new Date() }) } catch (e) { return fail(c, e) }
  })
  app.post('/splits', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { return c.json({ id: await createSplit(sql, c.get('customer') as any, { total: b.total, note: b.note, shares: Array.isArray(b.shares) ? b.shares : [] }) }) } catch (e) { return fail(c, e) }
  })
  app.post('/requests/:id/pay', async c => {
    const b = await c.req.json().catch(() => ({}))
    try {
      const j = await payRequest(sql, c.get('customer') as any, c.req.param('id'), String(b.pin ?? ''))
      return c.json(receipt(j))
    } catch (e) { return fail(c, e) }
  })
  app.post('/requests/:id/decline', async c => { try { await declineRequest(sql, c.get('customer') as any, c.req.param('id')); return c.json({ ok: true }) } catch (e) { return fail(c, e) } })
  app.post('/requests/:id/cancel', async c => { try { await cancelRequest(sql, c.get('customer').id, c.req.param('id')); return c.json({ ok: true }) } catch (e) { return fail(c, e) } })

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
      return c.json(receipt(j))
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
      return c.json({ ...receipt(r.journal), token: r.token, amount: r.amount, fee: r.fee, label: r.label })
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
      return c.json(receipt(j))
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

  // Q-C1 (PO-4): Change PIN checks the current PIN first, before a new one is typed. Counts like every PIN (K6).
  const WRONG_CURRENT_PIN = "That's not your current PIN."
  app.post('/me/pin/check', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { await checkPin(sql, c.get('customer').id, String(b.pin ?? ''), WRONG_CURRENT_PIN); return c.json({ ok: true }) } catch (e) { return fail(c, e) }
  })

  // 0.8.1 sends both PINs here at once; 0.9.0 after /me/pin/check. Either way a wrong current PIN counts (Q-C1 AC6).
  app.post('/me/pin', async c => {
    const b = await c.req.json().catch(() => ({}))
    try { await checkPin(sql, c.get('customer').id, String(b.oldPin ?? ''), WRONG_CURRENT_PIN) } catch (e) { return fail(c, e) }
    if (!isAcceptablePin(String(b.newPin ?? ''))) return c.json({ error: 'WEAK_PIN', message: "Choose 6 digits that aren't all the same or in a row." }, 400)
    await sql`update customers set pin_hash = ${await hashPassword(String(b.newPin))} where id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  // Q-C2: a wrong current password counts toward the same 5 tries and locks on the 5th.
  app.post('/me/password', async c => {
    const b = await c.req.json().catch(() => ({}))
    const id = c.get('customer').id
    const [me] = await sql`select password_hash from customers where id = ${id}`
    if (!(await verifyPassword(String(b.oldPassword ?? ''), me.password_hash))) {
      const u = await countWrongTry(sql, id)
      if (u.locked) return c.json({ error: 'LOCKED', message: LOCKED_MESSAGE }, 400)
      return c.json({ error: 'WRONG_PASSWORD', message: `That's not your current password. ${triesLeft(u.left)}`, attemptsLeft: u.left }, 400)
    }
    await sql`update customers set failed_attempts = 0 where id = ${id}`
    if (!isStrongPassword(String(b.newPassword ?? ''))) return c.json({ error: 'WEAK_PASSWORD', message: 'Use at least 8 characters, with both letters and numbers.' }, 400)
    await sql`update customers set password_hash = ${await hashPassword(String(b.newPassword))} where id = ${c.get('customer').id}`
    return c.json({ ok: true })
  })

  return app
}
