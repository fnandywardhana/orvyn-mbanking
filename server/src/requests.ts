import type { Sql } from './db.ts'
import { lookupRecipient, sendTransfer, TransferError } from './transfers.ts'
import { notify } from './inbox.ts'

// Stories S2.6–S2.7: Money Requests between ORVYN customers, and Split Bills built from them.

const MAX_PER_DAY = 20
const rp = (n: number) => 'Rp' + n.toLocaleString('en-US')
type Customer = { id: string, tier: string, full_name: string }

/** Open requests past their date read as expired; nothing needs to sweep them. */
const STATUS = (sql: Sql) => sql`case when r.status = 'open' and r.expires_at < now() then 'expired' else r.status end`

async function payerFor(sql: Sql, requesterId: string, accountNumber: string) {
  const to = await lookupRecipient(sql, requesterId, 'ORVYN', String(accountNumber ?? ''))
  const [a] = await sql`select customer_id from accounts where number = ${to.accountNumber}`
  return { id: a.customer_id as string, name: to.name }
}

async function checkDailyCount(sql: Sql, requesterId: string, adding: number) {
  const [{ n }] = await sql`
    select count(*)::int as n from money_requests where requester_id = ${requesterId}
      and created_at >= (date_trunc('day', now() at time zone 'Asia/Jakarta') at time zone 'Asia/Jakarta')`
  if (n + adding > MAX_PER_DAY) throw new TransferError('OVER_LIMIT', `You can send up to ${MAX_PER_DAY} requests a day. Try again tomorrow.`)
}

function cleanAmount(v: any) {
  const amount = Math.floor(Number(v))
  if (!(amount >= 1_000)) throw new TransferError('BAD_AMOUNT', 'Ask for at least Rp1,000.')
  return amount
}

async function insertRequest(sql: Sql, requester: Customer, payer: { id: string }, amount: number, note: string | null, splitId: string | null) {
  const [r] = await sql`
    insert into money_requests (requester_id, payer_id, amount, note, split_id)
    values (${requester.id}, ${payer.id}, ${amount}, ${note}, ${splitId}) returning id`
  await notify(sql, payer.id, 'Money request', `${requester.full_name} asks for ${rp(amount)}${note ? `: ${note}` : '.'}`, `request:${r.id}`)
  return r.id as string
}

export async function createRequest(sql: Sql, requester: Customer, input: { accountNumber: string, amount: any, note?: string }) {
  const amount = cleanAmount(input.amount)
  const payer = await payerFor(sql, requester.id, input.accountNumber)
  await checkDailyCount(sql, requester.id, 1)
  return insertRequest(sql, requester, payer, amount, input.note?.trim().slice(0, 50) || null, null)
}

/** S2.7: one request per friend. The creator's own share is whatever the friends don't cover. */
export async function createSplit(sql: Sql, creator: Customer, input: { total: any, note?: string, shares: { accountNumber: string, amount: any }[] }) {
  const total = cleanAmount(input.total)
  const shares = (input.shares ?? []).slice(0, 10)
  if (shares.length < 1 || shares.length > 9) throw new TransferError('BAD_AMOUNT', 'Split with 1 to 9 friends.')
  const payers = []
  for (const s of shares) payers.push({ ...(await payerFor(sql, creator.id, s.accountNumber)), amount: cleanAmount(s.amount) })
  if (new Set(payers.map(p => p.id)).size !== payers.length) throw new TransferError('BAD_AMOUNT', 'Each friend can only be in the split once.')
  if (payers.reduce((sum, p) => sum + p.amount, 0) > total) throw new TransferError('BAD_AMOUNT', "Your friends' shares add up to more than the total.")
  await checkDailyCount(sql, creator.id, payers.length)
  const note = input.note?.trim().slice(0, 50) || null
  const [split] = await sql`insert into splits (customer_id, total, note) values (${creator.id}, ${total}, ${note}) returning id`
  for (const p of payers) await insertRequest(sql, creator, p, p.amount, note, split.id)
  return split.id as string
}

