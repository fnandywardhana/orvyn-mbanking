# Every balance comes from a double-entry ledger

Even though all money is simulated, every balance change is a Journal Entry whose Postings sum to zero per currency, and the other side of anything leaving or entering a customer lands in a bank-owned House Account (External Clearing, Fee Income, Merchant Settlement, Biller Settlement, Demo Equity). Pockets, Credit Accounts, Time Deposits and FX Accounts are ordinary Accounts in the same ledger rather than separate tables with their own balance columns. We chose this over a simple `balance` column per customer because the demo's credibility rests on balances never drifting (PRD KR2), Reversals and Officer Adjustments must stay traceable, and an Internal Transfer must move both sides atomically. The cost is a little more code per money movement.

## Consequences

- Nothing may update a stored balance directly; the stored balance is a cache rewritten in the same database transaction as its Postings.
- A nightly check (and a check after every Journal Entry) asserts that all Postings per currency sum to zero and that each cached balance equals the sum of its Postings.
- Each Account holds one currency; an FX conversion is one Journal Entry touching an FX Position House Account in each currency.
