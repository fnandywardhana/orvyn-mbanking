import { Hono } from 'hono'
import { html } from 'hono/html'
import type { Sql } from './db.ts'
import { shell, audit, requireRole, type Officer } from './admin.ts'

type Env = { Variables: { officer: Officer } }
const input = 'style="font:inherit;font-size:15px;padding:10px 12px;border:1px solid var(--outline-strong);border-radius:10px;background:var(--surface);color:var(--text);width:100%"'

// Story S1.36: banners on the home screen and help articles, changed without an app update.
export function contentRoutes(sql: Sql) {
  const app = new Hono<Env>()
  app.use('*', requireRole('super_admin', 'content'))
  const form = async (c: any) => Object.fromEntries(Object.entries(await c.req.parseBody()).map(([k, x]) => [k, String(x).trim()])) as Record<string, string>

  app.get('/', async c => {
    const banners = await sql`select * from banners order by ends_at > now() desc, sort, id`
    const faqs = await sql`select * from faqs order by category, sort, id`
    return c.html(shell(c.get('officer'), '/admin/content', 'Content', html`<div class="formgrid" style="max-width:820px">
<div class="card" style="gap:12px"><span class="eyebrow">Home banners</span>
${banners.map(b => html`<div class="actions" style="align-items:center;border-bottom:1px solid var(--outline);padding-bottom:10px">
<div style="flex:1"><b>${b.title}</b><br><span class="hint">${b.body} · ${b.style} · until ${b.ends_at.toISOString().slice(0, 10)}${b.ends_at < new Date() ? ' (ended)' : ''}</span></div>
<form method="post" action="/admin/content/banners/${b.id}/delete"><button class="btn secondary">Remove</button></form></div>`)}
<form method="post" action="/admin/content/banners" style="display:grid;gap:10px;margin-top:6px">
<b>Add a banner</b>
<label>Title<input name="title" maxlength="40" required></label>
<label>One line under the title<input name="body" maxlength="90" required></label>
<label>Shown when tapped<textarea name="detail" rows="3" maxlength="400" required ${input}></textarea></label>
<label>Look<select name="style" style="height:44px;border-radius:10px;padding:0 10px;font:inherit"><option value="blue">Blue</option><option value="black">Black</option><option value="gold">Gold (Prestige feel)</option></select></label>
<label>Show until<input type="date" name="ends" required></label>
<button class="btn" style="width:max-content">Add banner</button></form></div>

<div class="card" style="gap:12px"><span class="eyebrow">Help articles (FAQ)</span>
${faqs.map(f => html`<div class="actions" style="align-items:flex-start;border-bottom:1px solid var(--outline);padding-bottom:10px">
<div style="flex:1"><span class="hint">${f.category}</span><br><b>${f.question}</b><br><span class="hint">${f.answer}</span></div>
<form method="post" action="/admin/content/faqs/${f.id}/delete"><button class="btn secondary">Remove</button></form></div>`)}
<form method="post" action="/admin/content/faqs" style="display:grid;gap:10px;margin-top:6px">
<b>Add an article</b>
<label>Category<input name="category" required list="cats"><datalist id="cats">${[...new Set(faqs.map(f => f.category))].map(k => html`<option value="${k}">`)}</datalist></label>
<label>Question<input name="question" maxlength="120" required></label>
<label>Answer<textarea name="answer" rows="3" maxlength="800" required ${input}></textarea></label>
<button class="btn" style="width:max-content">Add article</button></form></div></div>`))
  })

  app.post('/banners', async c => {
    const v = await form(c)
    const [b] = await sql`insert into banners (title, body, detail, style, ends_at, sort)
      values (${v.title}, ${v.body}, ${v.detail}, ${['blue', 'black', 'gold'].includes(v.style) ? v.style : 'blue'}, ${new Date(`${v.ends}T23:59:59+07:00`)}, 99) returning id`
    await audit(sql, c.get('officer').id, 'banner.add', String(b.id), null, { title: v.title })
    return c.redirect('/admin/content')
  })
  app.post('/banners/:id/delete', async c => {
    const [b] = await sql`delete from banners where id = ${c.req.param('id')} returning title`
    await audit(sql, c.get('officer').id, 'banner.remove', c.req.param('id'), b ? { title: b.title } : null, null)
    return c.redirect('/admin/content')
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
