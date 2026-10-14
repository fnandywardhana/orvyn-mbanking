import { Hono } from 'hono'
import { html } from 'hono/html'
import type { HtmlEscapedString } from 'hono/utils/html'
import { getCookie, setCookie, deleteCookie } from 'hono/cookie'
import type { Sql } from './db.ts'
import { loginOfficer, officerForSession, logoutOfficer, changeOfficerPassword, isStrongPassword, IDLE_MINUTES } from './auth.ts'
import { checkLedger } from './ledger.ts'
import { customerRoutes } from './admin-customers.ts'
import { merchantRoutes } from './admin-merchants.ts'
import { messageRoutes } from './admin-messages.ts'
import { contentRoutes } from './admin-content.ts'
import { transactionRoutes } from './admin-transactions.ts'
import { registrationRoutes } from './admin-registrations.ts'

export type Officer = { id: string; email: string; name: string; role: 'super_admin' | 'operator' | 'content'; must_change_password: boolean }
type Env = { Variables: { officer: Officer } }
export type Html = HtmlEscapedString | Promise<HtmlEscapedString>

const COOKIE = 'orvyn_admin'
const ROLE_LABEL = { super_admin: 'Super Admin', operator: 'Operator', content: 'Content' }
// Menu entries per role; later phases add Customers, Transactions, Content, Settings here.
const MENU: { href: string; label: string; roles: Officer['role'][] }[] = [
  { href: '/admin', label: 'Dashboard', roles: ['super_admin', 'operator', 'content'] },
  { href: '/admin/customers', label: 'Customers', roles: ['super_admin', 'operator'] },
  { href: '/admin/applications', label: 'Applications', roles: ['super_admin', 'operator'] },
  { href: '/admin/transactions', label: 'Transactions', roles: ['super_admin', 'operator'] },
  { href: '/admin/merchants', label: 'Merchants', roles: ['super_admin', 'operator'] },
  { href: '/admin/messages', label: 'Messages', roles: ['super_admin', 'content'] },
  { href: '/admin/content', label: 'Content', roles: ['super_admin', 'content'] },
]

/** Records one officer change (story S1.38). */
export async function audit(sql: Sql, officerId: string, action: string, target: string | null, before: object | null, after: object | null) {
  await sql`insert into audit_entries (officer_id, action, target, before, after)
    values (${officerId}, ${action}, ${target}, ${before ? sql.json(before) : null}, ${after ? sql.json(after) : null})`
}

/** Blocks officers whose role isn't allowed here (menus hide it too, story S0.2). */
export const requireRole = (...roles: Officer['role'][]) => async (c: any, next: () => Promise<void>) => {
  if (!roles.includes(c.get('officer').role)) return c.text('Your role does not have access to this page.', 403)
  await next()
}

