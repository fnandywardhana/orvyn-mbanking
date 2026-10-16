# ORVYN Design System: Master

Single source of truth for the Android app (Jetpack Compose) and ORVYN Admin (web). Page-specific overrides live in `design-system/pages/<page>.md` and win over this file. Terms follow `CONTEXT.md`.

**Direction:** Minimal + Accessible, with one brand moment per screen. Nerazzurri blue and black from Inter Milan, vertical jersey stripes as the only graphic motif. Calm, confident, premium; never playful or decorative.

## 1. Color

All contrast ratios were measured (WCAG 2.x). Components use **semantic tokens only**, never raw hex.

### Brand constants (same in both themes)

| Token | Hex | Use |
|---|---|---|
| `brand.nerazzurro` | `#0032A0` | Brand blue (Pantone 286) |
| `brand.nero` | `#0A0B10` | Brand black |
| `brand.giallo` | `#F5C400` | Prestige accent. **Fill only**, always with Nero text (11.96:1). Never as text on light surfaces. |
| `brand.gialloInk` | `#7A5E00` | Giallo for text on light surfaces (6.12:1 on white) |
| `hero.gradient` | `#0032A0 → #0A1650 → #0A0B10`, 160° | Home header, success screen, receipt. White text 16.9:1 on the midpoint. |
| `hero.stripes` | white at 5%, stripe 22dp / gap 22dp, vertical | Overlay on the hero gradient only |
| `hero.textMuted` | `#C9D4F5` | Secondary text on the hero (7.24:1 on Nerazzurro) |

### Semantic tokens

| Token | Light | Dark | Contrast notes |
|---|---|---|---|
| `background` | `#F4F6FB` | `#070A12` | |
| `surface` | `#FFFFFF` | `#0F1422` | Cards, sheets, inputs |
| `surfaceVariant` | `#EDF1F8` | `#161D2E` | Grouped rows, keypad keys, skeletons |
| `primary` | `#0032A0` | `#7FA2FF` | 10.69:1 on white; 7.45:1 on dark surface |
| `onPrimary` | `#FFFFFF` | `#001A5C` | 6.54:1 on dark primary |
| `primaryContainer` | `#E5ECFA` | `#0032A0` | Tonal buttons, selected chips, icon tiles |
| `onPrimaryContainer` | `#001A5C` | `#DCE5FF` | 13.6:1 (light) |
| `text` | `#0B1020` | `#E8ECF6` | 17.5:1 / 15.5:1 |
| `textSecondary` | `#4D5872` | `#A3ADC2` | 7.11:1 / 8.15:1 |
| `textTertiary` | `#6B7690` | `#7C879E` | 4.55:1 / 5.09:1. Hints and timestamps only. |
| `outline` | `#DCE2EE` | `#212A3E` | Dividers and card borders (decorative, below 3:1 by design) |
| `outlineStrong` | `#858FA3` | `#5D6884` | Input borders and focus-less controls (3.25:1 / 3.3:1) |
| `success` | `#0E7A47` | `#4CC78A` | 5.39:1 / 8.61:1 |
| `successContainer` | `#E3F4EA` | `#0F2A1E` | |
| `error` | `#C62828` | `#FF8A80` | 5.62:1 / 8.04:1 |
| `errorContainer` | `#FCE8E8` | `#3A1416` | |
| `warning` | `#9A5B00` | `#F0B44C` | 5.43:1 / 9.92:1 |
| `warningContainer` | `#FFF1D6` | `#33240A` | |
| `scrim` | black at 50% | black at 60% | Behind sheets and dialogs |

### Money colors
- Incoming amount: `success`, with a **+** sign. Outgoing amount: `text`, with a **−** sign. Outgoing money is never red, because spending is normal, not an error.
- Never rely on color alone. The sign, an arrow icon, and the label all carry the meaning.

## 2. Typography

**Family:** Plus Jakarta Sans (400, 500, 600, 700, 800), bundled in the APK. Fallback is the system sans-serif. Every number that can change uses **tabular figures** (`fontFeatureSettings = "tnum"`).

| Role | Size / line height (sp) | Weight | Use |
|---|---|---|---|
| `displayBalance` | 34 / 40, tracking −0.5% | 800 | Total balance, success amount |
| `headlineLarge` | 28 / 34 | 700 | Screen titles on large headers |
| `headlineMedium` | 24 / 30 | 700 | Screen titles |
| `titleLarge` | 20 / 26 | 600 | Section titles, sheet titles |
| `titleMedium` | 16 / 22 | 600 | List item titles, card titles |
| `bodyLarge` | 16 / 24 | 400 | Main reading text, inputs |
| `bodyMedium` | 14 / 20 | 400 | Secondary text, subtitles |
| `labelLarge` | 15 / 20 | 600 | Buttons |
| `labelMedium` | 12 / 16 | 600 | Chips, badges, nav labels |
| `eyebrow` | 11 / 16, tracking +8%, uppercase | 700 | Section eyebrows ("REKENING") |

