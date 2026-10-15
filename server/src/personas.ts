import type { TransactionSql } from 'postgres'
import { BANKS } from './transfers.ts'
import { postJournal, houseAccount, LedgerError } from './ledger.ts'
import { issueCard, spendOnCard } from './cards.ts'

// Persona Templates (CONTEXT.md): ready-made customers with six months of history, modelled on
// OCBC Indonesia segments (PRD §5). Merchant names are made up; billers use real Indonesian names.

export type Party = { name: string; bank?: string; account?: string }
export type Memo = { from?: Party; to?: Party; channel: string; category: string; fee?: number; note?: string }

type Spec =
  | { type: 'in'; amount: number; from: Party; channel: string; category: string; source?: 'EXTERNAL_CLEARING' | 'MERCHANT_SETTLEMENT' }
  | { type: 'transfer'; amount: number; to: Party; rtgs?: boolean; category: string; note?: string }
  | { type: 'qris'; amount: number; merchant: string; category: string }
  | { type: 'bill'; amount: number; fee: number; biller: string; channel: string; category: string }

type Persona = {
  key: string
  fullName: string
  tier: 'everyday' | 'prestige'
  opening: number
  summary: string
  credit?: { product: 'cashback' | 'voyage'; limit: number; shops: string[]; spend: [number, number] }
  day: (d: Date, r: () => number) => Spec[]
}

const pick = <T>(r: () => number, xs: T[]) => xs[Math.floor(r() * xs.length)]
const between = (r: () => number, lo: number, hi: number, step = 1000) => Math.round((lo + r() * (hi - lo)) / step) * step

const RAKA_FOOD = ['Kopi Senja Cipete', 'Warteg Bahari', 'Mie Ayam Pak Kumis', 'Bakmi Kemang 88', 'Sate Pak Darto', 'Nasi Padang Sederhana Raya']
const RAKA_SHOP = ['Toko Buku Aksara', 'Minimarket Sejahtera', 'Distro Lokal Blok M', 'Apotek Sehat Selalu']
const SINTA_SUPPLIERS: Party[] = [
  { name: 'CV Sumber Pangan', bank: 'Mandiri' }, { name: 'UD Ayam Segar', bank: 'BCA' }, { name: 'Toko Bumbu Jaya', bank: 'BRI' },
]
const HENDRA_DINING = ['Rumah Makan Lembayung', 'Kedai Sate Menteng', 'Bistro Kemang Raya', 'Dapur Nusantara SCBD']

