import type { Sql } from './db.ts'
import { notify } from './inbox.ts'

// Stories S2.8–S2.11: where the money went, budgets, and one sentence for the home screen.

export const CATEGORIES = ['food', 'transport', 'shopping', 'bills', 'entertainment', 'transfer', 'other'] as const
export type Category = typeof CATEGORIES[number]
export const LABELS: Record<Category, string> = {
  food: 'Food and drinks', transport: 'Transport', shopping: 'Shopping', bills: 'Bills',
  entertainment: 'Fun', transfer: 'Transfers', other: 'Other',
}
/** Money that leaves for good. Moves to pockets and paying one's own card bill are not spending. */
export const SPEND_KINDS = ['qris_payment', 'bill_payment', 'card_spend', 'internal_transfer', 'external_transfer']
const rp = (n: number) => 'Rp' + n.toLocaleString('en-US')

/** The category a customer sees for a journal: their own choice, else the mapped memo category. */
export const categoryOf = (sql: Sql) => sql`coalesce((select o.category from category_overrides o where o.journal_id = j.id), spend_category(j.memo->>'category'))`

/** The month in Jakarta as YYYY-MM, shifted by `delta` months. */
export function monthKey(now: Date, delta = 0) {
  const t = new Date(now.getTime() + 7 * 3600_000)
  const d = new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth() + delta, 1))
  return d.toISOString().slice(0, 7)
}
const monthStart = (key: string) => new Date(`${key}-01T00:00:00+07:00`)

/** Spending per month and category from `from` (inclusive) up to `to` (exclusive). */
async function spending(sql: Sql, customerId: string, from: Date, to: Date) {
  const rows = await sql`
    select to_char(j.created_at at time zone 'Asia/Jakarta', 'YYYY-MM') as month, ${categoryOf(sql)} as category, sum(-p.amount)::bigint as total
    from postings p join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
    where a.customer_id = ${customerId} and a.kind in ('savings', 'credit') and p.amount < 0
      and j.kind in ${sql(SPEND_KINDS)} and j.created_at >= ${from} and j.created_at < ${to}
      and not exists (select 1 from journal_entries r where r.reverses_id = j.id)
    group by 1, 2`
  return rows.map(r => ({ month: r.month as string, category: r.category as Category, total: Number(r.total) }))
}

export async function insights(sql: Sql, customerId: string, now = new Date()) {
  const thisMonth = monthKey(now), lastMonth = monthKey(now, -1)
  const rows = await spending(sql, customerId, monthStart(monthKey(now, -5)), monthStart(monthKey(now, 1)))
  const sum = (month: string, cat?: Category) => rows.filter(r => r.month === month && (!cat || r.category === cat)).reduce((s, r) => s + r.total, 0)
  const budgets = await sql`select category, monthly_limit from budgets where customer_id = ${customerId}`
  return {
    month: thisMonth,
    months: [-5, -4, -3, -2, -1, 0].map(d => ({ month: monthKey(now, d), total: sum(monthKey(now, d)) })),
    categories: CATEGORIES.map(c => {
      const total = sum(thisMonth, c), previous = sum(lastMonth, c)
      return { key: c, label: LABELS[c], total, previous, change: previous > 0 ? Math.round((total - previous) * 100 / previous) : null }
    }).filter(c => c.total > 0 || c.previous > 0).sort((a, b) => b.total - a.total),
    budgets: budgets.map(b => ({ category: b.category, label: LABELS[b.category as Category], limit: Number(b.monthly_limit), spent: sum(thisMonth, b.category) })),
  }
}

/**
 * S2.11: one sentence for the home screen. A budget at 80% or more wins; otherwise the category that changed most
 * against the same days last month. Nothing until there are two months of spending to compare.
 */
