import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { connect, migrate } from '../src/db.ts'
import { createCustomer } from '../src/customers.ts'
import { postJournal, houseAccount, reverse } from '../src/ledger.ts'
import { issueCard, spendOnCard } from '../src/cards.ts'
import { apiRoutes } from '../src/api.ts'
import { insights, insightCard, setBudget, setCategory, runBudgetAlerts } from '../src/insights.ts'

const sql = connect()
let raka: any
const NOW = new Date('2026-10-20T05:00:00Z') // 20 Oct, 12:00 WIB
const spend = async (kind: string, category: string, amount: number, at: string) =>
  postJournal(sql, { kind, at: new Date(at), memo: { category }, postings: [{ accountId: raka.savings.id, amount: -amount }, { accountId: await houseAccount(sql, 'MERCHANT_SETTLEMENT'), amount }] })

before(async () => {
  await sql`drop schema public cascade`
  await sql`create schema public`
  await migrate(sql)
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Raka Pratama', phone: '081300000041', password: 'rahasia123' }))
  raka = { ...r.customer, savings: r.savings }
  await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -50_000_000 }, { accountId: raka.savings.id, amount: 50_000_000 }] })
  await spend('qris_payment', 'food', 400_000, '2026-09-05T05:00:00Z')
  await spend('bill_payment', 'bills', 300_000, '2026-09-10T05:00:00Z')
  await spend('qris_payment', 'food', 500_000, '2026-10-03T05:00:00Z')
  await spend('qris_payment', 'groceries', 120_000, '2026-10-04T05:00:00Z')
  await spend('bill_payment', 'bills', 300_000, '2026-10-10T05:00:00Z')
  await postJournal(sql, { kind: 'pocket_move', at: new Date('2026-10-11T05:00:00Z'), memo: { category: 'pocket' }, postings: [
    { accountId: raka.savings.id, amount: -1_000_000 }, { accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: 1_000_000 }] })
})
after(() => sql.end())

