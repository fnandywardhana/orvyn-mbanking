# Waiting for the owner

Claude's working notes for things that need your answer. None of them blocks the work. Each item lists the default Claude is using until you decide.

| # | Question | Default in use | Since |
|---|---|---|---|
| 6 | Passkey test on the Note10+ (allowed by the owner on 15 Oct) stopped at Google's "Set up screen lock to create passkeys": the phone has no screen lock, and Claude won't change that. To finish: set a screen lock (PIN or pattern) on the Note10+, tell Claude, and remove it afterwards if you like. | Google accepted ORVYN's request for orvyn.delvault.uk and reached the screen-lock step; cancelling is handled in the app | 15 Oct |


# Decided

| # | Decision | Date |
|---|---|---|
| 1 | Vyna replies in the language the customer writes in. | 15 Oct |
| 2 | Fingerprint sign-in: the owner tested it on another phone and it works. | 15 Oct |
| 3 | Firebase project `orvyn-94fcb` is set up; both files are in `secrets/` and were checked against Google. | 15 Oct |
| 5 | Anthropic API key (workspace-scoped) is in `.env` and was checked with a test message to Claude Haiku 5.5. | 15 Oct |
| 7 | The project is on GitHub at github.com/fnandywardhana/orvyn-mbanking (KR7), private for now at the owner's request; switch to public in Settings when ready to show it. Commits use the GitHub noreply email. | 15 Oct |
| 4 | Banners use real photos that Claude picks (public-domain, see `docs/CREDITS.md`), chosen from a photo library in admin. No uploads. Done in 0.4.1. | 15 Oct |

# For your information

- **15 Oct:** Relationship Managers use a monogram, not a photo. The free-licence portraits available were either old, casual, or of a real public figure, and putting a real stranger's face on a made-up bank employee felt wrong.
- **15 Oct:** Hotfix 0.7.1: since 0.5.0, a first sign-in asked for a PIN the customer hadn't made yet. Fixed and released the same day.

- **14 Oct, about 21:30 WIB:** a test tap landed on another app on the Note10+ (`com.splitbill.app`) while ORVYN was restarting. It only opened the app; nothing was typed. Claude pressed Home to leave it. The phone test scripts now refuse to tap or type unless ORVYN is the app in front.