// Tokens mirror design-system/MASTER.md.
const css = html`<style>
@font-face{font-family:"Plus Jakarta Sans";src:url(/fonts/PlusJakartaSans.ttf) format("truetype");font-weight:200 800;font-display:swap}
:root{--primary:#0032A0;--on-primary:#fff;--primary-container:#E5ECFA;--nero:#0A0B10;--giallo:#F5C400;
--bg:#F4F6FB;--surface:#fff;--surface-variant:#EDF1F8;--text:#0B1020;--text2:#4D5872;--text3:#6B7690;
--outline:#DCE2EE;--outline-strong:#858FA3;--success:#0E7A47;--success-c:#E3F4EA;--error:#C62828;--error-c:#FCE8E8;color-scheme:light}
@media (prefers-color-scheme:dark){:root{--primary:#7FA2FF;--on-primary:#001A5C;--primary-container:#0032A0;
--bg:#070A12;--surface:#0F1422;--surface-variant:#161D2E;--text:#E8ECF6;--text2:#A3ADC2;--text3:#7C879E;
--outline:#212A3E;--outline-strong:#5D6884;--success:#4CC78A;--success-c:#0F2A1E;--error:#FF8A80;--error-c:#3A1416;color-scheme:dark}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:14px/1.5 "Plus Jakarta Sans",system-ui,sans-serif;font-variant-numeric:tabular-nums}
a{color:var(--primary)}
.shell{display:grid;grid-template-columns:240px 1fr;min-height:100vh}
.side{background:var(--nero);color:#fff;padding:24px 16px;display:flex;flex-direction:column;gap:24px}
.brand{display:flex;align-items:center;gap:10px;font-weight:800;letter-spacing:.14em;font-size:18px}
.brand img{width:28px;height:28px;display:block}
.side nav{display:grid;gap:4px}
.side nav a{color:#C9D4F5;text-decoration:none;padding:10px 12px;border-radius:10px;font-weight:600}
.side nav a[aria-current]{background:#0032A0;color:#fff}
.side .me{margin-top:auto;font-size:13px;color:#C9D4F5;display:grid;gap:8px}
main{padding:32px 24px;max-width:1280px;width:100%}
h1{font-size:24px;line-height:30px;margin:0 0 24px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px}
.card{background:var(--surface);border:1px solid var(--outline);border-radius:14px;padding:16px;display:grid;gap:6px}
.eyebrow{font-size:11px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--text2)}
.big{font-size:28px;font-weight:800}
.chip{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:2px 10px;font-size:12px;font-weight:600;width:max-content}
.chip.ok{background:var(--success-c);color:var(--success)}.chip.bad{background:var(--error-c);color:var(--error)}
.center{min-height:100vh;display:grid;place-items:center;padding:24px}
.panel{background:var(--surface);border:1px solid var(--outline);border-radius:20px;padding:32px;width:100%;max-width:400px;display:grid;gap:20px}
.panel .brand{color:var(--text)}
label{display:grid;gap:6px;font-weight:600}
input{font:inherit;font-size:16px;height:48px;padding:0 14px;border:1px solid var(--outline-strong);border-radius:10px;background:var(--surface);color:var(--text)}
input:focus{outline:none;border:2px solid var(--primary);padding:0 13px}
button{font:inherit;font-weight:600;font-size:15px;height:48px;border:0;border-radius:14px;cursor:pointer;background:var(--primary);color:var(--on-primary)}
button.link{background:none;color:#C9D4F5;height:auto;padding:0;text-align:left;text-decoration:underline}
.err{background:var(--error-c);color:var(--error);border-radius:10px;padding:10px 12px}
.hint{color:var(--text2);font-size:13px;margin:0}
.toolbar{display:flex;gap:12px;align-items:center;justify-content:space-between;margin-bottom:16px;flex-wrap:wrap}
.toolbar form{display:flex;gap:8px}
.toolbar input{height:40px;min-width:260px}
.btn{display:inline-flex;align-items:center;justify-content:center;height:40px;padding:0 16px;border-radius:12px;font-weight:600;text-decoration:none;background:var(--primary);color:var(--on-primary);border:0;font-size:14px;cursor:pointer}
.btn.secondary{background:var(--primary-container);color:var(--text)}
.btn.danger{background:var(--error);color:#fff}
.tablewrap{background:var(--surface);border:1px solid var(--outline);border-radius:14px;overflow-x:auto}
table{border-collapse:collapse;width:100%;min-width:720px}
th,td{text-align:left;padding:0 16px;height:44px;border-bottom:1px solid var(--outline);white-space:nowrap}
th{font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--text2);position:sticky;top:0;background:var(--surface)}
tr:last-child td{border-bottom:0}
td.num{text-align:right}
.mono{font-family:ui-monospace,Menlo,monospace}
.formgrid{display:grid;gap:16px;max-width:560px}
.personas{display:grid;gap:10px}
.persona{display:flex;gap:12px;align-items:flex-start;border:1px solid var(--outline-strong);border-radius:14px;padding:14px;cursor:pointer;font-weight:400}
.persona:has(input:checked){border:2px solid var(--primary);padding:13px}
.persona input{height:auto;margin-top:3px}
.persona b{display:block}
.ok-box{background:var(--success-c);border-radius:14px;padding:16px 20px;display:grid;gap:6px;max-width:560px}
.ok-box pre{margin:0;font:13px/1.6 ui-monospace,Menlo,monospace;white-space:pre-wrap;color:var(--text)}
.actions{display:flex;gap:8px;flex-wrap:wrap}
.chip.tier{background:var(--primary-container);color:var(--text)}
:focus-visible{outline:2px solid var(--primary);outline-offset:2px}
@media (max-width:720px){.shell{grid-template-columns:1fr}.side{flex-direction:row;align-items:center;flex-wrap:wrap}.side .me{margin:0 0 0 auto}}
</style>`

const page = (title: string, body: Html) => html`<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${title} · ORVYN Admin</title>
<link rel="icon" href="/mark-light-bg.svg">${css}</head><body>${body}</body></html>`

// The mark's black stripes vanish on dark surfaces, so dark surfaces get the white-striped variant.
const brand = (onDark: boolean) => html`<div class="brand"><picture>
${onDark ? '' : html`<source srcset="/mark-dark-bg.svg" media="(prefers-color-scheme: dark)">`}
<img src="/${onDark ? 'mark-dark-bg' : 'mark-light-bg'}.svg" alt=""></picture>ORVYN</div>`

export function shell(o: Officer, current: string, title: string, body: Html) {
  return page(title, html`<div class="shell">
<aside class="side">${brand(true)}
<nav aria-label="Main menu">${MENU.filter(m => m.roles.includes(o.role)).map(m =>
    html`<a href="${m.href}" ${m.href === current ? html`aria-current="page"` : ''}>${m.label}</a>`)}</nav>
<div class="me"><div>${o.email}<br>${ROLE_LABEL[o.role]}</div>
<form method="post" action="/admin/logout"><button class="link">Sign out</button></form></div></aside>
<main><h1>${title}</h1>${body}</main></div>`)
}

