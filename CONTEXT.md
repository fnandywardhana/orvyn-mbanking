# ORVYN Bank

A fictional retail bank used as a portfolio demo. Every balance and record is simulated, and every piece of it can be changed from the admin website. Canonical terms are in English (code, API, docs); the label in brackets is the word customers see in the Indonesian UI.

## Language

### People and access

**Customer** [Nasabah]:
A person who holds products at ORVYN Bank and signs in to the app. Each tester gets exactly one Customer.
_Avoid_: user, client, member, account (for the person)

**Registration** [Pendaftaran]:
A request to become a Customer, submitted from the app and waiting for an Officer to approve or reject it. Approval creates the Customer, a Savings Account and a Debit Card.
_Avoid_: onboarding (for the record), application, KYC

**Persona Template**:
A ready-made customer profile (Raka, Sinta, Hendra) with a Tier, products and six months of generated history, used by an Officer to create a Customer in one step.
_Avoid_: demo account, seed user

**Tier** [Tier]:
The service level of a Customer: Everyday, Prestige or Private. Set by an Officer, never computed automatically.
_Avoid_: segment, level, class

**Registered Device** [Perangkat terdaftar]:
The one phone a Customer is currently signed in on, identified by a random code the app creates at install. Signing in on another phone replaces it.
_Avoid_: device ID, IMEI

**Officer** [Petugas]:
A person who signs in to the admin website. Each Officer has one Role: Super Admin, Operator or Content.
_Avoid_: admin user, staff, operator (for the person)

**Audit Entry**:
A permanent record of one change an Officer made: who, what, when, and the value before and after.
_Avoid_: log, history

### Money

**Account** [Rekening]:
A place that holds money in exactly one currency and has a balance. Savings Accounts, Pockets, Time Deposits, FX Accounts and Credit Accounts are all Accounts.
_Avoid_: wallet, balance (for the container)

**Savings Account** [Tabungan]:
A Customer's main Rupiah Account with an account number that other people can send money to. Its balance can never go below zero.
_Avoid_: main account, primary account

**Pocket** [Kantong]:
An Account owned by a Customer for a single goal, with an optional target amount and date. Money only moves between a Pocket and its owner's Savings Account; it has no account number of its own.
_Avoid_: sub-account, goal, jar, vault

**Credit Account**:
The Account behind a Credit Card. Its balance is the amount the Customer owes, up to the Credit Limit.
_Avoid_: credit card balance

**House Account**:
An Account owned by the bank itself that the other side of a customer movement lands in, for example External Clearing, Fee Income, Merchant Settlement or Biller Settlement. Customers never see House Accounts.
_Avoid_: system account, GL account, suspense

**Journal Entry**:
One complete money movement, made of two or more Postings that add up to zero in each currency. Nothing changes a balance except a Journal Entry.
_Avoid_: ledger row, transaction (for the ledger record)

**Posting**:
One line of a Journal Entry that adds to or takes from exactly one Account.
_Avoid_: entry, line item, debit/credit row

**Transaction** [Transaksi]:
A Journal Entry as a Customer sees it in their history: a type, a counterparty, an amount, a status and a receipt.
_Avoid_: mutation (in code), activity

**Reversal** [Pembatalan]:
A new Journal Entry that exactly mirrors an earlier one, made by an Officer. The original is never edited or deleted.
_Avoid_: refund, void, cancel, rollback

**Adjustment** [Penyesuaian saldo]:
A Journal Entry an Officer creates to add or remove money from a Customer's Account, always with a written reason.
_Avoid_: top up (for this), correction, inject

**Simulated Inflow** [Uang masuk]:
A Journal Entry an Officer creates that looks to the Customer like real incoming money, such as a salary or a transfer from another bank.
_Avoid_: fake transfer, mock credit

**Daily Limit** [Batas harian]:
The most a Customer may pay out in one Bank Day, set per Tier. Transfers, QRIS payments and Bill Payments count; moving money between a Customer's own Accounts does not.
_Avoid_: quota, cap

**Bank Day**:
A calendar day in Western Indonesia Time (WIB). Daily Limits reset at midnight WIB.
_Avoid_: business day, day (unqualified)

### Ways to move money

**Transfer** [Transfer]:
Money sent from a Customer's Savings Account to another account number. An Internal Transfer goes to another ORVYN Customer; an External Transfer goes to another bank.
_Avoid_: send, payment (for transfers)

**Internal Transfer** [Sesama ORVYN]:
A Transfer where both sides are ORVYN Accounts: the sender's balance goes down and the recipient's goes up in the same Journal Entry.
_Avoid_: on-us, intrabank

**External Transfer** [Antar bank]:
A Transfer to another bank by BI-FAST or RTGS. Only the sender's side is real; the other side lands in the External Clearing House Account.
_Avoid_: interbank, outgoing transfer

**External Directory**:
Holder names for account numbers at other banks. Any number of valid length gets a consistent made-up name; Officers can set specific numbers and names.
_Avoid_: fake accounts, mock bank

**Saved Recipient** [Penerima favorit]:
An account number a Customer has saved for quick Transfers.
_Avoid_: beneficiary, payee, contact

**Merchant** [Merchant]:
A made-up shop that accepts QRIS payments. Money paid to a Merchant lands in the Merchant Settlement House Account.
_Avoid_: store, seller

**Static QR / Dynamic QR**:
A Merchant QR code where the Customer types the amount (static) or the amount is already in the code (dynamic).

**Biller** [Biller]:
A made-up company a Customer can pay, such as PLN, a mobile operator, an e-wallet or BPJS. Each Biller has a list of made-up customer numbers and names.
_Avoid_: vendor, provider

**Bill Payment** [Bayar & beli]:
A payment to a Biller for a specific customer number: a bill, a token, airtime or an e-wallet top-up.
_Avoid_: purchase, PPOB

### Cards

**Card** [Kartu]:
A plastic card linked to an Account: a Debit Card to a Savings Account, or a Credit Card to a Credit Account. It can be frozen and have its online, overseas and contactless use turned on or off.
_Avoid_: plastic, instrument

**Freeze** [Blokir sementara]:
A state the Customer can switch on and off that stops all use of a Card. A Card is never permanently closed in this demo.
_Avoid_: block, lock, suspend

### Content

**Banner**:
A promotional image with text in both languages, shown on the home screen between a start and end date.

**Announcement** [Pengumuman]:
A one-time pop-up message shown to Customers when they open the app.

**Inbox Message** [Kotak masuk]:
A notification kept in the app, whether or not a push notification was also sent.

## Relationships

- A **Customer** has one **Savings Account**, any number of **Pockets**, one or more **Cards**, and at most one **Registered Device**.
- Every **Account** belongs to either one **Customer** or the bank (**House Account**), and has one currency.
- A **Journal Entry** has two or more **Postings**; each **Posting** touches one **Account**.
- A **Transaction** is one **Journal Entry** seen from one **Customer**'s side. An **Internal Transfer** therefore shows up as two Transactions, one for each Customer.
- A **Reversal** points to exactly one earlier **Journal Entry**, and a Journal Entry can be reversed at most once.
- A **Registration** becomes at most one **Customer**.

## Flagged ambiguities

- "Rekening" was used for both the person and the money container. Resolved: the person is a **Customer**, the container is an **Account**.
- "Top up" was used for both an Officer adding money and a Customer topping up an e-wallet. Resolved: an Officer makes an **Adjustment** or **Simulated Inflow**; "Top up" means only an e-wallet **Bill Payment**.
- "Akun demo" was dropped. Testers get a normal **Customer** created from a **Persona Template**.
