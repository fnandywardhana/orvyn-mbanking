import type { Sql } from './db.ts'
import { verifyPassword } from './auth.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import type { Memo } from './personas.ts'
import { notifyJournal } from './inbox.ts'
import { countWrongTry, triesLeft } from './customers.ts'

// Banks a customer can send to. 'ORVYN' is an Internal Transfer; the rest are External Transfers.
export const BANKS = [
  { code: 'ORVYN', name: 'ORVYN Bank' },
  { code: 'BCA', name: 'BCA' }, { code: 'MANDIRI', name: 'Mandiri' }, { code: 'BRI', name: 'BRI' }, { code: 'BNI', name: 'BNI' },
  { code: 'BSI', name: 'Bank Syariah Indonesia' }, { code: 'CIMB', name: 'CIMB Niaga' }, { code: 'PERMATA', name: 'Permata' },
  { code: 'DANAMON', name: 'Danamon' }, { code: 'BTN', name: 'BTN' }, { code: 'MAYBANK', name: 'Maybank Indonesia' },
  { code: 'PANIN', name: 'Panin' }, { code: 'MEGA', name: 'Bank Mega' }, { code: 'JAGO', name: 'Bank Jago' }, { code: 'SEABANK', name: 'SeaBank' },
]
const bankName = (code: string) => BANKS.find(b => b.code === code)?.name ?? code

/** Posts, then drops a message in each customer's inbox. A replayed (idempotent) entry doesn't notify twice. */
export async function withNotice<T extends { id: string; replayed?: boolean }>(sql: Sql, posting: Promise<T>) {
  const j = await posting
  if (!j.replayed) await notifyJournal(sql, j.id)
  return j
}

export class TransferError extends Error {
  code: 'NOT_FOUND' | 'OWN_ACCOUNT' | 'BAD_NUMBER' | 'BAD_AMOUNT' | 'OVER_LIMIT' | 'INSUFFICIENT_FUNDS' | 'WRONG_PIN' | 'LOCKED' | 'BAD_METHOD' | 'ALREADY_PAID' | 'MAINTENANCE'
  extra: object
  constructor(code: TransferError['code'], message: string, extra: object = {}) {
    super(message)
    this.code = code
    this.extra = extra
  }
}

const FIRST = ['Andi', 'Budi', 'Citra', 'Dewi', 'Eko', 'Fajar', 'Gita', 'Hadi', 'Indah', 'Joko', 'Kartika', 'Lestari', 'Made', 'Nia', 'Putri', 'Rizky', 'Sari', 'Taufik', 'Wulan', 'Yusuf']
const LAST = ['Saputra', 'Wijaya', 'Hidayat', 'Kusuma', 'Nugroho', 'Pratiwi', 'Santoso', 'Lubis', 'Siregar', 'Halim', 'Setiawan', 'Rahman', 'Anggraini', 'Gunawan', 'Utami']

/** Made-up but stable holder name for an account at another bank (CONTEXT.md: External Directory). */
export function generatedName(bank: string, number: string) {
  let h = 7
  for (const ch of bank + number) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return `${FIRST[h % FIRST.length]} ${LAST[(h >>> 8) % LAST.length]}`
}

export async function lookupRecipient(sql: Sql, customerId: string, bank: string, accountNumber: string) {
  const number = accountNumber.replace(/\D/g, '')
  if (!BANKS.some(b => b.code === bank)) throw new TransferError('NOT_FOUND', 'Choose a bank from the list.')
  if (bank === 'ORVYN') {
    if (!/^\d{10}$/.test(number)) throw new TransferError('BAD_NUMBER', 'ORVYN account numbers have 10 digits.')
    const [a] = await sql`select a.number, a.customer_id, c.full_name from accounts a join customers c on c.id = a.customer_id where a.number = ${number} and a.kind = 'savings'`
    if (!a) throw new TransferError('NOT_FOUND', "We couldn't find an ORVYN account with that number. Check it and try again.")
    if (a.customer_id === customerId) throw new TransferError('OWN_ACCOUNT', "That's your own account. Pick someone else's.")
    return { bank, bankName: bankName(bank), accountNumber: number, name: a.full_name as string, internal: true }
  }
  if (!/^\d{10,16}$/.test(number)) throw new TransferError('BAD_NUMBER', 'Account numbers have 10 to 16 digits.')
  const [d] = await sql`select name from external_directory where bank = ${bank} and account_number = ${number}`
  return { bank, bankName: bankName(bank), accountNumber: number, name: (d?.name ?? generatedName(bank, number)) as string, internal: false }
}

