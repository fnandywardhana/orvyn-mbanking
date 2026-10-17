import { Hono } from 'hono'
import { html } from 'hono/html'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { approveRegistration, rejectRegistration, RegistrationError } from './registrations.ts'

type Env = { Variables: { officer: Officer } }

// Story S1.4: officers approve or reject applications from the app. There are no photos to review (ADR 0002).
export function registrationRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'operator'))

  /** [rejectError]: the application whose Reject the server just refused for having no reason (Q-C7). */
  const list = async (c: any, rejectError?: string) => {
    const pending = await sql`select * from registrations where status = 'pending' order by submitted_at`
    const recent = await sql`select full_name, phone, status, reason, submitted_at from registrations where status in ('approved', 'rejected') order by submitted_at desc limit 10`
    const done = c.req.query('done')
    return c.html(shell(c.get('officer'), '/admin/applications', 'Applications', html`<div class="formgrid" style="max-width:820px">
${done ? html`<div class="ok-box"><pre>${done}</pre></div>` : ''}
${pending.length === 0 ? html`<div class="card"><b>No applications waiting.</b><p class="hint">When someone taps "Open an account" in the app and finishes, they show up here.</p></div>` : ''}
${pending.map(r => html`<div class="card" style="gap:10px">
<b>${r.full_name}</b><span class="hint">Sent ${r.submitted_at.toLocaleString('en-GB', { timeZone: 'Asia/Jakarta' })} WIB</span>
<div class="tablewrap"><table style="min-width:0"><tbody>
${[['Phone', r.phone], ['NIK', r.nik], ['Date of birth', r.birth_date?.toISOString().slice(0, 10)], ['Address', r.address], ['Work', r.occupation], ['Income', r.income_range], ['Why an account', r.purpose]]
  .map(([k, v]) => html`<tr><td class="hint">${k}</td><td style="white-space:normal">${v}</td></tr>`)}</tbody></table></div>
<p class="hint">Photos of the KTP and selfie stay on the applicant's phone and are never sent, so approve from the details above.</p>
<form method="post" action="/admin/applications/${r.id}/approve" class="actions" style="align-items:end">
<label>Tier<select name="tier" style="height:40px;border-radius:10px;padding:0 10px;font:inherit"><option value="everyday">Everyday</option><option value="prestige">Prestige</option><option value="private">Private</option></select></label>
<button class="btn">Approve</button></form>
<form method="post" action="/admin/applications/${r.id}/reject" class="actions" style="align-items:end">
${rejectError === r.id ? html`<div class="err-box" role="alert" style="flex-basis:100%">Write a reason first. The applicant sees it in the app.</div>` : ''}
<label style="flex:1">Reason (the applicant sees it)<input name="reason" required maxlength="120" placeholder="The name doesn't match the NIK."
${rejectError === r.id ? html`aria-invalid="true" autofocus` : ''}></label>
<button class="btn danger">Reject</button></form></div>`)}
${recent.length ? html`<div class="tablewrap"><table><thead><tr><th>Recent</th><th>Phone</th><th>Result</th></tr></thead><tbody>
${recent.map(r => html`<tr><td>${r.full_name}</td><td class="mono">${r.phone}</td><td>${r.status === 'approved' ? html`<span class="chip ok">Approved</span>` : html`<span class="chip bad">Rejected</span> <span class="hint">${r.reason}</span>`}</td></tr>`)}
</tbody></table></div>` : ''}</div>`), rejectError ? 400 : 200)
  }
  app.get('/', c => list(c))

  app.post('/:id/approve', async c => {
    const tier = String((await c.req.parseBody()).tier) as any
    try {
      const cu = await approveRegistration(sql, c.req.param('id'), ['everyday', 'prestige', 'private'].includes(tier) ? tier : 'everyday')
      await audit(sql, c.get('officer').id, 'application.approve', c.req.param('id'), null, { customer: cu.id, tier })
      return c.redirect(`/admin/applications?done=${encodeURIComponent(`Approved. ${cu.full_name} can sign in now.`)}`)
    } catch (e) {
      if (e instanceof RegistrationError) return c.redirect(`/admin/applications?done=${encodeURIComponent(`Not done: ${e.message}`)}`)
      throw e
    }
  })

  app.post('/:id/reject', async c => {
    const reason = String((await c.req.parseBody()).reason ?? '').trim().slice(0, 120)
    try { await rejectRegistration(sql, c.req.param('id'), reason) }
    catch (e) { if (e instanceof RegistrationError && e.code === 'NO_REASON') return list(c, c.req.param('id')); throw e }
    await audit(sql, c.get('officer').id, 'application.reject', c.req.param('id'), null, { reason })
    return c.redirect(`/admin/applications?done=${encodeURIComponent('Rejected. The applicant sees your reason in the app.')}`)
  })
  return app
}
