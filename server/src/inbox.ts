import type { Sql } from './db.ts'

// Inbox Messages (CONTEXT.md). Push notifications, when Firebase is set up, send the same title and body.

const rp = (n: number) => 'Rp' + Math.abs(n).toLocaleString('en-US')

export async function notify(sql: Sql, customerId: string, title: string, body: string, link?: string) {
  await sql`insert into inbox_messages (customer_id, title, body, link) values (${customerId}, ${title}, ${body}, ${link ?? null})`
}

/** One message per customer touched by a journal entry: money in, money out, or a card purchase. */
export async function notifyJournal(sql: Sql, journalId: string) {
  const rows = await sql`
    select a.customer_id, p.amount, j.kind, j.memo from postings p
    join journal_entries j on j.id = p.journal_id join accounts a on a.id = p.account_id
    where j.id = ${journalId} and a.customer_id is not null`
  for (const r of rows) {
    const amount = Number(r.amount) + (Number(r.amount) < 0 ? Number(r.memo?.fee ?? 0) : 0)
    const party = amount < 0 ? r.memo?.to?.name : r.memo?.from?.name
    const [title, body]: string[] =
      r.kind === 'card_spend' ? ['Card payment', `${rp(amount)} at ${party} with your ${r.memo?.channel ?? 'card'}.`]
      : r.kind === 'card_payment' ? (amount < 0 ? ['Card bill paid', `${rp(amount)} paid toward your credit card.`] : [])
      : amount > 0 ? ['Money in', `${rp(amount)} from ${party ?? 'ORVYN Bank'}.`]
      : ['Money out', `${rp(amount)} to ${party ?? r.memo?.channel}.`]
    if (title) await notify(sql, r.customer_id, title, body, `tx:${journalId}`)
  }
}
