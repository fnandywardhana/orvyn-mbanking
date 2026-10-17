# ORVYN Design System: Master

Single source of truth for the Android app (Jetpack Compose) and ORVYN Admin (web). Page-specific overrides live in `design-system/pages/<page>.md` and win over this file. Terms follow `CONTEXT.md`.

**Direction:** Minimal + Accessible, with one brand moment per screen. Nerazzurri blue and black from Inter Milan, vertical jersey stripes as the only graphic motif. Calm, confident, premium; never playful or decorative.

Last change: 0.9.0 quality pass (`docs/features/2026-10-16-quality-pass/design.md`): `textTertiary`, 15-role type scale, chips and segmented buttons, sticky action areas, transitions, snackbars, error states. QA round 1 (section 17): sign per account viewed, IME actions on forms, save-recipient label without a name.

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
| `surfaceVariant` | `#EDF1F8` | `#161D2E` | Grouped rows, skeletons, neutral status chips |
| `primary` | `#0032A0` | `#7FA2FF` | 10.69:1 on white; 7.45:1 on dark surface |
| `onPrimary` | `#FFFFFF` | `#001A5C` | 6.54:1 on dark primary |
| `primaryContainer` | `#E5ECFA` | `#0032A0` | Tonal buttons, icon tiles, info boxes |
| `onPrimaryContainer` | `#001A5C` | `#DCE5FF` | 13.6:1 (light), 8.5:1 (dark) |
| `text` | `#0B1020` | `#E8ECF6` | 17.5:1 / 15.5:1 |
| `textSecondary` | `#4D5872` | `#A3ADC2` | 7.11:1 / 8.15:1 |
| `textTertiary` | `#616D86` | `#7C879E` | Light: 4.81:1 on `background`, 4.59:1 on `surfaceVariant`, 5.20:1 on `surface`. Dark: 5.48 / 4.66 / 5.09. Hints, timestamps, the demo label. |
| `outline` | `#DCE2EE` | `#212A3E` | Dividers and card borders (decorative, below 3:1 by design) |
| `outlineStrong` | `#858FA3` | `#5D6884` | Input borders, unselected chips and segments. 3.01:1 on `background`, 3.25:1 on `surface` (light); 3.56 / 3.30 (dark) |
| `success` | `#0E7A47` | `#4CC78A` | 5.39:1 / 8.61:1 |
| `successContainer` | `#E3F4EA` | `#0F2A1E` | |
| `error` | `#C62828` | `#FF8A80` | 5.62:1 / 8.04:1 |
| `errorContainer` | `#FCE8E8` | `#3A1416` | |
| `warning` | `#9A5B00` | `#F0B44C` | 5.43:1 / 9.92:1; 4.86:1 / 8.11:1 on `warningContainer` |
| `warningContainer` | `#FFF1D6` | `#33240A` | Offline and refresh banners |
| `scrim` | black at 50% | black at 60% | Behind sheets and dialogs |
| `chart.barMuted` | `#7088C4` | `#55689C` | Non-selected bars in single-series charts. 3.50:1 on white, 3.36:1 on dark surface |

**Compose names.** Two names differ from Material 3: MASTER `outline` is `colorScheme.outlineVariant`, and MASTER `outlineStrong` is `colorScheme.outline`. `textSecondary`, `textTertiary`, `success*`, `warning*` and `chartBarMuted` live in `OrvynTheme.colors`.

**Placement rule.** Unselected chips need `surface` or `background` behind them. On `surfaceVariant` the `outlineStrong` border drops below 3:1.

### Money colors
- Incoming amount: `success`, with a **+** sign. Outgoing amount: `text`, with a **−** sign (U+2212). Outgoing money is never red, because spending is normal, not an error. A falling fund price is `text` with a down arrow, never red.
- Never rely on color alone. The sign, an arrow icon, and the label all carry the meaning.
- The sign follows the account you're looking at. A credit card bill payment is `+` in the card's list (it lowers what you owe) and `−` in savings History (money left savings). A transaction detail opened from a list shows the same side as the row you tapped, so the title, sign and color never change between the two.

