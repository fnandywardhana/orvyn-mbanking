import { randomInt } from 'node:crypto'
import type { TransactionSql } from 'postgres'
import type { Sql } from './db.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { TransferError, withNotice } from './transfers.ts'
import { notify, notifyJournal } from './inbox.ts'
import { verifyPassword } from './auth.ts'

// Cards (stories S1.23–S1.26). A credit card is a Card on a Credit Account whose floor is minus the credit limit.

export const PRODUCTS = {
  debit: { name: 'ORVYN Debit', kind: 'debit' },
  cashback: { name: 'ORVYN Cashback', kind: 'credit' },
  voyage: { name: 'ORVYN Voyage', kind: 'credit' },
} as const
type Product = keyof typeof PRODUCTS

function luhnNumber() {
  const digits = [9, ...Array.from({ length: 14 }, () => randomInt(0, 10))]
  const sum = digits.reverse().reduce((s, d, i) => s + (i % 2 === 0 ? (d * 2 > 9 ? d * 2 - 9 : d * 2) : d), 0)
  return [...digits.reverse(), (10 - (sum % 10)) % 10].join('')
}

/** Opens a card; credit products also open their Credit Account. Runs in the caller's transaction. */
export async function issueCard(tx: TransactionSql, customerId: string, product: Product, opts: { savingsId: string; creditLimit?: number }) {
  let accountId = opts.savingsId
  if (PRODUCTS[product].kind === 'credit') {
    const limit = opts.creditLimit ?? 15_000_000
    const [a] = await tx`insert into accounts (kind, customer_id, name, currency, floor) values ('credit', ${customerId}, ${PRODUCTS[product].name}, 'IDR', ${-limit}) returning id`
    accountId = a.id
  }
  const exp = new Date(); exp.setFullYear(exp.getFullYear() + 5)
  const [card] = await tx`
    insert into cards (customer_id, account_id, kind, product, number, cvv, expiry, daily_limit)
    values (${customerId}, ${accountId}, ${PRODUCTS[product].kind}, ${product}, ${luhnNumber()}, ${String(randomInt(0, 1000)).padStart(3, '0')},
      ${String(exp.getMonth() + 1).padStart(2, '0') + '/' + String(exp.getFullYear()).slice(2)}, ${PRODUCTS[product].kind === 'credit' ? 20_000_000 : 10_000_000})
    returning *`
  return card
}

/** What the Cards tab shows. Never includes the full number or CVV. */
export async function listCards(sql: Sql, customerId: string) {
  const rows = await sql`select c.*, a.balance, a.floor from cards c join accounts a on a.id = c.account_id where c.customer_id = ${customerId} order by c.kind desc, c.created_at`
  return rows.map(c => {
    const base = {
      id: c.id, kind: c.kind, product: c.product, name: PRODUCTS[c.product as Product].name, last4: c.number.slice(-4),
      frozen: c.frozen, online: c.online, overseas: c.overseas, contactless: c.contactless, dailyLimit: Number(c.daily_limit),
    }
    if (c.kind !== 'credit') return base
    const limit = -Number(c.floor), owed = Math.max(0, -Number(c.balance))
    const due = new Date(); due.setDate(25); if (due < new Date()) due.setMonth(due.getMonth() + 1)
    return { ...base, credit: { limit, owed, available: limit - owed, minimum: owed === 0 ? 0 : Math.min(owed, Math.max(50_000, Math.round(owed * 0.05 / 1000) * 1000)), dueDate: due.toISOString().slice(0, 10) } }
  })
}

async function ownCard(sql: Sql, customerId: string, cardId: string) {
  const [c] = await sql`select * from cards where id = ${cardId} and customer_id = ${customerId}`
  if (!c) throw new TransferError('NOT_FOUND', "We couldn't find that card.")
  return c
}

export async function updateCard(sql: Sql, customerId: string, cardId: string, patch: { frozen?: boolean; online?: boolean; overseas?: boolean; contactless?: boolean; dailyLimit?: number }) {
  const c = await ownCard(sql, customerId, cardId)
  const max = c.kind === 'credit' ? 50_000_000 : 20_000_000
  if (patch.dailyLimit !== undefined && !(patch.dailyLimit >= 100_000 && patch.dailyLimit <= max)) {
    throw new TransferError('BAD_AMOUNT', `Set a daily limit between Rp100,000 and Rp${max.toLocaleString('en-US')}.`)
  }
  await sql`update cards set
    frozen = ${patch.frozen ?? c.frozen}, online = ${patch.online ?? c.online}, overseas = ${patch.overseas ?? c.overseas},
    contactless = ${patch.contactless ?? c.contactless}, daily_limit = ${patch.dailyLimit ?? c.daily_limit}
    where id = ${cardId}`
}

/** Full number, CVV and expiry, only after the PIN (S1.23 AC3). */
export async function cardDetails(sql: Sql, customerId: string, cardId: string, pin: string) {
  const c = await ownCard(sql, customerId, cardId)
  const [cu] = await sql`select pin_hash from customers where id = ${customerId}`
  if (!cu.pin_hash || !(await verifyPassword(pin, cu.pin_hash))) throw new TransferError('WRONG_PIN', 'Wrong PIN. Try again.')
  return { number: c.number, cvv: c.cvv, expiry: c.expiry }
}

