import type { Sql } from './db.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { authorizePayout, TransferError } from './transfers.ts'
import { notify } from './inbox.ts'

// Stories S3.3–S3.4: Time Deposits (CONTEXT.md). Interest is simple, on actual days, paid at maturity after 20% tax.

const TENORS = [1, 3, 6, 12]
const TAX = 0.2
const rp = (n: number) => 'Rp' + n.toLocaleString('en-US')
type Customer = { id: string, tier: string }

const jakartaDate = (d: Date) => new Date(d.getTime() + 7 * 3600_000).toISOString().slice(0, 10)
/** `date` plus whole months; the 31st becomes the month's last day. */
export function addMonths(date: string, months: number) {
  const [y, m, d] = date.split('-').map(Number)
  const last = new Date(Date.UTC(y, m - 1 + months + 1, 0)).getUTCDate()
  return new Date(Date.UTC(y, m - 1 + months, Math.min(d, last))).toISOString().slice(0, 10)
}
const days = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86400_000)

/** Interest for a term: gross, the 20% tax, and what the customer receives. */
export function interest(principal: number, rateBp: number, from: string, to: string) {
  const gross = Math.floor(principal * rateBp / 10_000 * days(from, to) / 365)
  const tax = Math.floor(gross * TAX)
  return { gross, tax, net: gross - tax }
}

export async function depositRates(sql: Sql) {
  const [s] = await sql`select value from settings where key = 'deposit_rates'`
  return TENORS.map(t => ({ tenor: t, rateBp: Number(s.value[String(t)]) }))
}

export async function listDeposits(sql: Sql, customerId: string) {
  const rows = await sql`select d.*, a.balance from time_deposits d join accounts a on a.id = d.account_id
    where d.customer_id = ${customerId} and d.closed_at is null order by d.maturity_date`
  return rows.map(r => {
    const opened = r.opened_on.toISOString().slice(0, 10), matures = r.maturity_date.toISOString().slice(0, 10)
    return { id: r.account_id, principal: Number(r.balance), rateBp: r.rate_bp, tenor: r.tenor_months, openedOn: opened, maturityDate: matures,
      instruction: r.instruction, expectedInterest: interest(Number(r.balance), r.rate_bp, opened, matures).net }
  })
}

/** What the review screen shows before the PIN (S3.3 AC2). */
export async function depositQuote(sql: Sql, amountIn: any, tenor: number, now = new Date()) {
  const amount = Math.floor(Number(amountIn))
  if (!(amount >= 1_000_000)) throw new TransferError('BAD_AMOUNT', 'The smallest deposit is Rp1,000,000.')
  const rate = (await depositRates(sql)).find(r => r.tenor === Number(tenor))
  if (!rate) throw new TransferError('BAD_AMOUNT', 'Pick 1, 3, 6 or 12 months.')
  const opened = jakartaDate(now), matures = addMonths(opened, rate.tenor)
  return { amount, tenor: rate.tenor, rateBp: rate.rateBp, openedOn: opened, maturityDate: matures, ...interest(amount, rate.rateBp, opened, matures) }
}

