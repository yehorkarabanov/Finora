# Finora "Obsidian" redesign — design

Date: 2026-11-11 · Branch: `denys/feat/ui-refurbished`
Visual source: `docs/stitch_finora_personal_finance_app/dd_*` (`dd_home`, `dd_analytics`, `dd_add_money`, `dd_card_editor`) and their `DESIGN.md` ("Obsidian Wealth").
Mockups agreed during brainstorming: `.superpowers/brainstorm/935-1794350235/content/*.html` (open in a browser; file names are cited per section below).

## 1. Intent

Give Finora the full look *and feel* of the `dd_*` screens — obsidian surfaces, electric‑mint accent, Space Grotesk numbers, Manrope text, pill controls, tonal (shadow‑free) layering, springy motion — across the **whole** app.

The `dd_*` screens describe a banking app, not Finora. They are a **style guide, not a feature list**. Finora keeps its product logic (multi‑currency accounts, hierarchical categories, tags, recurring, statistics, backup/CSV). Layouts may be reworked where that makes Finora easier to use.

## 2. Goals, non‑goals, anti‑goals

**Goals**
- Every screen uses the Obsidian visual language and the shared components in §4.
- The navigation shell, Home, Add/Edit transaction, Transactions, Accounts, Statistics, More and the Organize/App screens follow the decisions in §5.
- Motion is part of the design (§6), not an afterthought.
- Light theme stays as an equal option, built from the same tokens (§3.1).

**Non‑goals**
- No new domain features, no changes to financial rules, DB schema or ViewModel logic beyond what a screen needs to feed its new layout (e.g. a new derived series for a chart).
- No banking features from the mockups: cards, Send, Exchange, slide‑to‑top‑up, yield optimizer, avatar/profile, notifications, "VAULT PRO".
- No search on Home.

**Anti‑goals (it fails even if it "works")**
- Copying banking content or wording into Finora.
- Changing how something behaves just to match a picture (e.g. dropping a filter, a tab, or a save path that exists today).
- A total‑wealth hero on Home. (The user removed it explicitly; Home leads with a chart.)
- Cluttered screens: the user rejected several drafts as "messy". Prefer one grouped card with dividers over many separate cards; prefer fewer, larger touch targets.

## 3. Foundations

Already started on this branch (uncommitted): `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `res/font/{manrope,space_grotesk}.ttf`, `FinanceColors.kt`. Finish and use them; don't restart.

### 3.1 Color
- Dark scheme = `DESIGN.md` tokens verbatim (background/surface `#121314`, surfaceContainer Low `#1B1C1D`, base `#1F2021`, High `#292A2B`, Highest `#343536`, primary `#6DFFBA`, primaryContainer `#00E599`, …). Mint `#00E599` is the accent for primary CTAs, selected states, positive numbers and chart lines.
- Negative / below‑zero = a soft coral red (≈ `#FF7A6B` dark). Exact light/dark values are the builder's call, ≥ 4.5:1 on their surface.
- Light scheme: tonal counterpart of the same tokens (already drafted in `Color.kt`: primary `#006C46`, near‑white surfaces). Must look intentional, not inverted — mint used as fills/containers, deep green for text on light.
- Hairline borders: 1px white at 6–8% (dark) / black at 6–8% (light). No drop shadows except the soft mint glow on the primary CTA/FAB and active chart point.
- Category/account colors keep using the existing 28‑color palette; tinted circles are `color @ ~14% alpha` behind an icon in `color`.

### 3.2 Type
Space Grotesk for amounts, headings, keypad digits (tabular numerals). Manrope for everything else. Scale from `DESIGN.md` (already mapped in `Type.kt`). Big money: integer part full weight, decimals in `onSurfaceVariant` at ~60% size (see `dd_analytics` "$2,840.50").

### 3.3 Shape & spacing
Pills (`999dp`) for every button, chip, segmented control, nav bar and FAB. Cards 22–24dp. Bottom sheets 28dp top corners. Screen margin 18–20dp. Grouped lists: one card, rows separated by 1px dividers, ~12dp vertical row padding.

### 3.4 Icons
Line icons, 1.8 stroke, rounded caps, consistent set (no emoji anywhere). Inside 36–40dp round tinted containers on rows; bare in the nav bar.

### 3.5 Docs
Rewrite `docs/ui-design-system.md` to describe this system (it currently describes the indigo scheme and "minimal motion"). Keep its useful rules: native amounts keep their currency, converted values are marked `≈`, every summary number is tappable.

## 4. Shared components