export function adminRoutes(sql: Sql) {
  const app = new Hono<Env>()
  const cookieOpts = { httpOnly: true, secure: true, sameSite: 'Lax', path: '/admin', maxAge: IDLE_MINUTES * 60 } as const

  const loginForm = (error?: string) => page('Sign in', html`<div class="center"><form class="panel" method="post" action="/admin/login">
${brand(false)}<div><h1 style="margin:0">Sign in to Admin</h1><p class="hint">For ORVYN Bank officers only.</p></div>
${error ? html`<div class="err" role="alert">${error}</div>` : ''}
<label>Email<input type="email" name="email" autocomplete="username" required autofocus></label>
<label>Password<input type="password" name="password" autocomplete="current-password" required></label>
<button>Sign in</button></form></div>`)

  app.get('/login', c => c.html(loginForm()))
  app.post('/login', async c => {
    const f = await c.req.parseBody()
    const r = await loginOfficer(sql, String(f.email ?? ''), String(f.password ?? ''))
    if (!r.ok) return c.html(loginForm(r.reason === 'locked'
      ? 'Too many wrong passwords. Your account is locked for 15 minutes.'
      : 'That email and password don\'t match.'), 401)
    setCookie(c, COOKIE, r.token, cookieOpts)
    return c.redirect(r.mustChangePassword ? '/admin/password' : '/admin')
  })

  // Everything below needs a signed-in officer.
  app.use('*', async (c, next) => {
    const o = await officerForSession(sql, getCookie(c, COOKIE)) as Officer | null
    if (!o) return c.redirect('/admin/login')
    setCookie(c, COOKIE, getCookie(c, COOKIE)!, cookieOpts) // slide the idle window
    if (o.must_change_password && !c.req.path.endsWith('/password') && !c.req.path.endsWith('/logout')) return c.redirect('/admin/password')
    c.set('officer', o)
    await next()
  })

  const passwordForm = (o: Officer, error?: string) => shell(o, '', 'Change password', html`<form class="panel" method="post" style="max-width:440px">
${o.must_change_password ? html`<p class="hint">This is your first sign-in. Set your own password to continue.</p>` : ''}
${error ? html`<div class="err" role="alert">${error}</div>` : ''}
<label>New password<input type="password" name="password" autocomplete="new-password" required minlength="8"></label>
<p class="hint">At least 8 characters, with letters and numbers.</p>
<label>Repeat new password<input type="password" name="confirm" autocomplete="new-password" required></label>
<button>Save password</button></form>`)

  app.get('/password', c => c.html(passwordForm(c.get('officer'))))
  app.post('/password', async c => {
    const o = c.get('officer')
    const f = await c.req.parseBody()
    const pw = String(f.password ?? '')
    if (!isStrongPassword(pw)) return c.html(passwordForm(o, 'Use at least 8 characters, with both letters and numbers.'), 400)
    if (pw !== f.confirm) return c.html(passwordForm(o, 'The two passwords don\'t match.'), 400)
    await changeOfficerPassword(sql, o.id, pw)
    return c.redirect('/admin')
  })

  app.post('/logout', async c => {
    await logoutOfficer(sql, getCookie(c, COOKIE)!)
    deleteCookie(c, COOKIE, { path: '/admin' })
    return c.redirect('/admin/login')
  })

  app.get('/', async c => {
    const ledger = await checkLedger(sql)
    const [{ customers }] = await sql`select count(*)::int as customers from customers`
    const [{ today, moved }] = await sql`
      select count(distinct j.id)::int as today, coalesce(sum(p.amount) filter (where p.amount > 0), 0)::bigint as moved
      from journal_entries j join postings p on p.journal_id = j.id
      where j.created_at >= date_trunc('day', now() at time zone 'Asia/Jakarta') at time zone 'Asia/Jakarta'`
    const [{ pending }] = await sql`select count(*)::int as pending from registrations where status = 'pending'`
    return c.html(shell(c.get('officer'), '/admin', 'Dashboard', html`<div class="cards">
<div class="card"><span class="eyebrow">Customers</span><span class="big">${customers}</span></div>
<div class="card"><span class="eyebrow">Transactions today</span><span class="big">${today}</span><span class="hint">Rp${Number(moved).toLocaleString('en-US')} moved</span></div>
<div class="card"><span class="eyebrow">Applications waiting</span><span class="big">${pending}</span>${pending ? html`<a href="/admin/applications">Review them</a>` : ''}</div>
<div class="card"><span class="eyebrow">Ledger</span>
${ledger.ok ? html`<span class="chip ok">✓ Balanced</span><p class="hint">Every balance matches its transaction history.</p>`
  : html`<span class="chip bad">✕ Out of balance</span><p class="hint">${ledger.problems.join(' · ')}</p>`}</div></div>`))
  })

  app.route('/customers', customerRoutes(sql))
  app.route('/merchants', merchantRoutes(sql))
  app.route('/messages', messageRoutes(sql))
  app.route('/content', contentRoutes(sql))
  app.route('/transactions', transactionRoutes(sql))
  app.route('/applications', registrationRoutes(sql))

  return app
}
