import { createHash } from 'node:crypto'
import type { Sql } from './db.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { authorizePayout, TransferError } from './transfers.ts'
import { notify } from './inbox.ts'
import { listFx } from './fx.ts'
import { listDeposits } from './deposits.ts'
import { listPockets } from './pockets.ts'
import { listCards } from './cards.ts'

// Stories S3.5–S3.9: risk profile, funds, bonds, gold and Total Assets. Holdings live outside the ledger (ADR 0003).

type Customer = { id: string, tier: string }
const rp = (n: number) => 'Rp' + n.toLocaleString('en-US')
const jakartaDate = (d: Date) => new Date(d.getTime() + 7 * 3600_000).toISOString().slice(0, 10)
const addDays = (date: string, n: number) => new Date(Date.parse(date) + n * 86400_000).toISOString().slice(0, 10)

// ---- Risk profile (S3.5)

/** Five questions, each answered 0 (most careful) to 3 (most daring). */
export const QUIZ = [
  { q: 'When will you need this money?', a: ['Within a year', 'In 1 to 3 years', 'In 3 to 5 years', 'Not for 5 years or more'] },
  { q: 'Your investment drops 15% in a month. What do you do?', a: ['Sell everything', 'Sell some', 'Wait it out', 'Buy more'] },
  { q: 'What matters most to you?', a: ['Not losing any money', 'Steady, small growth', 'Growth, with some ups and downs', 'The most growth, whatever the swings'] },
  { q: 'How much have you invested before?', a: ['Never', 'Only deposits or savings', 'Funds or bonds', 'Shares'] },
  { q: 'How much of your savings is this?', a: ['Most of it', 'About half', 'About a quarter', 'A small part'] },
]
const MAX_RISK = { conservative: 2, moderate: 3, aggressive: 4 } as const
export type Profile = keyof typeof MAX_RISK

export async function setRiskProfile(sql: Sql, customerId: string, answers: number[]): Promise<Profile> {
  if (answers.length !== QUIZ.length || answers.some(a => !Number.isInteger(a) || a < 0 || a > 3)) throw new TransferError('BAD_AMOUNT', 'Answer every question.')
  const score = answers.reduce((s, a) => s + a, 0) // 0..15
  const profile: Profile = score <= 5 ? 'conservative' : score <= 10 ? 'moderate' : 'aggressive'
  await sql`update customers set risk_profile = ${profile} where id = ${customerId}`
  return profile
}

// ---- Prices

/** Same product and day, same number: a stable pseudo-random normal draw for the simulated NAV. */
// SHA-256, not a quick string hash: FNV on near-identical seeds ("ORV-EQ2026-10-14") came out biased (mean -0.21 a day).
function noise(seed: string) {
  const h = createHash('sha256').update(seed).digest()
  const u1 = (h.readUInt32BE(0) + 1) / 4294967297, u2 = (h.readUInt32BE(4) + 1) / 4294967297
  return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2)
}

/** Fills in each fund's NAV for every day up to today, starting a year back at 1,000.0000 (S3.6 AC3). */
export async function ensurePrices(sql: Sql, now = new Date()) {
  const today = jakartaDate(now)
  const funds = await sql`select id, drift_bp, vol_bp from products where kind = 'fund'`
  for (const f of funds) {
    const [last] = await sql`select day, price from prices where product_id = ${f.id} order by day desc limit 1`
    let day = last ? addDays(last.day.toISOString().slice(0, 10), 1) : addDays(today, -400)
    let price = last ? Number(last.price) : 1000
    const rows: { product_id: string, day: string, price: number }[] = []
    const mu = f.drift_bp / 10_000 / 365, sigma = f.vol_bp / 10_000 / Math.sqrt(365)
    while (day <= today) {
      price = Math.max(1, price * Math.exp(mu - sigma * sigma / 2 + sigma * noise(f.id + day)))
      rows.push({ product_id: f.id, day, price: Math.round(price * 10_000) / 10_000 })
      day = addDays(day, 1)
    }
    if (rows.length) await sql`insert into prices ${sql(rows)} on conflict do nothing`
  }
}

async function goldPrice(sql: Sql) {
  const [g] = await sql`select value from settings where key = 'gold_price'`
  return { buy: Number(g.value.buy), sell: Number(g.value.sell) }
}