export async function openDeposit(sql: Sql, customer: Customer, input: { amount: any, tenor: number, instruction: string, pin: string, idempotencyKey: string }) {
  const q = await depositQuote(sql, input.amount, input.tenor)
  const instruction = input.instruction === 'payout' ? 'payout' : 'rollover'
  await authorizePayout(sql, customer, q.amount, input.pin, Number.MAX_SAFE_INTEGER) // own money: no Daily Limit
  const [savings] = await sql`select id, name from accounts where customer_id = ${customer.id} and kind = 'savings'`
  return sql.begin(async tx => {
    const [dup] = await tx`select id from journal_entries where idempotency_key = ${`${customer.id}:${input.idempotencyKey}`}`
    if (dup) return dup
    const [a] = await tx`insert into accounts (kind, customer_id, name, currency, floor) values ('deposit', ${customer.id}, ${`${q.tenor}-month deposit`}, 'IDR', 0) returning id`
    await tx`insert into time_deposits (account_id, customer_id, principal, rate_bp, tenor_months, opened_on, maturity_date, instruction)
      values (${a.id}, ${customer.id}, ${q.amount}, ${q.rateBp}, ${q.tenor}, ${q.openedOn}, ${q.maturityDate}, ${instruction})`
    return postJournal(tx, {
      kind: 'deposit_open', idempotencyKey: `${customer.id}:${input.idempotencyKey}`,
      memo: { from: { name: savings.name }, to: { name: `${q.tenor}-month deposit` }, channel: `Time deposit · ${(q.rateBp / 100).toFixed(2)}% a year`, category: 'deposit' },
      postings: [{ accountId: savings.id, amount: -q.amount }, { accountId: a.id, amount: q.amount }],
    }).catch(e => { throw e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS' ? new TransferError('INSUFFICIENT_FUNDS', "You don't have enough in ORVYN Everyday for this deposit.") : e })
  })
}

export async function setInstruction(sql: Sql, customerId: string, id: string, instruction: string) {
  const [d] = await sql`update time_deposits set instruction = ${instruction === 'payout' ? 'payout' : 'rollover'}
    where account_id = ${id} and customer_id = ${customerId} and closed_at is null returning account_id`
  if (!d) throw new TransferError('NOT_FOUND', "We couldn't find that deposit.")
}

/** S3.4 AC3: early withdrawal returns the principal only. */
export async function breakDeposit(sql: Sql, customer: Customer, id: string, pin: string) {
  const [d] = await sql`select d.*, a.balance from time_deposits d join accounts a on a.id = d.account_id
    where d.account_id = ${id} and d.customer_id = ${customer.id} and d.closed_at is null`
  if (!d) throw new TransferError('NOT_FOUND', "We couldn't find that deposit.")
  await authorizePayout(sql, customer, 0, pin, Number.MAX_SAFE_INTEGER)
  const [savings] = await sql`select id, name from accounts where customer_id = ${customer.id} and kind = 'savings'`
  return sql.begin(async tx => {
    await tx`update time_deposits set closed_at = now() where account_id = ${id}`
    return postJournal(tx, {
      kind: 'deposit_close', memo: { from: { name: `${d.tenor_months}-month deposit` }, to: { name: savings.name }, channel: 'Time deposit · withdrawn early, no interest', category: 'deposit' },
      postings: [{ accountId: id, amount: -Number(d.balance) }, { accountId: savings.id, amount: Number(d.balance) }],
    })
  })
}

/** S3.4 AC1: pays the interest, then renews or pays everything out. Also what the admin "Mature now" button runs. */
export async function matureDeposit(sql: Sql, id: string, now = new Date()) {
  let note: [string, string, string] | null = null // inbox message, sent after the transaction commits
  const result = await sql.begin(async tx => {
    const [d] = await tx`select d.*, a.balance from time_deposits d join accounts a on a.id = d.account_id where d.account_id = ${id} and d.closed_at is null for update`
    if (!d) return null
    const opened = d.opened_on.toISOString().slice(0, 10), due = d.maturity_date.toISOString().slice(0, 10)
    const today = jakartaDate(now)
    const end = today < due ? today : due // "Mature now" in a demo pays interest up to today
    const principal = Number(d.balance), i = interest(principal, d.rate_bp, opened, end)
    const [savings] = await tx`select id, name from accounts where customer_id = ${d.customer_id} and kind = 'savings'`
    const [expense, tax] = [await houseAccount(tx as any, 'INTEREST_EXPENSE'), await houseAccount(tx as any, 'TAX_PAYABLE')]
    const name = `${d.tenor_months}-month deposit`
    if (d.instruction === 'rollover') {
      if (i.gross > 0) await postJournal(tx, {
        kind: 'deposit_interest', memo: { from: { name: 'ORVYN Bank' }, to: { name }, channel: `Interest after 20% tax (${rp(i.tax)})`, category: 'income' },
        postings: [{ accountId: expense, amount: -i.gross }, { accountId: tax, amount: i.tax }, { accountId: id, amount: i.net }],
      })
      const [r] = await tx`select value from settings where key = 'deposit_rates'`
      const rateBp = Number(r.value[String(d.tenor_months)])
      await tx`update time_deposits set principal = ${principal + i.net}, rate_bp = ${rateBp}, opened_on = ${today}, maturity_date = ${addMonths(today, d.tenor_months)} where account_id = ${id}`
      note = [d.customer_id, 'Deposit renewed', `Your ${name} earned ${rp(i.net)} after tax and renewed for another ${d.tenor_months} ${d.tenor_months === 1 ? 'month' : 'months'} with ${rp(principal + i.net)}.`]
    } else {
      await postJournal(tx, {
        kind: 'deposit_close', memo: { from: { name }, to: { name: savings.name }, channel: `Time deposit matured · interest ${rp(i.net)} after tax`, category: 'income' },
        postings: [{ accountId: id, amount: -principal }, { accountId: expense, amount: -i.gross }, { accountId: tax, amount: i.tax }, { accountId: savings.id, amount: principal + i.net }],
      })
      await tx`update time_deposits set closed_at = now() where account_id = ${id}`
      note = [d.customer_id, 'Deposit paid out', `Your ${name} matured: ${rp(principal + i.net)} is in ORVYN Everyday, including ${rp(i.net)} interest after tax.`]
    }
    return i
  })
  if (note) await notify(sql, ...(note as [string, string, string]))
  return result
}

/** Runs every minute; anything due today or earlier (Jakarta) matures once. */
export async function runMaturities(sql: Sql, now = new Date()) {
  const due = await sql`select account_id from time_deposits where closed_at is null and maturity_date <= ${jakartaDate(now)}`
  for (const d of due) await matureDeposit(sql, d.account_id, now)
}