## 2. Typography

**Family:** Plus Jakarta Sans (400, 500, 600, 700, 800), bundled in the APK. Every one of the 15 Material 3 roles is defined, so no text and no Material component (date picker, dialog, snackbar, segmented button) falls back to Roboto. Every role uses **tabular figures** (`fontFeatureSettings = "tnum"`).

| Material 3 role | Size / line height (sp) | Weight | Use |
|---|---|---|---|
| `displayLarge` | 44 / 52 | 800 | Defined only so nothing falls back |
| `displayMedium` | 40 / 48 | 800 | Defined only so nothing falls back |
| `displaySmall` (= `displayBalance`) | 34 / 40, tracking −0.5% | 800 | Total balance, amount entry, success amount, foreign amounts |
| `headlineLarge` | 28 / 34 | 700 | Screen titles on large headers |
| `headlineMedium` | 24 / 30 | 700 | Screen titles |
| `headlineSmall` | 22 / 28 | 700 | Pocket title, Vyna greeting |
| `titleLarge` | 20 / 26 | 600 | Section and sheet titles, PIN title at large font sizes |
| `titleMedium` | 16 / 22 | 600 | List item titles, card titles |
| `titleSmall` | 14 / 20 | 600 | Small card titles (pocket cards) |
| `bodyLarge` | 16 / 24 | 400 | Main reading text, inputs |
| `bodyMedium` | 14 / 20 | 400 | Secondary text, row subtitles and captions (asset %, tenor, NAV), helpers |
| `bodySmall` | 12 / 16 | 400 | **Only** captions under charts |
| `labelLarge` | 15 / 20 | 600 | Buttons |
| `labelMedium` | 12 / 16 | 600 | Chips, badges, nav labels |
| `labelSmall` (eyebrow) | 11 / 16, tracking +8%, uppercase | 700 | Section eyebrows ("ACCOUNTS") |

Rules:
- Nothing below 11sp. Body text 14sp minimum.
- Layouts survive 200% font scale on a 360×640dp screen: wrap, don't truncate amounts or names. If something must truncate, show the full text on tap.
- **Font-scale caps.** Only two things stop growing at 1.5×: keypad digits and bottom-nav labels. Both sit in fixed-width cells, and both keep their full TalkBack label. Nothing else is capped.
- **Amounts shrink before they wrap.** Large amounts shrink to fit one line, down to 24sp (scaled by the font size). If they still don't fit, they wrap only after a thousands comma (`Rp834,912,` / `398`), never inside a group. Size is measured from the final value so a count-up never resizes.
- **Money is never uppercased.** Eyebrows uppercase words only. A total next to an eyebrow goes on its own line.

## 3. Space, size, shape

- **Spacing scale (dp):** 4, 8, 12, 16, 20, 24, 32, 40, 48. Screen gutter 20. Card padding 16. Gap between sections 24. Gap between list rows 0, separated by dividers.
- **Touch targets:** at least 48×48dp, with 8dp between targets. Keypad keys are 64dp tall, 56dp on screens shorter than 700dp at font scale 1.3 or more.
- **Radius:** `xs` 6 (small badges), `sm` 10 (inputs, icon tiles, error boxes), `md` 14 (cards, buttons, snackbars, banners), `lg` 20 (hero card, bottom sheet top corners, dialogs), `full` (pills, chips, avatars, status chips).
- **Elevation:**
  - Light theme: level 1 is a 1dp `outline` border plus shadow `0 1 2` at 6% `#0B1020`. Level 2 (floating quick actions, sheets) is shadow `0 8 24` at 12% `#002080`.
  - Dark theme: no shadows. Elevation comes from a lighter surface (`surfaceVariant`) and the `outline` border.
- **Icons:** Phosphor, Regular weight for UI. Fill weight only for the active bottom-nav item and for an "on" state that the icon itself shows (the QRIS flashlight). Only the icons we use are imported as vector drawables, with no icon library dependency. Sizes: 16, 20, 24 (default), 32. No emoji anywhere.

### Screen patterns

