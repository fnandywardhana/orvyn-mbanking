import { Hono } from 'hono'
import { html } from 'hono/html'
import { readdir } from 'node:fs/promises'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'

type Env = { Variables: { officer: Officer } }
const ACTIONS: Record<string, string> = { '': 'No button', transfer: 'Transfer', qris: 'Scan QRIS', pay: 'Pay a bill', topup: 'Top up', cards: 'Cards' }
const input = 'style="font:inherit;font-size:15px;padding:10px 12px;border:1px solid var(--outline-strong);border-radius:10px;background:var(--surface);color:var(--text);width:100%"'

// Story S1.36: banners on the home screen and help articles, changed without an app update.
export function contentRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'content'))
  const form = async (c: any) => Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, x]) => [k, String(x).trim()])) as Record<string, string>

  const day = (d: Date) => new Date(d.getTime() + 7 * 3_600_000).toISOString().slice(0, 10) // WIB date for <input type=date>
  const nice = (d: Date) => d.toLocaleDateString('en-GB', { timeZone: 'Asia/Jakarta', day: 'numeric', month: 'short', year: 'numeric' })
  const statusOf = (b: any, now = new Date()) => b.starts_at > now ? 'Scheduled' : b.ends_at <= now ? 'Ended' : 'Live'

  /** Q-F8 (design.md 12.5): one form adds or edits; [v] keeps what was typed when the dates don't fit. */
  const page = async (c: any, opts: { editId?: string, error?: string, v?: Record<string, string> } = {}) => {
    const now = new Date()
    const all = await sql`select * from banners order by sort, id`
    const rank = { Live: 0, Scheduled: 1, Ended: 2 } as Record<string, number>
    const banners = [...all].sort((x, y) => rank[statusOf(x, now)] - rank[statusOf(y, now)] || x.sort - y.sort || Number(x.id) - Number(y.id))
    const faqs = await sql`select * from faqs order by category, sort, id`
    const photos = (await readdir('./public/banners')).filter(f => /^[a-z]+\.webp$/.test(f)).sort()
    const editing = opts.editId ? all.find(b => String(b.id) === opts.editId) : undefined
    const v = opts.v ?? (editing ? { title: editing.title, body: editing.body, detail: editing.detail, image: editing.image ?? '', action: editing.action ?? '',
      starts: day(editing.starts_at), ends: day(editing.ends_at), sort: String(editing.sort) } : { starts: day(now), sort: String(all.length + 1), image: photos[0] ?? '' })
    const done = c.req.query('done')
    return c.html(shell(c.get('officer'), '/admin/content', 'Content', html`<div class="formgrid" style="max-width:820px">
${done ? html`<div class="ok-box" role="status">${done}</div>` : ''}
<div class="card" style="gap:12px"><span class="eyebrow">Home banners</span>
${banners.map(b => { const st = statusOf(b, now); return html`<div class="actions" style="align-items:center;border-bottom:1px solid var(--outline);padding-bottom:10px">
${b.image ? html`<img src="/banners/${b.image}" alt="" width="112" height="49" style="border-radius:8px;object-fit:cover">` : ''}
<div style="flex:1"><b>${b.title}</b><br><span class="hint">${b.body}</span><br>
<span class="chip ${st === 'Live' ? 'ok' : ''}" style="${st === 'Live' ? '' : 'background:var(--surface-variant);color:var(--text2)'}">${st}</span>
<span class="hint" style="${st === 'Ended' ? 'opacity:.7' : ''}"> ${nice(b.starts_at)} to ${nice(b.ends_at)} · Position ${b.sort} · Button: ${ACTIONS[b.action ?? '']}</span></div>
<a class="btn secondary" href="/admin/content?edit=${b.id}#banner-form">Edit</a>
<form method="post" action="/admin/content/banners/${b.id}/delete"><button class="btn secondary">Remove</button></form></div>` })}
<form id="banner-form" method="post" action="${editing ? `/admin/content/banners/${editing.id}` : '/admin/content/banners'}" style="display:grid;gap:10px;margin-top:6px">
<b>${editing ? 'Edit banner' : 'Add a banner'}</b>
${opts.error ? html`<div class="err-box" role="alert">${opts.error}</div>` : ''}
<label>Title<input name="title" maxlength="40" required value="${v.title ?? ''}"></label>
<label>One line under the title<input name="body" maxlength="90" required value="${v.body ?? ''}"></label>
<label>Shown when tapped<textarea name="detail" rows="3" maxlength="400" required ${input}>${v.detail ?? ''}</textarea></label>
<fieldset style="border:0;padding:0;margin:0;display:grid;gap:8px"><legend style="margin-bottom:8px;font-weight:600">Photo</legend>
<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px">
${photos.map(f => html`<label style="display:grid;gap:4px;cursor:pointer"><img src="/banners/${f}" alt="" style="width:100%;aspect-ratio:16/7;object-fit:cover;border-radius:8px">
<span><input type="radio" name="image" value="${f}" ${v.image === f ? 'checked' : ''}> ${f.replace('.webp', '')}</span></label>`)}
<label style="display:grid;gap:4px;cursor:pointer"><span style="aspect-ratio:16/7;border-radius:8px;background:linear-gradient(135deg,var(--primary),var(--nero))"></span><span><input type="radio" name="image" value="" ${!v.image ? 'checked' : ''}> ORVYN blue, no photo</span></label>
</div></fieldset>
<label>Button<select name="action" style="height:44px;border-radius:10px;padding:0 10px;font:inherit">${Object.entries(ACTIONS).map(([k, l]) => html`<option value="${k}" ${(v.action ?? '') === k ? 'selected' : ''}>${l}</option>`)}</select></label>
<label>Show from<input type="date" name="starts" required value="${v.starts ?? ''}"></label>
<label>Show until<input type="date" name="ends" required value="${v.ends ?? ''}" ${opts.error ? html`aria-invalid="true"` : ''}></label>
<label>Position<input type="number" name="sort" min="1" required value="${v.sort ?? ''}"></label>
<p class="hint">1 shows first.</p>
<div class="actions">${editing ? html`<button class="btn" style="width:max-content">Save changes</button><a class="btn secondary" href="/admin/content">Cancel</a>`
  : html`<button class="btn" style="width:max-content">Add banner</button>`}</div></form></div>

<div class="card" style="gap:12px"><span class="eyebrow">Help articles (FAQ)</span>
${faqs.map(f => html`<div class="actions" style="align-items:flex-start;border-bottom:1px solid var(--outline);padding-bottom:10px">
<div style="flex:1"><span class="hint">${f.category}</span><br><b>${f.question}</b><br><span class="hint">${f.answer}</span></div>
<form method="post" action="/admin/content/faqs/${f.id}/delete"><button class="btn secondary">Remove</button></form></div>`)}
<form method="post" action="/admin/content/faqs" style="display:grid;gap:10px;margin-top:6px">
<b>Add an article</b>
<label>Category<input name="category" required list="cats"><datalist id="cats">${[...new Set(faqs.map(f => f.category))].map(k => html`<option value="${k}">`)}</datalist></label>
<label>Question<input name="question" maxlength="120" required></label>
<label>Answer<textarea name="answer" rows="3" maxlength="800" required ${input}></textarea></label>
<button class="btn" style="width:max-content">Add article</button></form></div></div>`), opts.error ? 400 : 200)
  }
  app.get('/', c => page(c, { editId: c.req.query('edit') }))

  /** Show from and Show until are whole WIB days; until runs to the end of its day. */
  const fields = (v: Record<string, string>) => ({
    title: v.title, body: v.body, detail: v.detail,
    image: /^[a-z]+\.webp$/.test(v.image ?? '') ? v.image : null,
    action: v.action in ACTIONS && v.action ? v.action : null,
    starts_at: new Date(`${v.starts}T00:00:00+07:00`), ends_at: new Date(`${v.ends}T23:59:59+07:00`),
    sort: Math.max(1, Math.floor(Number(v.sort) || 1)),
  })
  const badDates = (f: ReturnType<typeof fields>) => isNaN(f.starts_at.getTime()) || isNaN(f.ends_at.getTime()) || f.ends_at < f.starts_at
  const back = (c: any, msg: string) => c.redirect(`/admin/content?done=${encodeURIComponent(msg)}`)

  app.post('/banners', async c => {
    const v = await form(c)
    const f = fields(v)
    if (badDates(f)) return page(c, { error: "Show until can't be before Show from.", v })
    const [b] = await sql`insert into banners ${sql(f)} returning id`
    await audit(sql, c.get('officer').id, 'banner.add', String(b.id), null, { title: f.title, starts: v.starts, ends: v.ends, sort: f.sort })
    return back(c, 'Banner added.')
  })
  app.post('/banners/:id', async c => {
    const v = await form(c)
    const f = fields(v)
    if (badDates(f)) return page(c, { editId: c.req.param('id'), error: "Show until can't be before Show from.", v })
    const [before] = await sql`select title, body, detail, image, action, starts_at, ends_at, sort from banners where id = ${c.req.param('id')}`
    if (!before) return c.notFound()
    await sql`update banners set ${sql(f)} where id = ${c.req.param('id')}`
    await audit(sql, c.get('officer').id, 'banner.edit', c.req.param('id'), { ...before }, f)
    return back(c, 'Banner saved.')
  })
  app.post('/banners/:id/delete', async c => {
    const [b] = await sql`delete from banners where id = ${c.req.param('id')} returning title`
    await audit(sql, c.get('officer').id, 'banner.remove', c.req.param('id'), b ? { title: b.title } : null, null)
    return back(c, 'Banner removed.')
  })
  app.post('/faqs', async c => {
    const v = await form(c)
    const [f] = await sql`insert into faqs (category, question, answer, sort) values (${v.category}, ${v.question}, ${v.answer}, 99) returning id`
    await audit(sql, c.get('officer').id, 'faq.add', String(f.id), null, { question: v.question })
    return c.redirect('/admin/content')
  })
  app.post('/faqs/:id/delete', async c => {
    const [f] = await sql`delete from faqs where id = ${c.req.param('id')} returning question`
    await audit(sql, c.get('officer').id, 'faq.remove', c.req.param('id'), f ? { question: f.question } : null, null)
    return c.redirect('/admin/content')
  })
  return app
}
