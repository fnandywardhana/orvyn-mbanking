import { generateRegistrationOptions, verifyRegistrationResponse, generateAuthenticationOptions, verifyAuthenticationResponse } from '@simplewebauthn/server'
import type { Sql } from './db.ts'
import { registerDevice } from './customers.ts'

// Story S3.12: passkeys. The phone's password manager keeps the private key; ORVYN stores only the public key.
export const RP_ID = process.env.PASSKEY_RP_ID ?? 'orvyn.delvault.uk'
// The Android app signs WebAuthn requests with an origin built from its signing certificate (release key, debug key).
const CERTS = [
  '95:E8:BE:2E:96:57:C7:3B:F7:92:83:69:DD:13:21:BD:5A:3B:6A:88:5E:2F:85:55:37:58:66:52:21:50:7E:69',
  '76:6E:03:53:B4:20:68:7F:04:89:D7:7A:F9:16:07:A5:3B:79:48:F5:1D:D7:36:75:C0:9F:91:C2:95:37:C3:48',
]
export const ORIGINS = CERTS.map(c => 'android:apk-key-hash:' + Buffer.from(c.replace(/:/g, ''), 'hex').toString('base64url'))

/** Digital Asset Links: tells Android that the ORVYN app may use passkeys for this domain. */
export const assetLinks = () => [{
  relation: ['delegate_permission/common.handle_all_urls', 'delegate_permission/common.get_login_creds'],
  target: { namespace: 'android_app', package_name: 'id.orvyn.app', sha256_cert_fingerprints: CERTS },
}]

// ponytail: challenges in memory for 5 minutes; a server restart just means trying again. One process only.
const challenges = new Map<string, { customerId: string | null, until: number }>()
function remember(challenge: string, customerId: string | null) {
  const now = Date.now()
  for (const [k, v] of challenges) if (v.until < now) challenges.delete(k)
  challenges.set(challenge, { customerId, until: now + 5 * 60_000 })
}
function take(clientDataJSON: string) {
  const { challenge } = JSON.parse(Buffer.from(clientDataJSON, 'base64url').toString())
  const c = challenges.get(challenge)
  challenges.delete(challenge)
  return c && c.until > Date.now() ? { challenge, ...c } : null
}

export class PasskeyError extends Error {}

export async function registrationOptions(sql: Sql, customer: { id: string, full_name: string, phone: string }) {
  const existing = await sql`select id, transports from passkeys where customer_id = ${customer.id}`
  const options = await generateRegistrationOptions({
    rpName: 'ORVYN', rpID: RP_ID, userName: customer.phone, userDisplayName: customer.full_name,
    userID: new Uint8Array(Buffer.from(customer.id.replace(/-/g, ''), 'hex')),
    attestationType: 'none', excludeCredentials: existing.map(p => ({ id: p.id, transports: p.transports ?? undefined })),
    authenticatorSelection: { residentKey: 'required', userVerification: 'required' },
  })
  remember(options.challenge, customer.id)
  return options
}

export async function finishRegistration(sql: Sql, customerId: string, response: any) {
  const c = take(response?.response?.clientDataJSON ?? '')
  if (!c || c.customerId !== customerId) throw new PasskeyError('That took too long. Try again.')
  const v = await verifyRegistrationResponse({ response, expectedChallenge: c.challenge, expectedOrigin: ORIGINS, expectedRPID: RP_ID, requireUserVerification: true })
    .catch(() => { throw new PasskeyError("That passkey couldn't be checked. Try again.") })
  if (!v.verified || !v.registrationInfo) throw new PasskeyError("That passkey couldn't be checked. Try again.")
  const cred = v.registrationInfo.credential
  await sql`insert into passkeys (id, customer_id, public_key, counter, transports) values (${cred.id}, ${customerId}, ${Buffer.from(cred.publicKey)}, ${cred.counter}, ${response.response?.transports ?? null})`
}

export async function signInOptions() {
  const options = await generateAuthenticationOptions({ rpID: RP_ID, userVerification: 'required' }) // discoverable: the phone offers the account
  remember(options.challenge, null)
  return options
}

/** Same outcome as a password sign-in (S3.12 AC5): this phone becomes the Registered Device. */
export async function signInWithPasskey(sql: Sql, response: any) {
  const c = take(response?.response?.clientDataJSON ?? '')
  if (!c) throw new PasskeyError('That took too long. Try again.')
  const [p] = await sql`select p.*, cu.pin_hash, cu.locked from passkeys p join customers cu on cu.id = p.customer_id where p.id = ${String(response?.id ?? '')}`
  if (!p) throw new PasskeyError("This passkey doesn't work anymore. Sign in with your phone number and password.") // e.g. after a demo reset (Q-C3)
  if (p.locked) throw new PasskeyError('Your account is locked. Ask the ORVYN team to unlock it.')
  const v = await verifyAuthenticationResponse({
    response, expectedChallenge: c.challenge, expectedOrigin: ORIGINS, expectedRPID: RP_ID, requireUserVerification: true,
    credential: { id: p.id, publicKey: new Uint8Array(p.public_key), counter: Number(p.counter), transports: p.transports ?? undefined },
  }).catch(() => ({ verified: false }) as any)
  if (!v.verified) throw new PasskeyError("That passkey couldn't be checked. Try again.")
  await sql`update passkeys set counter = ${v.authenticationInfo.newCounter}, last_used = now() where id = ${p.id}`
  return registerDevice(sql, { id: p.customer_id, pin_hash: p.pin_hash })
}

export async function listPasskeys(sql: Sql, customerId: string) {
  return (await sql`select id, created_at, last_used from passkeys where customer_id = ${customerId} order by created_at`)
    .map(p => ({ id: p.id, createdAt: p.created_at, lastUsed: p.last_used }))
}
export async function deletePasskey(sql: Sql, customerId: string, id: string) {
  await sql`delete from passkeys where id = ${id} and customer_id = ${customerId}`
}
