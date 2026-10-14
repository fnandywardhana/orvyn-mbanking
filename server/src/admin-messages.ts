import { Hono } from 'hono'
import { html } from 'hono/html'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'
import { notify } from './inbox.ts'
import { normalizePhone } from './customers.ts'

type Env = { Variables: { officer: Officer } }

// Story S1.28: messages to customers' inboxes, and one-time announcement pop-ups.
export function messageRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'content'))

  const page = async (o: Officer, v: Record<string, string> = {}, note?: string, preview?: { count: number }) => {
    const active = await sql`select id, title, ends_at from announcements where ends_at > now() order by id desc`
    return shell(o, '/admin/messages', 'Messages', html`<div class="formgrid" style="max-width:720px">
${note ? html`<div class="ok-box"><pre>${note}</pre></div>` : ''}
<form method="post" action="/admin/messages/${preview ? 'send' : 'preview'}" class="card" style="gap:12px">
<span class="eyebrow">Send to inboxes</span>
<label>Who<select name="target" style="height:44px;border-radius:10px;padding:0 10px;font:inherit">
${[['all', 'All customers'], ['everyday', 'Everyday customers'], ['prestige', 'Prestige customers'], ['private', 'Private customers'], ['one', 'One customer (phone number below)']]
  .map(([k, l]) => html`<option value="${k}" ${v.target === k ? 'selected' : ''}>${l}</option>`)}</select></label>
<label>Phone number (for one customer)<input name="phone" value="${v.phone ?? ''}" placeholder="0812 3456 7890"></label>
<label>Title<input name="title" maxlength="60" required value="${v.title ?? ''}" placeholder="New: pay bills in ORVYN"></label>
<label>Message<textarea name="body" maxlength="240" required rows="3" style="font:inherit;font-size:16px;padding:12px;border:1px solid var(--outline-strong);border-radius:10px;background:var(--surface);color:var(--text)">${v.body ?? ''}</textarea></label>
${preview ? html`<div style="border:1px solid var(--outline);border-radius:14px;padding:14px;background:var(--surface-variant)">
<span class="eyebrow">Preview · goes to ${preview.count} ${preview.count === 1 ? 'customer' : 'customers'}</span>
<b style="display:block;margin-top:6px">${v.title}</b><span>${v.body}</span></div>
<div class="actions"><button class="btn">Send now</button><a class="btn secondary" href="/admin/messages">Start over</a></div>`
  : html`<button class="btn" style="width:max-content">Preview</button>`}
</form>
<form method="post" action="/admin/messages/announcement" class="card" style="gap:12px">
<span class="eyebrow">Announcement pop-up</span>
<p class="hint">Shows once to each customer when they open ORVYN, until the end date.</p>
<label>Title<input name="title" maxlength="60" required></label>
<label>Message<input name="body" maxlength="240" required></label>
<label>Show until<input name="ends" type="date" required></label>
<button class="btn" style="width:max-content">Publish announcement</button>
${active.length ? html`<span class="eyebrow" style="margin-top:8px">Live now</span>${active.map(a => html`<div class="actions" style="align-items:center">
<span style="flex:1">${a.title} · until ${a.ends_at.toISOString().slice(0, 10)}</span>
<button class="btn secondary" formaction="/admin/messages/announcement/${a.id}/end" formnovalidate>End now</button></div>`)}` : ''}
</form></div>`)
  }

  const targets = async (v: Record<string, string>) => {
    if (v.target === 'one') {
      try { return await sql`select id from customers where phone = ${normalizePhone(v.phone ?? '')}` } catch { return [] }
    }
    return v.target === 'all' ? await sql`select id from customers` : await sql`select id from customers where tier = ${v.target}`
  }
  const form = async (c: any) => Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, x]) => [k, String(x)])) as Record<string, string>

  app.get('/', async c => c.html(await page(c.get('officer'))))
  app.post('/preview', async c => {
    const v = await form(c)
    const count = (await targets(v)).length
    if (!count) return c.html(await page(c.get('officer'), v, 'Nobody matches. Check the phone number or pick another group.'))
    return c.html(await page(c.get('officer'), v, undefined, { count }))
  })
  app.post('/send', async c => {
    const v = await form(c)
    const ids = await targets(v)
    for (const t of ids) await notify(sql, t.id, v.title.slice(0, 60), v.body.slice(0, 240))
    await audit(sql, c.get('officer').id, 'message.send', v.target, null, { title: v.title, count: ids.length })
    return c.html(await page(c.get('officer'), {}, `Sent to ${ids.length} ${ids.length === 1 ? 'inbox' : 'inboxes'}.`))
  })
  app.post('/announcement', async c => {
    const v = await form(c)
    const ends = new Date(`${v.ends}T23:59:59+07:00`)
    const [a] = await sql`insert into announcements (title, body, ends_at) values (${v.title.slice(0, 60)}, ${v.body.slice(0, 240)}, ${ends}) returning id`
    await audit(sql, c.get('officer').id, 'announcement.publish', String(a.id), null, { title: v.title, ends: v.ends })
    return c.html(await page(c.get('officer'), {}, 'Announcement published. Customers see it the next time they open ORVYN.'))
  })
  app.post('/announcement/:id/end', async c => {
    await sql`update announcements set ends_at = now() where id = ${c.req.param('id')}`
    await audit(sql, c.get('officer').id, 'announcement.end', c.req.param('id'), null, null)
    return c.redirect('/admin/messages')
  })
  return app
}