export async function insightCard(sql: Sql, customerId: string, now = new Date()) {
  const i = await insights(sql, customerId, now)
  const tight = i.budgets.map(b => ({ ...b, used: Math.round(b.spent * 100 / b.limit) })).filter(b => b.used >= 80).sort((a, b) => b.used - a.used)[0]
  if (tight) return { text: tight.used >= 100 ? `You've gone over your ${tight.label} budget this month.` : `You've used ${tight.used}% of your ${tight.label} budget this month.`, category: tight.category }

  const day = new Date(now.getTime() + 7 * 3600_000).getUTCDate()
  const lastFrom = monthStart(monthKey(now, -1))
  const lastSameDay = new Date(Math.min(lastFrom.getTime() + day * 86400_000, monthStart(monthKey(now)).getTime()))
  const before = await spending(sql, customerId, lastFrom, lastSameDay)
  const so = await spending(sql, customerId, monthStart(monthKey(now)), now)
  if (!before.length) return null
  const best = CATEGORIES.map(c => {
    const a = so.filter(r => r.category === c).reduce((s, r) => s + r.total, 0)
    const b = before.filter(r => r.category === c).reduce((s, r) => s + r.total, 0)
    return { c, a, b, change: b > 0 ? Math.round((a - b) * 100 / b) : 0 }
  }).filter(x => x.b >= 100_000 && Math.abs(x.change) >= 10).sort((x, y) => Math.abs(y.change) - Math.abs(x.change))[0]
  if (!best) return { text: `You've spent ${rp(so.reduce((s, r) => s + r.total, 0))} so far this month, about the same as last month.`, category: null }
  return { text: `${LABELS[best.c]} is ${best.change > 0 ? 'up' : 'down'} ${Math.abs(best.change)}% on the same days last month.`, category: best.c }
}

export async function setBudget(sql: Sql, customerId: string, category: string, limit: number | null) {
  if (!CATEGORIES.includes(category as Category)) throw new Error('bad category')
  if (limit === null || !(limit > 0)) await sql`delete from budgets where customer_id = ${customerId} and category = ${category}`
  else await sql`insert into budgets (customer_id, category, monthly_limit) values (${customerId}, ${category}, ${Math.floor(limit)})
    on conflict (customer_id, category) do update set monthly_limit = excluded.monthly_limit`
}

export async function setCategory(sql: Sql, customerId: string, journalId: string, category: string) {
  if (!CATEGORIES.includes(category as Category)) return false
  const [own] = await sql`select 1 from postings p join accounts a on a.id = p.account_id where p.journal_id = ${journalId} and a.customer_id = ${customerId} limit 1`
  if (!own) return false
  await sql`insert into category_overrides (customer_id, journal_id, category) values (${customerId}, ${journalId}, ${category})
    on conflict (customer_id, journal_id) do update set category = excluded.category`
  return true
}

/** S2.10 AC2: runs every minute; each budget warns once at 80% and once at 100% per month. */
export async function runBudgetAlerts(sql: Sql, now = new Date()) {
  const month = monthKey(now)
  const budgets = await sql`select customer_id, category, monthly_limit from budgets`
  for (const b of budgets) {
    const [{ spent }] = await sql`
      select coalesce(sum(-p.amount), 0)::bigint as spent
      from postings p join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
      where a.customer_id = ${b.customer_id} and a.kind in ('savings', 'credit') and p.amount < 0 and j.kind in ${sql(SPEND_KINDS)}
        and j.created_at >= ${monthStart(month)} and ${categoryOf(sql)} = ${b.category}
        and not exists (select 1 from journal_entries r where r.reverses_id = j.id)`
    const used = Number(spent) * 100 / Number(b.monthly_limit)
    for (const threshold of [100, 80]) {
      if (used < threshold) continue
      const [fresh] = await sql`insert into budget_alerts (customer_id, category, month, threshold) values (${b.customer_id}, ${b.category}, ${month}, ${threshold})
        on conflict do nothing returning threshold`
      if (fresh) await notify(sql, b.customer_id, threshold === 100 ? 'Budget used up' : 'Budget nearly used',
        `${LABELS[b.category as Category]}: ${rp(Number(spent))} of your ${rp(Number(b.monthly_limit))} budget this month.`)
      break // at 100% the 80% note is no longer news
    }
  }
}
