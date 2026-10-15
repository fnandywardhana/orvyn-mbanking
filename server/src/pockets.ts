import type { Sql } from './db.ts'
import { postJournal, LedgerError } from './ledger.ts'
import { notify } from './inbox.ts'

// Stories S2.1–S2.3: Pockets (CONTEXT.md). Money only moves between a Pocket and its owner's Savings Account.

export const POCKET_IMAGES = ['travel', 'home', 'car', 'study', 'wedding', 'gadget', 'safety', 'gift']
const MAX_POCKETS = 10
const rp = (n: number) => 'Rp' + n.toLocaleString('en-US')

export class PocketError extends Error {
  code: 'NOT_FOUND' | 'TOO_MANY' | 'BAD_INPUT' | 'INSUFFICIENT_FUNDS' | 'NOT_EMPTY'
  constructor(code: PocketError['code'], message: string) {
    super(message)
    this.code = code
  }
}

export type Rule = { type: 'payday', percent?: number, amount?: number } | { type: 'weekly', day: number, amount: number } | { type: 'roundup' }

function cleanRule(r: any): Rule | null {
  if (!r) return null
  const whole = (n: any) => Math.floor(Number(n))
  if (r.type === 'roundup') return { type: 'roundup' }
  if (r.type === 'weekly' && whole(r.day) >= 1 && whole(r.day) <= 7 && whole(r.amount) >= 10_000) return { type: 'weekly', day: whole(r.day), amount: whole(r.amount) }
  if (r.type === 'payday' && r.percent != null && whole(r.percent) >= 1 && whole(r.percent) <= 50) return { type: 'payday', percent: whole(r.percent) }
  if (r.type === 'payday' && whole(r.amount) >= 10_000) return { type: 'payday', amount: whole(r.amount) }
  throw new PocketError('BAD_INPUT', 'Pick how much to save: between 1% and 50% of pay, or at least Rp10,000.')
}

function cleanDetails(input: { name?: any, image?: any, target?: any, targetDate?: any }) {
  const name = String(input.name ?? '').trim()
  if (!name || name.length > 24) throw new PocketError('BAD_INPUT', 'Give the pocket a name of up to 24 characters.')
  const image = POCKET_IMAGES.includes(String(input.image)) ? String(input.image) : POCKET_IMAGES[0]
  const target = input.target ? Math.floor(Number(input.target)) : null
  if (target !== null && !(target >= 10_000)) throw new PocketError('BAD_INPUT', 'A target needs to be at least Rp10,000.')
  const targetDate = input.targetDate ? String(input.targetDate).slice(0, 10) : null
  if (targetDate && !/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) throw new PocketError('BAD_INPUT', 'Pick a valid target date.')
  return { name, image, target, targetDate }
}

export async function listPockets(sql: Sql, customerId: string) {
  const rows = await sql`
    select a.id, a.name, a.balance, p.image, p.target, p.target_date, p.rule, p.reached_at
    from pockets p join accounts a on a.id = p.account_id
    where p.customer_id = ${customerId} and p.closed_at is null order by a.created_at`
  return rows.map(r => ({
    id: r.id, name: r.name, balance: Number(r.balance), image: r.image,
    target: r.target === null ? null : Number(r.target), targetDate: r.target_date?.toISOString().slice(0, 10) ?? null,
    rule: r.rule, reached: r.reached_at !== null,
  }))
}

async function ownPocket(sql: Sql, customerId: string, pocketId: string) {
  const [p] = await sql`
    select a.id, a.name, a.balance, p.target, p.reached_at from pockets p join accounts a on a.id = p.account_id
    where p.account_id = ${pocketId} and p.customer_id = ${customerId} and p.closed_at is null`
  if (!p) throw new PocketError('NOT_FOUND', "We couldn't find that pocket.")
  return p
}

const savingsOf = async (sql: Sql, customerId: string) =>
  (await sql`select id, name, balance from accounts where customer_id = ${customerId} and kind = 'savings'`)[0]

export async function createPocket(sql: Sql, customerId: string, input: { name?: any, image?: any, target?: any, targetDate?: any }) {
  const d = cleanDetails(input)
  return sql.begin(async tx => {
    await tx`select id from customers where id = ${customerId} for update` // two quick taps can't both slip under the limit
    const [{ n }] = await tx`select count(*)::int as n from pockets where customer_id = ${customerId} and closed_at is null`
    if (n >= MAX_POCKETS) throw new PocketError('TOO_MANY', `You can have up to ${MAX_POCKETS} pockets. Close one to make room.`)
    const [a] = await tx`insert into accounts (kind, customer_id, name, currency, floor) values ('pocket', ${customerId}, ${d.name}, 'IDR', 0) returning id`
    await tx`insert into pockets (account_id, customer_id, image, target, target_date) values (${a.id}, ${customerId}, ${d.image}, ${d.target}, ${d.targetDate})`
    return a.id as string
  })
}

/** Name, picture, target and the auto-save rule. A new rule only reacts to money that moves from now on. */
export async function updatePocket(sql: Sql, customerId: string, pocketId: string, input: { name?: any, image?: any, target?: any, targetDate?: any, rule?: any }) {
  const p = await ownPocket(sql, customerId, pocketId)
  const d = cleanDetails(input)
  const rule = cleanRule(input.rule)
  await sql`update accounts set name = ${d.name} where id = ${p.id}`
  await sql`update pockets set image = ${d.image}, target = ${d.target}, target_date = ${d.targetDate},
    rule = ${rule ? sql.json(rule) : null}, rule_since = case when ${rule ? sql.json(rule) : null}::jsonb is distinct from rule then now() else rule_since end
    where account_id = ${p.id}`
}