| Component | Spec | Mockup |
|---|---|---|
| **Floating nav bar** | Detached pill (56dp tall, 18dp side margins, 14dp from bottom, translucent `rgba(30,32,36,.95)` + blur where supported). 4 tabs: Home, Transactions, Accounts, Statistics. Inactive = icon only; active = mint pill with icon + label. Content scrolls under it with a bottom fade. | `home-v2.html` (A), `footer-plus.html` |
| **Add FAB** | Floats **above** the nav bar, right‑aligned. At list top it's an extended pill "+ Add"; scrolling down collapses it to a 52dp circle; a fast downward fling hides it, scrolling up brings it back. Tap: icon rotates to ✕, scrim dims, a speed‑dial fans out (Expense / Income / Transfer) with staggered spring; each opens the editor preset to that type. Shown on **Transactions** and **Accounts** (on Accounts the FAB adds an *account*, no speed‑dial). Not on Home (Home has quick actions) or Statistics. | `fab-above.html` |
| **Top bar** | Large screen title in Space Grotesk at left; round 38dp icon buttons at right. Home: "FINORA" wordmark + **⋯** (opens More). No search on Home. | `home-v2.html` |
| **Segmented control** | Pill track `surfaceContainerLow`, selected segment `surfaceContainerHighest` + white text. | everywhere |
| **Grouped list card** | One rounded card; rows = round tinted icon, title + subtitle, right‑aligned amount (+ optional % badge/sub‑line). | `transactions.html`, `accounts-list-v5.html` |
| **Bottom sheet** | 28dp top radius, grab handle, scrim; content in grouped cards; primary CTA full‑width mint pill at bottom. | `add-tx-sheet-v2.html` |
| **Wave chart** | Smooth cubic curve, 3dp mint stroke with soft glow, gradient fill to transparent. Optional **zero line** (dashed): segment/fill above zero mint, below zero red. Tap/drag scrubs: vertical dashed guide + white dot with colored ring + value bubble. Optional dashed grey "previous period" curve behind. Draws in left→right on first show / data change. | `home-wave-zero.html`, `statistics.html` |
| **Split bar** | Thin (8–10dp) stacked rounded segments, 3dp gaps, colored by item; replaces donuts. | `statistics.html`, `accounts-list-v5.html` |
| **Range wheels** | Bottom sheet "Choose range": FROM (month + year wheels) → TO (month + year wheels) sharing one highlight band; FROM selection white, TO selection mint; edges fade; snap + haptic tick per step; month‑count chip top‑right; future months faded; invalid (end < start / future) turns chip red and disables CTA. CTA text = "Show Nov 2025 – Feb 2026". Also offers a "Custom days" path into the existing day range picker. | `range-wheels-v2.html` |
| **Numeric keypad** | 3×4 borderless grid, Space Grotesk 26sp, circular press wash `#25282E`, `.` bottom‑left, backspace bottom‑right. | `add-tx-sheet-v2.html` |

## 5. Screens

