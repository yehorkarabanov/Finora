# Finora UI design system: "Obsidian"

The reference for every screen. Dark tokens are the `DESIGN.md` set verbatim (obsidian surfaces, electric-mint accent); light is the tonal counterpart. Both themes ship and are equal. Spec: `docs/superpowers/specs/2026-11-11-obsidian-redesign-design.md`.

All reusable visual building blocks live in `app/src/main/java/com/yehor/finora/designsystem/`. Screens (`ui/<feature>/`) compose them and own only feature logic.

---

## 1. Principles

1. **Fast entry first.** Adding an expense is the main job: quick actions on Home, a keypad-first editor, the FAB on lists.
2. **Privacy by default.** No total-wealth hero on Home (it is seen every time the user adds an expense). The Accounts tab owns the valuation.
3. **Dark, tonal, quiet.** Hierarchy comes from surface tone and 1px hairlines, not elevation. The only glows are the soft mint glow on the primary CTA/FAB and the live chart point.
4. **Money is honest.** See section 6.
5. **Motion is part of the feel**, and always has an instant fallback (section 5).
6. **Light and dark are equal.** Every component and screen has a light and a dark `@Preview`.
7. **No banking features:** no cards, Send, Exchange, yield, avatar, notifications; no search on Home.

---

## 2. Package map (`designsystem/`)

| Package | Contents | Rule of thumb |
|---|---|---|
| `theme/` | `Color.kt` (schemes), `FinanceColors.kt`, `Type.kt`, `Shape.kt`, `Theme.kt` (`FinoraTheme`), `Grain.kt`, `PreviewTheme.kt` | tokens only, no components |
| `motion/` | `Motion.kt`: `FinoraMotion`, `rememberReduceMotion()`, `Modifier.pressScale()` | every animation spec and the reduce-motion switch |
| `icon/` | `AppIcons`, `IconCatalog`, `IconKeys` | UI chrome icons come only from `AppIcons` |
| `chart/` | `WaveGeometry` (pure), `WaveChart`, `SplitBar`, `Charts.kt` (donut) | drawing only; data prep stays in `domain/` or `ui/<feature>/` |
| `component/button/` | `PrimaryButton`, `RoundIconButton` | |
| `component/input/` | `SegmentedPill`, `FilterPill` | |
| `component/list/` | `GroupedCard`, `ListRow`, `RoundIcon`, `SectionHeader` | |
| `component/navigation/` | `FinoraNavBar`, `AddFab`, `FabScroll` (pure) | |
| `component/sheet/` | `FinoraSheet`, `RangeWheelsSheet`, `RangeWheelsLogic` (pure) | |
| `component/display/` | `AmountText` | |
| `catalog/` | `ComponentCatalog.kt`: `@Preview` gallery of every component, light + dark | |

Boundary rules (enforced by `DesignSystemBoundaryTest`, which must stay green):
- `designsystem` never imports `ui.*`, `data.*` or ViewModels. It may import `domain` value types it renders (e.g. `Money`).
- Screens under `ui/` never hard-code colors (`Color(0x...)`). They use `MaterialTheme.colorScheme`, `MaterialTheme.finance` or the stored palette via `finance.accent(argb)`.
- Every public component takes `modifier: Modifier = Modifier`, reads colors only from the theme, and **is added to `catalog/ComponentCatalog.kt` with light + dark previews**. Adding a component without a catalog entry is incomplete.
- Naming: plain nouns (`PrimaryButton`, `GroupedCard`); a `Finora` prefix only where Material already has the name (`FinoraNavBar`, `FinoraSheet`).
- Feature-specific pure logic stays with its feature (`domain/analytics/NetSeries.kt`, `ui/analytics/HomePeriod.kt`, `ChartWindow.kt`, `TagRanking.kt`).
- User-visible strings go in `res/values/strings.xml` **and** `res/values-ru/strings.xml`.

---

## 3. Foundations (tokens)

### Theme
`FinoraTheme(themeMode, dynamicColor = false)` (`theme/Theme.kt`) uses the fixed Obsidian schemes from `theme/Color.kt`; every M3 role is set explicitly. `LocalFinanceColors` is always provided. `PreviewTheme(dark)` wraps previews with the same schemes.