/** Moves money in ('in': savings → pocket) or out. No PIN and no fee: it stays the customer's own money. */
export async function movePocket(sql: Sql, customerId: string, pocketId: string, direction: 'in' | 'out', amountInput: any, idempotencyKey: string, channel = 'Pocket') {
  const amount = Math.floor(Number(amountInput))
  if (!(amount >= 1)) throw new PocketError('BAD_INPUT', 'Enter an amount.')
  const p = await ownPocket(sql, customerId, pocketId)
  const savings = await savingsOf(sql, customerId)
  const [from, to] = direction === 'in' ? [savings, p] : [p, savings]
  try {
    const j = await postJournal(sql, {
      kind: 'pocket_move', idempotencyKey: `${customerId}:${idempotencyKey}`,
      memo: { from: { name: from.name }, to: { name: to.name }, channel, category: 'pocket' },
      postings: [{ accountId: from.id, amount: -amount }, { accountId: to.id, amount }],
    })
    return { journalId: j.id as string, reached: await checkTarget(sql, customerId, p.id) }
  } catch (e) {
    if (e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS')
      throw new PocketError('INSUFFICIENT_FUNDS', `You have ${rp(Number(from.balance))} in ${from.name}.`)
    throw e
  }
}

/** S2.2 AC4: one congratulation per time the target is reached. Returns true when it was reached just now. */
async function checkTarget(sql: Sql, customerId: string, pocketId: string) {
  const [p] = await sql`select p.reached_at, p.target, a.balance, a.name from pockets p join accounts a on a.id = p.account_id where p.account_id = ${pocketId}`
  if (!p || p.target === null) return false
  const reached = Number(p.balance) >= Number(p.target)
  if (reached === (p.reached_at !== null)) return false
  await sql`update pockets set reached_at = ${reached ? new Date() : null} where account_id = ${pocketId}`
  if (reached) await notify(sql, customerId, 'Goal reached', `${p.name} has reached ${rp(Number(p.target))}. Nice work.`)
  return reached
}

/** S2.1 AC5: moves whatever is left back to savings, then closes the pocket. */
export async function closePocket(sql: Sql, customerId: string, pocketId: string, idempotencyKey: string) {
  const p = await ownPocket(sql, customerId, pocketId)
  if (Number(p.balance) > 0) await movePocket(sql, customerId, pocketId, 'out', Number(p.balance), idempotencyKey)
  await sql`update pockets set closed_at = now() where account_id = ${p.id}`
}

/** S2.3: runs every minute. Each source (a payment, a pay day, a week day) is handled once, saved or skipped. */
export async function runAutoSave(sql: Sql, now = new Date()) {
  const pockets = await sql`
    select p.account_id, p.customer_id, p.rule, p.rule_since, a.name from pockets p join accounts a on a.id = p.account_id
    where p.rule is not null and p.closed_at is null`
  for (const p of pockets) {
    const savings = await savingsOf(sql, p.customer_id)
    const jobs: { source: string, amount: number }[] = []
    if (p.rule.type === 'weekly') {
      const wib = new Date(now.getTime() + 7 * 3600_000)
      if ((wib.getUTCDay() || 7) === p.rule.day) jobs.push({ source: `day:${wib.toISOString().slice(0, 10)}`, amount: p.rule.amount })
    } else {
      const kinds = p.rule.type === 'roundup' ? ['qris_payment', 'bill_payment', 'card_spend'] : ['inflow']
      const rows = await sql`
        select j.id, p.amount from postings p join journal_entries j on j.id = p.journal_id
        where p.account_id = ${savings.id} and j.kind in ${sql(kinds)} and j.created_at > ${p.rule_since}
          and not exists (select 1 from journal_entries r where r.reverses_id = j.id)
          ${p.rule.type === 'roundup' ? sql`and p.amount < 0 and p.amount % 10000 <> 0` : sql`and p.amount > 0 and j.memo->>'category' = 'income'`}
          and not exists (select 1 from autosave_runs r where r.pocket_id = ${p.account_id} and r.source = j.id::text)
        order by j.created_at limit 50`
      for (const r of rows) {
        const x = Math.abs(Number(r.amount))
        const amount = p.rule.type === 'roundup' ? Math.ceil(x / 10_000) * 10_000 - x
          : p.rule.percent ? Math.floor(x * p.rule.percent / 100) : p.rule.amount
        jobs.push({ source: r.id, amount })
      }
    }
    for (const job of jobs) await autosaveOnce(sql, p, savings, job)
  }
}

async function autosaveOnce(sql: Sql, p: any, savings: any, job: { source: string, amount: number }) {
  const [claimed] = await sql`insert into autosave_runs (pocket_id, source) values (${p.account_id}, ${job.source}) on conflict do nothing returning source`
  if (!claimed) return
  try {
    const j = await postJournal(sql, {
      kind: 'pocket_move', idempotencyKey: `autosave:${p.account_id}:${job.source}`,
      memo: { from: { name: savings.name }, to: { name: p.name }, channel: 'Auto-save', category: 'pocket' },
      postings: [{ accountId: savings.id, amount: -job.amount }, { accountId: p.account_id, amount: job.amount }],
    })
    await sql`update autosave_runs set journal_id = ${j.id} where pocket_id = ${p.account_id} and source = ${job.source}`
    await checkTarget(sql, p.customer_id, p.account_id)
  } catch (e) {
    if (!(e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS')) throw e
    await sql`update autosave_runs set skipped = 'insufficient_funds' where pocket_id = ${p.account_id} and source = ${job.source}`
    await notify(sql, p.customer_id, 'Auto-save skipped', `${rp(job.amount)} didn't go into ${p.name} because your balance was too low. It'll try again next time.`)
  }
}
