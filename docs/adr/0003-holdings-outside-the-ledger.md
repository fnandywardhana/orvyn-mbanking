# Investment holdings are counted in units outside the ledger

Funds, Bonds and Gold Savings are held in Units, face value or grams, and their Rupiah value moves every Bank Day. We record each Customer's Holding (quantity and what they paid) in its own table and keep only the Rupiah side in the ledger: buying moves Rupiah from the Savings Account to the Investment Clearing House Account, selling moves it back at today's price. We chose this over a ledger Account per Product because the ledger's rule is that a balance only changes through a Journal Entry, and a fund's value changes every day without any money moving; booking daily revaluations as Journal Entries would fill History with noise and make "balance" mean two different things.

## Consequences

- Total Assets adds Holdings at today's price to the ledger balances; it is a view, not a stored number.
- Investment Clearing can end up above or below zero after gains and losses. That is the bank's simulated profit or loss, like Demo Equity.
- Quantity changes (buy, sell, bond maturity) happen in the same database transaction as their Journal Entry, so the two never disagree.