Rules: nothing below 11sp. Body text 14sp minimum. Layouts must survive 200% font scale: wrap, don't truncate amounts or names. If something must truncate, show the full text on tap.

## 3. Space, size, shape

- **Spacing scale (dp):** 4, 8, 12, 16, 20, 24, 32, 40, 48. Screen gutter 20. Card padding 16. Gap between sections 24. Gap between list rows 0, separated by dividers.
- **Touch targets:** at least 48×48dp, with 8dp between targets.
- **Radius:** `xs` 6 (small badges), `sm` 10 (inputs, small chips), `md` 14 (cards, buttons), `lg` 20 (hero card, bottom sheet top corners), `full` (pills, avatars, status chips).
- **Elevation:**
  - Light theme: level 1 is a 1dp `outline` border plus shadow `0 1 2` at 6% `#0B1020`. Level 2 (floating quick actions, sheets) is shadow `0 8 24` at 12% `#002080`.
  - Dark theme: no shadows. Elevation comes from a lighter surface (`surfaceVariant`) and the `outline` border.
- **Icons:** Phosphor, Regular weight for UI and Fill weight only for the active bottom-nav item. Only the icons we use are imported as vector drawables, with no icon library dependency. Sizes: 16, 20, 24 (default), 32. No emoji anywhere.

## 4. Motion

| Token | Value | Use |
|---|---|---|
| `short` | 150ms | Press states, toggles |
| `medium` | 250ms | Screen transitions, sheets entering |
| `long` | 400ms | Success check drawing |
| `exit` | 70% of the matching enter | Exits are faster than entries |
| `easing.enter` | Emphasized decelerate | Things arriving |
| `easing.exit` | Emphasized accelerate | Things leaving |
| `press` | Spring, scale 0.97 | Tappable cards and buttons |
| `balance` | 600ms count-up | Only when a balance actually changes |

- **Haptics:** a CONFIRM pulse on successful money movement, REJECT on a wrong PIN (with a 300ms horizontal shake of the PIN dots), and nothing else.
- **Animation budget:** at most 1–2 animated elements per screen. Forward navigation slides in from the right, back slides out to the right, and predictive back is supported.
- **Reduced motion:** when the system animator scale is 0, balance count-up, shimmer and success drawing are replaced by instant state changes.

## 5. Components

