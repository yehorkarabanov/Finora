# Finora UI design system

The reference for every screen. It is taken from the two screens that were redesigned first:
**Dashboard** (`ui/analytics/DashboardScreen.kt`) and **New/Edit transaction**
(`ui/operations/TransactionEditScreen.kt`). Visual inspiration: `docs/stitch_finora_personal_finance_app/`
(take the layout ideas only, not its content: the Stitch dashboard's total-balance hero is deliberately excluded, see §8).

When a screen is touched, bring it in line with this document. Don't redesign screens that weren't asked for.

---

## 1. Principles

1. **Fast entry first.** Adding an expense is the main job. Primary actions sit at the top (quick actions) or in a bottom bar (CTA), never behind a menu.
2. **Privacy by default.** Don't put aggregate wealth on screens the user opens to add expenses (§8).
3. **Cards on a plain background.** Content lives in rounded `Surface`s on `background`. No elevation or shadows; hierarchy comes from surface tone.
4. **Money is always readable and honest.** Native amounts keep their currency; converted values are marked as estimates (PRD 10.6 / NFR-14). Income/expense colors come from the theme, never hard-coded.
5. **One tap away.** Every summary number is tappable and opens the filtered transaction list or the detail behind it.
6. **Light and dark are equal.** Every new composable gets checked in a dark preview.
7. **Minimal motion.** Only functional animation: chart reveal (500 ms, once), caret blink, standard M3 transitions.

---

## 2. Color

### Theme
- `FinoraTheme(themeMode, dynamicColor = false)` (`ui/theme/Theme.kt`) uses the **fixed indigo brand scheme** from `ui/theme/Color.kt` (`FinoraLightColorScheme` / `FinoraDarkColorScheme`, every M3 role set explicitly, seed `#6B5CE7`). `dynamicColor = true` switches to wallpaper colors; it is opt-in, off by default and has no settings UI. `LocalFinanceColors` is always provided.
- `PreviewTheme(dark)` (`ui/theme/PreviewTheme.kt`) uses exactly the same two schemes. Dark previews sit on `#0F1412`.
- Dark follows Stitch (`DESIGN.md`); light is the tonal counterpart. All `on*` roles are >= 4.5:1 on their pair.

| Role | Light | Dark |
|---|---|---|
| `primary` / `onPrimary` | `#5847D0` / `#FFFFFF` | `#C8BFFF` / `#2E138E` |
| `primaryContainer` / `onPrimaryContainer` | `#E3DEFF` / `#190064` | `#7565D7` / `#FFFEFF` |
| `secondaryContainer` | `#E6E0F7` | `#463795` |
| `tertiary` (mint) | `#006C4F` | `#5CDDAB` |
| `error` | `#BA1A1A` | `#FFB4AB` |
| `background` = `surface` | `#FCF8FF` | `#0F1412` |
| `surfaceContainer` Lowest / Low / base / High / Highest | `#FFFFFF` / `#F6F2FD` / `#F0ECF7` / `#EAE6F1` / `#E4E0EB` | `#0A0F0C` / `#181D1A` / `#1C211E` / `#262B28` / `#313632` |
| `onSurface` / `onSurfaceVariant` | `#1B1B21` / `#47454F` | `#DFE4DE` / `#C9C4D5` |

### Finance colors: `MaterialTheme.finance` (`ui/theme/FinanceColors.kt`)
| Token | Light | Dark | Use |
|---|---|---|---|
| `income` | `#006C4F` | `#5CDDAB` | positive amounts, income icons, net > 0, income bar |
| `incomeContainer` | `#CDEFE0` | `#123B2D` | income tile/icon background |
| `expense` | `#BA1A1A` | `#FFB4AB` | expense amounts/icons, net < 0, expense bar |
| `expenseContainer` | `#FFDAD6` | `#4A1E1C` | expense tile/icon background |

- **Never** write `Color(0xFF006C4F)` or use `colorScheme.error`/`tertiary` to mean income or expense. Use `MaterialTheme.finance.*`.
- `colorScheme.error` is for **errors** only (validation, outdated rates, overflow). A negative account balance still uses `error` (current `BalanceText` behavior).
- Transfer uses `primary` / `primaryContainer`.

### User colors (categories, accounts, tags)
- The palette is `ColorPalette.colors` (`ui/common/ColorPalette.kt`): 28 colors ordered by hue (reds, oranges/yellows, greens, teals/cyans, blues, indigos/violets, purples/pinks, browns, neutrals), each >= 3:1 against a white check mark. Defaults: account blue `#2563EB`, expense red `#DC2626`, income green `#16A34A`, transfer slate `#64748B`.
- Seeded categories (`DefaultCategories`) use palette literals; keep them in sync. A null stored color falls back to the default; an out-of-palette value renders unchanged (`ColorPalette.resolve`).
- Draw stored ARGB through `MaterialTheme.finance.accent(argb)`. It lifts the color 25% toward white in dark theme (>= 4:1 on `surfaceContainer` for every palette entry). Stored values never change.
- `IconBadge` already does this: a circle filled with the tint at 18% alpha, with the icon in the tint.

### Surfaces (tonal hierarchy)
| Level | Token |
|---|---|
| Page | `background` |
| Card / section / tile on the page | `surfaceContainer` |
| Control inside a card (pill, keypad key, chip-like) | `surfaceContainerHigh` |
| Selected row, track, inner icon circle | `surfaceContainerHighest` |
| "Active filter" pill | `secondaryContainer` / `onSecondaryContainer` |

---

## 3. Shape & spacing

| Element | Shape |
|---|---|
| Section card (dashboard sections) | `RoundedCornerShape(24.dp)` |
| Hero card (amount + keypad) | `28.dp` |
| Form group card | outer `20.dp`, inner `4.dp` (see §5.3) |
| Tile (quick action, account, flow tile) | `16.dp` |
| Keypad key | `18.dp` |
| Icon container in a tile | `14.dp`, 44dp square |
| Pills, segmented tabs, primary CTA | fully round (`CircleShape` / `50%` / `28.dp` on 56dp height) |

- Screen padding: 16dp horizontal. Between sections: 16dp (dashboard `LazyColumn`), 12dp (form `Column`).
- Inside cards: 16dp padding (dashboard), 14dp (form `SectionCard`). Gap between tiles: 8dp.
- `LazyColumn` bottom padding is 88dp when a FAB is shown.
- Touch targets ≥ 44dp (pills `heightIn(min = 44.dp)`, icon buttons 44dp, keypad keys 56dp).

---

## 4. Typography

| Use | Style |
|---|---|
| Amount being entered | `displayMedium` SemiBold, `tnum`, auto-size 24–48sp |
| Big summary number | `titleMedium`/`titleLarge` SemiBold |
| Section header | `titleMedium` + `semantics { heading() }` |
| Tile/pill label | `labelLarge` / `labelMedium` |
| Secondary text, notes | `bodySmall`/`bodyMedium` in `onSurfaceVariant` |
| Overline (currency code) | `labelSmall` `onSurfaceVariant` |

Amounts are always **SemiBold**. Signed amounts use a real minus `−` (U+2212) and an explicit `+`.

---

## 5. Components

### 5.1 Dashboard building blocks (private in `DashboardScreen.kt`, extract when reused)
- **`DashboardCard`**: `Surface(surfaceContainer, 24dp)`, full width. Every dashboard section is one.
- **`SectionHeader(title, actionLabel, onAction)`**: title on the left, `TextButton` "See all ›" / "Details ›" / "Manage ›" on the right. Hide the action when there's nothing behind it (empty list).
- **Quick actions row**: 4 equal tiles (Expense, Income, Transfer, Accounts). Tile = `surfaceContainer` 16dp, 44dp icon box filled with the semantic container (`expenseContainer`, `incomeContainer`, `primaryContainer`, `tertiaryContainer`), label `labelMedium`. Disabled = 38% alpha (Transfer needs ≥ 2 active accounts).
- **Account tile carousel**: `LazyRow` of 156dp tiles: `IconBadge` 32 + currency code, name, native balance. The last tile is an outlined "+ New account". Tap opens account history (PRD 11.4).
- **Flow tiles**: tinted container tile with a 24dp circular icon, label and `AmountText`. Tap opens the filtered transaction list.
- **Net row + FlowBar**: signed net in `netColor()`, an 8dp rounded split bar (income vs expense, 2dp gap).
- **Empty states**: an icon in `onSurfaceVariant`, one sentence that tells the user what to do. First-run: a centered card on `primaryContainer` with a 64dp icon circle and a filled `Button`.

### 5.2 Scope controls: `PeriodSelector` + `AccountSelector` (`AnalyticsComponents.kt`)
Used on Dashboard, Statistics and Account analytics. Always place them as **one row above the content they scope**:
```kotlin
Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(8.dp)) {
    PeriodSelector(..., modifier = Modifier.weight(1f), arrows = true, canShiftForward, onShift)
    AccountSelector(...)            // omit when ≤ 1 account or not applicable
}
```
- Period pill: `‹ November 2026 ▾ ›`. The label comes from `periodTitle()` ("Today", "3 – 9 Nov", "November 2026", "2026", custom range). Tapping the label opens a `ModalBottomSheet` with a radio list (Today / This week / This month / This year / Custom range…). Custom opens the shared full-screen `DateRangePickerDialog` (`ui/common/DateRangePickerDialog.kt`): X + "Clear" on top, the range picker, and a bottom bar with the selected range, day count and a 56dp "Apply · 3 – 9 Nov" button. Dates after today are not selectable; cancelling returns to the period sheet.
- Account pill: wallet icon + "All", or stacked badges (≤ 3) + name/count. Uses `secondaryContainer` when a filter is active. Sheet: "All accounts" + a checklist (badge, name, currency, **no balances**) and a "Done" button.
- Don't bring back the 5-chip row or the full-width dropdown card.

### 5.3 Form building blocks (`TransactionEditScreen.kt`)
- **Segmented kind tabs**: a `surfaceContainer` pill track with 4dp padding; selected tab = kind color at 16% alpha, icon + `labelLarge` SemiBold. In edit mode, unselected tabs fade to 38%.
- **Amount hero card**: `surfaceContainer` 28dp, centered. From top to bottom: an account/currency pill (`surfaceContainerHigh`, `primary` text, ▾), the big amount with a colored sign and blinking caret (`liveRegion` polite), an inline error, quick-add pills (+10/+25/+50, clear), and the **custom number pad** (3×4, 56dp keys, haptics, long-press ⌫ clears). Use the keypad for money entry, not the system keyboard.
- **Grouped field cards**: related fields form one connected group with `spacedBy(2.dp)`: the first card has `GroupTopShape` (20dp top / 4dp bottom), middle ones `GroupMiddleShape` (4dp), and the last `GroupBottomShape`. Each row = `SectionCard`: a 44dp leading circle (`IconBadge` or `LeadingIcon` on `surfaceContainerHighest`), title + value, optional quick picks. Errors draw a 1dp `error` border on that card only.
- **Pills**: `SmallPill` (32dp, `surfaceContainerHigh`, `labelLarge`) for quick actions; `Pill` (`labelSmall` on `surfaceContainerHighest`) for read-only badges.
- **Bottom CTA bar**: full-width 56dp fully-rounded `Button` on `background`, padded with `navigationBarsPadding()`. The label carries the context ("Add expense — zł 12.50"). **When disabled, it says what's missing** ("Enter an amount", "Choose a category") instead of just going grey. A secondary `TextButton` "Save & add another" sits under it in create mode.
- **Date & time picker** (`DateTimePickerDialog`): full-screen, same chrome as `DateRangePickerDialog` (X + `titleMedium` heading, calendar, 56dp round CTA). Between them a row of 44dp pills: time (`surfaceContainerHigh`, opens a `surfaceContainer` 28dp time dialog with Cancel / round "Set") and "Now". The CTA reads "Set · 9 Nov, 14:30"; a future time on the max date disables it ("Time is in the future").

### 5.4 Charts (`ui/common/charts/Charts.kt`, `AnalyticsSections.kt`)
- Category split = **`CategoryBreakdown(title, entries, total)`**: a 176dp ring with an 18dp stroke, rounded caps and 2.5° gaps, the total in the center ("Spent / 1380.00 zł"), and a full-width legend below (dot, name, amount, 4dp share bar, %).
- Tapping a segment or legend row selects it: the segment gets thicker, the others dim to 35%, the center shows the category name, amount and %. Tapping again clears the selection.
- Colors come from `donutEntries()` → `finance.accent(...)`. "Other" and Uncategorized are grey.
- A chart is never the only carrier of a number: the legend/rows always show the figures as text, and the chart has a `contentDescription`.
- Animations must respect `LocalInspectionMode` so previews render the final state.

### 5.5 Lists
- `TransactionRow` inside a card: pass `colors = ListItemDefaults.colors(containerColor = Color.Transparent)`.
- `TransactionRow` subtitle: transfers show `From → To` as two single-line ellipsized names around a 14dp `TransferDirection` arrow (`onSurfaceVariant`). Other rows show `Category · Account` only when the title is the description; when the title is the category, the subtitle is just the account.
- Amount colors: `finance.income` for > 0, `finance.expense` for < 0.
- Long lists (`TransactionsScreen.kt`): each day is one connected group of `surfaceContainer` rows (Top/Middle/Bottom shapes from §5.3, 2dp gaps) under a sticky `labelLarge` SemiBold day header ("Today · 9 Nov 2026").

### 5.6 Search & filter bar (`TransactionsScreen.kt`)
- A persistent search field (fully rounded `TextField`, `surfaceContainer`, no indicator) above the list. Not hidden behind an icon.
- Below it, one `LazyRow` of 44dp pills: type pills (All / Expenses / Income / Transfers, toggle in place), a divider, then one pill per dimension (Date, `AccountSelector`, Categories, Tags, Amount). An active pill shows its value ("Food +2", "This month") on `secondaryContainer`; tapping opens a sheet for **that dimension only**. "Clear all" ends the row when anything is active.
- Sort is the last pill ("⇅ Newest first", never highlighted) and opens a radio sheet; amount options are disabled with a hint when several currencies are in scope. The top bar keeps only the overflow (Export CSV): no filter/sort icons.
- Category sheet (`CategoryFilterSheet.kt`): Expense/Income segmented tabs with selected counts, an "Uncategorized" card, then one connected group of rows (badge, name, checkbox, expand chevron). Selecting a parent includes its subcategories (shown checked and dimmed); a "With subcategories ▾ / Direct only ▾" pill on the selected parent switches the match. Search appears above 10 categories. Changes apply immediately; "Done" closes.
- The filtered summary (income / expenses / net per currency) is the first list item, only while a filter is active.

### 5.7 Icons (`ui/common/AppIcons.kt`, `IconCatalog.kt`, `IconKeys.kt`)
- **UI chrome:** `AppIcons` is the only source. Call sites never import `androidx.compose.material.icons.*`. The family is **Outlined**; directional icons are `AutoMirrored`. Add a new named property there before using a new glyph.
- **One concept, one icon:**

| Concept | `AppIcons` | Concept | `AppIcons` |
|---|---|---|---|
| Back / Close / Add / More | `Back` / `Close` / `Add` / `More` | Income (money in) | `Income` ↓ |
| Edit / Delete / Export | `Edit` / `Delete` / `Export` | Expense (money out) | `Expense` ↑ |
| Search / no results / Sort | `Search` / `SearchEmpty` / `Sort` | Transfer | `Transfer` ⇄ |
| Date | `Calendar` | Recurring | `Recurring` |
| Account | `Account` (wallet) | Category / Tag / Merchant | `Category` / `Tag` / `Merchant` |
| Opens detail / period prev, next | `Next` / `Previous` | Expand / collapse | `Expand` / `Collapse` |
| Dropdown caret | `Dropdown` | Success / Warning / Error / Help | `Success` / `Warning` / `Error` / `Help` |

  Quick actions, kind tabs, flow tiles, the recurring list and transaction rows all use the same Income/Expense/Transfer glyphs.
- **Bottom navigation:** unselected `Nav*` is Outlined, the selected tab shows the Filled `Nav*Selected` (`TopLevelDestination.selectedIcon`). This is the only place Filled is used for chrome.
- **Sizes (`IconSize`):** `Default` 24dp (IconButtons, list leading icons), `Medium` 20dp (trailing dropdown carets in selector pills), `Small` 18dp (inside pills, chips and small buttons). Sizes fixed by a component above stay: `IconBadge` icon = 55% of the badge, 44dp tile icon boxes, the 64dp empty-state circle, 14dp in the flow tile circle.
- **User-content icons** (categories, accounts) come from `IconCatalog` (key → `IconEntry(vector, label)`) in the **Rounded** family. Keys live in `IconKeys` (plain Kotlin, grouped as `IconGroup`). Keys are the stored identifier but may change during development; unknown or null keys render `other`. A key appears once per list (`category` / `account`, flattened from `categoryGroups` / `accountGroups`) but may be offered to both. Every key needs `icon_<key>` in `strings.xml` (search and TalkBack read it, never the raw key) and a group label `icon_group_<id>`.
- **Icon picker (`IconPicker`):** in the account and category forms it is one row (44dp `IconBadge` in the chosen color, "Icon" + the icon's name, `Next` chevron). Tapping it opens a `ModalBottomSheet` (`skipPartiallyExpanded`) with a search field and one `titleSmall` header per group above a grid of 48dp circles (`surfaceContainerHigh`; selected = tint at 20% plus a 2dp tint border). Picking an icon closes the sheet.
---

## 6. Interaction rules
- Numbers are links: income/expense/net tiles, legend rows, account tiles and "See all" all navigate to the filtered destination (`TransactionFilter` carries the period and accounts).
- Sheets (`ModalBottomSheet`) for choosing; dialogs only for date pickers and destructive confirmation.
- Show warnings inline next to what they're about (outdated rates, partial conversion ⚠, schedules needing attention as an `errorContainer` banner inside the card).
- Haptics on keypad presses.

## 7. Accessibility
- Section titles use `semantics { heading() }`. Selectable rows use `semantics { selected = … }`, and checklists use `Role.Checkbox`/`Role.RadioButton`.
- Decorative icons get `contentDescription = null`. Icon-only buttons get a label.
- The amount display is a polite live region. Charts get `clearAndSetSemantics { contentDescription = summary }`.

## 8. Product decisions that override the PRD / templates
- **No total balance / total valuation on the Dashboard** (privacy: it's seen every time the user adds an expense). The Accounts tab keeps its `ValuationCard`. Per-account native balances in the dashboard carousel are currently kept.
- The Dashboard top bar shows the app name ("Finora"). No greeting, avatar, notifications or "live sync" (local-only app).

## 9. Known inconsistencies to fix when touching these files
- Money formatting differs: the form uses `symbol + grouped digits` ("zł5,000.00"), while `Money.format()` / `Estimate.format()` use "5000.00 zł". Unify on one locale-aware formatter.
- Statistics, Accounts, Recurring, Categories, Tags and Settings screens still use plain `ListItem`/`Card` layouts. Migrate them to `DashboardCard` + `SectionHeader` + grouped `SectionCard` when they are next edited.
- `DashboardCard`, `SectionHeader`, `SectionCard`, `SmallPill` and `LeadingIcon` are private. Move them to `ui/common/` the first time a second screen needs them.
