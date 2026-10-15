import type { Sql } from './db.ts'

// Story S2.17: a month of the savings account, straight from the ledger. The app turns it into a PDF on the phone.

const monthStart = (key: string) => new Date(`${key}-01T00:00:00+07:00`)
function shift(key: string, delta: number) {
  const [y, m] = key.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1 + delta, 1)).toISOString().slice(0, 7)
}

/** Full months the account was open, newest first, up to 12, not counting the month in progress. */
export async function statementMonths(sql: Sql, customerId: string, now = new Date()) {
  const [a] = await sql`select a.id, min(j.created_at) as first from accounts a left join postings p on p.account_id = a.id left join journal_entries j on j.id = p.journal_id
    where a.customer_id = ${customerId} and a.kind = 'savings' group by a.id`
  if (!a?.first) return []
  const current = new Date(now.getTime() + 7 * 3600_000).toISOString().slice(0, 7)
  const out: string[] = []
  for (let k = shift(current, -1); out.length < 12 && monthStart(shift(k, 1)) > a.first; k = shift(k, -1)) out.push(k)
  return out
}

export async function statement(sql: Sql, customerId: string, month: string) {
  if (!/^\d{4}-\d{2}$/.test(month) || !(await statementMonths(sql, customerId)).includes(month)) return null
  const [acc] = await sql`select a.id, a.name, a.number, c.full_name from accounts a join customers c on c.id = a.customer_id where a.customer_id = ${customerId} and a.kind = 'savings'`
  const from = monthStart(month), to = monthStart(shift(month, 1))
  const [{ opening }] = await sql`select coalesce(sum(p.amount), 0)::bigint as opening from postings p join journal_entries j on j.id = p.journal_id
    where p.account_id = ${acc.id} and j.created_at < ${from}`
  const rows = await sql`select j.created_at, j.kind, j.memo, p.amount from postings p join journal_entries j on j.id = p.journal_id
    where p.account_id = ${acc.id} and j.created_at >= ${from} and j.created_at < ${to} order by j.created_at, p.id`
  let balance = Number(opening)
  const items = rows.map(r => {
    const amount = Number(r.amount), party = amount < 0 ? r.memo?.to : r.memo?.from
    balance += amount
    return { at: r.created_at, description: r.kind === 'reversal' ? 'Reversal by ORVYN Bank' : party?.name ?? r.memo?.channel ?? 'Transaction', channel: r.memo?.channel ?? null, amount, balance }
  })
  return {
    name: acc.full_name, account: { name: acc.name, number: acc.number }, month, opening: Number(opening), closing: balance,
    moneyIn: items.filter(i => i.amount > 0).reduce((s, i) => s + i.amount, 0), moneyOut: -items.filter(i => i.amount < 0).reduce((s, i) => s + i.amount, 0), items,
  }
}
