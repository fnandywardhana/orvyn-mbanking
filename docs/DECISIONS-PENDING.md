# Waiting for the owner

Claude's working notes for things that need your answer. None of them blocks the work. Each item lists the default Claude is using until you decide.

| # | Question | Default in use | Since |
|---|---|---|---|
| 3 | Push notifications need a Firebase project. Steps are in the 15 Oct chat. Put `google-services.json` and `firebase-service-account.json` in `/home/fnandywardhana/mbanking_apps/secrets/`. | In-app Inbox works without it; push is switched off until the files exist | 14 Oct |
| 5 | Vyna needs your Anthropic API key as `ANTHROPIC_API_KEY=...` in `/home/fnandywardhana/mbanking_apps/.env`. Add it yourself so the key never appears in chat. | Phase 2 starts with the features that need no key; Vyna comes when the key is there | 15 Oct |

# Decided

| # | Decision | Date |
|---|---|---|
| 1 | Vyna replies in the language the customer writes in. | 15 Oct |
| 2 | Fingerprint sign-in: the owner tested it on another phone and it works. | 15 Oct |
| 4 | Banners use real photos that Claude picks (public-domain, see `docs/CREDITS.md`), chosen from a photo library in admin. No uploads. Done in 0.4.1. | 15 Oct |

# For your information

- **14 Oct, about 21:30 WIB:** a test tap landed on another app on the Note10+ (`com.splitbill.app`) while ORVYN was restarting. It only opened the app; nothing was typed. Claude pressed Home to leave it. The phone test scripts now refuse to tap or type unless ORVYN is the app in front.
