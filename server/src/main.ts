import { Hono } from 'hono'
import { serve } from '@hono/node-server'
import { serveStatic } from '@hono/node-server/serve-static'
import { connect, migrate } from './db.ts'
import { seedSuperAdmin } from './auth.ts'
import { checkLedger } from './ledger.ts'
import { adminRoutes } from './admin.ts'
import { apiRoutes } from './api.ts'
import { backfillDebitCards } from './cards.ts'
import { runAutoSave } from './pockets.ts'
import { runScheduledTransfers } from './scheduled.ts'
import { runBudgetAlerts } from './insights.ts'
import { runMaturities } from './deposits.ts'
import { ensurePrices, runBonds } from './investments.ts'

const sql = connect()
await migrate(sql)
await seedSuperAdmin(sql, process.env.ADMIN_EMAIL, process.env.ADMIN_PASSWORD)
const issued = await backfillDebitCards(sql)
if (issued) console.log(`Issued debit cards to ${issued} existing customers`)

const app = new Hono()
app.get('/health', async c => (await sql`select 1`, c.text('ok')))
app.use('/fonts/*', serveStatic({ root: './public' }))
app.use('/mark-*', serveStatic({ root: './public' }))
app.use('/banners/*', async (c, next) => { await next(); c.res.headers.set('Cache-Control', 'public, max-age=86400') })
app.use('/banners/*', serveStatic({ root: './public' }))
// APKs and release notes for testers, mounted from ./releases on the host
app.use('/download/*', async (c, next) => {
  await next()
  if (c.req.path.endsWith('.apk')) c.res.headers.set('Content-Type', 'application/vnd.android.package-archive') // lets Android offer "Install"
  // orvyn.apk points at a new version after every release, so neither Cloudflare nor the phone may keep a copy.
  // Versioned files never change and can be cached.
  c.res.headers.set('Cache-Control', /orvyn-\d+\.\d+\.\d+\./.test(c.req.path) ? 'public, max-age=31536000, immutable' : 'no-store')
})
app.use('/download/*', serveStatic({ root: './', rewriteRequestPath: p => p.replace(/^\/download/, '/releases') }))
app.route('/admin', adminRoutes(sql))
app.route('/api', apiRoutes(sql))
app.get('/', c => c.redirect('/admin'))

// ponytail: in-process daily timer; a missed run after a restart just waits for the next one.
setInterval(async () => {
  const r = await checkLedger(sql)
  if (!r.ok) console.error('LEDGER CHECK FAILED', r.problems)
}, 24 * 60 * 60 * 1000)

// Jobs that run on their own (auto-save, scheduled transfers, budget reminders, deposit maturities). Each is safe to run twice.
setInterval(() => runAutoSave(sql).catch(e => console.error('auto-save failed', e)), 60_000)
setInterval(() => runScheduledTransfers(sql).catch(e => console.error('scheduled transfers failed', e)), 60_000)
setInterval(() => runBudgetAlerts(sql).catch(e => console.error('budget alerts failed', e)), 60_000)
setInterval(() => runMaturities(sql).catch(e => console.error('deposit maturities failed', e)), 60_000)
setInterval(() => runBonds(sql).catch(e => console.error('bond coupons failed', e)), 60_000)
await ensurePrices(sql) // fund NAVs up to today, then a new one each day
setInterval(() => ensurePrices(sql).catch(e => console.error('fund prices failed', e)), 3_600_000)

const port = Number(process.env.PORT ?? 8790)
serve({ fetch: app.fetch, port }, () => console.log(`ORVYN server on :${port}`))