async function rules(sql: Sql) {
  const rows = await sql`select key, value from settings where key in ('daily_limit', 'transfer_fees')`
  const v = Object.fromEntries(rows.map(r => [r.key, r.value]))
  return { limits: v.daily_limit as Record<string, number>, fees: v.transfer_fees as { bifast: number; bifast_max: number; rtgs: number; rtgs_min: number } }
}

// Payouts that count toward the Daily Limit (CONTEXT.md). Moves between a customer's own accounts don't.
const PAYOUT_KINDS = ['internal_transfer', 'external_transfer', 'qris_payment', 'bill_payment']

/** Rupiah the customer has already paid out today (WIB). Fees don't count toward the limit. */
// ponytail: checked before posting, not under a lock, so two simultaneous transfers can overshoot by one; fine for a demo.
export async function spentToday(sql: Sql, customerId: string) {
  const [{ total }] = await sql`
    select coalesce(sum(-p.amount - coalesce((j.memo->>'fee')::bigint, 0)), 0)::bigint as total
    from postings p join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
    where a.customer_id = ${customerId} and p.amount < 0 and j.kind in ${sql(PAYOUT_KINDS)}
      and j.created_at >= (date_trunc('day', now() at time zone 'Asia/Jakarta') at time zone 'Asia/Jakarta')`
  return Number(total)
}

export type Method = 'internal' | 'bifast' | 'rtgs'

/** What the review screen shows: available methods with fees, today's remaining limit, balance after. */
export async function transferOptions(sql: Sql, customer: { id: string; tier: string }, internal: boolean, amount: number) {
  const { limits, fees } = await rules(sql)
  const limit = limits[customer.tier]
  const remaining = Math.max(0, limit - await spentToday(sql, customer.id))
  const methods = internal ? [{ method: 'internal' as Method, label: 'ORVYN transfer', fee: 0, note: 'Instantly' }]
    : [
      ...(amount <= fees.bifast_max ? [{ method: 'bifast' as Method, label: 'BI-FAST', fee: fees.bifast, note: 'In seconds' }] : []),
      ...(amount >= fees.rtgs_min ? [{ method: 'rtgs' as Method, label: 'RTGS', fee: fees.rtgs, note: 'Within the hour' }] : []),
    ]
  return { methods, dailyLimit: limit, remainingToday: remaining }
}

export const LOCKED_MESSAGE = 'Your account is locked. Ask the ORVYN team to unlock it.' // PO-7: right whether 5 wrong tries or an officer locked it

/**
 * Every PIN check after sign-in: payments, card details, Change PIN. A wrong PIN counts toward the same 5 as the unlock
 * screen (K6). [wrong] starts the message, e.g. "That's not your current PIN." on Change PIN.
 */
export async function checkPin(sql: Sql, customerId: string, pin: string, wrong = 'Wrong PIN.') {
  const [c] = await sql`select pin_hash, locked from customers where id = ${customerId}`
  if (c.locked) throw new TransferError('LOCKED', LOCKED_MESSAGE)
  if (c.pin_hash && await verifyPassword(pin, c.pin_hash)) {
    await sql`update customers set failed_attempts = 0 where id = ${customerId}`
    return
  }
  const u = await countWrongTry(sql, customerId)
  if (u.locked) throw new TransferError('LOCKED', LOCKED_MESSAGE)
  throw new TransferError('WRONG_PIN', `${wrong} ${triesLeft(u.left)}`, { attemptsLeft: u.left })
}

/** Daily Limit then PIN: the two checks every payout passes before money moves. A null PIN means it was given earlier (a Scheduled Transfer). */
export async function authorizePayout(sql: Sql, customer: { id: string; tier: string }, amount: number, pin: string | null, remainingToday?: number) {
  const [m] = await sql`select value from settings where key = 'maintenance'`
  if (m?.value?.on) throw new TransferError('MAINTENANCE', m.value.message) // story S1.37 AC4: no money moves during maintenance
  const remaining = remainingToday ?? (await transferOptions(sql, customer, true, amount)).remainingToday
  if (amount > remaining) {
    throw new TransferError('OVER_LIMIT', `That's over your daily limit. You can still pay out Rp${remaining.toLocaleString('en-US')} today.`, { remainingToday: remaining })
  }
  if (pin !== null) await checkPin(sql, customer.id, pin)
}