| Role | Light | Dark |
|---|---|---|
| `primary` / `onPrimary` | `#006C46` / `#FFFFFF` | `#6DFFBA` / `#003822` |
| `primaryContainer` / `onPrimaryContainer` | `#4DFFB2` / `#002112` | `#00E599` / `#00613E` |
| `secondary` | `#5D5E63` | `#C6C6CB` |
| `error` / `errorContainer` | `#BA1A1A` / `#FFDAD6` | `#FFB4AB` / `#93000A` |
| `background` = `surface` | `#F8F8F9` | `#121314` |
| `surfaceContainer` Lowest / Low / base / High / Highest | `#FFFFFF` / `#F2F2F3` / `#ECECED` / `#E6E6E7` / `#E0E0E1` | `#0D0E0F` / `#1B1C1D` / `#1F2021` / `#292A2B` / `#343536` |
| `onSurface` / `onSurfaceVariant` | `#1B1C1D` / `#3B4A41` | `#E3E2E3` / `#BACBBE` |
| `outline` / `outlineVariant` | `#6B7B70` / `#BACBBE` | `#849589` / `#3B4A41` |

Mint `#00E599` is the accent for primary CTAs, selected states, positive numbers and chart lines.

**One mint fill.** Every solid mint fill uses the same pair: container `colorScheme.primaryContainer` (`#4DFFB2` light / `#00E599` dark) and content `MaterialTheme.finance.onMint` (`#003822` in both themes; 10.24:1 light, 7.97:1 dark). That covers `PrimaryButton`, the FAB and its primary speed-dial item, the nav bar active pill, the Home Expense quick action, date-picker day/year selection, the time-picker selector and the Accounts empty-state card. Never fill with `colorScheme.primary`. `primary` is only for mint text, icons, thin indicators, cursors, rings and the soft-chip tint (`primary` content on `primary` @ 12%, used by `FilterPill` active, Restore, Resume and the accounts chip). `finance.income` is for positive numbers and chart lines, never fills.

### Finance colors: `MaterialTheme.finance` (`theme/FinanceColors.kt`)
| Token | Light | Dark | Use |
|---|---|---|---|
| `income` | `#006C46` | `#00E297` | positive amounts, income icons, net > 0 |
| `incomeContainer` | `#C6F7DD` | `#0B3324` | income tile/icon background |
| `expense` | `#BA1A1A` | `#FFB4AB` | expense amounts/icons |
| `expenseContainer` | `#FFDAD6` | `#3F1D1B` | expense tile/icon background |
| `negative` | `#C2362B` | `#FF7A6B` | below-zero balances and net, soft coral |
| `accentGlow` | `#00A36C` @ 25% | `#00E599` @ 35% | shadow color of the primary CTA/FAB and live point |
| `isDark` | false | true | pick hairline / grain alpha |

- Never use `colorScheme.error` to mean expense or negative; `error` is for validation and failures.
- `finance.accent(argb)` draws a stored category/account color (lifted 25% toward white in dark). Stored values never change.
- Contrast (checked): `negative` 5.13:1 / 7.31:1 on `surface` (light/dark), 4.87 / 6.70 on `surfaceContainerLow`; `onSurfaceVariant` 8.8 / 11.0 on `surface`; `finance.onMint` on `primaryContainer` 10.24 / 7.97 (the mint fill); `onPrimaryContainer` is not used on fills (only 4.6:1 in dark). Text must stay >= 4.5:1 in both themes.
- Hairlines: `hairline(isDark)` is 1px white @ 8% (dark) / black @ 6% (light). Cards and pills use it instead of shadows.
- User colors come from `ColorPalette.colors` (`ui/common/ColorPalette.kt`, 28 entries). Tinted circles are the color at ~14-18% alpha behind an icon in the color.

### Grain (`theme/Grain.kt`)
Every **visible gradient** (chart fills, card gradients, fades over content) also draws the shared film grain, subtle: `rememberGrainAlpha()` = 5% dark, 3.5% light.
- Backgrounds: `Modifier.grainyBackground(brush, shape)`.
- Inside a Canvas: `DrawScope.drawGrain(GrainBrush, alpha, path)` (the canvas must be an offscreen layer; it paints with `SrcAtop`, so it is invisible where the gradient is transparent).
- Masks used only for alpha (e.g. the wheel edge fade) are exempt.

### Type (`theme/Type.kt`)
Space Grotesk for amounts, headings and keypad digits (tabular numerals); Manrope for everything else. Big money: integer part full weight, decimals in the amount's own colour at 70% alpha and ~60% size, currency suffix at full size (see `AmountText`).

