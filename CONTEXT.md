# ORVYN Bank

A fictional retail bank used as a portfolio demo. Every balance and record is simulated, and every piece of it can be changed from the admin website. The app and admin are English only, so the terms below are also the words customers and officers see on screen.

## Language

### People and access

**Customer**:
A person who holds products at ORVYN Bank and signs in to the app. Each tester gets exactly one Customer.
_Avoid_: user, client, member, account (for the person)

**Registration**:
A request to become a Customer, submitted from the app and waiting for an Officer to approve or reject it. Approval creates the Customer, a Savings Account and a Debit Card.
_Avoid_: onboarding (for the record), application, KYC

**Persona Template**:
A ready-made customer profile (Raka, Sinta, Hendra) with a Tier, products and six months of generated history, used by an Officer to create a Customer in one step.
_Avoid_: demo account, seed user

**Tier**:
The service level of a Customer: Everyday, Prestige or Private. Set by an Officer, never computed automatically.
_Avoid_: segment, level, class

**Registered Device**:
The one phone a Customer is currently signed in on, identified by a random code the app creates at install. Signing in on another phone replaces it.
_Avoid_: device ID, IMEI

**Lock**:
A state in which a Customer can't sign in or approve anything until an Officer unlocks them. It starts after 5 wrong tries counted together across the unlock screen, payments, Change PIN, Change password and password sign-in, or when an Officer locks the Customer. A correct try resets the count.
_Avoid_: block, freeze (that is for Cards), suspend

**Passkey**:
A sign-in key kept by the phone's password manager. The server stores only its public half, so deleting it on the server (or a demo reset) makes the copy on the phone useless.
_Avoid_: biometric login, FIDO key

**Officer**:
A person who signs in to the admin website. Each Officer has one Role: Super Admin, Operator or Content.
_Avoid_: admin user, staff, operator (for the person)

**Audit Entry**:
A permanent record of one change an Officer made: who, what, when, and the value before and after.
_Avoid_: log, history

### Money

**Account**:
A place that holds money in exactly one currency and has a balance. Savings Accounts, Pockets, Time Deposits, FX Accounts and Credit Accounts are all Accounts.
_Avoid_: wallet, balance (for the container)

**Savings Account**:
A Customer's main Rupiah Account with an account number that other people can send money to. Its balance can never go below zero.
_Avoid_: main account, primary account

**Pocket**:
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

**Transaction**:
A Journal Entry as a Customer sees it in their history: a type, a counterparty, an amount, a status and a receipt.
_Avoid_: mutation (in code), activity

**Reversal**:
A new Journal Entry that exactly mirrors an earlier one, made by an Officer. The original is never edited or deleted.
_Avoid_: refund, void, cancel, rollback

**Adjustment**:
A Journal Entry an Officer creates to add or remove money from a Customer's Account, always with a written reason.
_Avoid_: top up (for this), correction, inject

**Simulated Inflow**:
A Journal Entry an Officer creates that looks to the Customer like real incoming money, such as a salary or a transfer from another bank.
_Avoid_: fake transfer, mock credit

**Daily Limit**:
The most a Customer may pay out in one Bank Day, set per Tier. Transfers, QRIS payments and Bill Payments count; moving money between a Customer's own Accounts does not.
_Avoid_: quota, cap

**Bank Day**:
A calendar day in Western Indonesia Time (WIB). Daily Limits reset at midnight WIB.
_Avoid_: business day, day (unqualified)

**FX Account**:
An Account in one foreign currency (USD, SGD, JPY, AUD or EUR) owned by a Customer. It has no account number; money only moves between it and the Customer's Savings Account through an FX Exchange.
_Avoid_: valas account, multicurrency wallet

**Exchange Rate**:
The Rupiah price of one unit of a foreign currency, set by an Officer as two numbers: the Buy Rate (what ORVYN pays when a Customer sells) and the Sell Rate (what a Customer pays when buying). Prestige and Private Customers get a narrower spread.
_Avoid_: kurs (in code), price

**FX Exchange**:
One Journal Entry that takes money from one of a Customer's Accounts and puts the converted amount in another of their Accounts in a different currency, with an FX Position House Account on each side.
_Avoid_: conversion, swap

**Time Deposit**:
An Account holding Rupiah for a fixed Tenor (1, 3, 6 or 12 months) at a fixed yearly rate. On the Maturity Date it either renews with the interest added (Automatic Rollover) or pays principal and interest into the Savings Account. Withdrawing early forfeits the interest.
_Avoid_: deposito (in code), fixed deposit, term deposit

### Investments

**Product**:
Something a Customer can invest in: a Fund, a Bond or Gold Savings. Each Product has a Risk Level from 1 (low) to 4 (high).
_Avoid_: instrument, asset (for the product)

**Fund**:
A made-up mutual fund whose Net Asset Value per unit changes once a Bank Day. Customers buy and sell it in Rupiah and hold Units.
_Avoid_: reksadana (in code), mutual fund (in UI copy is fine)

**Bond**:
A made-up Indonesian retail government bond with a fixed yearly coupon paid monthly into the Savings Account. It is bought in multiples of Rp1,000,000 and held to maturity.
_Avoid_: SBN (in code), obligasi

