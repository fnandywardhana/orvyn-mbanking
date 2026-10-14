# Waiting for the owner

Claude's working notes for things that need your answer. None of them blocks the work. Each item lists the default Claude is using until you decide.

| # | Question | Default in use | Since |
|---|---|---|---|
| 1 | Vyna is shown in English. If a customer writes in Indonesian, should Vyna reply in Indonesian, or always in English? | Replies in the customer's language | 14 Oct |
| 2 | Fingerprint sign-in hasn't been tested because the Note10+ on the server has no fingerprint enrolled. Can you enrol one finger on it? | Fingerprint code is built but untested | 14 Oct |
| 3 | Push notifications need a Firebase project on your Google account (free). Steps: console.firebase.google.com → Add project "ORVYN" → Add Android app with package `id.orvyn.app` → download `google-services.json`; then Project settings → Service accounts → Generate new private key. Put both files in `/home/fnandywardhana/mbanking_apps/secrets/`. | In-app Inbox works without it; push is switched off until the files exist | 14 Oct |

# For your information

- **14 Oct, about 21:30 WIB:** a test tap landed on another app on the Note10+ (`com.splitbill.app`) while ORVYN was restarting. It only opened the app; nothing was typed. Claude pressed Home to leave it. The phone test scripts now refuse to tap or type unless ORVYN is the app in front.