export type CardSpend = { cardId: string; merchant: string; amount: number; channel: 'in_store' | 'online' | 'overseas' | 'contactless'; at?: Date }

/** Story S1.26 AC3: declined purchases still tell the customer why. */
async function declined(sql: Sql, card: any, s: CardSpend, reason: string) {
  if (!s.at) await notify(sql, card.customer_id, 'Card payment declined', `Rp${s.amount.toLocaleString('en-US')} at ${s.merchant} was declined: ${reason.toLowerCase()}.`)
  return { approved: false as const, reason }
}

/** Story S1.26: a card purchase, approved or declined by the card's own switches and limits. */
export async function spendOnCard(sql: Sql | TransactionSql, s: CardSpend): Promise<{ approved: true; journalId: string } | { approved: false; reason: string }> {
  const [c] = await sql`select * from cards where id = ${s.cardId}`
  if (!c) return { approved: false, reason: 'Unknown card' }
  if (c.frozen) return declined(sql as Sql, c, s, 'Card is frozen')
  if (s.channel === 'online' && !c.online) return declined(sql as Sql, c, s, 'Online payments are off')
  if (s.channel === 'overseas' && !c.overseas) return declined(sql as Sql, c, s, 'Overseas payments are off')
  if (s.channel === 'contactless' && !c.contactless) return declined(sql as Sql, c, s, 'Contactless is off')
  const [{ spent }] = await sql`
    select coalesce(sum((j.memo->>'amount')::bigint), 0)::bigint as spent from journal_entries j
    where j.kind = 'card_spend' and j.memo->>'cardId' = ${c.id}
      and j.created_at >= (date_trunc('day', now() at time zone 'Asia/Jakarta') at time zone 'Asia/Jakarta')`
  if (!s.at && Number(spent) + s.amount > Number(c.daily_limit)) return declined(sql as Sql, c, s, "Over the card's daily limit")
  try {
    const j = await postJournal(sql, {
      kind: 'card_spend', at: s.at,
      memo: { to: { name: s.merchant }, channel: `${PRODUCTS[c.product as Product].name} ·••• ${c.number.slice(-4)}`, category: 'shopping', cardId: c.id, amount: s.amount, cardChannel: s.channel },
      postings: [{ accountId: c.account_id, amount: -s.amount }, { accountId: await houseAccount(sql as Sql, 'MERCHANT_SETTLEMENT'), amount: s.amount }],
    })
    if (!s.at) await notifyJournal(sql as Sql, j.id)
    return { approved: true, journalId: j.id }
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') return declined(sql as Sql, c, s, c.kind === 'credit' ? 'Not enough credit left' : 'Not enough money in the account')
    throw e
  }
}

/** Story S1.25: pay the credit card bill from the Savings Account. Moving between own accounts: no Daily Limit. */
export async function payCardBill(sql: Sql, customerId: string, cardId: string, amount: number, pin: string, idempotencyKey: string) {
  const c = await ownCard(sql, customerId, cardId)
  if (c.kind !== 'credit') throw new TransferError('BAD_METHOD', 'Only credit cards have a bill.')
  const [cu] = await sql`select pin_hash from customers where id = ${customerId}`
  if (!cu.pin_hash || !(await verifyPassword(pin, cu.pin_hash))) throw new TransferError('WRONG_PIN', 'Wrong PIN. Try again.')
  const [credit] = await sql`select balance from accounts where id = ${c.account_id}`
  const owed = Math.max(0, -Number(credit.balance))
  if (!(amount >= 10_000 && amount <= owed)) throw new TransferError('BAD_AMOUNT', owed === 0 ? 'Nothing to pay right now.' : `Pay between Rp10,000 and Rp${owed.toLocaleString('en-US')}.`)
  const [savings] = await sql`select id from accounts where customer_id = ${customerId} and kind = 'savings'`
  try {
    return await withNotice(sql, postJournal(sql, {
      kind: 'card_payment', idempotencyKey: `${customerId}:${idempotencyKey}`,
      memo: { from: { name: 'Your savings' }, to: { name: `${PRODUCTS[c.product as Product].name} bill` }, channel: 'Credit card payment', category: 'transfer', cardId: c.id },
      postings: [{ accountId: savings.id, amount: -amount }, { accountId: c.account_id, amount }],
    }))
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') throw new TransferError('INSUFFICIENT_FUNDS', "You don't have enough in your savings for this payment.")
    throw e
  }
}

/** Customers created before cards existed get their Debit Card at server start. Safe to run every time. */
export async function backfillDebitCards(sql: Sql) {
  const missing = await sql`
    select c.id, a.id as savings_id from customers c join accounts a on a.customer_id = c.id and a.kind = 'savings'
    where not exists (select 1 from cards k where k.customer_id = c.id and k.kind = 'debit')`
  for (const m of missing) await sql.begin(tx => issueCard(tx, m.id, 'debit', { savingsId: m.savings_id }))
  return missing.length
}