**Gold Savings**:
Gold held in grams at a Buy Price and Sell Price per gram set by an Officer.
_Avoid_: emas, gold account

**Holding**:
How much of one Product a Customer owns, in Units, face value or grams, with what they paid for it. Holdings are not Accounts: the Rupiah that bought them sits in the Investment Clearing House Account.
_Avoid_: position, portfolio line

**Risk Profile**:
Conservative, Moderate or Aggressive, from a five-question quiz. A Customer can still buy a Product above their profile after confirming they understand.
_Avoid_: risk appetite, investor type

**Total Assets**:
Everything a Customer owns valued in Rupiah today: Savings, Pockets, Time Deposits, FX Accounts at the Buy Rate, and Holdings at today's value. Money owed on Credit Cards is shown next to it, not subtracted.
_Avoid_: net worth, wealth

### People

**Relationship Manager**:
A named ORVYN employee assigned by an Officer to a Prestige or Private Customer, with a photo and phone number shown in the app.
_Avoid_: RM (in UI copy), personal banker

### Ways to move money

**Transfer**:
Money sent from a Customer's Savings Account to another account number. An Internal Transfer goes to another ORVYN Customer; an External Transfer goes to another bank.
_Avoid_: send, payment (for transfers)

**Internal Transfer**:
A Transfer where both sides are ORVYN Accounts: the sender's balance goes down and the recipient's goes up in the same Journal Entry.
_Avoid_: on-us, intrabank

**External Transfer**:
A Transfer to another bank by BI-FAST or RTGS. Only the sender's side is real; the other side lands in the External Clearing House Account.
_Avoid_: interbank, outgoing transfer

**External Directory**:
Holder names for account numbers at other banks. Any number of valid length gets a consistent made-up name; Officers can set specific numbers and names.
_Avoid_: fake accounts, mock bank

**Saved Recipient**:
An account number a Customer has saved for quick Transfers.
_Avoid_: beneficiary, payee, contact

**Money Request**:
A request one Customer sends another to pay a set amount to their Savings Account. The payer opens it from the inbox, then pays through the usual review screen and PIN, or declines it. It expires after 7 days. A Split Bill is a group of Money Requests.
_Avoid_: invoice, bill (that is a Biller's), payment request

**Merchant**:
A made-up shop that accepts QRIS payments. Money paid to a Merchant lands in the Merchant Settlement House Account.
_Avoid_: store, seller

**Static QR / Dynamic QR**:
A Merchant QR code where the Customer types the amount (static) or the amount is already in the code (dynamic).

**Biller**:
A made-up company a Customer can pay, such as PLN, a mobile operator, an e-wallet or BPJS. Each Biller has a list of made-up customer numbers and names.
_Avoid_: vendor, provider

**Bill Payment**:
A payment to a Biller for a specific customer number: a bill, a token, airtime or an e-wallet top-up.
_Avoid_: purchase, PPOB

**Quick Amount**:
A button on an amount screen that fills in a preset amount in one tap. A screen only offers Quick Amounts its own product rule accepts (minimum and fixed steps), so a Time Deposit screen never offers less than Rp1,000,000.
_Avoid_: pill (the business team's word; fine in conversation, not in docs or code), preset, shortcut

### Cards

**Card**:
A plastic card linked to an Account: a Debit Card to a Savings Account, or a Credit Card to a Credit Account. It can be frozen and have its online, overseas and contactless use turned on or off.
_Avoid_: plastic, instrument

**Freeze**:
A state the Customer can switch on and off that stops all use of a Card. A Card is never permanently closed in this demo.
_Avoid_: block, lock, suspend

### Content

**Banner**:
A promotional image with text in both languages, shown on the home screen below recent transactions between a start and end date, in the order an Officer sets.

**Maintenance Mode**:
A setting a Super Admin turns on to stop all transactions. The server rejects them, and an open app switches to the maintenance screen within a minute.
_Avoid_: downtime, offline (that means the phone has no connection)

**Announcement**:
A one-time pop-up message shown to Customers when they open the app.

**Inbox Message**:
A notification kept in the app, whether or not a push notification was also sent.

## Relationships

- A **Customer** has one **Savings Account**, any number of **Pockets**, one or more **Cards**, and at most one **Registered Device**.
- Every **Account** belongs to either one **Customer** or the bank (**House Account**), and has one currency.
- A **Journal Entry** has two or more **Postings**; each **Posting** touches one **Account**.
- A **Transaction** is one **Journal Entry** seen from one **Customer**'s side. An **Internal Transfer** therefore shows up as two Transactions, one for each Customer.
- A **Reversal** points to exactly one earlier **Journal Entry**, and a Journal Entry can be reversed at most once.
- A **Registration** becomes at most one **Customer**.
- A **Customer** has at most one **FX Account** per currency, any number of **Time Deposits**, at most one **Holding** per **Product**, and at most one **Relationship Manager**.

## Flagged ambiguities

- "Account" was used for both the person and the money container. Resolved: the person is a **Customer**, the container is an **Account**.
- "Top up" was used for both an Officer adding money and a Customer topping up an e-wallet. Resolved: an Officer makes an **Adjustment** or **Simulated Inflow**; "Top up" means only an e-wallet **Bill Payment**.
- "Demo account" was dropped. Testers get a normal **Customer** created from a **Persona Template**.
