import { createHash } from 'node:crypto'
import type { Sql } from './db.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { authorizePayout, TransferError, generatedName } from './transfers.ts'

// Bill Payments (stories S1.18–S1.21). All customer numbers, names and bills are made up.

const OPERATORS: [string, string[]][] = [
  ['Telkomsel', ['0811', '0812', '0813', '0821', '0822', '0823', '0851', '0852', '0853']],
  ['Indosat', ['0814', '0815', '0816', '0855', '0856', '0857', '0858']],
  ['XL', ['0817', '0818', '0819', '0859', '0877', '0878']],
  ['Axis', ['0831', '0832', '0833', '0838']],
  ['Tri', ['0895', '0896', '0897', '0898', '0899']],
  ['Smartfren', ['0881', '0882', '0883', '0884', '0885', '0886', '0887', '0888', '0889']],
]

export function operatorFor(phone: string) {
  const p = phone.replace(/\D/g, '').replace(/^62/, '0')
  return OPERATORS.find(([, prefixes]) => prefixes.includes(p.slice(0, 4)))?.[0] ?? null
}

const hash = (s: string) => { let h = 7; for (const ch of s) h = (h * 31 + ch.charCodeAt(0)) >>> 0; return h }
const period = (d = new Date()) => new Date(d.getTime() + 7 * 3_600_000).toISOString().slice(0, 7) // WIB month
const POWER = ['900 VA', '1,300 VA', '2,200 VA', '3,500 VA', '5,500 VA']

export type Inquiry = {
  biller: string
  billerName: string
  kind: string
  number: string
  name: string | null
  details: string | null
  fee: number
  bill: { period: string; amount: number } | null      // postpaid: fixed amount due
  products: { id: string; label: string; price: number }[] // prepaid: pick one
  minAmount: number | null                                 // e-wallet: free amount
}

/** Who and what is behind a customer number, before paying (S1.18 AC1, S1.19 AC1, S1.20 AC1, S1.21 AC2). */
export async function inquire(sql: Sql, billerCode: string, numberInput: string): Promise<Inquiry> {
  const [b] = await sql`select * from billers where code = ${billerCode}`
  if (!b) throw new TransferError('NOT_FOUND', 'Choose what you want to pay from the list.')
  const number = numberInput.replace(/\D/g, '').replace(/^62/, '0')
  const base = { biller: b.code, billerName: b.name, kind: b.kind, number, fee: Number(b.fee), bill: null, products: [], minAmount: null, details: null }
  const [override] = await sql`select * from biller_customers where biller = ${b.code} and number = ${number}`
  const name = override?.name ?? generatedName(b.code, number)

  switch (b.kind) {
    case 'pln_token':
      if (!/^\d{11,12}$/.test(number)) throw new TransferError('BAD_NUMBER', 'Meter numbers have 11 or 12 digits.')
      return { ...base, name, details: `Prepaid · ${POWER[hash(number) % POWER.length]}`, products: b.products }
    case 'pln_postpaid':
    case 'bpjs': {
      const valid = b.kind === 'bpjs' ? /^\d{13}$/.test(number) : /^\d{12}$/.test(number)
      if (!valid) throw new TransferError('BAD_NUMBER', b.kind === 'bpjs' ? 'BPJS numbers have 13 digits.' : 'PLN customer IDs have 12 digits.')
      const p = period()
      const [paid] = await sql`select 1 from bill_payments where biller = ${b.code} and number = ${number} and period = ${p}`
      if (paid) throw new TransferError('ALREADY_PAID', "This month's bill is already paid.")
      const amount = override?.monthly_bill != null ? Number(override.monthly_bill)
        : b.kind === 'bpjs' ? [42_000, 100_000, 150_000][hash(number) % 3] * (1 + hash(number + 'm') % 4)
        : Math.round((250_000 + hash(number) % 1_250_000) / 100) * 100
      return { ...base, name, details: b.kind === 'bpjs' ? `${1 + hash(number + 'm') % 4} family members` : `Postpaid · ${POWER[hash(number) % POWER.length]}`, bill: { period: p, amount } }
    }
    case 'mobile': {
      const op = operatorFor(number)
      if (!/^08\d{8,11}$/.test(number) || !op) throw new TransferError('BAD_NUMBER', "We don't recognise that mobile number. Check the first four digits.")
      return { ...base, name: null, details: op, products: b.products }
    }
    case 'ewallet':
      if (!/^08\d{8,11}$/.test(number)) throw new TransferError('BAD_NUMBER', `${b.name} accounts use a mobile number, like 0812 3456 7890.`)
      return { ...base, name, details: `${b.name} account`, minAmount: 10_000 }
  }
  throw new TransferError('NOT_FOUND', 'That payment type is not available.')
}

/** A 20-digit PLN token, shown as 4-4-4-4-4. */
const plnToken = (seed: string) => [...createHash('sha256').update(seed).digest()].slice(0, 20).map(b => b % 10).join('')

export async function payBill(sql: Sql, customer: { id: string; tier: string },
  input: { biller: string; number: string; productId?: string; amount?: number; pin: string; idempotencyKey: string }) {
  const q = await inquire(sql, input.biller, input.number)
  let amount: number, label: string
  if (q.bill) { amount = q.bill.amount; label = `${q.billerName} · ${q.bill.period}` }
  else if (q.products.length) {
    const p = q.products.find(x => x.id === input.productId)
    if (!p) throw new TransferError('BAD_AMOUNT', 'Pick one of the options.')
    amount = p.price; label = q.kind === 'mobile' ? `${q.details} · ${p.label}` : `${q.billerName} · ${p.label}`
  } else {
    amount = Math.floor(Number(input.amount))
    if (!(amount >= (q.minAmount ?? 1))) throw new TransferError('BAD_AMOUNT', `The smallest top-up is Rp${(q.minAmount ?? 1).toLocaleString('en-US')}.`)
    label = `${q.billerName} top-up`
  }
  await authorizePayout(sql, customer, amount, input.pin)

  const token = q.kind === 'pln_token' ? plnToken(`${input.idempotencyKey}${q.number}`) : null
  const [from] = await sql`select id from accounts where customer_id = ${customer.id} and kind = 'savings'`
  const postings = [{ accountId: from.id, amount: -(amount + q.fee) }, { accountId: await houseAccount(sql, 'BILLER_SETTLEMENT'), amount }]
  if (q.fee) postings.push({ accountId: await houseAccount(sql, 'FEE_INCOME'), amount: q.fee })
  const category = q.kind === 'ewallet' ? 'transport' : q.kind === 'bpjs' ? 'health' : 'bills'
  let j
  try {
    j = await postJournal(sql, {
      kind: 'bill_payment', idempotencyKey: `${customer.id}:${input.idempotencyKey}`, postings,
      memo: { to: { name: q.billerName }, channel: label, category, fee: q.fee, customerNumber: q.number, customerName: q.name, token },
    })
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS') throw new TransferError('INSUFFICIENT_FUNDS', "You don't have enough money for this payment and its fee.")
    throw e
  }
  if (q.bill) await sql`insert into bill_payments (biller, number, period, journal_id) values (${q.biller}, ${q.number}, ${q.bill.period}, ${j.id}) on conflict do nothing`
  await sql`
    insert into saved_bill_numbers (customer_id, biller, number, name) values (${customer.id}, ${q.biller}, ${q.number}, ${q.name ?? q.details ?? q.number})
    on conflict (customer_id, biller, number) do update set last_used = now()`
  return { journal: j, amount, fee: q.fee, token: (j.memo as any)?.token ?? token, label }
}