### 5.1 Home (was Dashboard) — `home-scroll.html`, `home-wave-zero.html`
Top to bottom:
1. Top bar: FINORA + ⋯ (More).
2. **Days | Months** segmented control + period chip ("November 2026 ▾") which opens the Range wheels.
3. Hero: label ("NET IN NOVEMBER"), big **net** amount (mint if ≥ 0, red if < 0), small pill "In $X · Out $Y".
4. **Net wave card** with zero line:
   - *Days*: running cumulative net through the month (starts at 0, dips on spending, jumps on income). Future days not drawn; a "today" marker on the last point.
   - *Months*: each month's net in the selected year.
   - **Horizontally swipeable**: snap paging to previous periods (month in Days, year in Months); hero/label/chip update on snap; page dots; can't page past today.
   - Scrub to inspect a point (value bubble).
   - Currency follows the existing `resolveScope` rule unchanged: if all selected accounts share one currency, show exact native amounts; otherwise convert to the display currency and mark `≈`. Keep the existing account selector (multi‑account filter) — placement is the builder's call (e.g. inside the period row or as a chip).
   - *(Designer's call)* Home gets **its own period state**, no longer shared with Statistics. Days = one calendar month, Months = one calendar year. If the Range wheels pick a multi‑month range: in Days the running net spans the whole range (x‑axis = days of the range), in Months each month of the range is a point; hero label becomes "NET · NOV 2025 – FEB 2026"; swipe paging steps back by the range's length.
5. Quick actions: four round buttons — **Expense** (mint, primary), Income, Transfer, Recurring.
6. Upcoming recurring (if any active schedules) and Recent transactions, as grouped list cards. The current Accounts carousel and Distribution section are **removed from Home** (Accounts has its own tab; distribution lives in Statistics).
7. Empty state (no accounts) keeps today's "create first account" flow, restyled.

### 5.2 Add / Edit transaction — `add-tx-sheet-v2.html`
- Screen: close (✕) at left; **Expense | Income | Transfer** segmented at top; huge amount (Space Grotesk ~56sp, decimals muted); account pill under it (icon, name, balance, ⇅ → account picker); keypad; **Next** mint CTA.
- **Next** slides up a **details shelf** (bottom sheet) over the keypad while the amount + account shrink slightly and stay visible above it. Tapping the scrim or handle drops it back to edit the amount without losing input.
- Shelf (one grouped card): **Account**, **Category** (tinted icon), **Date** (today, time), **Note** (optional), **Tags** (chips). Then a toggle "Add another after saving" (remembered) and the CTA "Save expense / Save income".
- **Transfer**: shelf rows are **From**, **To**, Date, Note, Tags; when currencies differ, a **"They receive"** amount row appears (existing transfer conversion rules).
- Edit mode opens the same screen; ⋯ menu keeps Duplicate / Delete. Existing validation messages, recent‑category shortcuts, "created by schedule" banner and dialogs (currency change, opening‑order, delete) keep working — restyle only.

### 5.3 Transactions — `transactions.html`
Same features as today (search, filters: type / date / categories / tags / amount; sort; In/Out/Net summary; day grouping; CSV export in ⋯). Layout: title + sort + ⋯; pill search field; horizontally scrolling filter pills (active = mint tint with ✕); 3‑column In/Out/Net summary card; day headers ("TODAY · NOV 10" + that day's net at right); each day is one grouped list card. Row: tinted category icon, name, "Category · Account" (+ tag chips), amount + time right. Transfer rows show "A → B" and the converted pair. Swipe‑to‑delete with Undo is a nice‑to‑have, only if it's cheap.

### 5.4 Accounts — `accounts-list-v5.html`, `accounts-archived.html`, `accounts.html` (detail)
- **List**: title + Transfer button. Header (no card background): "TOTAL · <display currency>" with the converted total (≈ semantics per existing rules), "N accounts" at right, and a split bar of each account's share in its own color. **No per‑currency chips, no "this month" badge.**
- Active accounts in **one grouped card**: icon tile (account color), name, "Type · CCY", small sparkline of balance trend in account color, balance + this‑month change (mint/red).
- **Archived** at the very bottom: a quiet row (archive icon, "Archived · N accounts · hidden from totals", chevron). Tap expands with animation into a greyed grouped card; each row has a mint **Restore** chip; tapping a row opens detail.
- FAB: "+ Account".
- **Detail**: large card in the account's color gradient (type icon, "TYPE · CCY", balance, sparkline wave, opening balance, change this month); round actions **Expense / Income / Transfer / Stats**; Recent list; Edit / Archive / Delete in ⋯.

### 5.5 Statistics — `statistics.html`, `stats-tags.html`, `stats-hierarchy.html`
- Text tabs with mint underline: **Expenses | Income | Tags | Accounts** (existing `StatsTab`).
- **Period block** (same on every tab): full‑width **Week | Month | Year** segmented; below it a stepper row `‹  November 2026 ▾  ›` (name opens Range wheels; `›` disabled when the next period is in the future). ☰ in the top bar holds account filter + compare toggle.
- Hero: "SPENT IN NOVEMBER", big total, comparison line ("↘ 8% less than October").
- Trend (**user decision: replaces** today's fixed 12‑month / 5‑year trend and its Monthly/Yearly chips): a wave of the **selected period only** — daily buckets for Week/Month, monthly buckets for Year — with avg/day (or /month); previous period as dashed grey curve when compare is on; tap a point → value bubble; tap‑through opens that bucket's transactions (existing behavior). Needs new bucket plans and a previous‑period series aligned to the current one; `trendYearly` goes.
- **Gestures on the trend chart (user request)**:
  - **Pan** with one finger to slide the window back/forward in time (can't go past today). The chart follows the finger continuously, not page‑by‑page.
  - **Pinch** in/out to show fewer/more days. Granularity switches automatically: daily buckets up to ~3 months visible, monthly beyond that (cross‑fade between the two).
  - On gesture end the visible window **becomes the selected period** for the whole tab: hero total, comparison, categories, by‑account/by‑tag all recompute. The period block reflects it — if the window matches a calendar week/month/year, that segment is selected and the stepper shows its name; otherwise no segment is selected and the stepper shows the custom range ("Oct 3 – Nov 18"). Recompute after the gesture settles (debounced), not on every frame.
  - Scrub (tap/hold on a point) still shows the value bubble; a long‑press‑and‑drag scrubs while a plain drag pans.
  - *(Designer's call)* Home's chart keeps the approved snap paging, without pinch, so Home stays simple.
- **Categories, 3 levels with drill‑down**: split bar + grouped rows (tinted icon, name, "N subcategories ›" or "N transactions", amount, % badge in category color). Tapping a parent replaces the list with its children (list slides, split bar re‑forms). A **"Directly in <parent>"** row (dashed icon, muted) shows money assigned to the parent itself. Breadcrumb trail ("All › Food & Dining › Restaurants") jumps back; system back pops a level. Leaf rows open filtered Transactions.
- *(Designer's call)* **Existing sections stay**: on Expenses/Income, the current **By account** and **By tag** sections follow Categories as grouped list cards (with split bars). `PeriodKind.TODAY` and `CUSTOM` stay reachable from the sheet behind the period name (quick options "Today" and "Custom days" above the wheels). The Tags tab keeps its **filter sheet** (types, accounts, categories, dates) behind ☰. The new tag ranking is new derived data (per‑tag totals for the period).
- **Tags tab**: opens on a **ranking of tags** for the period (Spent/Earned switch; rows with # tile, amount, relative bar, transaction count). Tap a tag → tag detail; long‑press to multi‑select and combine (Any / All). Tag detail: picked tags as mint chips with ✕ in a horizontal row; In/Out/Net figures + count + comparison; "Over time" wave with Spent / Earned / Moved switch; categories (same drill‑down); By account; Recent with "All N ›" → Transactions pre‑filtered. Returning to the tab restores the last tag view; ✕ on all chips returns to the ranking.
- **Accounts tab**: same visual language (grouped rows per account with period in/out/net); exact layout is the builder's call.

### 5.6 More — `more-and-lists.html`
Opened by ⋯ on Home as a **bottom sheet** (not a screen/tab): "ORGANIZE" three round tiles (Categories, Tags, Recurring); "APP" grouped rows (Data — backup, restore, CSV; Settings — theme, language, currency). The `MoreScreen` route can go; deep links into its children stay.

### 5.7 Organize & App screens (restyle, no separate mockups)
- **Categories**: Expense | Income | Transfer segmented; one tree card; children indented with a thin connector line; expand/collapse per level; tap to edit; long‑press to move (existing move rules, max depth 3).
- **Recurring**: "Next 30 days" card (out / in), "UPCOMING" grouped list (icon, name, "Monthly · in 3 days", amount + date), "PAUSED" grouped list faded with a toggle (existing `ScheduleStatus.PAUSED`).
- **Tags, Settings, Data, CSV export, all edit forms (account, category, recurring), pickers and dialogs**: apply §3–§4 — grouped cards, round icons, pill controls, bottom sheets for choices, mint full‑width CTA. Settings keeps Theme (System/Light/Dark), Language, Display currency, About.

## 6. Motion

Motion is wanted ("the whole feelings, animations"). Replaces the old "minimal motion" rule.
- **Press**: springy scale (~0.96) on buttons, chips, rows, keypad keys (keypad also shows the circular wash).
- **Sheets**: slide up with a decelerating curve (~350ms) + scrim fade; drag‑to‑dismiss.
- **FAB**: extend/collapse on scroll, hide/show on fling, rotate + speed‑dial stagger (spring with slight overshoot).
- **Charts**: wave draws in left→right (~500ms) on first show and on data change; paging between periods slides; scrub bubble follows the finger.
- **Numbers**: big hero amounts animate (count/roll) when the value changes.
- **Lists**: drill‑down slides in from the right / back to the left; archived section expands with height animation; chevrons rotate with a spring.
- **Tabs**: nav bar active pill slides/morphs between tabs.
- **Haptics**: light tick on wheel steps, FAB open, save success, period snap.
- Respect the system "remove animations" setting (fall back to instant/crossfade).

## 7. Constraints
- Android, Jetpack Compose + Material 3, existing Hilt/ViewModel architecture. Restyle by changing composables and theme; keep ViewModels' public behavior.
- Fully offline; fonts are bundled (already in `res/font`).
- Localization: every new string in `strings.xml` (English + Russian exist today).
- Accessibility: touch targets ≥ 48dp, text contrast ≥ 4.5:1 in both themes, content descriptions on icon‑only buttons (nav bar inactive tabs, round buttons).
- Previews: each restyled screen has light and dark previews.

## 8. Left to the builder
- Exact light‑theme values, red value, glow intensities, durations/springs within the ranges above.
- Chart implementation (Canvas in Compose is fine; no new chart library unless clearly cheaper).
- Where the Home account filter lives.
- Statistics → Accounts tab layout.
- Whether swipe‑to‑delete on Transactions ships.
- Order of implementation; suggested: foundations → shared components → shell (nav + FAB + More sheet) → Add/Edit → Home → Transactions → Accounts → Statistics → remaining screens → motion polish → docs.