/** Today's Rupiah price of one unit: NAV for funds, 1 for bond face value, the buy-back price per gram for gold. */
async function unitValue(sql: Sql, p: { id: string, kind: string }) {
  if (p.kind === 'bond') return 1
  if (p.kind === 'gold') return (await goldPrice(sql)).buy
  const [r] = await sql`select price from prices where product_id = ${p.id} order by day desc limit 1`
  return Number(r.price)
}

// ---- Products and holdings

export async function listProducts(sql: Sql, customer: Customer) {
  const [{ risk_profile }] = await sql`select risk_profile from customers where id = ${customer.id}`
  const products = await sql`select * from products order by kind, risk_level, id`
  const gold = await goldPrice(sql)
  const out = []
  for (const p of products) {
    const base = { id: p.id, kind: p.kind, name: p.name, subtype: p.subtype, riskLevel: p.risk_level, manager: p.manager, minBuy: Number(p.min_buy),
      description: p.description, suitable: risk_profile ? p.risk_level <= MAX_RISK[risk_profile as Profile] : null }
    if (p.kind === 'fund') {
      const [now] = await sql`select price from prices where product_id = ${p.id} order by day desc limit 1`
      const [ago] = await sql`select price from prices where product_id = ${p.id} and day <= current_date - 365 order by day desc limit 1`
      out.push({ ...base, nav: Number(now.price), oneYear: ago ? Math.round((Number(now.price) / Number(ago.price) - 1) * 10_000) / 100 : null })
    } else if (p.kind === 'bond') out.push({ ...base, couponBp: p.coupon_bp, maturity: p.maturity.toISOString().slice(0, 10) })
    else out.push({ ...base, buyPrice: gold.buy, sellPrice: gold.sell })
  }
  return { riskProfile: risk_profile as Profile | null, products: out }
}

export async function priceHistory(sql: Sql, productId: string, days: number) {
  const rows = await sql`select day, price from prices where product_id = ${productId} and day > current_date - ${Math.min(400, Math.max(7, days))}::int order by day`
  return rows.map(r => ({ day: r.day.toISOString().slice(0, 10), price: Number(r.price) }))
}

export async function listHoldings(sql: Sql, customerId: string) {
  const rows = await sql`select h.*, p.kind, p.name from holdings h join products p on p.id = h.product_id where h.customer_id = ${customerId} and h.quantity > 0 order by p.kind, p.name`
  const out = []
  for (const h of rows) {
    const value = Math.round(Number(h.quantity) * await unitValue(sql, { id: h.product_id, kind: h.kind })) // round: floor showed Rp1 "lost" right after buying
    out.push({ productId: h.product_id, kind: h.kind, name: h.name, quantity: Number(h.quantity), cost: Number(h.cost), value, gain: value - Number(h.cost) })
  }
  return out
}

// ---- Buying and selling (S3.6–S3.8)


async function product(sql: Sql, id: string) {
  const [p] = await sql`select * from products where id = ${id}`
  if (!p) throw new TransferError('NOT_FOUND', "We couldn't find that product.")
  return p
}

/** Money and quantity change together: the Journal Entry and the holding update share one database transaction. */
async function move(sql: Sql, customer: Customer, p: any, idr: number, quantity: number, costChange: number, memo: object, key?: string) {
  const [savings] = await sql`select id, name from accounts where customer_id = ${customer.id} and kind = 'savings'`
  const clearing = await houseAccount(sql, 'INVESTMENT_CLEARING')
  try {
    return await sql.begin(async tx => {
      await tx`insert into holdings (customer_id, product_id, quantity, cost) values (${customer.id}, ${p.id}, 0, 0) on conflict do nothing`
      const [h] = await tx`update holdings set quantity = quantity + ${quantity}, cost = greatest(0, cost + ${costChange})
        where customer_id = ${customer.id} and product_id = ${p.id} returning quantity`
      if (Number(h.quantity) < 0) throw new TransferError('INSUFFICIENT_FUNDS', "You don't hold that much.")
      return postJournal(tx, {
        kind: 'investment', idempotencyKey: key ? `${customer.id}:${key}` : undefined, memo,
        postings: idr > 0 ? [{ accountId: savings.id, amount: -idr }, { accountId: clearing, amount: idr }] : [{ accountId: clearing, amount: idr }, { accountId: savings.id, amount: -idr }],
      })
    })
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') throw new TransferError('INSUFFICIENT_FUNDS', "You don't have enough in ORVYN Everyday for this.")
    if (e?.code === '23514') throw new TransferError('INSUFFICIENT_FUNDS', "You don't hold that much.") // quantity >= 0 check
    throw e
  }
}

