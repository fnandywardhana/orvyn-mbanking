import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createHash, generateKeyPairSync, randomBytes, sign } from 'node:crypto'
import { isoCBOR } from '@simplewebauthn/server/helpers'
import { connect, migrate } from '../src/db.ts'
import { createCustomer } from '../src/customers.ts'
import { registrationOptions, finishRegistration, signInOptions, signInWithPasskey, listPasskeys, deletePasskey, ORIGINS, RP_ID, assetLinks } from '../src/passkeys.ts'

const sql = connect()
let me: any
const b64 = (b: Uint8Array | Buffer) => Buffer.from(b).toString('base64url')
const sha = (b: Buffer | string) => createHash('sha256').update(b).digest()

/** A tiny software authenticator: the same bytes an Android phone would send, signed with a P-256 key. */
const key = generateKeyPairSync('ec', { namedCurve: 'P-256' })
const credId = randomBytes(16)
function attestation(challenge: string) {
  const jwk = key.publicKey.export({ format: 'jwk' }) as any
  const cose = isoCBOR.encode(new Map<number, any>([[1, 2], [3, -7], [-1, 1], [-2, Buffer.from(jwk.x, 'base64url')], [-3, Buffer.from(jwk.y, 'base64url')]]))
  const authData = Buffer.concat([sha(RP_ID), Buffer.from([0x45]), Buffer.alloc(4), Buffer.alloc(16), Buffer.from([0, credId.length]), credId, Buffer.from(cose)])
  const clientDataJSON = Buffer.from(JSON.stringify({ type: 'webauthn.create', challenge, origin: ORIGINS[0] }))
  const attestationObject = isoCBOR.encode(new Map<string, any>([['fmt', 'none'], ['attStmt', new Map()], ['authData', authData]]))
  return { id: b64(credId), rawId: b64(credId), type: 'public-key', clientExtensionResults: {},
    response: { clientDataJSON: b64(clientDataJSON), attestationObject: b64(attestationObject), transports: ['internal'] } }
}
function assertion(challenge: string, counter: number, origin = ORIGINS[0]) {
  const authData = Buffer.concat([sha(RP_ID), Buffer.from([0x05]), Buffer.from([0, 0, 0, counter])])
  const clientDataJSON = Buffer.from(JSON.stringify({ type: 'webauthn.get', challenge, origin }))
  const signature = sign('sha256', Buffer.concat([authData, sha(clientDataJSON)]), key.privateKey)
  return { id: b64(credId), rawId: b64(credId), type: 'public-key', clientExtensionResults: {},
    response: { clientDataJSON: b64(clientDataJSON), authenticatorData: b64(authData), signature: b64(signature) } }
}

before(async () => {
  await sql`drop schema public cascade`; await sql`create schema public`; await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Raka Pratama', phone: '081300000101', password: 'rahasia123' }))
  me = r.customer
})
after(() => sql.end())

test('the domain vouches for both app signing keys', () => {
  assert.equal(assetLinks()[0].target.package_name, 'id.orvyn.app')
  assert.equal(assetLinks()[0].target.sha256_cert_fingerprints.length, 2)
})

test('a passkey made on the phone signs the customer in on a new phone, once per challenge', async () => {
  const reg = await registrationOptions(sql, me)
  await finishRegistration(sql, me.id, attestation(reg.challenge))
  assert.equal((await listPasskeys(sql, me.id)).length, 1)

  const opt = await signInOptions()
  const r = await signInWithPasskey(sql, assertion(opt.challenge, 1))
  assert.ok(r.ok && r.deviceToken && r.sessionToken)
  await assert.rejects(signInWithPasskey(sql, assertion(opt.challenge, 2)), /took too long/) // challenge already used

  const evil = await signInOptions()
  await assert.rejects(signInWithPasskey(sql, assertion(evil.challenge, 3, 'https://phishing.example')), /couldn't be checked/)
})

test('a deleted passkey no longer works', async () => {
  const [p] = await listPasskeys(sql, me.id)
  await deletePasskey(sql, me.id, p.id)
  const opt = await signInOptions()
  await assert.rejects(signInWithPasskey(sql, assertion(opt.challenge, 4)), /isn't linked/)
})
