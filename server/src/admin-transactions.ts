import { Hono } from 'hono'
import { html } from 'hono/html'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { reverse, LedgerError } from './ledger.ts'
import { notifyJournal } from './inbox.ts'

type Env = { Variables: { officer: Officer } }
const rp = (n: number) => (n < 0 ? '−' : '') + 'Rp' + Math.abs(n).toLocaleString('en-US')
const KINDS = ['internal_transfer', 'external_transfer', 'qris_payment', 'bill_payment', 'card_spend', 'card_payment', 'inflow', 'adjustment', 'reversal', 'opening']

// Story S1.35: every journal entry, searchable, with Reversals.
export function transactionRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'operator'))

  app.get('/', async c => {
    const q = c.req.query()
    const like = q.q ? `%${q.q.trim()}%` : null
    const rows = await sql`
      select j.id, j.kind, j.created_at, j.memo, j.reverses_id,
        exists (select 1 from journal_entries r where r.reverses_id = j.id) as reversed,
        json_agg(json_build_object('name', cu.full_name, 'amount', p.amount, 'customer', cu.id) order by p.amount) filter (where cu.id is not null) as parties
      from journal_entries j
      join postings p on p.journal_id = j.id join accounts a on a.id = p.account_id left join customers cu on cu.id = a.customer_id
      where true
        ${q.kind ? sql`and j.kind = ${q.kind}` : sql``}
        ${q.from ? sql`and j.created_at >= ${new Date(`${q.from}T00:00:00+07:00`)}` : sql``}
        ${q.to ? sql`and j.created_at < ${new Date(`${q.to}T00:00:00+07:00`)} + interval '1 day'` : sql``}
        ${like ? sql`and exists (select 1 from postings p2 join accounts a2 on a2.id = p2.account_id join customers c2 on c2.id = a2.customer_id
            where p2.journal_id = j.id and (c2.full_name ilike ${like} or c2.phone ilike ${like} or a2.number ilike ${like}))` : sql``}
        ${q.min ? sql`and exists (select 1 from postings p3 where p3.journal_id = j.id and abs(p3.amount) >= ${Number(q.min)})` : sql``}
      group by j.id order by j.created_at desc limit 150`
    const sel = (name: string, v: string) => html`value="${q[name] ?? ''}" ${v}`
    return c.html(shell(c.get('officer'), '/admin/transactions', 'Transactions', html`
<form method="get" class="toolbar" style="justify-content:flex-start">
<input name="q" placeholder="Customer, phone or account" ${sel('q', '')} style="min-width:220px">
<select name="kind" style="height:40px;border-radius:10px;padding:0 10px;font:inherit"><option value="">All kinds</option>
${KINDS.map(k => html`<option value="${k}" ${q.kind === k ? 'selected' : ''}>${k.replace('_', ' ')}</option>`)}</select>
<input type="date" name="from" ${sel('from', '')} aria-label="From"><input type="date" name="to" ${sel('to', '')} aria-label="To">
<input name="min" type="number" placeholder="Min amount" ${sel('min', '')} style="min-width:120px">
<button class="btn secondary">Filter</button></form>
${c.req.query('done') ? html`<div class="ok-box" style="margin-bottom:16px"><pre>${c.req.query('done')}</pre></div>` : ''}
<div class="tablewrap"><table><thead><tr><th>When (WIB)</th><th>Kind</th><th>What</th><th>Customers</th><th></th></tr></thead><tbody>
${rows.map(r => html`<tr>
<td class="mono">${r.created_at.toLocaleString('en-GB', { timeZone: 'Asia/Jakarta', dateStyle: 'short', timeStyle: 'short' })}</td>
<td>${r.kind.replace('_', ' ')}</td>
<td>${[r.memo?.to?.name ?? r.memo?.from?.name, r.memo?.channel].filter(Boolean).join(' · ')}${r.memo?.note ? html` <span class="hint">“${r.memo.note}”</span>` : ''}</td>
<td>${(r.parties ?? []).map((p: any) => html`<a href="/admin/customers/${p.customer}">${p.name}</a> <span class="mono">${rp(Number(p.amount))}</span><br>`)}</td>
<td>${r.reversed ? html`<span class="chip bad">Reversed</span>` : r.kind === 'reversal' || r.kind === 'opening' ? '' : html`<a class="btn secondary" href="/admin/transactions/${r.id}/reverse">Reverse</a>`}</td></tr>`)}
</tbody></table></div>`))
  })

  app.get('/:id/reverse', async c => {
    const [j] = await sql`select id, kind, memo, created_at from journal_entries where id = ${c.req.param('id')}`
    if (!j) return c.notFound()
    return c.html(shell(c.get('officer'), '/admin/transactions', 'Reverse transaction', html`<form method="post" class="formgrid">
<div class="card"><b>${j.memo?.channel ?? j.kind}</b><span class="hint">${j.created_at.toLocaleString('en-GB', { timeZone: 'Asia/Jakarta' })} WIB</span>
<p class="hint">A new entry puts every rupiah back where it came from. The original stays in the history, marked Reversed. Customers are told in their inbox.</p></div>
<label>Reason<input name="reason" required maxlength="80" placeholder="Sent to the wrong person"></label>
<label>Type REVERSE to confirm<input name="confirm" required pattern="REVERSE" autocomplete="off"></label>
<div class="actions"><button class="btn danger">Reverse transaction</button><a class="btn secondary" href="/admin/transactions">Cancel</a></div></form>`))
  })

  app.post('/:id/reverse', async c => {
    const f = await c.req.parseBody()
    if (f.confirm !== 'REVERSE') return c.redirect(`/admin/transactions/${c.req.param('id')}/reverse`)
    let msg
    try {
      const r = await reverse(sql, c.req.param('id'), { reason: String(f.reason).slice(0, 80), channel: 'Reversal', category: 'reversal', note: String(f.reason).slice(0, 80) })
      await notifyJournal(sql, r.id)
      await audit(sql, c.get('officer').id, 'transaction.reverse', c.req.param('id'), null, { reason: f.reason, reversal: r.id })
      msg = 'Reversed. Both sides have their money back and a message in their inbox.'
    } catch (e) {
      if (!(e instanceof LedgerError)) throw e
      // S1.35 AC3: say which account would go below zero
      msg = e.code === 'ALREADY_REVERSED' ? 'Not done: this transaction was already reversed.'
        : e.code === 'INSUFFICIENT_FUNDS' ? `Not done: ${await whoIsShort(sql, c.req.param('id'))} Add money to their account first, then try again.`
        : `Not done: ${e.message}`
    }
    return c.redirect(`/admin/transactions?done=${encodeURIComponent(msg)}`)
  })
  return app
}

async function whoIsShort(sql: Sql, journalId: string) {
  const [r] = await sql`
    select cu.full_name, a.balance, p.amount from postings p join accounts a on a.id = p.account_id join customers cu on cu.id = a.customer_id
    where p.journal_id = ${journalId} and p.amount > 0 and a.floor is not null and a.balance - p.amount < a.floor limit 1`
  return r ? `${r.full_name} has only Rp${Number(r.balance).toLocaleString('en-US')} and would need Rp${(Number(r.amount) - Number(r.balance)).toLocaleString('en-US')} more.` : 'an account would go below zero.'
}