async function checkProfile(sql: Sql, customerId: string, p: any, acknowledged: boolean) {
  const [{ risk_profile }] = await sql`select risk_profile from customers where id = ${customerId}`
  if (!risk_profile) throw new TransferError('BAD_METHOD', 'Take the risk profile quiz before your first investment.')
  if (p.risk_level > MAX_RISK[risk_profile as Profile] && !acknowledged)
    throw new TransferError('BAD_METHOD', `This is riskier than your ${risk_profile} profile. Confirm you understand the risk to go ahead.`)
}

/** Buy in Rupiah: fund units at today's NAV, bond face value in Rp1,000,000 steps, or gold at the sell price. Gold may also be bought in grams. */
export async function buy(sql: Sql, customer: Customer, input: { productId: string, amount?: number, grams?: number, pin: string, acknowledgeRisk?: boolean, idempotencyKey: string }) {
  const p = await product(sql, input.productId)
  await checkProfile(sql, customer.id, p, input.acknowledgeRisk === true)
  let idr: number, quantity: number
  if (p.kind === 'gold') {
    const { sell } = await goldPrice(sql)
    if (input.grams) {
      quantity = Math.floor(Number(input.grams) * 10_000) / 10_000
      if (!(quantity >= 0.01)) throw new TransferError('BAD_AMOUNT', 'The smallest purchase is 0.01 g.')
      idr = Math.ceil(quantity * sell)
    } else {
      idr = Math.floor(Number(input.amount))
      quantity = Math.floor(idr / sell * 10_000) / 10_000
    }
    if (!(idr >= Number(p.min_buy))) throw new TransferError('BAD_AMOUNT', `The smallest purchase is ${rp(Number(p.min_buy))}.`)
  } else {
    idr = Math.floor(Number(input.amount))
    if (!(idr >= Number(p.min_buy))) throw new TransferError('BAD_AMOUNT', `The smallest purchase is ${rp(Number(p.min_buy))}.`)
    if (p.kind === 'bond' && idr % 1_000_000) throw new TransferError('BAD_AMOUNT', 'Bonds are bought in steps of Rp1,000,000.')
    quantity = p.kind === 'bond' ? idr : Math.floor(idr / await unitValue(sql, p) * 1e6) / 1e6
  }
  await authorizePayout(sql, customer, idr, input.pin, Number.MAX_SAFE_INTEGER) // own money: no Daily Limit
  const what = p.kind === 'gold' ? `${quantity.toFixed(4)} g` : p.kind === 'fund' ? `${quantity.toLocaleString('en-US', { maximumFractionDigits: 4 })} units` : 'face value'
  return move(sql, customer, p, idr, quantity, idr,
    { from: { name: 'ORVYN Everyday' }, to: { name: p.name }, channel: `Bought ${what}`, category: 'invest', productId: p.id }, input.idempotencyKey)
}

/** Sell fund units or grams of gold at today's price (bonds are held to maturity). Leaving `quantity` out sells everything. */
export async function sell(sql: Sql, customer: Customer, input: { productId: string, quantity?: number, pin: string, idempotencyKey: string }) {
  const p = await product(sql, input.productId)
  if (p.kind === 'bond') throw new TransferError('BAD_METHOD', 'Bonds are held until they mature, then the money comes back to you.')
  const [h] = await sql`select quantity, cost from holdings where customer_id = ${customer.id} and product_id = ${p.id}`
  const held = Number(h?.quantity ?? 0)
  const quantity = input.quantity == null ? held : Math.floor(Number(input.quantity) * 1e6) / 1e6
  if (!(quantity > 0) || quantity > held + 1e-9) throw new TransferError('INSUFFICIENT_FUNDS', "You don't hold that much.")
  const idr = Math.floor(quantity * await unitValue(sql, p))
  if (!(idr >= 1)) throw new TransferError('BAD_AMOUNT', 'That is too little to sell.')
  await authorizePayout(sql, customer, 0, input.pin, Number.MAX_SAFE_INTEGER)
  const costOut = Math.round(Number(h.cost) * quantity / held)
  return move(sql, customer, p, -idr, -quantity, -costOut,
    { from: { name: p.name }, to: { name: 'ORVYN Everyday' }, channel: `Sold ${p.kind === 'gold' ? `${quantity.toFixed(4)} g` : `${quantity.toLocaleString('en-US', { maximumFractionDigits: 4 })} units`}`, category: 'invest', productId: p.id }, input.idempotencyKey)
}