/** Equal shares rounded down to the rupiah; the creator covers the leftover (S2.7 AC2). */
export function equalShares(total: number, friends: number) {
  return Math.floor(total / (friends + 1))
}

export async function listRequests(sql: Sql, customerId: string) {
  const rows = await sql`
    select r.id, r.amount, r.note, r.split_id, r.created_at, r.expires_at, ${STATUS(sql)} as status,
      r.requester_id = ${customerId} as mine, q.full_name as requester, p.full_name as payer
    from money_requests r join customers q on q.id = r.requester_id join customers p on p.id = r.payer_id
    where r.requester_id = ${customerId} or r.payer_id = ${customerId}
    order by r.created_at desc limit 100`
  return rows.map(r => ({
    id: r.id, amount: Number(r.amount), note: r.note, splitId: r.split_id, status: r.status, mine: r.mine,
    from: r.requester, to: r.payer, createdAt: r.created_at, expiresAt: r.expires_at,
  }))
}

export async function getRequest(sql: Sql, customerId: string, id: string) {
  const r = (await listRequests(sql, customerId)).find(x => x.id === id)
  if (!r) throw new TransferError('NOT_FOUND', "We couldn't find that request.")
  return r
}

export async function listSplits(sql: Sql, customerId: string) {
  const splits = await sql`select id, total, note, created_at from splits where customer_id = ${customerId} order by created_at desc limit 20`
  const people = splits.length ? await sql`
    select r.split_id, r.id, r.amount, ${STATUS(sql)} as status, p.full_name as name
    from money_requests r join customers p on p.id = r.payer_id where r.split_id in ${sql(splits.map(s => s.id))} order by r.created_at` : []
  return splits.map(s => ({
    id: s.id, total: Number(s.total), note: s.note, createdAt: s.created_at,
    people: people.filter(p => p.split_id === s.id).map(p => ({ requestId: p.id, name: p.name, amount: Number(p.amount), status: p.status })),
  }))
}

/** Pays with the PIN like any ORVYN transfer. The key ties the payment to the request, so a second tap can't pay twice. */
export async function payRequest(sql: Sql, payer: Customer, id: string, pin: string) {
  const [r] = await sql`
    select r.*, ${STATUS(sql)} as state, a.number from money_requests r join accounts a on a.customer_id = r.requester_id and a.kind = 'savings'
    where r.id = ${id} and r.payer_id = ${payer.id}`
  if (!r) throw new TransferError('NOT_FOUND', "We couldn't find that request.")
  if (r.state !== 'open') throw new TransferError('ALREADY_PAID', r.state === 'paid' ? 'This request is already paid.' : `This request was ${r.state}.`)
  const j = await sendTransfer(sql, payer, {
    bank: 'ORVYN', accountNumber: r.number, amount: Number(r.amount), method: 'internal', note: r.note ?? 'Money request', pin, idempotencyKey: `request:${id}`,
  })
  const [done] = await sql`update money_requests set status = 'paid', journal_id = ${j.id} where id = ${id} and status = 'open' returning requester_id`
  if (done) await notify(sql, r.requester_id, 'Request paid', `${payer.full_name} paid your request for ${rp(Number(r.amount))}.`, `tx:${j.id}`)
  return j
}

export async function declineRequest(sql: Sql, payer: Customer, id: string) {
  const [r] = await sql`update money_requests set status = 'declined' where id = ${id} and payer_id = ${payer.id} and status = 'open' and expires_at > now() returning requester_id, amount`
  if (!r) throw new TransferError('NOT_FOUND', 'This request is no longer open.')
  await notify(sql, r.requester_id, 'Request declined', `${payer.full_name} declined your request for ${rp(Number(r.amount))}.`)
}

export async function cancelRequest(sql: Sql, requesterId: string, id: string) {
  const [r] = await sql`update money_requests set status = 'cancelled' where id = ${id} and requester_id = ${requesterId} and status = 'open' returning payer_id`
  if (!r) throw new TransferError('NOT_FOUND', 'This request is no longer open.')
}