export const PERSONAS: Persona[] = [
  {
    key: 'raka', fullName: 'Raka Pratama', tier: 'everyday', opening: 8_500_000,
    summary: 'Everyday · 28, office worker in Jakarta, salary Rp12M, pays almost everything by QRIS',
    credit: { product: 'cashback', limit: 15_000_000, shops: ['Toko Online Nusantara', 'Tiket Pesawat Kita', 'Elektronik Mangga Dua', 'Bioskop Layar Lebar'], spend: [80_000, 900_000] },
    day: (d, r) => {
      const s: Spec[] = []
      const date = d.getUTCDate(), dow = d.getUTCDay()
      if (date === 25) s.push({ type: 'in', amount: 12_000_000, from: { name: 'PT Nusantara Digital', bank: 'Mandiri' }, channel: 'Salary', category: 'income' })
      if (date === 1) s.push({ type: 'transfer', amount: 3_500_000, to: { name: 'Ratna Sari', bank: 'BCA' }, category: 'housing', note: 'Kos bulanan' })
      if (date === 15) s.push({ type: 'transfer', amount: 1_000_000, to: { name: 'Sri Wahyuni', bank: 'BRI' }, category: 'family', note: 'Untuk Ibu' })
      if (date === 5) s.push({ type: 'bill', amount: 200_000, fee: 2_500, biller: 'PLN', channel: 'PLN token', category: 'bills' })
      if (date === 10) s.push({ type: 'bill', amount: 100_000, fee: 0, biller: 'Telkomsel', channel: 'Mobile data', category: 'bills' })
      if (dow === 1) s.push({ type: 'bill', amount: 200_000, fee: 1_000, biller: 'GoPay', channel: 'GoPay top-up', category: 'transport' })
      if (r() < 0.7) s.push({ type: 'qris', amount: between(r, 25_000, 85_000), merchant: pick(r, RAKA_FOOD), category: 'food' })
      if ((dow === 0 || dow === 6) && r() < 0.4) s.push({ type: 'qris', amount: between(r, 100_000, 450_000), merchant: pick(r, RAKA_SHOP), category: 'shopping' })
      if (r() < 0.04) s.push({ type: 'in', amount: between(r, 50_000, 300_000, 5000), from: { name: 'Dimas Prakoso', bank: 'BCA' }, channel: 'BI-FAST', category: 'transfer' })
      return s
    },
  },
  {
    key: 'sinta', fullName: 'Sinta Maharani', tier: 'everyday', opening: 25_000_000,
    summary: 'Everyday · 34, runs a catering business in Surabaya, pays suppliers twice a week',
    credit: { product: 'cashback', limit: 25_000_000, shops: ['Grosir Peralatan Dapur', 'Toko Online Nusantara', 'SPBU Rungkut'], spend: [150_000, 1_500_000] },
    day: (d, r) => {
      const s: Spec[] = []
      const date = d.getUTCDate(), dow = d.getUTCDay()
      if (dow !== 0) s.push({ type: 'in', amount: between(r, 800_000, 2_500_000), from: { name: 'QRIS settlement · Dapur Sinta' }, channel: 'QRIS settlement', category: 'income', source: 'MERCHANT_SETTLEMENT' })
      if (dow === 1 || dow === 4) s.push({ type: 'transfer', amount: between(r, 1_500_000, 6_000_000, 50_000), to: pick(r, SINTA_SUPPLIERS), category: 'business', note: 'Bahan baku' })
      if (date === 3) s.push({ type: 'transfer', amount: 1_750_000, to: { name: 'Sekolah Cahaya Bangsa', bank: 'BCA' }, category: 'education', note: 'SPP' })
      if (date === 20) s.push({ type: 'bill', amount: between(r, 1_000_000, 1_400_000), fee: 2_500, biller: 'PLN', channel: 'PLN postpaid', category: 'bills' })
      if (date === 8) s.push({ type: 'bill', amount: 450_000, fee: 2_500, biller: 'IndiHome', channel: 'Home internet', category: 'bills' })
      if (date === 12) s.push({ type: 'bill', amount: 480_000, fee: 2_500, biller: 'BPJS Kesehatan', channel: 'BPJS Kesehatan', category: 'health' })
      if (dow === 6) s.push({ type: 'qris', amount: between(r, 300_000, 900_000), merchant: pick(r, ['Pasar Atom Surabaya', 'Swalayan Sinar Jaya', 'Toko Sembako Barokah']), category: 'groceries' })
      return s
    },
  },
  {
    key: 'hendra', fullName: 'Hendra Wijaya', tier: 'prestige', opening: 1_250_000_000, // stays above the Rp1B Prestige line
    summary: 'Prestige · 47, company director in Jakarta, large transfers and RTGS',
    credit: { product: 'voyage', limit: 150_000_000, shops: ['Maskapai Garuda Nusa', 'Hotel Bintang Singapura', 'Butik Plaza Senayan', 'Restoran Marina Bay'], spend: [1_000_000, 12_000_000] },
    day: (d, r) => {
      const s: Spec[] = []
      const date = d.getUTCDate(), dow = d.getUTCDay(), month = d.getUTCMonth()
      if (date === 25) s.push({ type: 'in', amount: 85_000_000, from: { name: 'PT Arunika Energi', bank: 'BNI' }, channel: 'Salary', category: 'income' })
      if (date === 2) s.push({ type: 'transfer', amount: 15_000_000, to: { name: 'Kevin Wijaya', bank: 'BCA' }, category: 'family', note: 'Uang bulanan' })
      if (date === 10) s.push({ type: 'bill', amount: between(r, 2_800_000, 3_600_000), fee: 2_500, biller: 'PLN', channel: 'PLN postpaid', category: 'bills' })
      if (date === 11) s.push({ type: 'bill', amount: 850_000, fee: 2_500, biller: 'IndiHome', channel: 'Home internet', category: 'bills' })
      if (date === 18 && month % 2 === 0) s.push({ type: 'transfer', amount: 150_000_000, rtgs: true, to: { name: 'PT Griya Properti Utama', bank: 'Mandiri' }, category: 'housing', note: 'Cicilan unit' })
      if (date === 6) s.push({ type: 'qris', amount: 2_500_000, merchant: 'Klub Golf Pondok Hijau', category: 'leisure' })
      if ((dow === 5 || dow === 6) && r() < 0.6) s.push({ type: 'qris', amount: between(r, 350_000, 1_800_000), merchant: pick(r, HENDRA_DINING), category: 'food' })
      return s
    },
  },
]

