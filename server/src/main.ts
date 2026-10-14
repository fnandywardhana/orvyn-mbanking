import { Hono } from 'hono'
import { serve } from '@hono/node-server'
import { serveStatic } from '@hono/node-server/serve-static'
import { connect, migrate } from './db.ts'
import { seedSuperAdmin } from './auth.ts'
import { checkLedger } from './ledger.ts'
import { adminRoutes } from './admin.ts'
import { apiRoutes } from './api.ts'
import { backfillDebitCards } from './cards.ts'

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

const port = Number(process.env.PORT ?? 8790)
serve({ fetch: app.fetch, port }, () => console.log(`ORVYN server on :${port}`))
