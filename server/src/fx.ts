import type { Sql } from './db.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { authorizePayout, TransferError } from './transfers.ts'

// Stories S3.1–S3.2: FX Accounts and FX Exchanges (CONTEXT.md). Amounts in an FX Account are in minor units (cents; yen).

export const CURRENCIES: Record<string, { name: string, decimals: number }> = {
  USD: { name: 'US dollar', decimals: 2 }, SGD: { name: 'Singapore dollar', decimals: 2 }, JPY: { name: 'Japanese yen', decimals: 0 },
  AUD: { name: 'Australian dollar', decimals: 2 }, EUR: { name: 'Euro', decimals: 2 },
}
const MIN_IDR = 100_000
type Customer = { id: string, tier: string, full_name?: string }

/** Rates a customer gets. Prestige and Private pay a narrower spread around the middle rate (S3.2 AC4). */
export async function ratesFor(sql: Sql, tier: string) {
  const rows = await sql`select currency, buy, sell from fx_rates order by array_position(array['USD', 'SGD', 'EUR', 'JPY', 'AUD'], currency::text)`
  const [s] = await sql`select value from settings where key = 'fx_prestige_spread'`
  const factor = tier === 'everyday' ? 1 : Number(s?.value?.factor ?? 0.5)
  return rows.map(r => {
    const buy = Number(r.buy), sell = Number(r.sell), mid = (buy + sell) / 2, half = (sell - buy) / 2 * factor
    const round = (n: number) => Math.round(n * 10_000) / 10_000
    return { currency: r.currency as string, name: CURRENCIES[r.currency].name, decimals: CURRENCIES[r.currency].decimals, buy: round(mid - half), sell: round(mid + half), special: factor < 1 }
  })
}

/** Rupiah for an amount of foreign currency, exactly: BigInt on 4-decimal rates. Customers never get the rounding. */
export function toRupiah(minor: number, decimals: number, rate: number, side: 'buy' | 'sell') {
  const num = BigInt(minor) * BigInt(Math.round(rate * 10_000)), den = 10n ** BigInt(decimals) * 10_000n
  return Number(side === 'buy' ? (num + den - 1n) / den : num / den) // the customer buys: round up; sells: round down
}

export async function listFx(sql: Sql, customer: Customer) {
  const rates = await ratesFor(sql, customer.tier)
  const rows = await sql`select id, currency, balance from accounts where customer_id = ${customer.id} and kind = 'fx' order by currency`
  return rows.map(r => {
    const rate = rates.find(x => x.currency === r.currency)!
    return { id: r.id, currency: r.currency, name: rate.name, decimals: rate.decimals, balance: Number(r.balance), idrValue: toRupiah(Number(r.balance), rate.decimals, rate.buy, 'sell') }
  })
}

export async function openFx(sql: Sql, customerId: string, currency: string) {
  if (!CURRENCIES[currency]) throw new TransferError('NOT_FOUND', 'Pick USD, SGD, JPY, AUD or EUR.')
  const [a] = await sql`insert into accounts (kind, customer_id, name, currency, floor) values ('fx', ${customerId}, ${currency + ' account'}, ${currency}, 0)
    on conflict do nothing returning id`
  return a?.id ?? (await sql`select id from accounts where customer_id = ${customerId} and kind = 'fx' and currency = ${currency}`)[0].id
}

export async function closeFx(sql: Sql, customerId: string, currency: string) {
  const [a] = await sql`select id, balance from accounts where customer_id = ${customerId} and kind = 'fx' and currency = ${currency}`
  if (!a) throw new TransferError('NOT_FOUND', "You don't have that account.")
  if (Number(a.balance) !== 0) throw new TransferError('BAD_AMOUNT', 'Sell what is left first, then close the account.')
  // Accounts with history stay in the ledger; closing is "no FX account in this currency" from the customer's side.
  await sql`update accounts set kind = 'house', customer_id = null, code = ${'CLOSED_' + a.id} where id = ${a.id}`
}

/** What the customer pays and gets, before the PIN (S3.2 AC1). `amount` is in the foreign currency's minor units. */
export async function quote(sql: Sql, customer: Customer, currency: string, side: 'buy' | 'sell', amount: number) {
  const rate = (await ratesFor(sql, customer.tier)).find(r => r.currency === currency)
  if (!rate) throw new TransferError('NOT_FOUND', 'Pick USD, SGD, JPY, AUD or EUR.')
  const minor = Math.floor(Number(amount))
  if (!(minor > 0)) throw new TransferError('BAD_AMOUNT', 'Enter an amount.')
  const used = side === 'buy' ? rate.sell : rate.buy
  const idr = toRupiah(minor, rate.decimals, used, side)
  if (idr < MIN_IDR) throw new TransferError('BAD_AMOUNT', 'The smallest exchange is worth Rp100,000.')
  return { currency, side, foreign: minor, decimals: rate.decimals, idr, rate: used, special: rate.special }
}

export async function exchange(sql: Sql, customer: Customer, input: { currency: string, side: 'buy' | 'sell', amount: number, pin: string, idempotencyKey: string }) {
  const q = await quote(sql, customer, input.currency, input.side, input.amount)
  await authorizePayout(sql, customer, q.idr, input.pin, Number.MAX_SAFE_INTEGER) // PIN and maintenance; own money, so no Daily Limit
  const [savings] = await sql`select id, name from accounts where customer_id = ${customer.id} and kind = 'savings'`
  const fx = await openFx(sql, customer.id, q.currency)
  const [posIdr, posFx] = [await houseAccount(sql, 'FX_POSITION_IDR'), await houseAccount(sql, 'FX_POSITION_' + q.currency)]
  const buy = q.side === 'buy'
  const shown = (q.foreign / 10 ** q.decimals).toLocaleString('en-US', { minimumFractionDigits: q.decimals, maximumFractionDigits: q.decimals })
  try {
    return await postJournal(sql, {
      kind: 'fx_exchange', idempotencyKey: `${customer.id}:${input.idempotencyKey}`,
      memo: {
        from: { name: buy ? savings.name : `${q.currency} account` }, to: { name: buy ? `${q.currency} account` : savings.name },
        channel: `${buy ? 'Bought' : 'Sold'} ${q.currency} ${shown} at Rp${q.rate.toLocaleString('en-US')}`, category: 'fx',
        fx: { currency: q.currency, foreign: q.foreign, rate: q.rate, side: q.side },
      },
      postings: buy
        ? [{ accountId: savings.id, amount: -q.idr }, { accountId: posIdr, amount: q.idr }, { accountId: posFx, amount: -q.foreign }, { accountId: fx, amount: q.foreign }]
        : [{ accountId: fx, amount: -q.foreign }, { accountId: posFx, amount: q.foreign }, { accountId: posIdr, amount: -q.idr }, { accountId: savings.id, amount: q.idr }],
    })
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS')
      throw new TransferError('INSUFFICIENT_FUNDS', buy ? "You don't have enough Rupiah for this." : `You don't have that much ${q.currency}.`)
    throw e
  }
}