- **Sticky action area.** On flow screens the main content scrolls and the primary action sits in a sticky area at the bottom, above the system navigation inset. The snackbar sits just above that area.
- **Screens with a keypad** (PIN entry, amount entry): the top part (title, explanation, dots or amount, Quick Amounts, messages) scrolls and may shrink; the keypad and the primary button never scroll. When a message appears, the top part scrolls to show it.
- **No text field on a screen with a keypad.** Notes and other text go on the next step (the review screen).
- **Success screen:** details scroll, the actions stick to the bottom (see §5).
- **Large font sizes (`fontScale >= 1.5`):** list rows stack the amount under the title, home quick actions become a grid with as many 112dp columns as fit, the home greeting gets its own line, and PIN screens hide the explanation line visually (it stays in the title's TalkBack label).

## 4. Motion

| Token | Value | Use |
|---|---|---|
| `short` | 150ms | Press states, toggles, chevrons, tab crossfade |
| `medium` | 250ms | Screen and step transitions, sheets entering |
| `long` | 400ms | Success check drawing |
| `exit` | 70% of the matching enter (175ms for screens) | Exits are faster than entries |
| `easing.enter` | Emphasized decelerate `cubic(0.05, 0.7, 0.1, 1.0)` | Things arriving |
| `easing.exit` | Emphasized accelerate `cubic(0.3, 0.0, 0.8, 0.15)` | Things leaving |
| `press` | Spring, scale 0.97 | Tappable cards and buttons |
| `balance` | 600ms count-up | Only when a balance actually changes |

- **Screen transitions:** forward, the new screen slides in from the right by a quarter of the width and fades in (250ms); the old one slides left a quarter and fades out (175ms). Back is the mirror image. Steps inside a flow (`AnimatedContent`) use the same rule by step order. Bottom-nav tabs crossfade in 150ms without sliding. The success screen fades in without sliding. No 700ms fades anywhere.
- **Predictive back** is on (`enableOnBackInvokedCallback`). System Back always goes back one step, except while a PIN is being processed.
- **Input guard:** a new screen or step ignores taps until its enter transition ends (250ms, 0 with animations off). Buttons, rows that open screens, and chips that move a flow forward fire once and ignore taps for 600ms or until the screen changes. Keypad keys never use the 600ms guard, so fast PIN typing works.
- **Haptics:** a CONFIRM pulse on successful money movement, REJECT on a rejected PIN (wrong, too weak, or not matching) with a 300ms horizontal shake of the PIN dots, and nothing else. Low balance, limits and offline never shake or buzz.
- **Animation budget:** at most 1–2 animated elements per screen.
- **Reduced motion:** when the system animator scale is 0, transitions, balance count-up, shimmer, PIN busy pulse, Vyna's thinking dots and success drawing are replaced by instant state changes. Banner carousels don't auto-advance.

## 5. Components

| Component | Spec |
|---|---|
| **Button: primary** | Filled `primary`, `onPrimary` label, `heightIn(min = 52dp)` (never a fixed height), radius `md`, full width in flows. Labels wrap to more lines at large font sizes. **One per screen.** While busy, a spinner replaces the label, the size stays the same, and the button can't be tapped again. |
| **Button: secondary** | Tonal: `primaryContainer` with `onPrimaryContainer` |
| **Button: text** | `primary` label, no fill, 48dp minimum |
| **Button: destructive** | Two forms. *Filled* (`error` fill, `onError` label): only the confirm button of a confirmation dialog. *Text* (`error` label with `ph_trash`): the entry point that opens that dialog, placed at least 24dp below the primary action. |
| **Disabled** | 38% opacity label, 12% fill, not tappable, announced as disabled. The label says why ("Enter an amount", "Minimum Rp10,000"). |
| **Text field** | Label always above (never placeholder-only), 56dp, `outlineStrong` border, 2dp `primary` border when focused. Helper text below; error text below in `error` with an icon. Validates on blur. Uses the right keyboard type and autofill hints. IME action: Next on every text field but the last, which moves focus to the next text field. Done on the last: it runs the primary action if nothing else is asked below and the button is enabled; if the button is disabled it only closes the keyboard; if choices (chips) follow, it closes the keyboard and scrolls the next question into view, without submitting. Enter on a hardware keyboard does the same. Password fields have a show/hide toggle. Focusing a field scrolls it, its message and the next button above the keyboard. |
| **Amount input** | Large `displayBalance` amount that formats as you type (`Rp1,250,000`) and shrinks to fit (§2), custom 3×4 keypad, Quick Amount chips, and the available balance beneath. The keypad and primary button are sticky (§3). Each screen offers only Quick Amounts its product rule accepts (default 50k, 100k, 250k, 500k; Time Deposit 1M, 5M, 10M, 50M). Labels are whole thousands + `k` below Rp1,000,000 (`50k`) and whole millions + `M` from Rp1,000,000 (`1M`), never decimals. Chips are at least 48×48dp, wrap to a second centred row at large font sizes, and TalkBack reads the full amount (`Rp1,000,000`) and "selected". Errors such as insufficient balance or over the limit appear inline under the amount. If the balance or limit can't load, the button says "Balance didn't load" and a Try again sits under the amount. Gram entry (gold) swaps the `000` key for `.` and has no Quick Amounts. |
| **PIN pad** | Six 16dp dots, the keypad (biometric key bottom-left, delete bottom-right), and a status slot under the dots that wraps and never clips. **Busy** after the sixth digit: dots stay filled and pulse (still with reduced motion), the keypad, biometric key and back arrow go to 38% and stop accepting input, and the slot shows what's happening ("Checking your PIN…", "Sending Rp500,000…", "Paying Rp25,000…"), announced once. A rejected PIN shakes the dots, gives a REJECT haptic, clears the dots and shows the reason ("Wrong PIN. 2 tries left."). Any other error closes the PIN pad and shows the message on the review screen. The window is `FLAG_SECURE` while the pad is shown. At large font sizes the title is one line and the explanation is read by TalkBack only (§3). |
| **List row** | 64dp minimum, grows with content: 40dp leading icon tile or avatar, title (`titleMedium`, wraps by word), subtitle (`bodyMedium`, `textSecondary`), trailing amount (tabular, at most 45% of the row). At font scale 1.5 or more the amount moves to its own line under the subtitle. One TalkBack node per row. |
| **Switch row** | The whole row is one `toggleable` with `Role.Switch`; the inner switch has no semantics of its own. TalkBack: "Freeze card, switch, on". When the change needs the server, the switch moves at once, a 16dp spinner shows, the row is disabled, and the switch snaps back with a message if the server says no. Confirmation snackbars only appear after the server says yes. |
| **Account card** | `surface`, radius `md`, account name, masked number with a copy action, balance. The whole card opens the account detail screen. |
| **Hero header** | `hero.gradient` + `hero.stripes`, greeting, Tier badge, balance with a hide toggle. Quick actions float over its bottom edge at level 2. While loading, the balance is a 200×40dp skeleton block (white 16%), never "Rp—". |
| **Quick actions** | 4–5 items in a row: 40dp `primaryContainer` icon tile + `labelMedium`, each a 72dp touch target. At large font sizes: a grid (§3). Offline: 38% opacity, still tappable, and a tap explains with a snackbar. |
| **Bottom nav (Fase 1)** | Home, History, **QRIS** (center, raised `primary` button with its label underneath in the same column), Cards, Profile. `heightIn(min = 64dp)`, labels always visible (capped at 1.5×), active item uses the Fill icon + `primary`. Notifications sit behind a bell in the home top bar. |
| **Tier badge** | Everyday: `primaryContainer` / `onPrimaryContainer`. Prestige: `giallo` / `nero`. Private: `nero` / `giallo`. Pill, `labelMedium`. |
| **Choice chip** | Pill, 48dp minimum height and width. Unselected: `surface` fill, 1dp `outlineStrong` border, `text` label. Selected: `primary` fill, `onPrimary` label, and a 16dp `ph_check` before the label (so selection is never color alone). Same in both themes. Wraps in a FlowRow, never scrolls sideways. TalkBack: radio or checkbox role with "selected". |
| **Filter chip** | Same as the choice chip. Chips that open a sheet end with a 16dp `ph_caret_down`. An active filter shows as selected with its value as the label ("Last month", "2 types"). A "Clear filters" text button follows when any filter is on. |
| **Segmented button** | For exactly two mutually exclusive options ("Send now"/"Schedule", "Buy"/"Sell", "Renew"/"Pay out", "Rupiah"/"Grams"). Material 3 `SingleChoiceSegmentedButtonRow`, full width, 48dp minimum, same colors and check icon as the choice chip. |
| **Status chip** | Icon + text, never color alone. Transactions: Successful (success), Processing (warning), Failed (error), Reversed (`textSecondary` on `surfaceVariant`, struck-through amount). Money requests: Waiting (`textSecondary` on `surfaceVariant`, clock), To pay (warning, hand with coins), Paid (success), Declined, Cancelled, Expired (`textSecondary` on `surfaceVariant`). |
| **Bottom sheet** | `surface`, top radius `lg`, drag handle, 50% scrim, swipe down or the close button to dismiss. Asks for confirmation if there is unsaved input. Errors inside a sheet use the error box, never a snackbar (the scrim hides it). |
| **Dialog** | Only for destructive or irreversible confirmations. Title, one sentence, two actions. The confirm button is filled destructive; the dismiss button names what stays ("Cancel", "Keep request"). While the server works the confirm button shows a spinner and the dialog stays open. |
| **Snackbar** | Material 3 snackbar on `inverseSurface`, radius `md`. Bottom, just above the sticky action area or the nav; it never covers the primary button. Errors start with a 20dp `ph_warning_circle`. 4s without an action, 10s with one ("Try again"); with TalkBack on, snackbars with an action stay until dismissed. One at a time. Announced politely, never steals focus. |
| **Error box** | For failures that don't belong to one field: `errorContainer`, radius `sm`, 12dp padding, 20dp `ph_warning_circle` in `error`, `bodyMedium` text in `text`. Sits right above the primary button of the screen or sheet. The content scrolls to show it. Announced once. |
| **Status banner** | Offline or "couldn't refresh" while cached data shows: `warningContainer`, radius `md`, 20dp icon (`ph_wifi_slash` or `ph_warning_circle`) in `warning`, first line `bodyMedium` 600 in `text`, second line in `textSecondary`. Says how old the data is ("from Yesterday, 21:40"). The "couldn't refresh" version has a Try again text button. On home it sits right under the quick actions. |
| **Skeleton** | `surfaceVariant` blocks in the final layout shape, pulse 1.2s, appears only after 300ms of waiting. The blocks have no semantics; the screen announces "Loading" once. |
| **Empty state** | 48dp Phosphor icon in a `primaryContainer` circle, one-line title, one sentence, one action when there is something to do |
| **Error state** | Icon in an `errorContainer` circle, a title that says what failed, the server message (offline: "You're offline. Check your connection and try again."; nothing: "Couldn't load this. Check your connection and try again."), and a secondary "Try again" button that reloads in place. Every loading screen has one. Loading that passes 15 seconds becomes this error. Inside a card or a list end, the same message is one inline row with a Try again text button. |
| **Success screen** | Hero gradient, animated check (`long`), amount in `displayBalance` (shrinks to fit), recipient, then a white details card whose first line is always "Date" (`16 Oct 2026, 19:27 WIB`, from the server). The details scroll; the actions stick to the bottom above the navigation inset: optional tertiary text action ("Save as a recipient", never with a name: the subline above already shows the full name, and a first word can be "PT" or a title), "Share receipt" (secondary, 1dp white border at 56%, only when money went to someone else), and "Done" (white primary). Back equals Done. |
| **Receipt image** | 1080px wide, hero gradient top band with the ORVYN logo, details on white, reference number, "Demo · ORVYN Bank is a fictional bank" footer. Long names wrap to 2 lines, then ellipsis. Shared from the cache, never saved to the gallery. |
| **Charts** (Fase 2+) | Bars for categories (no pie past 5 slices), line for trends, faint grid, direct value labels, a text summary for TalkBack. Single-series highlight: selected bar `primary`, others `chart.barMuted`. Segments of composition bars are at least 6dp wide with 2dp `surface` gaps. Run `validate_palette.js` (dataviz skill) on any categorical palette. |

## 6. Writing (applies to all copy, before the humanizer pass)

- The app and admin are **English only**. Address the customer as "you". Sentence case everywhere, no ALL CAPS except eyebrows.
- Buttons say exactly what happens: "Send Rp500,000", "Buy USD 100.00", not "Continue", "Review" or "Submit" when an action and amount are known.
- Errors give the cause and the fix: "You're Rp120,000 short. Top up or change the amount."
- Failures are never silent. Every failed action shows a message: in the field, the sheet, the error box or a snackbar.
- Attempts left: "Wrong PIN. 2 tries left." / "Wrong PIN. 1 try left." Use the same pattern for current PIN and password checks.
- Fees are always shown before the PIN, never after.
- **Formats:**
  - Money: `Rp1,250,000` (no decimals for Rupiah). Negative numbers and percentages use the true minus sign (U+2212).
  - Dates: `14 Oct 2026, 19:27 WIB`. Bill periods: `Oct 2026`. Relative days: `Today`, `Yesterday`, `Mon, 12 Oct`.
  - Account numbers in groups of 4.
  - Exchange rates below Rp1,000 per unit are shown per 100 units with 2 decimals: `100 JPY = Rp10,802.50`.
- Use the terms from `CONTEXT.md` (Account, Pocket, Saved recipient, Freeze, …).

## 7. ORVYN Admin (web)

- Same tokens as CSS custom properties (`--primary`, `--surface`, …). Light by default, dark via `prefers-color-scheme`.
- Font: Plus Jakarta Sans, base 14px for dense data, tabular numbers in tables.
- **Layout:** 240px `nero` sidebar with the logo, content max 1280px, 24px gutters. Tables use 44px rows, sticky headers, sortable columns, and filters above the table.
- Destructive actions (Reversal, Reset demo) use the destructive button, a typed confirmation, and are never adjacent to primary actions.
- Messages: `ok-box` for success, `err-box` (`--error` on `--errorContainer` with a warning icon) for errors. Secrets (starting passwords) are shown once from the server session, never through a URL or a log.
- Status in lists is a chip with a word ("Live", "Scheduled", "Ended"), never color alone.

## 8. Anti-patterns (do not do)

- Glassmorphism, neon, purple or pink gradients, decorative 3D
- Emoji as icons; mixing icon families or weights at one level
- Giallo as text on light backgrounds; yellow outside Prestige and key highlights
- Red for normal outgoing money or a falling price
- More than one primary button per screen
- Hidden fees, or a fee shown only after the PIN
- Placeholder-only labels; errors only at the top of a form
- Raw hex values in components
- Animations that block input or that run longer than 500ms (except the balance count-up)
- A text field on a screen with a keypad
- Silent failures: a skeleton that never ends, a button that never enables, an action that fails without a message
- Shaking the PIN dots or a REJECT buzz for anything other than a rejected PIN
- Fixed heights on anything that holds text; `maxLines = 1` on names and amounts

## 9. Pre-delivery checklist (per screen)

- [ ] Light and dark screenshots reviewed, plus one check on the Galaxy Note10+
- [ ] Contrast meets the table above; no new colors outside the tokens
- [ ] 200% font scale at 360×640dp and 411dp: nothing clipped or overlapping, keypad and primary button reachable
- [ ] TalkBack: every control labelled, toggles and chips announce their state, reading order matches the visual order
- [ ] Touch targets ≥ 48dp, pressed feedback within 100ms, double taps fire once
- [ ] Loading, empty, error (with Try again) and offline states designed; every failed action shows a message
- [ ] Reduced motion respected
- [ ] Copy in English, humanized, using `CONTEXT.md` labels
