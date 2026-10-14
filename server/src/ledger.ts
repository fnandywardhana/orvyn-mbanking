import type { Sql } from './db.ts'
import type { TransactionSql } from 'postgres'

export type Posting = { accountId: string; amount: number }
export type JournalInput = {
  kind: string
  postings: Posting[]
  memo?: object
  idempotencyKey?: string
  reversesId?: string
  at?: Date // backdating, for generated persona history only
}

export class LedgerError extends Error {
  code: 'UNBALANCED' | 'INSUFFICIENT_FUNDS' | 'ALREADY_REVERSED' | 'DRIFT'
  constructor(code: LedgerError['code'], message: string) {
    super(message)
    this.code = code
  }
}

// The only way money moves (docs/adr/0001-double-entry-ledger.md).
// Pass a transaction to make the entry part of a larger unit of work (it then runs in a savepoint).
export async function postJournal(sql: Sql | TransactionSql, input: JournalInput) {
  const run = 'savepoint' in sql ? sql.savepoint.bind(sql) : (sql as Sql).begin.bind(sql)
  return run(async (tx: TransactionSql) => {
    if (input.postings.length < 2) throw new LedgerError('UNBALANCED', 'a journal entry needs at least two postings')
    const ids = [...new Set(input.postings.map(p => p.accountId))]
    // Lock in a fixed order so concurrent entries touching the same accounts can't deadlock.
    const accounts = await tx`select id, currency from accounts where id in ${tx(ids)} order by id for update`
    const currency = new Map(accounts.map(a => [a.id, a.currency as string]))
    const sums = new Map<string, number>()
    for (const p of input.postings) {
      const c = currency.get(p.accountId)!
      sums.set(c, (sums.get(c) ?? 0) + p.amount)
    }
    for (const [c, s] of sums) if (s !== 0) throw new LedgerError('UNBALANCED', `${c} postings sum to ${s}`)

    const [j] = await tx`
      insert into journal_entries (kind, memo, idempotency_key, reverses_id, created_at)
      values (${input.kind}, ${tx.json(input.memo ?? {})}, ${input.idempotencyKey ?? null}, ${input.reversesId ?? null}, ${input.at ?? new Date()})
      on conflict (idempotency_key) do nothing returning *`.catch(e => {
        throw e.code === '23505' && e.constraint_name === 'journal_entries_reverses_id_key'
          ? new LedgerError('ALREADY_REVERSED', `journal ${input.reversesId} was already reversed`) : e
      })
    // Same key seen before (e.g. a double tap): hand back the original instead of moving money again.
    if (!j) return (await tx`select * from journal_entries where idempotency_key = ${input.idempotencyKey!}`)[0]
    for (const p of input.postings) {
      await tx`insert into postings (journal_id, account_id, amount) values (${j.id}, ${p.accountId}, ${p.amount})`
      // The accounts.floor check constraint is what actually guards balances.
      await tx`update accounts set balance = balance + ${p.amount} where id = ${p.accountId}`.catch(e => {
        throw e.code === '23514' ? new LedgerError('INSUFFICIENT_FUNDS', `account ${p.accountId} would go below its floor`) : e
      })
    }
    const drifted = await tx`
      select a.id from accounts a where a.id in ${tx(ids)}
        and a.balance <> (select coalesce(sum(amount), 0) from postings where account_id = a.id)`
    if (drifted.length) throw new LedgerError('DRIFT', `balance drifted on ${drifted.map(d => d.id).join(', ')}`)
    return j
  })
}

// Mirrors an earlier journal entry; the original is never edited.
export async function reverse(sql: Sql, journalId: string, memo: object) {
  const postings = await sql`select account_id, amount from postings where journal_id = ${journalId}`
  return postJournal(sql, {
    kind: 'reversal',
    reversesId: journalId,
    memo,
    postings: postings.map(p => ({ accountId: p.account_id, amount: -Number(p.amount) })),
  })
}

export async function balanceOf(sql: Sql, accountId: string) {
  const [a] = await sql`select balance from accounts where id = ${accountId}`
  return Number(a.balance)
}

export async function houseAccount(sql: Sql, code: string) {
  const [a] = await sql`select id from accounts where code = ${code}`
  return a.id as string
}

// Invariants from ADR 0001. Run nightly and from the admin dashboard.
export async function checkLedger(sql: Sql) {
  const problems: string[] = []
  for (const r of await sql`
    select a.currency, sum(p.amount) as total from postings p join accounts a on a.id = p.account_id
    group by a.currency having sum(p.amount) <> 0`)
    problems.push(`${r.currency} postings sum to ${r.total}, expected 0`)
  for (const r of await sql`
    select a.id, a.balance, coalesce(sum(p.amount), 0) as total from accounts a left join postings p on p.account_id = a.id
    group by a.id having a.balance <> coalesce(sum(p.amount), 0)`)
    problems.push(`account ${r.id} balance ${r.balance} but postings sum to ${r.total}`)
  return { ok: problems.length === 0, problems }
}