// Deterministic per customer so a regenerated persona looks the same.
function seeded(seed: string) {
  let h = 2166136261
  for (const ch of seed) h = Math.imul(h ^ ch.charCodeAt(0), 16777619)
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507)
    h = Math.imul(h ^ (h >>> 13), 3266489909)
    return ((h ^= h >>> 16) >>> 0) / 4294967296
  }
}

/** Fills a fresh Savings Account with six months of history, ending yesterday. Runs in the caller's transaction. */
export async function generateHistory(tx: TransactionSql, personaKey: string, savingsId: string, customerId: string, now = new Date()) {
  const persona = PERSONAS.find(p => p.key === personaKey)!
  const house = Object.fromEntries(await Promise.all(
    ['EXTERNAL_CLEARING', 'FEE_INCOME', 'MERCHANT_SETTLEMENT', 'BILLER_SETTLEMENT', 'DEMO_EQUITY'].map(async c => [c, await houseAccount(tx, c)])))
  const r = seeded(customerId)
  // Work in Western Indonesia Time so salaries land on the 25th and coffee happens in the daytime, wherever the server runs.
  const DAY = 86_400_000, WIB = 7 * 3_600_000
  const today = Math.floor((now.getTime() + WIB) / DAY) * DAY - WIB // midnight WIB, as UTC millis
  const first = today - 182 * DAY

  await postJournal(tx, { kind: 'opening', at: new Date(first + 8 * 3_600_000), memo: { from: { name: 'ORVYN Bank' }, channel: 'Opening balance', category: 'income' } satisfies Memo,
    postings: [{ accountId: house.DEMO_EQUITY, amount: -persona.opening }, { accountId: savingsId, amount: persona.opening }] })

  const card = persona.credit && await issueCard(tx, customerId, persona.credit.product, { savingsId, creditLimit: persona.credit.limit })

  for (let day = first; day < today; day += DAY) {
    const wibDate = new Date(day + WIB) // its UTC fields are the WIB calendar date
    if (card && persona.credit) {
      // About two card purchases a week, and the full bill paid from savings on the 20th.
      if (r() < 0.28) await spendOnCard(tx, { cardId: card.id, merchant: pick(r, persona.credit.shops), amount: between(r, ...persona.credit.spend), channel: r() < 0.5 ? 'online' : 'in_store', at: new Date(day + (9 + r() * 12) * 3_600_000) })
      if (wibDate.getUTCDate() === 20) {
        const [{ balance }] = await tx`select balance from accounts where id = ${card.account_id}`
        if (Number(balance) < 0) await postJournal(tx, { kind: 'card_payment', at: new Date(day + 10 * 3_600_000),
          memo: { from: { name: 'Your savings' }, to: { name: 'Credit card bill' }, channel: 'Credit card payment', category: 'transfer', cardId: card.id },
          postings: [{ accountId: savingsId, amount: Number(balance) }, { accountId: card.account_id, amount: -Number(balance) }] }).catch(e => { if (!(e instanceof LedgerError)) throw e })
      }
    }
    for (const spec of persona.day(wibDate, r)) {
      const at = new Date(day + (7 + r() * 14) * 3_600_000) // 07:00–21:00 WIB
      try {
        await post(tx, spec, savingsId, house, at)
      } catch (e) {
        if (!(e instanceof LedgerError && e.code === 'INSUFFICIENT_FUNDS')) throw e // skip what the customer couldn't afford
      }
    }
  }

  // Story S2.19 AC2: a pocket or two and some budgets, so pockets and insights have something to show in a demo.
  const goals = GOALS[personaKey] ?? { pockets: [], budgets: {} }
  for (const g of goals.pockets) {
    const [a] = await tx`insert into accounts (kind, customer_id, name, currency, floor) values ('pocket', ${customerId}, ${g.name}, 'IDR', 0) returning id`
    await tx`insert into pockets (account_id, customer_id, image, target, rule, rule_since)
      values (${a.id}, ${customerId}, ${g.image}, ${g.target}, ${g.rule ? tx.json(g.rule) : null}, ${g.rule ? now : null})`
    await postJournal(tx, { kind: 'pocket_move', at: new Date(today - 3 * DAY + 9 * 3_600_000), memo: { from: { name: 'ORVYN Everyday' }, to: { name: g.name }, channel: 'Pocket', category: 'pocket' },
      postings: [{ accountId: savingsId, amount: -g.saved }, { accountId: a.id, amount: g.saved }] }).catch(e => { if (!(e instanceof LedgerError)) throw e })
  }
  for (const [category, limit] of Object.entries(goals.budgets))
    await tx`insert into budgets (customer_id, category, monthly_limit) values (${customerId}, ${category}, ${limit})`
}