### Shape and spacing (`theme/Shape.kt`)
`PillShape` for every button, chip, segmented control, nav bar and FAB. Cards 22-24dp. Sheets 28dp top corners. Screen margin 18-20dp. Grouped lists: one card, rows separated by 1px dividers, ~12dp vertical row padding.

### Icons (`icon/`)
Line icons, no emoji. UI chrome icons come only from `AppIcons` (call sites never import `androidx.compose.material.icons.*`); add a named property there before using a new glyph. Category/account icons come from `IconCatalog` keyed by `IconKeys`; every key needs `icon_<key>` and `icon_group_<id>` strings.

---

## 4. Components

Path prefix: `designsystem/component/` unless noted.

| Component | File | Key parameters / behavior |
|---|---|---|
| `PrimaryButton` | `button/PrimaryButton.kt` | mint (`primaryContainer` / `finance.onMint`) 52dp pill CTA with accent glow. `destructive` switches to error colors (irreversible actions); `fillWidth = false` for dialog slots; `enabled` removes the glow. Springy press. |
| `RoundIconButton` | `button/RoundIconButton.kt` | 40dp circle in a 48dp hit area; `contentDescription` is required in practice; `enabled = false` dims to 38% and ignores taps. |
| `SegmentedPill` | `input/SegmentedPill.kt` | `options`, `selected`, `onSelect`. `enabled = false` fixes the choice (selected stays readable, others dim). `selected = -1` shows no selection. 48dp segments. |
| `FilterPill` | `input/FilterPill.kt` | `label`, `active`, `onClick`; `onClear` adds a separate 48dp clear target while active. |
| `GroupedCard`, `ListRow`, `RoundIcon`, `SectionHeader` | `list/` | `GroupedCard` = one rounded hairline card of rows; `ListRow(icon, title, subtitle, trailing, onClick, onLongClick)` is tappable (with press scale) only when a handler is given; `RoundIcon(icon, tint)` is the tinted circle; `SectionHeader(title, trailing)`. |
| `FinoraNavBar` | `navigation/FinoraNavBar.kt` | floating 56dp pill, 4 tabs; inactive icon only, active = mint pill with label that morphs between tabs. |
| `AddFab` | `navigation/AddFab.kt` | `mode` (`EXTENDED` / `COLLAPSED` / `HIDDEN`), `expanded`, `speedDial: List<SpeedDialItem>?`. Open: scrim, icon rotates to X, staggered spring dial, haptic. `null` dial = plain add (Accounts). Scroll logic is pure in `FabScroll.kt`. |
| `BindShellFab` | `ui/navigation/ShellFab.kt` | screen-side hook: `BindShellFab(listState, items, label, onClick)` registers the screen's list state and actions with the shell-hosted `AddFab`. Used by Transactions and Accounts. Not on Home or Statistics. A screen that binds the FAB adds `ShellFabClearance` (72dp) to its list's bottom content padding so the last row scrolls clear of the FAB, and lifts its own `SnackbarHost` by the same amount; the shell snackbar sits at `shellSnackbarBottom(fabBound)` (over the FAB while one is bound, 76dp otherwise) and is drawn above it. |
| `FinoraSheet` | `sheet/FinoraSheet.kt` | Obsidian bottom sheet: 28dp top corners, grab handle, 55% black scrim. Use for choosing; dialogs only for destructive confirmation. |
| `RangeWheelsSheet` | `sheet/RangeWheelsSheet.kt` | FROM / TO month wheels with snap and a haptic tick per step, month-count chip, quick options, confirm CTA. Validation is pure in `RangeWheelsLogic.kt`. |
| `AmountText` | `display/AmountText.kt` | `AmountText(text, style, color, animate = true)`. `splitAmount` rule: whole + fraction + optional currency suffix (`^(.*?)([.,]\d{1,2})(\s*\D+)?$`). A `.` or `,` is the decimal separator only when 1-2 digits follow it and nothing but a non-digit suffix comes after, so `1,234` and `1.234.567` stay whole while `48.20 $`, `1 234,56 ₴`, `-48.20` and `$48.20` split. The fraction renders in the amount's own colour at 70% alpha and 60% size (no size change when the style's font size is unspecified); the suffix stays full size and colour. A changed value rolls vertically (bigger from below, smaller from above) unless `animate = false` (list rows) or reduce-motion. Hero amounts (Home, Statistics) keep `animate = true`. |
| `WaveChart` | `chart/WaveChart.kt` | smooth area chart: `values`, `compare`, `zeroLine`, `selectedIndex`/`onSelect` (tap or hold-drag scrubs), `color`. `gestures: WaveGestures?` adds horizontal pan and pinch (vertical drags stay with the parent scroll); `sparkline = true` draws a bare 2dp line; `slide: (() -> Float)?` shifts the drawing sideways by a fraction of the width every frame, so paging can move the wave with the fingers. The canvas extends a 16dp **bleed** beyond its bounds so glow, halos and caps are never clipped; do not clip the parent. Draws in over 500ms, instantly when data changes during a gesture. |
| `SplitBar` | `chart/SplitBar.kt` | `parts: List<Pair<Float, Color>>` normalised, rounded segments, 3dp gaps. |
| Donut | `chart/Charts.kt` | category ring with selection; legend rows always repeat the numbers as text. |