| Component | Spec |
|---|---|
| **Button: primary** | Filled `primary`, `onPrimary` label, 52dp high, radius `md`, full width in flows. **One per screen.** While busy, a spinner replaces the label, the width stays the same, and the button can't be tapped again. |
| **Button: secondary** | Tonal: `primaryContainer` with `onPrimaryContainer` |
| **Button: text** | `primary` label, no fill |
| **Button: destructive** | `error` fill. Always behind a confirmation, and placed apart from primary actions. |
| **Disabled** | 38% opacity, not focusable as an action, announced as disabled |
| **Text field** | Label always above (never placeholder-only), 56dp, `outlineStrong` border, 2dp `primary` border when focused. Helper text below; error text below in `error` with an icon. Validates on blur. Uses the right keyboard type and autofill hints. Password fields have a show/hide toggle. |
| **Amount input** | Large `displayBalance` amount that formats as you type (Rp1.250.000), custom 3×4 keypad with 64dp keys, Quick Amount chips, and the available balance shown beneath. Each screen offers only Quick Amounts its product rule accepts (default 50k, 100k, 250k, 500k; Time Deposit 1M, 5M, 10M, 50M). Labels are whole thousands + `k` below Rp1,000,000 (`50k`) and whole millions + `M` from Rp1,000,000 (`1M`), never decimals. Chips are at least 48×48dp, wrap to a second centred row at large font sizes, and TalkBack reads the full amount (`Rp1,000,000`) and "selected". Errors such as insufficient balance or over the limit appear inline under the amount. |
| **PIN pad** | Six dots, the same keypad, a biometric key bottom-left and delete bottom-right. A wrong PIN shakes the dots, gives a REJECT haptic, and shows the remaining attempts. The screen blocks screenshots. |
| **List row** | 64–72dp: 40dp leading icon tile or avatar, title (`titleMedium`), subtitle (`bodyMedium`, `textSecondary`), trailing amount (tabular) and time |
| **Account card** | `surface`, radius `md`, account name, masked number with a copy action, balance |
| **Hero header** | `hero.gradient` + `hero.stripes`, greeting, Tier badge, balance with a hide toggle. Quick actions float over its bottom edge at level 2. |
| **Quick actions** | 4 items in a row: 40dp `primaryContainer` icon tile + `labelMedium`, each a 72dp touch target |
| **Bottom nav (Fase 1)** | Beranda, Riwayat, **QRIS** (center, raised `primary` button), Kartu, Profil. Labels always visible, active item uses the Fill icon + `primary`. Notifications sit behind a bell in the home top bar. In Fase 3, Profil moves to the home avatar and Wealth takes its slot. |
| **Tier badge** | Everyday: `primaryContainer` / `onPrimaryContainer`. Prestige: `giallo` / `nero`. Private: `nero` / `giallo`. Pill, `labelMedium`. |
| **Status chip** | Icon + text, never color alone: Berhasil (success), Diproses (warning), Gagal (error), Dibatalkan (`textSecondary` with strikethrough amount) |
| **Bottom sheet** | `surface`, top radius `lg`, drag handle, 50% scrim, swipe down or the close button to dismiss. Asks for confirmation if there is unsaved input. |
| **Dialog** | Only for destructive or irreversible confirmations. Title, one sentence, two actions. |
| **Snackbar** | Bottom, above the nav, 4s, one optional action (e.g. "Urungkan"). Never steals focus. |
| **Skeleton** | `surfaceVariant` blocks in the final layout shape, shimmer 1.2s, appears only after 300ms of waiting |
| **Empty state** | 48dp Phosphor icon in a `primaryContainer` circle, one-line title, one sentence, one action |
| **Error state** | What happened and what to do, plus a "Coba lagi" action. Offline uses a persistent banner with the last updated time. |
| **Success screen** | Hero gradient, animated check (`long`), amount in `displayBalance`, recipient, then "Bagikan bukti" (secondary) and "Selesai" (primary) |
| **Receipt image** | 1080px wide, hero gradient top band with the ORVYN logo, details on white, reference number, "Demo · ORVYN Bank adalah bank fiktif" footer |
| **Charts** (Fase 2+) | Bars for categories (no pie past 5 slices), line or area for trends, faint grid, direct value labels, a text summary for TalkBack |

## 6. Writing (applies to all copy, before the humanizer pass)

- The app and admin are **English only**. Address the customer as "you". Sentence case everywhere, no ALL CAPS except eyebrows.
- Buttons say exactly what happens: "Send Rp500,000", not "Continue" or "Submit".
- Errors give the cause and the fix: "You're Rp120,000 short. Top up or change the amount."
- Fees are always shown before the PIN, never after.
- **Formats:**
  - Money: `Rp1,250,000` (no decimals for Rupiah)
  - Dates: `14 Oct 2026, 19:27 WIB`
  - Account numbers in groups of 4.
- Use the terms from `CONTEXT.md` (Account, Pocket, Saved recipient, Freeze, …).

## 7. ORVYN Admin (web)

- Same tokens as CSS custom properties (`--primary`, `--surface`, …). Light by default, dark via `prefers-color-scheme`.
- Font: Plus Jakarta Sans, base 14px for dense data, tabular numbers in tables.
- **Layout:** 240px `nero` sidebar with the logo, content max 1280px, 24px gutters. Tables use 44px rows, sticky headers, sortable columns, and filters above the table.
- Destructive actions (Reversal, Reset demo) use the destructive button, a typed confirmation, and are never adjacent to primary actions.

## 8. Anti-patterns (do not do)

- Glassmorphism, neon, purple or pink gradients, decorative 3D
- Emoji as icons; mixing icon families or weights at one level
- Giallo as text on light backgrounds; yellow outside Prestige and key highlights
- Red for normal outgoing money
- More than one primary button per screen
- Hidden fees, or a fee shown only after the PIN
- Placeholder-only labels; errors only at the top of a form
- Raw hex values in components
- Animations that block input or that run longer than 500ms (except the balance count-up)

## 9. Pre-delivery checklist (per screen)

- [ ] Light and dark screenshots reviewed, plus one check on the Galaxy Note10+
- [ ] Contrast meets the table above; no new colors outside the tokens
- [ ] 200% font scale: nothing clipped or overlapping
- [ ] TalkBack: every control labelled, reading order matches the visual order
- [ ] Touch targets ≥ 48dp, pressed feedback within 100ms
- [ ] Loading, empty, error and offline states designed
- [ ] Reduced motion respected
- [ ] Copy in ID and EN, humanized, using `CONTEXT.md` labels
