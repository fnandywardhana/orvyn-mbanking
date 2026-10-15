# The story of ORVYN

## The idea

I wanted a portfolio piece that shows product judgement, not just screens: a bank that feels real enough that a tester forgets it isn't. The customer in mind was an OCBC Indonesia customer: a salaried professional in Jakarta, or a Prestige client with a Relationship Manager, used to good banking apps and impatient with bad ones.

Two rules shaped everything. The money is pretend, but it must behave like money: if Raka sends Sinta Rp85,000, both balances change in the same instant, and nothing anywhere can make the books disagree. And the app must not take anything from the phone it runs on.

## How it was made

The whole thing was built with Claude Code as the only developer, with me as product owner: I set the direction, answered questions and tried each release on real phones. Work went in phases, each one starting with a PRD section and user stories with acceptance criteria, and ending with a release testers installed:

1. **Foundation**: ledger, server, admin, design system, the first APK.
2. **MVP**: sign-up, transfers, QRIS, bills, cards, inbox, the admin tools to run a demo.
3. **Helping**: push, pockets, scheduled transfers, requests and split bills, insights and budgets, Vyna, e-statements, a widget.
4. **Growing**: foreign currency, time deposits, investments, total assets, Prestige, passkeys.

## Decisions that mattered

**A real ledger for fake money.** The cheap way is a balance column per customer. ORVYN uses double-entry bookkeeping instead: every movement is a journal entry whose lines add up to zero, with the bank's own accounts on the other side. It costs a little more code per feature and pays for itself the first time an officer reverses a transfer, or a scheduled transfer runs twice after a restart and the idempotency key quietly stops the second one.

**Privacy as a build rule.** The permission list is checked on every build, and the build fails if a library slips in something new. The KTP photo and selfie never leave the phone. Vyna's microphone uses only on-device speech recognition, and the button simply isn't there on phones that can't do that.

**An assistant that can't spend your money.** Vyna reads the signed-in customer's data through narrow, read-only tools, and anything that moves money comes back as a card with a Review button. The customer still checks the details and types their PIN. A 30-question evaluation keeps her honest: she gets all 30 right, and declines poems and prompt injection.

**Small dependencies.** The app has no image loader or HTTP client library; the server has six runtime dependencies. Less to update, less to break, smaller APK.

## Bugs worth remembering

Building in public with real testers found problems no unit test would have:

- **A Float in a count-up animation.** A Prestige customer's home screen showed Rp835,107,968 for Rp835,108,000 while the balance animated. Floats can't hold numbers that big exactly; the fix animates a fraction and computes the Rupiah exactly.
- **A biased hash.** Simulated fund prices came from a quick string hash, and near-identical seeds made it lean one way: a "growth" fund lost 48% in a year. SHA-256 fixed it, and the test now checks the drift.
- **A cached download link.** Cloudflare kept serving last week's APK from `/download/orvyn.apk` for hours. That link is now `no-store`, and messages to testers point at the versioned file.
- **A navigation regression.** Adding push links made the app's root recompose on every screen change, which recomputed the start screen and sent first-time users to "enter your PIN" before they had one. It shipped as a one-line hotfix the same day.

## Where it ended up

Four phases, around 13,000 lines of Kotlin and TypeScript, 92 server tests, and a demo testers can open on their own phones and use like a bank: open an account, get paid, split a dinner bill, ask Vyna where the money went, and put the rest in a deposit.