test('six months of totals, categories against last month, pocket moves are not spending', async () => {
  const i = await insights(sql, raka.id, NOW)
  assert.deepEqual(i.months.map(m => m.month), ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'])
  assert.deepEqual(i.months.slice(-2).map(m => m.total), [700_000, 920_000])
  const food = i.categories.find(c => c.key === 'food')!
  assert.deepEqual([food.total, food.previous, food.change], [500_000, 400_000, 25])
  assert.equal(i.categories.find(c => c.key === 'shopping')!.total, 120_000) // groceries count as shopping
})

test('the home card names the biggest change on the same days last month, or a budget near its limit', async () => {
  assert.equal((await insightCard(sql, raka.id, NOW))!.text, "You've spent 25% more on food and drinks than by this point last month.")
  await setBudget(sql, raka.id, 'bills', 350_000)
  assert.equal((await insightCard(sql, raka.id, NOW))!.text, "You've used 86% of your Bills budget this month.")
  await setBudget(sql, raka.id, 'bills', null)
})

test('moving a transaction to another category changes where it counts', async () => {
  const [j] = await sql`select j.id from journal_entries j where j.memo->>'category' = 'groceries'`
  assert.equal(await setCategory(sql, raka.id, j.id, 'food'), true)
  assert.equal(await setCategory(sql, raka.id, j.id, 'yachts'), false)
  const i = await insights(sql, raka.id, NOW)
  assert.equal(i.categories.find(c => c.key === 'food')!.total, 620_000)
  assert.equal(i.categories.find(c => c.key === 'shopping'), undefined)
})

test('budget reminders come once at 80% and once at 100% each month', async () => {
  await setBudget(sql, raka.id, 'food', 700_000) // 620k = 88%
  await runBudgetAlerts(sql, NOW)
  await runBudgetAlerts(sql, NOW)
  await spend('qris_payment', 'food', 100_000, '2026-10-19T05:00:00Z')
  await runBudgetAlerts(sql, NOW)
  await runBudgetAlerts(sql, NOW)
  const titles = (await sql`select title from inbox_messages where customer_id = ${raka.id} order by created_at`).map(r => r.title)
  assert.deepEqual(titles, ['Budget nearly used', 'Budget used up'])
})

// Hotfix 0.9.1, H91-2 (Q-02-F3): a category's detail lists savings and credit card spending, and adds up to its Insights total.
test('H91-2: category detail mixes savings and card rows, newest first, and matches the Insights total', async () => {
  const api = apiRoutes(sql)
  let session = ''
  const get = async (path: string) => (await api.request(path, { headers: { Authorization: `Bearer ${session}` } })).json() as Promise<any>
  const r = await sql.begin(tx => createCustomer(tx, { fullName: 'Hendra Wijaya', phone: '081300000042', password: 'rahasia123' }))
  const card = await sql.begin(tx => issueCard(tx, r.customer.id, 'voyage', { savingsId: r.savings.id }))
  const settle = await houseAccount(sql, 'MERCHANT_SETTLEMENT')
  await postJournal(sql, { kind: 'adjustment', postings: [{ accountId: await houseAccount(sql, 'DEMO_EQUITY'), amount: -5_000_000 }, { accountId: r.savings.id, amount: 5_000_000 }] })
  const qris = (category: string, amount: number, name: string) => postJournal(sql, { kind: 'qris_payment', memo: { to: { name }, channel: 'QRIS', category },
    postings: [{ accountId: r.savings.id, amount: -amount }, { accountId: settle, amount }] })
  await qris('shopping', 240_000, 'Minimarket Sejahtera')
  const tokopedia = await spendOnCard(sql, { cardId: card.id, merchant: 'Tokopedia', amount: 850_000, channel: 'online' })
  const gone = await spendOnCard(sql, { cardId: card.id, merchant: 'Returned', amount: 100_000, channel: 'online' })
  if (!tokopedia.approved || !gone.approved) throw new Error('card spend declined')
  await reverse(sql, gone.journalId, {}) // reversed spending counts nowhere
  const cinema = await spendOnCard(sql, { cardId: card.id, merchant: 'Bioskop XXI', amount: 75_000, channel: 'in_store' })
  if (!cinema.approved) throw new Error('card spend declined')
  await setCategory(sql, r.customer.id, cinema.journalId, 'entertainment') // a category with card spending only

  session = (await (await api.request('/auth/password', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone: '081300000042', password: 'rahasia123' }) })).json() as any).sessionToken
  const totals = Object.fromEntries((await insights(sql, r.customer.id)).categories.map(c => [c.key, c.total]))

  const shopping = (await get('/insights/categories/shopping/transactions')).items
  assert.deepEqual(shopping.map((t: any) => [t.title, t.amount]), [['Tokopedia', -850_000], ['Minimarket Sejahtera', -240_000]])
  assert.equal(-shopping.reduce((s: number, t: any) => s + t.amount, 0), totals.shopping)
  assert.equal(shopping[0].subtitle, `ORVYN Voyage ·••• ${card.number.slice(-4)}`)
  assert.equal(shopping[0].accountId, card.account_id, 'a card row opens on the card side, like Cards')
  assert.equal(shopping[1].accountId, r.savings.id)

  const fun = (await get('/insights/categories/entertainment/transactions')).items
  assert.deepEqual(fun.map((t: any) => t.title), ['Bioskop XXI'])
  assert.equal(-fun[0].amount, totals.entertainment)
  assert.deepEqual((await get('/insights/categories/food/transactions')).items, [])

  // The 0.9.0 app's savings-only list is unchanged.
  const old = (await get(`/accounts/${r.savings.id}/transactions?category=shopping`)).items
  assert.deepEqual(old.map((t: any) => t.title), ['Minimarket Sejahtera'])
})
