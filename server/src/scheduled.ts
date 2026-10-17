import type { Sql } from './db.ts'
import { lookupRecipient, transferOptions, sendTransfer, checkPin, TransferError, MINIMUM, SMALLEST_TRANSFER, type Method } from './transfers.ts'
import { notify } from './inbox.ts'

// Stories S2.4–S2.5: Scheduled Transfers. The PIN is checked once, when the schedule is made.

export type Repeat = 'once' | 'weekly' | 'monthly'
const SEND_HOUR_WIB = 7
const rp = (n: number) => 'Rp' + n.toLocaleString('en-US')

/** Today's date in Jakarta as YYYY-MM-DD, and the hour there. */
function jakarta(now: Date) {
  const t = new Date(now.getTime() + 7 * 3600_000)
  return { date: t.toISOString().slice(0, 10), hour: t.getUTCHours() }
}

function addDays(date: string, n: number) {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

/** Monthly on day 31 lands on the 30th, 28th or 29th in shorter months (S2.4 AC3). */
function onDay(year: number, month: number, day: number) {
  const last = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  return new Date(Date.UTC(year, month, Math.min(day, last))).toISOString().slice(0, 10)
}

/** The first send date strictly after `after`. */
export function nextAfter(repeat: Repeat, day: number, after: string): string | null {
  if (repeat === 'once') return null
  if (repeat === 'weekly') {
    let d = addDays(after, 1)
    while ((new Date(`${d}T00:00:00Z`).getUTCDay() || 7) !== day) d = addDays(d, 1)
    return d
  }
  const a = new Date(`${after}T00:00:00Z`)
  const thisMonth = onDay(a.getUTCFullYear(), a.getUTCMonth(), day)
  return thisMonth > after ? thisMonth : onDay(a.getUTCFullYear(), a.getUTCMonth() + 1, day)
}

export async function createSchedule(sql: Sql, customer: { id: string, tier: string }, input: {
  bank: string, accountNumber: string, amount: number, method: Method, note?: string, repeat: Repeat, firstDate: string, pin: string,
}) {
  const amount = Math.floor(Number(input.amount))
  if (!(amount >= MINIMUM.transfer)) throw new TransferError('BAD_AMOUNT', SMALLEST_TRANSFER())
  if (!['once', 'weekly', 'monthly'].includes(input.repeat)) throw new TransferError('BAD_AMOUNT', 'Pick once, every week or every month.')
  const today = jakarta(new Date()).date
  const first = String(input.firstDate ?? '').slice(0, 10)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(first) || first <= today) throw new TransferError('BAD_AMOUNT', 'Pick a date from tomorrow on.')
  const to = await lookupRecipient(sql, customer.id, input.bank, input.accountNumber)
  const { methods } = await transferOptions(sql, customer, to.internal, amount)
  if (!methods.some(m => m.method === input.method)) throw new TransferError('BAD_METHOD', 'That transfer method is not available for this amount.')
  await checkPin(sql, customer.id, input.pin)
  const firstDay = new Date(`${first}T00:00:00Z`)
  const day = input.repeat === 'weekly' ? (firstDay.getUTCDay() || 7) : firstDay.getUTCDate()
  const [s] = await sql`
    insert into scheduled_transfers (customer_id, bank, account_number, name, amount, method, note, repeat, day, next_run)
    values (${customer.id}, ${to.bank}, ${to.accountNumber}, ${to.name}, ${amount}, ${input.method}, ${input.note?.trim().slice(0, 50) || null}, ${input.repeat}, ${day}, ${first})
    returning id`
  return s.id as string
}

export async function listSchedules(sql: Sql, customerId: string) {
  const rows = await sql`select * from scheduled_transfers where customer_id = ${customerId} and deleted_at is null order by next_run nulls last, created_at`
  return rows.map(r => ({
    id: r.id, bank: r.bank, accountNumber: r.account_number, name: r.name, amount: Number(r.amount), method: r.method, note: r.note,
    repeat: r.repeat, day: r.day, nextRun: r.next_run?.toISOString().slice(0, 10) ?? null, paused: r.paused, lastResult: r.last_result,
  }))
}

export async function updateSchedule(sql: Sql, customerId: string, id: string, change: { paused?: boolean, delete?: boolean }) {
  const [s] = await sql`
    update scheduled_transfers set
      paused = coalesce(${change.paused ?? null}, paused),
      deleted_at = case when ${change.delete === true} then now() else deleted_at end
    where id = ${id} and customer_id = ${customerId} and deleted_at is null returning id`
  if (!s) throw new TransferError('NOT_FOUND', "We couldn't find that scheduled transfer.")
}

/**
 * Runs every minute. From 07:00 WIB, sends each schedule due today or earlier exactly once (the idempotency key is the
 * schedule and its date), then moves it to its next date after today. A failure is not retried that day (S2.4 AC4).
 */
export async function runScheduledTransfers(sql: Sql, now = new Date()) {
  const { date: today, hour } = jakarta(now)
  if (hour < SEND_HOUR_WIB) return
  const due = await sql`
    select s.*, c.tier, c.full_name from scheduled_transfers s join customers c on c.id = s.customer_id
    where s.next_run <= ${today} and not s.paused and s.deleted_at is null and not c.locked`
  for (const s of due) {
    let result: { at: string, ok: boolean, message: string, transactionId?: string }
    try {
      const j = await sendTransfer(sql, { id: s.customer_id, tier: s.tier, full_name: s.full_name }, {
        bank: s.bank, accountNumber: s.account_number, amount: Number(s.amount), method: s.method, note: s.note ?? undefined,
        pin: null, idempotencyKey: `scheduled:${s.id}:${s.next_run.toISOString().slice(0, 10)}`,
      })
      result = { at: now.toISOString(), ok: true, message: 'Sent', transactionId: j.id }
    } catch (e) {
      if (!(e instanceof TransferError)) throw e
      result = { at: now.toISOString(), ok: false, message: e.message }
      await notify(sql, s.customer_id, "Scheduled transfer didn't go through", `${rp(Number(s.amount))} to ${s.name}: ${e.message}`)
    }
    await sql`update scheduled_transfers set last_result = ${sql.json(result)}, next_run = ${nextAfter(s.repeat, s.day, today)} where id = ${s.id}`
  }
}
