import { readFileSync, existsSync } from 'node:fs'
import { createSign } from 'node:crypto'
import type { Sql } from './db.ts'

// Story S2.0: Firebase Cloud Messaging (HTTP v1). Without the service account file, push is off and the Inbox still works.
const file = process.env.FIREBASE_SERVICE_ACCOUNT ?? './secrets/firebase-service-account.json'
const sa = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null
let cached: { token: string, until: number } | null = null

export const pushEnabled = () => sa !== null

async function accessToken() {
  if (cached && cached.until > Date.now()) return cached.token
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url')
  const now = Math.floor(Date.now() / 1000)
  const unsigned = `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64({ iss: sa.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: sa.token_uri, iat: now, exp: now + 3600 })}`
  const jwt = `${unsigned}.${createSign('RSA-SHA256').update(unsigned).sign(sa.private_key, 'base64url')}`
  const r = await fetch(sa.token_uri, { method: 'POST', body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }) })
  if (!r.ok) throw new Error(`Google token ${r.status}`)
  const j = await r.json()
  cached = { token: j.access_token, until: Date.now() + (j.expires_in - 300) * 1000 }
  return cached.token
}

/** Sends a data message; the app builds the notification so it can hide details on the lock screen. Never throws. */
export async function push(sql: Sql, customerId: string, data: { title: string, body: string, link?: string | null }) {
  if (!sa) return
  const [d] = await sql`select push_token from registered_devices where customer_id = ${customerId} and push_token is not null`
  if (!d) return
  try {
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: 'POST', headers: { authorization: `Bearer ${await accessToken()}`, 'content-type': 'application/json' },
      body: JSON.stringify({ message: { token: d.push_token, android: { priority: 'high', ttl: '86400s' }, data: { title: data.title, body: data.body, link: data.link ?? '' } } }),
    })
    if (r.status === 404 || r.status === 400) {
      const e = await r.json().catch(() => ({}))
      // The app was uninstalled or the token is stale: stop sending to it.
      if (r.status === 404 || e?.error?.details?.some((x: any) => x.errorCode === 'UNREGISTERED' || x.errorCode === 'INVALID_ARGUMENT'))
        await sql`update registered_devices set push_token = null where customer_id = ${customerId} and push_token = ${d.push_token}`
    } else if (!r.ok) console.error('push failed', r.status)
  } catch (e) { console.error('push failed', (e as Error).message) }
}