type Goal = { name: string, image: string, target: number, saved: number, rule?: object }
const GOALS: Record<string, { pockets: Goal[], budgets: Record<string, number> }> = {
  raka: {
    pockets: [
      { name: 'New laptop', image: 'gadget', target: 15_000_000, saved: 2_500_000, rule: { type: 'weekly', day: 1, amount: 100_000 } },
      { name: 'Emergency fund', image: 'safety', target: 10_000_000, saved: 1_500_000 },
    ],
    budgets: { food: 1_500_000, transport: 600_000 },
  },
  sinta: {
    pockets: [{ name: 'Bali holiday', image: 'travel', target: 8_000_000, saved: 3_200_000, rule: { type: 'payday', percent: 10 } }],
    budgets: { shopping: 2_000_000, food: 2_500_000 },
  },
  hendra: {
    pockets: [
      { name: "Kevin's school", image: 'study', target: 200_000_000, saved: 75_000_000 },
      { name: 'Japan trip', image: 'travel', target: 60_000_000, saved: 20_000_000, rule: { type: 'roundup' } },
    ],
    budgets: { entertainment: 10_000_000, food: 8_000_000 },
  },
}

/** A stable 10-digit number for a made-up person at another bank. */
function accountFor(name: string) {
  let h = 17
  for (const ch of name) h = (h * 131 + ch.charCodeAt(0)) % 1e10
  return String(h).padStart(10, '1')
}

async function post(tx: TransactionSql, s: Spec, savings: string, house: Record<string, string>, at: Date) {
  switch (s.type) {
    case 'in':
      return postJournal(tx, { kind: 'inflow', at, memo: { from: s.from, channel: s.channel, category: s.category } satisfies Memo,
        postings: [{ accountId: house[s.source ?? 'EXTERNAL_CLEARING'], amount: -s.amount }, { accountId: savings, amount: s.amount }] })
    case 'transfer': {
      const fee = s.rtgs ? 25_000 : 2_500
      s = { ...s, to: { ...s.to, account: s.to.account ?? accountFor(s.to.name) } } // so they show up as recent recipients
      // ...and so sending to them again finds the same name (External Directory), not a generated one.
      const code = BANKS.find(b => b.name === s.to.bank || b.code === s.to.bank)?.code ?? s.to.bank
      await tx`insert into external_directory (bank, account_number, name) values (${code}, ${s.to.account!}, ${s.to.name}) on conflict do nothing`
      return postJournal(tx, { kind: 'external_transfer', at,
        memo: { to: s.to, channel: `${s.rtgs ? 'RTGS' : 'BI-FAST'} · ${s.to.bank}`, category: s.category, fee, note: s.note } satisfies Memo,
        postings: [{ accountId: savings, amount: -(s.amount + fee) }, { accountId: house.EXTERNAL_CLEARING, amount: s.amount }, { accountId: house.FEE_INCOME, amount: fee }] })
    }
    case 'qris':
      return postJournal(tx, { kind: 'qris_payment', at, memo: { to: { name: s.merchant }, channel: 'QRIS', category: s.category } satisfies Memo,
        postings: [{ accountId: savings, amount: -s.amount }, { accountId: house.MERCHANT_SETTLEMENT, amount: s.amount }] })
    case 'bill': {
      const postings = [{ accountId: savings, amount: -(s.amount + s.fee) }, { accountId: house.BILLER_SETTLEMENT, amount: s.amount }]
      if (s.fee) postings.push({ accountId: house.FEE_INCOME, amount: s.fee })
      return postJournal(tx, { kind: 'bill_payment', at, memo: { to: { name: s.biller }, channel: s.channel, category: s.category, fee: s.fee } satisfies Memo, postings })
    }
  }
}