Screen-level compositions that are reused inside one feature stay in that feature (e.g. `HomeChartPager`, `AmountKeypad`, `EditorShelf`). When a second feature needs one, move it into `designsystem/component/` with a catalog entry.

---

## 5. Motion

Motion is wanted; it is part of the Obsidian feel. All specs live in `FinoraMotion`; do not invent ad-hoc durations.

- **Press**: `Modifier.pressScale(interactionSource)` scales to 0.96 with `FinoraMotion.press`. Applied to `PrimaryButton`, `RoundIconButton`, `FilterPill`, `SegmentedPill` segments, clickable `ListRow`, Home quick actions and the FAB / speed-dial items. Keypad keys scale with their own circular wash.
- **Sheets**: slide up with a decelerating curve (`FinoraMotion.sheet`, 350ms) + scrim fade; drag to dismiss. The editor shelf drags down to the keypad.
- **FAB**: extend/collapse on scroll, hide on fast fling, rotate + staggered speed dial (`FinoraMotion.bouncy`).
- **Charts**: wave draws in left to right (`CHART_DRAW_MS` = 500) on first show and data change; paging slides; the scrub marker glides on a spring; the live point pulses.
- **Numbers**: hero `AmountText` rolls on value change.
- **Lists**: Statistics drill-down slides horizontally; archived section and category tree expand in height; chevrons rotate with a spring; ranking bars and split bars grow.
- **Nav**: the active pill morphs between tabs; page dots stretch.
- **Haptics** (`LocalHapticFeedback`): tick on each wheel step, FAB open, Home page snap, keypad taps, and a confirm on editor save.
- **Reduce motion**: `rememberReduceMotion()` is true when `Settings.Global.ANIMATOR_DURATION_SCALE == 0`. Every animation must then be instant or a crossfade: use `snap()`, `EnterTransition.None` / `ExitTransition.None`, or a short fade. Previews render the final state (`LocalInspectionMode`). A new animation without this fallback is a bug.

---

## 6. Money rules (kept)

- **Native currency first.** If the shown accounts share one currency, amounts are exact and native. With mixed currencies the screen shows the display currency and marks every converted figure `≈` (the `resolveScope` rule). Converted values are always estimates (PRD 10.6 / NFR-14).
- **Every summary number is tappable** and opens the filtered transaction list or the detail behind it (income/expense/net, legend rows, account tiles, "See all").
- Amount colors: `finance.income` for positive, `finance.expense` for expense, `finance.negative` for below-zero totals.
- Warnings sit next to what they concern (outdated rates, partial conversion, schedules needing attention).
- A chart is never the only carrier of a number; it also has a `contentDescription` summary.

---

## 7. Accessibility

- Touch targets >= 48dp (`RoundIconButton` and segments have a 48dp hit area around a smaller visual; filter and color swatches likewise).
- Icon-only buttons have a `contentDescription`; decorative icons use `null`.
- Text contrast >= 4.5:1 in both themes (values in section 3).
- Section titles use `semantics { heading() }`; selectable rows expose `selected`, with `Role.Tab` / `Role.RadioButton` / `Role.Checkbox` as appropriate.
- The amount display is a polite live region.
- Motion respects the system animator scale (section 5).

---

## 8. Product decisions that override the PRD / templates

- No total balance on Home. Home shows the net-over-time wave with paging, quick actions, upcoming and recent activity.
- No greeting, avatar, notifications or "live sync" (local-only app).
