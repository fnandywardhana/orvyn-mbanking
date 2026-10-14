import { test } from 'node:test'
import assert from 'node:assert/strict'
import { crc16, buildQris, parseQris } from '../src/qris.ts'

test('CRC-16/CCITT-FALSE matches the standard check value', () => {
  assert.equal(crc16('123456789'), '29B1')
})

test('merchant and personal QRs survive a round trip, with or without an amount', () => {
  assert.deepEqual(parseQris(buildQris({ kind: 'merchant', code: 'KSC001', name: 'Kopi Senja Cipete', city: 'Jakarta Selatan', mcc: '5814' })),
    { kind: 'merchant', code: 'KSC001', amount: null })
  assert.deepEqual(parseQris(buildQris({ kind: 'merchant', code: 'KSC001', name: 'Kopi Senja Cipete', city: 'Jakarta Selatan', mcc: '5814', amount: 38000 })),
    { kind: 'merchant', code: 'KSC001', amount: 38000 })
  assert.deepEqual(parseQris(buildQris({ kind: 'p2p', account: '4061594534', name: 'Sinta Maharani' })), { kind: 'p2p', account: '4061594534', amount: null })
})

test('tampered, foreign and non-QRIS codes are refused with a clear message', () => {
  const ok = buildQris({ kind: 'merchant', code: 'KSC001', name: 'Kopi Senja', city: 'Jakarta', mcc: '5814', amount: 38000 })
  assert.throws(() => parseQris(ok.replace('38000', '18000')), /couldn't read/)
  assert.throws(() => parseQris('https://example.com'), /isn't a QRIS/)
  const tlv = (t: string, v: string) => t + String(v.length).padStart(2, '0') + v
  const foreign = tlv('00', '01') + tlv('01', '11') + tlv('26', tlv('00', 'ID.CO.OTHERBANK') + tlv('01', 'ABC12345')) + tlv('52', '5814') + tlv('53', '360') + tlv('58', 'ID') + tlv('59', 'Kedai') + tlv('60', 'Jakarta') + '6304'
  assert.throws(() => parseQris(foreign + crc16(foreign)), /outside the ORVYN demo/)
})