// ---- Bonds (S3.7 AC3–AC4)

/** Runs every minute: on the 10th (WIB) each holder gets a month's coupon after 10% tax; at maturity the face value comes home. */
export async function runBonds(sql: Sql, now = new Date()) {
  const today = jakartaDate(now), month = today.slice(0, 7)
  const held = await sql`select h.customer_id, h.quantity, p.* from holdings h join products p on p.id = h.product_id where p.kind = 'bond' and h.quantity > 0`
  if (!held.length) return
  const [expense, tax, clearing] = [await houseAccount(sql, 'INTEREST_EXPENSE'), await houseAccount(sql, 'TAX_PAYABLE'), await houseAccount(sql, 'INVESTMENT_CLEARING')]
  for (const h of held) {
    const [savings] = await sql`select id from accounts where customer_id = ${h.customer_id} and kind = 'savings'`
    const face = Number(h.quantity)
    if (Number(today.slice(8)) >= 10) {
      const gross = Math.floor(face * h.coupon_bp / 10_000 / 12), cut = Math.floor(gross * 0.1)
      const [fresh] = await sql`insert into bond_coupons (customer_id, product_id, month) values (${h.customer_id}, ${h.id}, ${month}) on conflict do nothing returning month`
      if (fresh && gross > 0) {
        await postJournal(sql, { kind: 'bond_coupon', memo: { from: { name: h.name }, to: { name: 'ORVYN Everyday' }, channel: `Coupon after 10% tax`, category: 'income' },
          postings: [{ accountId: expense, amount: -gross }, { accountId: tax, amount: cut }, { accountId: savings.id, amount: gross - cut }] })
        await notify(sql, h.customer_id, 'Bond coupon paid', `${rp(gross - cut)} from ${h.name} is in ORVYN Everyday.`)
      }
    }
    if (h.maturity.toISOString().slice(0, 10) <= today) {
      await sql.begin(async tx => {
        await tx`update holdings set quantity = 0, cost = 0 where customer_id = ${h.customer_id} and product_id = ${h.id}`
        await postJournal(tx, { kind: 'investment', memo: { from: { name: h.name }, to: { name: 'ORVYN Everyday' }, channel: 'Bond matured', category: 'invest', productId: h.id },
          postings: [{ accountId: clearing, amount: -face }, { accountId: savings.id, amount: face }] })
      })
      await notify(sql, h.customer_id, 'Bond matured', `${rp(face)} from ${h.name} is back in ORVYN Everyday.`)
    }
  }
}

// ---- Total Assets (S3.9)

export async function totalAssets(sql: Sql, customer: Customer) {
  const [savings] = await sql`select balance from accounts where customer_id = ${customer.id} and kind = 'savings'`
  const pockets = (await listPockets(sql, customer.id)).reduce((s, p) => s + p.balance, 0)
  const deposits = (await listDeposits(sql, customer.id)).reduce((s, d) => s + d.principal, 0)
  const fx = (await listFx(sql, customer)).reduce((s, a) => s + a.idrValue, 0)
  const holdings = await listHoldings(sql, customer.id)
  const sum = (k: string) => holdings.filter(h => h.kind === k).reduce((s, h) => s + h.value, 0)
  const parts = [
    { key: 'cash', label: 'Savings and pockets', value: Number(savings.balance) + pockets },
    { key: 'deposits', label: 'Time deposits', value: deposits },
    { key: 'fx', label: 'Foreign currency', value: fx },
    { key: 'funds', label: 'Mutual funds', value: sum('fund') },
    { key: 'bonds', label: 'Bonds', value: sum('bond') },
    { key: 'gold', label: 'Gold', value: sum('gold') },
  ]
  const owed = (await listCards(sql, customer.id)).reduce((s, c: any) => s + (c.credit?.owed ?? 0), 0)
  return { total: parts.reduce((s, p) => s + p.value, 0), parts, creditOwed: owed }
}