/** Story S1.15: pay a QRIS merchant. Money lands in Merchant Settlement. */
export async function payMerchant(sql: Sql, customer: { id: string; tier: string }, input: { merchantId: string; amount: number; pin: string; idempotencyKey: string }) {
  const amount = Math.floor(Number(input.amount))
  if (!Number.isFinite(amount) || amount < 1_000) throw new TransferError('BAD_AMOUNT', 'The smallest QRIS payment is Rp1,000.')
  const [m] = await sql`select * from merchants where id = ${input.merchantId} and active`
  if (!m) throw new TransferError('NOT_FOUND', "This merchant isn't taking ORVYN payments right now.")
  await authorizePayout(sql, customer, amount, input.pin)
  const [from] = await sql`select id from accounts where customer_id = ${customer.id} and kind = 'savings'`
  try {
    return await withNotice(sql, postJournal(sql, {
      kind: 'qris_payment', idempotencyKey: `${customer.id}:${input.idempotencyKey}`,
      memo: { to: { name: m.name }, channel: 'QRIS', category: m.category, merchantId: m.id } as Memo,
      postings: [{ accountId: from.id, amount: -amount }, { accountId: await houseAccount(sql, 'MERCHANT_SETTLEMENT'), amount }],
    }))
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') throw new TransferError('INSUFFICIENT_FUNDS', "You don't have enough money for this payment.")
    throw e
  }
}

export type TransferInput = {
  bank: string
  accountNumber: string
  amount: number
  method: Method
  note?: string
  pin: string | null
  idempotencyKey: string
}

/** Story S1.12 / S1.13: validates everything, then posts one Journal Entry. */
export async function sendTransfer(sql: Sql, customer: { id: string; tier: string; full_name: string }, input: TransferInput) {
  const amount = Math.floor(Number(input.amount))
  if (!Number.isFinite(amount) || amount < 10_000) throw new TransferError('BAD_AMOUNT', 'The smallest transfer is Rp10,000.')
  const to = await lookupRecipient(sql, customer.id, input.bank, input.accountNumber)
  const opts = await transferOptions(sql, customer, to.internal, amount)
  const method = opts.methods.find(m => m.method === input.method)
  if (!method) throw new TransferError('BAD_METHOD', 'That transfer method is not available for this amount.')
  await authorizePayout(sql, customer, amount, input.pin, opts.remainingToday)

  const [from] = await sql`select id, number from accounts where customer_id = ${customer.id} and kind = 'savings'`
  const note = input.note?.trim().slice(0, 50) || undefined
  const sender = { name: customer.full_name, bank: 'ORVYN', account: from.number }
  try {
    if (to.internal) {
      const [dest] = await sql`select id from accounts where number = ${to.accountNumber}`
      return await withNotice(sql, postJournal(sql, {
        kind: 'internal_transfer', idempotencyKey: `${customer.id}:${input.idempotencyKey}`,
        memo: { from: sender, to: { name: to.name, bank: 'ORVYN', account: to.accountNumber }, channel: 'ORVYN transfer', category: 'transfer', note } satisfies Memo,
        postings: [{ accountId: from.id, amount: -amount }, { accountId: dest.id, amount }],
      }))
    }
    const fee = method.fee
    return await withNotice(sql, postJournal(sql, {
      kind: 'external_transfer', idempotencyKey: `${customer.id}:${input.idempotencyKey}`,
      memo: { from: sender, to: { name: to.name, bank: to.bankName, account: to.accountNumber }, channel: `${method.label} · ${to.bankName}`, category: 'transfer', fee, note } satisfies Memo,
      postings: [
        { accountId: from.id, amount: -(amount + fee) },
        { accountId: await houseAccount(sql, 'EXTERNAL_CLEARING'), amount },
        { accountId: await houseAccount(sql, 'FEE_INCOME'), amount: fee },
      ],
    }))
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') throw new TransferError('INSUFFICIENT_FUNDS', "You don't have enough money for this transfer and its fee.")
    throw e
  }
}
