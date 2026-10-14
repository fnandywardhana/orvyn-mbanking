// QRIS payloads (EMVCo merchant-presented mode): tag-length-value fields ending in a CRC-16/CCITT-FALSE checksum.
// ORVYN reads two kinds: merchant QRs (tag 26, ID.CO.ORVYN.MERCHANT) and personal "My QR" codes (tag 26, ID.CO.ORVYN.P2P).

const tlv = (tag: string, value: string) => tag + String(value.length).padStart(2, '0') + value

export function crc16(s: string) {
  let crc = 0xffff
  for (const ch of Buffer.from(s, 'utf8')) {
    crc ^= ch << 8
    for (let i = 0; i < 8; i++) crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff
  }
  return crc.toString(16).toUpperCase().padStart(4, '0')
}

function parseTlv(s: string) {
  const out = new Map<string, string>()
  for (let i = 0; i < s.length;) {
    const tag = s.slice(i, i + 2), len = Number(s.slice(i + 2, i + 4))
    if (tag.length < 2 || !Number.isInteger(len)) throw new Error('bad TLV')
    out.set(tag, s.slice(i + 4, i + 4 + len))
    i += 4 + len
  }
  return out
}

type Build = { kind: 'merchant'; code: string; name: string; city: string; mcc: string; amount?: number }
  | { kind: 'p2p'; account: string; name: string; amount?: number }

export function buildQris(q: Build) {
  const account = q.kind === 'merchant'
    ? tlv('26', tlv('00', 'ID.CO.ORVYN.MERCHANT') + tlv('01', q.code))
    : tlv('26', tlv('00', 'ID.CO.ORVYN.P2P') + tlv('01', q.account))
  const body = tlv('00', '01') + tlv('01', q.amount ? '12' : '11') + account
    + tlv('52', q.kind === 'merchant' ? q.mcc : '4829') + tlv('53', '360')
    + (q.amount ? tlv('54', String(q.amount)) : '')
    + tlv('58', 'ID') + tlv('59', q.name.slice(0, 25)) + tlv('60', (q.kind === 'merchant' ? q.city : 'Jakarta').slice(0, 15)) + '6304'
  return body + crc16(body)
}

export type Parsed = { kind: 'merchant'; code: string; amount: number | null } | { kind: 'p2p'; account: string; amount: number | null }

/** Throws a customer-readable message when the code isn't a QRIS ORVYN can pay. */
export function parseQris(payload: string): Parsed {
  const s = payload.trim()
  if (!s.startsWith('000201') || !/6304[0-9A-F]{4}$/i.test(s)) throw new Error("That isn't a QRIS code. Scan the QRIS sticker at the till.")
  if (crc16(s.slice(0, -4)).toUpperCase() !== s.slice(-4).toUpperCase()) throw new Error("We couldn't read that QR properly. Try scanning it again.")
  let f: Map<string, string>
  try { f = parseTlv(s) } catch { throw new Error("We couldn't read that QR properly. Try scanning it again.") }
  const amount = f.has('54') ? Math.round(Number(f.get('54'))) : null
  for (const tag of ['26', '27', '28', '29', '30', '31']) {
    const v = f.get(tag)
    if (!v) continue
    let sub: Map<string, string>
    try { sub = parseTlv(v) } catch { continue }
    if (sub.get('00') === 'ID.CO.ORVYN.MERCHANT') return { kind: 'merchant', code: sub.get('01')!, amount }
    if (sub.get('00') === 'ID.CO.ORVYN.P2P') return { kind: 'p2p', account: sub.get('01')!, amount }
  }
  throw new Error("This QRIS belongs to a real merchant outside the ORVYN demo, so it can't be paid here.")
}
