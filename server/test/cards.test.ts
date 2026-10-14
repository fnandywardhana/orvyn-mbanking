import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer, setPin } from '../src/customers.ts'
import { generateHistory } from '../src/personas.ts'
import { checkLedger, balanceOf } from '../src/ledger.ts'
import { listCards, updateCard, cardDetails, spendOnCard, payCardBill } from '../src/cards.ts'

const sql = connect()
let raka: any

const luhn = (n: string) => [...n].reverse().map(Number).reduce((s, d, i) => s + (i % 2 ? (d * 2 > 9 ? d * 2 - 9 : d * 2) : d), 0) % 10 === 0

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  raka = await sql.begin(async tx => {
    const r = await createCustomer(tx, { fullName: 'Raka Pratama', phone: '081500000001', password: 'rahasia123', persona: 'raka' })
    await generateHistory(tx, 'raka', r.savings.id, r.customer.id)
    return { ...r.customer, savings: r.savings }
  })
  await setPin(sql, raka.id, '482915')
})
after(() => sql.end())

test('Raka has a debit card and a Cashback credit card with six months of purchases, and card numbers pass the Luhn check', async () => {
  const cards = await listCards(sql, raka.id)
  assert.deepEqual(cards.map(c => c.product).sort(), ['cashback', 'debit'])
  const credit = cards.find(c => c.kind === 'credit')! as any
  assert.equal(credit.credit.limit, 15_000_000)
  for (const c of await sql`select number from cards`) assert.ok(luhn(c.number) && c.number.startsWith('9'), c.number)
  const [{ n }] = await sql`select count(*)::int as n from journal_entries where kind = 'card_spend'`
  assert.ok(n > 20, `${n} card purchases`)
  assert.deepEqual(await checkLedger(sql), { ok: true, problems: [] })
})

test('card details need the PIN', async () => {
  const [card] = await listCards(sql, raka.id)
  await assert.rejects(cardDetails(sql, raka.id, card.id, '000001'))
  assert.match((await cardDetails(sql, raka.id, card.id, '482915')).number, /^9\d{15}$/)
})

test('a frozen card, a switched-off channel or the daily limit decline a purchase', async () => {
  const credit = (await listCards(sql, raka.id)).find(c => c.kind === 'credit')!
  await updateCard(sql, raka.id, credit.id, { frozen: true })
  assert.deepEqual(await spendOnCard(sql, { cardId: credit.id, merchant: 'Toko', amount: 100_000, channel: 'in_store' }), { approved: false, reason: 'Card is frozen' })
  await updateCard(sql, raka.id, credit.id, { frozen: false, overseas: false, dailyLimit: 500_000 })
  assert.equal((await spendOnCard(sql, { cardId: credit.id, merchant: 'Hotel', amount: 100_000, channel: 'overseas' }) as any).reason, 'Overseas payments are off')
  assert.equal((await spendOnCard(sql, { cardId: credit.id, merchant: 'Toko', amount: 400_000, channel: 'online' })).approved, true)
  assert.equal((await spendOnCard(sql, { cardId: credit.id, merchant: 'Toko', amount: 200_000, channel: 'online' }) as any).reason, "Over the card's daily limit")
})

test('paying the credit card bill moves money from savings and frees up credit', async () => {
  const credit = (await listCards(sql, raka.id)).find(c => c.kind === 'credit')! as any
  const savingsBefore = await balanceOf(sql, raka.savings.id)
  await payCardBill(sql, raka.id, credit.id, credit.credit.owed, '482915', crypto.randomUUID())
  const after = (await listCards(sql, raka.id)).find(c => c.kind === 'credit')! as any
  assert.equal(after.credit.owed, 0)
  assert.equal(await balanceOf(sql, raka.savings.id), savingsBefore - credit.credit.owed)
})
