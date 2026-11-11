# Obsidian Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the whole Finora app to the "Obsidian" design (mint on obsidian, Space Grotesk/Manrope, pills, springy motion) with the reworked Home, Add/Edit, Transactions, Accounts, Statistics and More described in the spec, without changing financial logic.

**Architecture:** Pure, JVM‑testable logic goes into small files next to the screens that use them (chart geometry, net series, period/window math, FAB scroll states, range validation, tag ranking). All visual building blocks move into one `com.yehor.finora.designsystem` package (theme, motion, icons, components, charts, catalog); feature screens under `ui/<feature>/` build only from it, enforced by a source-scanning guard test. Screens are rebuilt on those components; ViewModels change only where the spec needs new data (Home period + net series, Statistics trend window + tag ranking).

**Tech Stack:** Kotlin 2.4, Jetpack Compose + Material 3 (BOM in `gradle/libs.versions.toml`), Hilt, Room, JUnit4 + kotlinx‑coroutines‑test + Turbine for unit tests. minSdk 33.

**Spec:** `docs/superpowers/specs/2026-11-11-obsidian-redesign-design.md`. Visual reference: `docs/superpowers/specs/obsidian-mockups/` (read its `README.md` first — where a mockup and the spec disagree, the spec wins).

## Global Constraints

- No banking features (cards, Send, Exchange, slide‑to‑top‑up, yield optimizer, avatar, notifications). No search on Home. No total‑wealth hero on Home.
- Don't drop existing behavior: every filter, tab, save path, dialog and validation that exists today keeps working.
- No DB schema / migration changes (`SchemaUnchangedTest` must stay green).
- Currency: keep the existing `resolveScope` rule — one shared account currency → exact native amounts; mixed → display currency, marked `≈`.
- Colors only from `MaterialTheme.colorScheme` / `MaterialTheme.finance`; no hard‑coded colors in screens. Dark tokens = `DESIGN.md` verbatim; light = tonal counterpart; both themes ship.
- Pills (`PillShape`) for buttons, chips, segmented controls, nav bar, FAB; cards 22–24dp; sheets 28dp top corners; screen margin 18–20dp. Line icons only, no emoji.
- Amounts in Space Grotesk with tabular numerals; decimals rendered in `onSurfaceVariant` at ~60% size.
- Every new user‑visible string in `res/values/strings.xml` **and** `res/values-ru/strings.xml`.
- Touch targets ≥ 48dp; text contrast ≥ 4.5:1 in both themes; content descriptions on icon‑only buttons.
- Motion respects the system animator scale: when `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, use instant/crossfade.
- Each restyled screen keeps (or gains) a light **and** dark `@Preview`.
- Build check for UI tasks: `./gradlew :app:compileDebugKotlin` succeeds and `./gradlew :app:testDebugUnitTest` stays green. (Windows: `gradlew.bat`.)

## Review Focus

1. **Empty and tiny data** — a new user with one account and no transactions: Home wave, Statistics wave and tag ranking must render a flat line / empty state, not crash or divide by zero. Tested in Tasks 2, 3, 9.
2. **Partial / unconvertible currencies** — a bucket whose `Cell.estimate` is null (missing rate or overflow): series must carry `null` and the chart must break the line there, not plot 0. Tested in Tasks 2, 3.
3. **Month and year boundaries** — Feb in leap years, ranges crossing Dec→Jan, week starting on Sunday vs Monday: period/window math must stay exact. Tested in Tasks 4, 5.
4. **"Today" as the hard right edge** — paging, pan, pinch and the wheels must never select a future period. Tested in Tasks 4, 5, 6.
5. **Pinch extremes** — zooming past the minimum (7 days) or maximum (5 years) must clamp, and the bucket unit switch at ~3 months must be stable (no flip‑flop at the threshold). Tested in Task 5.

---

## File Structure

```
com/yehor/finora/
├─ designsystem/                 every reusable visual building block (no feature logic, no ViewModels)
│  ├─ theme/       Color, FinanceColors, Type, Shape, Theme, PreviewTheme   (moved from ui/theme)
│  ├─ motion/      Motion.kt: springs, durations, rememberReduceMotion, pressScale
│  ├─ icon/        AppIcons, IconCatalog, IconKeys                         (moved from ui/common)
│  ├─ chart/       WaveGeometry (pure), WaveChart, SplitBar, legacy Charts  (moved from ui/common/charts)
│  ├─ component/
│  │  ├─ button/      PrimaryButton, RoundIconButton
│  │  ├─ input/       SegmentedPill, FilterPill
│  │  ├─ list/        GroupedCard, ListRow, RoundIcon, SectionHeader
│  │  ├─ navigation/  FinoraNavBar, AddFab, FabScroll (pure)
│  │  ├─ sheet/       FinoraSheet, RangeWheelsSheet, RangeWheelsLogic (pure)
│  │  └─ display/     AmountText
│  └─ catalog/     ComponentCatalog.kt: @Preview gallery of every component, light + dark
├─ ui/<feature>/                 screens, ViewModels, feature logic (analytics, operations, accounts, ...)
└─ domain/, data/                unchanged layering
```

Rules (enforced by `DesignSystemBoundaryTest`, Task 1b):
- `designsystem` never imports `ui.*`, `data.*` or ViewModels. It may import `domain` value types it renders (e.g. `Money`).
- Screens under `ui/` never hard-code colors (`Color(0x…)`); they use theme roles, `MaterialTheme.finance`, or the stored palette.
- Every public component takes `modifier: Modifier = Modifier`, reads colors only from the theme, and is added to `catalog/ComponentCatalog.kt` with light + dark previews.
- Naming: plain nouns (`PrimaryButton`, `GroupedCard`); `Finora` prefix only where Material already has the name (`FinoraNavBar`, `FinoraSheet`).

Feature-specific pure logic stays with its feature: `domain/analytics/NetSeries.kt`, `ui/analytics/HomePeriod.kt`, `ui/analytics/ChartWindow.kt`, `ui/analytics/TagRanking.kt`. `ui/navigation/MoreScreen.kt` is replaced by `ui/navigation/MoreSheet.kt`.

All paths below are under `app/src/main/java/com/yehor/finora/` (main) or `app/src/test/java/com/yehor/finora/` (tests) unless written from the repo root.

---

### Task 1: Finish and commit the foundations

**Files:**
- Modify (current location; Task 1b moves them): `ui/theme/Color.kt`, `FinanceColors.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`, `PreviewTheme.kt`
- Create: `ui/theme/Motion.kt` (Task 1b moves it to `designsystem/motion/Motion.kt`)
- Keep: `res/font/manrope.ttf`, `res/font/space_grotesk.ttf`

**Interfaces:**
- Produces: `FinoraTypography`, `FinoraShapes`, `PillShape`, `hairline(isDark)`, `MaterialTheme.finance` with fields `income`, `incomeContainer`, `expense`, `expenseContainer`, **new** `negative: Color`, `accentGlow: Color`, `isDark`, `accent(argb)`.
- Produces in `Motion.kt`: `object FinoraMotion { val press: SpringSpec<Float>; val sheet: TweenSpec<Float> /*350ms, FastOutSlowIn*/; val bouncy: SpringSpec<Float> /*dampingRatio 0.6, stiffness 400*/; const val CHART_DRAW_MS = 500 }`, `@Composable fun rememberReduceMotion(): Boolean`.

- [ ] **Step 1:** Review the uncommitted diff (`git diff` + untracked `Type.kt`, `Shape.kt`, `res/font/`). Keep it; fix compile errors only.
- [ ] **Step 2:** Add `negative` to `FinanceColors`: dark `#FF7A6B`, light `#C2362B`; `accentGlow`: dark `#00E599` at 35% alpha, light `#00A36C` at 25%. Check contrast of `negative` ≥ 4.5:1 on `surface` and `surfaceContainerLow` in both themes (write the ratio in a comment).
- [ ] **Step 3:** Create `Motion.kt` with the values above. `rememberReduceMotion()` reads `Settings.Global.ANIMATOR_DURATION_SCALE` via `LocalContext` (0f → true).
- [ ] **Step 4:** Ensure `FinoraTheme` applies `FinoraTypography` and `FinoraShapes`, and that the app background uses `colorScheme.background` (`#121314` dark).
- [ ] **Step 5:** Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `git add app/src/main/java/com/yehor/finora/ui app/src/main/res/font && git commit -m "Obsidian foundations: colors, type, shapes, motion"` (this also commits the already‑modified screen files from the branch's in‑progress work).

---

### Task 1b: Design-system package, move existing building blocks, guard test

**Files:**
- Move (`git mv`): `ui/theme/*` (incl. `Motion.kt` → `designsystem/motion/`) → `designsystem/theme/`; `ui/common/charts/Charts.kt` → `designsystem/chart/Charts.kt`; `ui/common/AppIcons.kt`, `IconCatalog.kt`, `IconKeys.kt` → `designsystem/icon/` (keep the object names)
- Create: `designsystem/catalog/ComponentCatalog.kt` (starts with color swatches + type scale previews; later tasks add their components)
- Test: create `designsystem/DesignSystemBoundaryTest.kt`; move `ui/common/IconKeysTest.kt` → `designsystem/icon/IconKeysTest.kt`

**Interfaces:**
- Produces: package `com.yehor.finora.designsystem.*` as laid out in File Structure. All later tasks put components there.

- [ ] **Step 1: Write the failing guard test** (same pattern as `DomainAnalyticsCleanlinessTest`)
  ```kotlin
  class DesignSystemBoundaryTest {
      private fun kt(path: String) = File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

      @Test fun `designsystem has no feature, data or viewmodel dependencies`() {
          val files = kt("src/main/java/com/yehor/finora/designsystem")
          assertTrue(files.isNotEmpty())
          files.forEach { f -> val t = f.readText()
              listOf("import com.yehor.finora.ui.", "import com.yehor.finora.data.", "ViewModel").forEach { bad ->
                  assertFalse("${f.name} uses $bad", t.contains(bad)) } }
      }

      @Test fun `screens hard-code no colors`() =
          kt("src/main/java/com/yehor/finora/ui").forEach { f ->
              assertFalse("${f.name} hard-codes a color", Regex("""Color\(0x""").containsMatchIn(f.readText())) }

      @Test fun `old theme and chart packages are gone`() {
          assertFalse(File("src/main/java/com/yehor/finora/ui/theme").exists())
          assertFalse(File("src/main/java/com/yehor/finora/ui/common/charts").exists())
      }
  }
  ```
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "*DesignSystemBoundaryTest"` → FAIL (old packages still exist).
- [ ] **Step 3:** Move the files, fix `package` lines and imports across `app/src` (about 37 files import `ui.theme` or `ui.common.charts`). Replace any `Color(0x…)` under `ui/` with theme roles; if `ui/common/ColorPalette.kt` holds `Color(0x…)` literals, move it to `designsystem/theme/Palette.kt`.
- [ ] **Step 4:** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL, all green.
- [ ] **Step 5:** Commit `"Introduce designsystem package and boundary test"`.

---

### Task 2: Net series (domain)

**Files:**
- Create: `domain/analytics/NetSeries.kt`
- Test: `domain/analytics/NetSeriesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object NetSeries {
      /** Per bucket: income - expense. Null when either side is null (unconvertible/overflow). Buckets after [lastIndex] are null (future). */
      fun perBucket(income: List<Long?>, expense: List<Long?>, lastIndex: Int = income.lastIndex): List<Long?>
      /** Running sum of perBucket starting at 0. A null bucket makes that point null but the running total continues from the last known value. */
      fun running(income: List<Long?>, expense: List<Long?>, lastIndex: Int = income.lastIndex): List<Long?>
  }
  ```
  Lists are bucket‑indexed minor units; caller maps `Cell?.estimate?.amountMinor` (an empty `Cell` → `0`, `estimate == null && !isEmpty` → `null`).

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun perBucketSubtracts() = assertEquals(listOf(70L, -20L), NetSeries.perBucket(listOf(100L, 0L), listOf(30L, 20L)))
  @Test fun runningAccumulatesFromZero() = assertEquals(listOf(-10L, 90L, 60L), NetSeries.running(listOf(0L, 100L, 0L), listOf(10L, 0L, 30L)))
  @Test fun futureBucketsAreNull() = assertEquals(listOf(5L, null, null), NetSeries.running(listOf(5L, 5L, 5L), listOf(0L, 0L, 0L), lastIndex = 0))
  @Test fun unknownBucketIsNullButRunningContinues() = assertEquals(listOf(10L, null, 15L), NetSeries.running(listOf(10L, null, 5L), listOf(0L, 0L, 0L)))
  @Test fun emptyInputGivesEmpty() = assertEquals(emptyList<Long?>(), NetSeries.running(emptyList(), emptyList()))
  @Test fun mismatchedSizesThrow() { assertThrows(IllegalArgumentException::class.java) { NetSeries.perBucket(listOf(1L), emptyList()) } }
  ```
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "*NetSeriesTest"` → FAIL (unresolved `NetSeries`).
- [ ] **Step 3:** Implement `NetSeries` in `domain/analytics/NetSeries.kt` (use `Math.addExact`; on overflow the point becomes null).
- [ ] **Step 4:** Same command → PASS.
- [ ] **Step 5:** `git add … && git commit -m "Add NetSeries for running and per-bucket net"`

---

### Task 3: Wave geometry + WaveChart + SplitBar

**Files:**
- Create: `designsystem/chart/WaveGeometry.kt`, `designsystem/chart/WaveChart.kt`, `designsystem/chart/SplitBar.kt`
- Test: `designsystem/chart/WaveGeometryTest.kt`

**Interfaces:**
- Produces (pure):
  ```kotlin
  data class WavePoint(val x: Float, val y: Float)
  data class CubicSegment(val from: WavePoint, val c1: WavePoint, val c2: WavePoint, val to: WavePoint)
  object WaveGeometry {
      /** Maps values to points in [width]x[height] with [padTop]/[padBottom]; null values split the result into separate runs. Range always includes 0 when [includeZero]. */
      fun layout(values: List<Long?>, width: Float, height: Float, padTop: Float, padBottom: Float, includeZero: Boolean): WaveLayout
      /** Monotone cubic (Fritsch–Carlson) segments for one run; never overshoots between two equal or monotone neighbours. */
      fun smooth(run: List<WavePoint>): List<CubicSegment>
  }
  data class WaveLayout(val runs: List<List<WavePoint>>, val zeroY: Float?, val minValue: Long, val maxValue: Long)
  ```
- Produces (Compose):
  ```kotlin
  @Composable fun WaveChart(
      values: List<Long?>, modifier: Modifier = Modifier,
      compare: List<Long?>? = null, zeroLine: Boolean = false,
      selectedIndex: Int? = null, onSelect: (Int?) -> Unit = {},
      label: (Int) -> String, // bubble text for an index
      gestures: WaveGestures? = null, // Task 11 fills this
      sparkline: Boolean = false, // Task 13: no fill glow, scrub or labels; 2dp stroke
      color: Color? = null, // null = finance.income
  )
  data class WaveGestures(val onPan: (deltaFraction: Float) -> Unit, val onZoom: (scale: Float, focusFraction: Float) -> Unit, val onGestureEnd: () -> Unit)
  @Composable fun SplitBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 10.dp)
  ```

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun flatSeriesIsHorizontal() { val l = WaveGeometry.layout(listOf(5L,5L,5L), 300f, 100f, 0f, 0f, includeZero = false); assertTrue(l.runs.single().all { it.y == l.runs.single()[0].y }) }
  @Test fun nullSplitsRuns() = assertEquals(2, WaveGeometry.layout(listOf(1L, null, 3L, 4L), 300f, 100f, 0f, 0f, false).runs.size)
  @Test fun zeroLineInsideRange() { val l = WaveGeometry.layout(listOf(-50L, 50L), 100f, 100f, 0f, 0f, true); assertEquals(50f, l.zeroY!!, 0.01f) }
  @Test fun singlePointIsCentered() = assertEquals(150f, WaveGeometry.layout(listOf(7L), 300f, 100f, 0f, 0f, false).runs.single().single().x, 0.01f)
  @Test fun allZeroDoesNotDivideByZero() { val l = WaveGeometry.layout(listOf(0L, 0L), 100f, 100f, 0f, 0f, true); assertTrue(l.runs.single().all { it.y.isFinite() }) }
  @Test fun smoothDoesNotOvershootMonotone() { val segs = WaveGeometry.smooth(listOf(WavePoint(0f,100f), WavePoint(10f,50f), WavePoint(20f,0f))); segs.forEach { s -> assertTrue(s.c1.y in 0f..100f && s.c2.y in 0f..100f) } }
  @Test fun emptyValuesGiveNoRuns() = assertTrue(WaveGeometry.layout(emptyList(), 100f, 100f, 0f, 0f, false).runs.isEmpty())
  ```
- [ ] **Step 2:** `--tests "*WaveGeometryTest"` → FAIL.
- [ ] **Step 3:** Implement `WaveGeometry` (y grows downward; flat range → centre line).
- [ ] **Step 4:** `--tests "*WaveGeometryTest"` → PASS.
- [ ] **Step 5:** Implement `WaveChart` on `Canvas`: 3dp stroke in `finance.income`; vertical gradient fill (35%→0%) to the zero line (or bottom); with `zeroLine`, clip above/below `zeroY` and draw the below part in `finance.negative`; dashed zero line `onSurface` @22%; `compare` as 1.5dp dashed `onSurfaceVariant` curve; glow = second wider stroke at `accentGlow` (skip when `rememberReduceMotion()`); draw‑in via `Animatable(0f→1f, CHART_DRAW_MS)` clipping x, restarted when `values` change; tap/hold → nearest index → `onSelect`, draws dashed guide + white dot with ring + bubble `label(i)`. `gestures` param is accepted but unused until Task 11. `SplitBar`: weights normalised, 3dp gaps, rounded ends.
- [ ] **Step 6:** Light + dark previews: running‑net with negative dip; flat; with compare. `compileDebugKotlin` → OK.
- [ ] **Step 7:** Commit `"Add wave chart and split bar"`.

---

### Task 4: Home period model

**Files:**
- Create: `ui/analytics/HomePeriod.kt`
- Test: `ui/analytics/HomePeriodTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class HomeMode { DAYS, MONTHS }
  /** Inclusive month range. DAYS default = current month; MONTHS default = Jan..Dec of current year. */
  data class HomePeriod(val mode: HomeMode, val from: YearMonth, val to: YearMonth) {
      val monthCount: Int
      fun shifted(steps: Int): HomePeriod          // moves by monthCount * steps
      fun canShiftForward(today: LocalDate): Boolean
      fun clampedTo(today: LocalDate): HomePeriod  // never starts after today's month
      fun withMode(mode: HomeMode, today: LocalDate): HomePeriod // DAYS→month of `to` (clamped), MONTHS→year of `to`
      fun buckets(zone: ZoneId, today: LocalDate): Pair<BucketUnit, List<Bucket>> // DAYS→one DAY bucket per day of range; MONTHS→one MONTH bucket per month
      fun lastElapsedIndex(today: LocalDate): Int  // index of the bucket containing today, or lastIndex if range is past
      companion object { fun current(mode: HomeMode, today: LocalDate): HomePeriod }
  }
  ```
  Use `BucketPlan` internals' rules: build `ResolvedPeriod` via `PeriodCalculator.resolve(Period(CUSTOM, from.atDay(1), to.atEndOfMonth()), zone, MONDAY)` then `BucketPlan.auto` **only for MONTHS when > 62 days**; for DAYS build day buckets directly (ranges up to 12 months allowed → may exceed `MAX_BUCKETS`; cap DAYS ranges at 3 months — longer ranges switch the hero chart to MONTHS automatically).

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  private val nov10 = LocalDate.of(2026, 11, 10)
  @Test fun currentDaysIsThisMonth() = assertEquals(HomePeriod(DAYS, YearMonth.of(2026,11), YearMonth.of(2026,11)), HomePeriod.current(DAYS, nov10))
  @Test fun currentMonthsIsThisYear() = assertEquals(HomePeriod(MONTHS, YearMonth.of(2026,1), YearMonth.of(2026,12)), HomePeriod.current(MONTHS, nov10))
  @Test fun shiftByRangeLength() = assertEquals(HomePeriod(DAYS, YearMonth.of(2025,7), YearMonth.of(2025,10)), HomePeriod(DAYS, YearMonth.of(2025,11), YearMonth.of(2026,2)).shifted(-1))
  @Test fun cannotShiftPastToday() = assertFalse(HomePeriod.current(DAYS, nov10).canShiftForward(nov10))
  @Test fun canShiftForwardFromPast() = assertTrue(HomePeriod(DAYS, YearMonth.of(2026,9), YearMonth.of(2026,9)).canShiftForward(nov10))
  @Test fun crossYearDayBuckets() { val (u, b) = HomePeriod(DAYS, YearMonth.of(2025,12), YearMonth.of(2026,1)).buckets(ZoneOffset.UTC, nov10); assertEquals(BucketUnit.DAY, u); assertEquals(62, b.size) }
  @Test fun leapFebruary() = assertEquals(29, HomePeriod(DAYS, YearMonth.of(2028,2), YearMonth.of(2028,2)).buckets(ZoneOffset.UTC, LocalDate.of(2028,3,1)).second.size)
  @Test fun longDaysRangeFallsBackToMonths() = assertEquals(BucketUnit.MONTH, HomePeriod(DAYS, YearMonth.of(2025,1), YearMonth.of(2025,12)).buckets(ZoneOffset.UTC, nov10).first)
  @Test fun lastElapsedIndexIsToday() = assertEquals(9, HomePeriod.current(DAYS, nov10).lastElapsedIndex(nov10))
  ```
- [ ] **Step 2:** `--tests "*HomePeriodTest"` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** → PASS.
- [ ] **Step 5:** Commit `"Add HomePeriod model"`.

---

### Task 5: Statistics chart window (pan/pinch math)

**Files:**
- Create: `ui/analytics/ChartWindow.kt`
- Test: `ui/analytics/ChartWindowTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class ChartWindow(val start: LocalDate, val end: LocalDate /*inclusive*/) {
      val days: Long
      fun panned(deltaFraction: Float, today: LocalDate): ChartWindow   // + = forward; shifts by round(days*fraction); end ≤ today
      fun zoomed(scale: Float, focusFraction: Float, today: LocalDate): ChartWindow // scale>1 = pinch out (fewer days); keeps the date under focus fixed; clamps MIN_DAYS..MAX_DAYS; end ≤ today
      fun unit(previous: BucketUnit?): BucketUnit  // DAY if days ≤ 92, MONTH if ≥ 100; between 93..99 keep `previous` (hysteresis), default DAY
      fun toPeriodState(firstDayOfWeek: DayOfWeek, today: LocalDate): PeriodState // exact calendar WEEK/MONTH/YEAR (with offset relative to today) else PeriodState.custom(start, end)
      companion object { const val MIN_DAYS = 7L; const val MAX_DAYS = 1827L; fun of(period: ResolvedPeriod): ChartWindow }
  }
  ```

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  private val today = LocalDate.of(2026, 11, 10)
  private val nov = ChartWindow(LocalDate.of(2026,11,1), LocalDate.of(2026,11,30))
  @Test fun panBackOneThird() = assertEquals(ChartWindow(LocalDate.of(2026,10,22), LocalDate.of(2026,11,20)), nov.panned(-0.3f, today))
  @Test fun panForwardStopsAtToday() = assertEquals(today, ChartWindow(LocalDate.of(2026,10,1), LocalDate.of(2026,10,31)).panned(1f, today).end)
  @Test fun pinchOutClampsToMinDays() = assertEquals(7L, nov.zoomed(100f, 0.5f, today).days)
  @Test fun pinchInClampsToMaxDays() = assertEquals(1827L, nov.zoomed(0.001f, 0.5f, today).days)
  @Test fun zoomKeepsFocusDate() { val w = ChartWindow(LocalDate.of(2026,9,1), LocalDate.of(2026,9,30)).zoomed(2f, 0.5f, today); assertTrue(LocalDate.of(2026,9,15) in w.start..w.end) }
  @Test fun unitHysteresis() { val w = ChartWindow(LocalDate.of(2026,1,1), LocalDate.of(2026,1,1).plusDays(95)); assertEquals(BucketUnit.DAY, w.unit(BucketUnit.DAY)); assertEquals(BucketUnit.MONTH, w.unit(BucketUnit.MONTH)) }
  @Test fun calendarMonthMapsToMonthPreset() = assertEquals(PeriodState(PeriodKind.MONTH, offset = -1), ChartWindow(LocalDate.of(2026,10,1), LocalDate.of(2026,10,31)).toPeriodState(DayOfWeek.MONDAY, today))
  @Test fun oddRangeMapsToCustom() = assertEquals(PeriodKind.CUSTOM, ChartWindow(LocalDate.of(2026,10,3), LocalDate.of(2026,11,8)).toPeriodState(DayOfWeek.MONDAY, today).kind)
  @Test fun sundayWeekRecognised() = assertEquals(PeriodKind.WEEK, ChartWindow(LocalDate.of(2026,11,1), LocalDate.of(2026,11,7)).toPeriodState(DayOfWeek.SUNDAY, today).kind)
  ```
- [ ] **Step 2:** `--tests "*ChartWindowTest"` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** → PASS.
- [ ] **Step 5:** Commit `"Add ChartWindow for statistics pan and pinch"`.

---

### Task 6: Shared design components + Range wheels

**Files:**
- Create: `designsystem/component/navigation/{FinoraNavBar,AddFab,FabScroll}.kt`, `designsystem/component/input/{SegmentedPill,FilterPill}.kt`, `designsystem/component/button/{PrimaryButton,RoundIconButton}.kt`, `designsystem/component/list/{GroupedCard,ListRow,RoundIcon,SectionHeader}.kt`, `designsystem/component/display/AmountText.kt`, `designsystem/component/sheet/{FinoraSheet,RangeWheelsSheet,RangeWheelsLogic}.kt`; add each to `designsystem/catalog/ComponentCatalog.kt`
- Test: `designsystem/component/navigation/FabScrollTest.kt`, `designsystem/component/sheet/RangeWheelsLogicTest.kt`

**Interfaces:**
- Produces (pure):
  ```kotlin
  enum class FabMode { EXTENDED, COLLAPSED, HIDDEN }
  object FabScroll { const val FLING_PX = 12f; fun next(mode: FabMode, firstItemVisible: Boolean, scrollOffsetPx: Int, deltaPx: Float): FabMode }
  // top of list (firstItemVisible && offset < 20) → EXTENDED; delta > FLING_PX → HIDDEN; delta < -4 → COLLAPSED (or EXTENDED at top); otherwise keep, but EXTENDED away from top → COLLAPSED

  sealed interface RangeValidity { data class Valid(val months: Int) : RangeValidity; data object EndBeforeStart : RangeValidity; data object InFuture : RangeValidity }
  object RangeWheelsLogic { fun validate(from: YearMonth, to: YearMonth, today: LocalDate): RangeValidity; fun years(today: LocalDate, earliest: LocalDate?): List<Int> /* earliest data year (or today-4) .. today.year */ }
  ```
- Produces (Compose): `FinoraNavBar(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit)`; `AddFab(mode: FabMode, expanded: Boolean, onToggle: () -> Unit, label: String, speedDial: List<SpeedDialItem>?)` with `data class SpeedDialItem(val label: String, val icon: ImageVector, val onClick: () -> Unit, val primary: Boolean)`; `SegmentedPill(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier)`; `FilterPill(label, active, onClick, onClear: (() -> Unit)?)`; `GroupedCard { ListRow(...) }` with `ListRow(icon: @Composable () -> Unit, title: String, subtitle: String?, trailing: @Composable () -> Unit, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?)`; `RoundIcon(icon: ImageVector, tint: Color, size: Dp = 38.dp)`; `AmountText(text: String, style: TextStyle, color: Color, animate: Boolean = true)` (splits decimals, animates digit change with `AnimatedContent` slide); `FinoraSheet(onDismiss, content)` (ModalBottomSheet, 28dp, handle, scrim); `RangeWheelsSheet(initialFrom: YearMonth, initialTo: YearMonth, today: LocalDate, earliest: LocalDate?, quickOptions: List<Pair<String, () -> Unit>>, onConfirm: (YearMonth, YearMonth) -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  // FabScrollTest
  @Test fun topIsExtended() = assertEquals(EXTENDED, FabScroll.next(COLLAPSED, true, 0, -2f))
  @Test fun flingDownHides() = assertEquals(HIDDEN, FabScroll.next(COLLAPSED, false, 400, 30f))
  @Test fun scrollUpShowsCollapsed() = assertEquals(COLLAPSED, FabScroll.next(HIDDEN, false, 400, -10f))
  @Test fun slowScrollAwayFromTopCollapses() = assertEquals(COLLAPSED, FabScroll.next(EXTENDED, false, 200, 3f))
  // RangeWheelsLogicTest
  private val today = LocalDate.of(2026, 11, 10)
  @Test fun crossYearRangeCountsMonths() = assertEquals(RangeValidity.Valid(4), RangeWheelsLogic.validate(YearMonth.of(2025,11), YearMonth.of(2026,2), today))
  @Test fun singleMonthIsOne() = assertEquals(RangeValidity.Valid(1), RangeWheelsLogic.validate(YearMonth.of(2026,11), YearMonth.of(2026,11), today))
  @Test fun endBeforeStart() = assertEquals(RangeValidity.EndBeforeStart, RangeWheelsLogic.validate(YearMonth.of(2026,3), YearMonth.of(2026,1), today))
  @Test fun futureEnd() = assertEquals(RangeValidity.InFuture, RangeWheelsLogic.validate(YearMonth.of(2026,10), YearMonth.of(2026,12), today))
  @Test fun yearsWithoutDataDefaultToFiveBack() = assertEquals((2022..2026).toList(), RangeWheelsLogic.years(today, null))
  ```
- [ ] **Step 2:** `--tests "*FabScrollTest" --tests "*RangeWheelsLogicTest"` → FAIL.
- [ ] **Step 3:** Implement `FabScroll` and `RangeWheelsLogic`.
- [ ] **Step 4:** → PASS.
- [ ] **Step 5:** Implement the composables per spec §4 and mockups `fab-above.html`, `range-wheels-v2.html`:
  - Nav bar: 56dp pill, 18dp side / 14dp bottom margin, `surfaceContainer` @95% (+ `Modifier.blur` is not for backgrounds — use solid translucent fill), inactive icon‑only with contentDescription, active = `primaryContainer` pill with icon + label; the active pill animates position/width with `FinoraMotion.bouncy`.
  - FAB: EXTENDED = "+ Add" pill, COLLAPSED = 52dp circle, HIDDEN = slide down + fade; expanded → icon rotates 135°, scrim `Color.Black` @55%, speed‑dial items staggered 50ms with `bouncy`; `HapticFeedbackType.LongPress`‑style tick on open.
  - Wheels: two `LazyColumn` wheel pairs with `rememberSnapFlingBehavior`, 44dp items, 5 visible, edge fade via `drawWithContent` gradient mask, shared highlight band behind; FROM selection `onSurface`, TO selection `primary`; month count chip; future items alpha 0.25; invalid → chip `finance.negative`, CTA disabled; CTA text from string `range_show_months` ("Show %1$s – %2$s"); haptic `TextHandleMove` on each snap.
  - Strings (en + ru): `fab_add`, `range_title`, `range_from`, `range_to`, `range_months_count` (plural), `range_show`, `range_invalid_order`, `range_in_future`, `range_today`, `range_custom_days`.
- [ ] **Step 6:** Previews (light + dark) for each component. `compileDebugKotlin` → OK.
- [ ] **Step 7:** Commit `"Add Obsidian shared components and range wheels"`.

---

### Task 7: App shell — floating nav, FAB host, More sheet

**Files:**
- Modify: `ui/navigation/Destinations.kt`, `ui/navigation/FinoraNavHost.kt`
- Create: `ui/navigation/MoreSheet.kt`; Delete: `ui/navigation/MoreScreen.kt`
- Test: `ui/navigation/TabSelectionTest.kt`, `ui/navigation/RoutesTest.kt`

**Interfaces:**
- Consumes: `FinoraNavBar`, `AddFab`, `FabScroll`, `FinoraSheet` (Task 6).
- Produces: `TopLevelDestination` = `DASHBOARD, TRANSACTIONS, ACCOUNTS, STATISTICS` (MORE removed; `Routes.MORE` string stays as the prefix of its children). `fun tabFor(route: String?): TopLevelDestination?` — `more/...` routes return `null` (no tab highlighted). Shell API for screens: `LocalShellFab` composition local exposing `fun bind(listState: LazyListState, items: List<SpeedDialItem>?, label: String, onClick: () -> Unit)` so Transactions/Accounts register their FAB; `LocalOpenMore: () -> Unit`.

- [ ] **Step 1: Update tests first**
  ```kotlin
  @Test fun fourTabs() = assertEquals(listOf("DASHBOARD","TRANSACTIONS","ACCOUNTS","STATISTICS"), TopLevelDestination.entries.map { it.name })
  @Test fun moreChildrenHighlightNoTab() { assertNull(tabFor(Routes.CATEGORIES)); assertNull(tabFor(Routes.SETTINGS)) }
  @Test fun accountDetailHighlightsAccounts() = assertEquals(TopLevelDestination.ACCOUNTS, tabFor(Routes.ACCOUNT_DETAIL))
  ```
  Remove the `moreTabCoversItsScreens` expectations that refer to a MORE tab (keep the `isRouteUnder` string assertions — the helper is unchanged).
- [ ] **Step 2:** `--tests "*TabSelectionTest" --tests "*RoutesTest"` → FAIL.
- [ ] **Step 3:** Implement: drop `MORE` from the enum; add `tabFor`; in `FinoraShell` replace `NavigationBar` with an overlay `Box` (content full‑height, bottom fade gradient 110dp, `FinoraNavBar`, FAB host), hidden on `ROUTES_WITHOUT_BOTTOM_BAR`; remove the `composable(Routes.MORE)` destination; `MoreSheet` (ORGANIZE tiles: Categories, Tags, Recurring; APP rows: Data, Settings — mockup `more-and-lists.html`) opened via `LocalOpenMore`. Screens must add `contentPadding` bottom = 56 + 14 + 16 dp so content clears the bar.
- [ ] **Step 4:** Tests → PASS; `compileDebugKotlin` → OK.
- [ ] **Step 5:** Commit `"Floating nav bar, FAB host and More sheet"`.

---

### Task 8: Add / Edit transaction — keypad screen + details shelf

**Files:**
- Modify: `ui/operations/TransactionEditScreen.kt`, `ui/operations/EditFormComponents.kt`, `ui/operations/TransferForm.kt`, `ui/operations/AmountKeypad.kt`
- Test: `ui/operations/EditorModeTest.kt` (extend)

**Interfaces:**
- Consumes: `SegmentedPill`, `GroupedCard`/`ListRow`, `PrimaryButton`, `AmountText`, `FinoraMotion` (Tasks 1, 6). Existing `TransactionEditViewModel` / `TransferEditViewModel` APIs unchanged.
- Produces: `enum class EditorStage { AMOUNT, DETAILS }` and `fun initialStage(isEdit: Boolean): EditorStage` in `EditFormComponents.kt` (edit → DETAILS so the shelf is open on existing items; new → AMOUNT).

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun newOpensOnAmount() = assertEquals(EditorStage.AMOUNT, initialStage(isEdit = false))
  @Test fun editOpensWithShelf() = assertEquals(EditorStage.DETAILS, initialStage(isEdit = true))
  ```
- [ ] **Step 2:** `--tests "*EditorModeTest"` → FAIL.
- [ ] **Step 3:** Implement `initialStage`; → PASS.
- [ ] **Step 4:** Rebuild the UI (mockup `add-tx-sheet-v2.html`):
  - Top: round ✕, `SegmentedPill` Expense | Income | Transfer (existing kind switching), spacer.
  - Amount: `AmountText` ~56sp; sign prefix `−`/`+` muted; account pill (icon, name, balance, ⇅ → existing account picker).
  - Keypad: restyle `AmountKeypad` composable to borderless 3×4, Space Grotesk 26sp, circular press wash `surfaceContainerHigh`, `.` bottom‑left, backspace bottom‑right; `AmountKeypad` logic object unchanged (its tests stay green).
  - `Next` (PrimaryButton) → stage DETAILS: shelf rises over the keypad (`AnimatedVisibility` slideInVertically + scrim; not a `ModalBottomSheet`, so the amount above stays interactive‑visible). Amount block scales to 0.85 and moves up 24dp. Scrim tap / handle drag down → AMOUNT; no input lost.
  - Shelf (one GroupedCard): Expense/Income → Account, Category (+ existing recent‑category shortcuts as a row of round icons under the card), Date, Note, Tags. Transfer → From, To, Date, Note, Tags; "They receive" row only when currencies differ (existing transfer amount fields).
  - Below: switch "Add another after saving" (backed by the existing "save and another" path; remember the choice in `rememberSaveable` + the existing VM if it already persists it, otherwise in `SettingsRepository` as a new boolean `addAnotherAfterSave` default false), then PrimaryButton "Save expense / Save income / Save transfer".
  - Keep: ⋯ menu (Duplicate, Delete), "created by schedule" banner, all validation messages and dialogs.
  - Strings: `editor_next`, `editor_details`, `editor_add_another`, `editor_save_expense`, `editor_save_income`, `editor_save_transfer`, `editor_they_receive`.
- [ ] **Step 5:** Previews: new expense (AMOUNT), shelf open, transfer with different currencies; light + dark. Build + all tests → green.
- [ ] **Step 6:** Commit `"Editor: keypad screen with details shelf"`.

---

### Task 9: Home

**Files:**
- Modify: `ui/analytics/DashboardViewModel.kt`, `ui/analytics/DashboardScreen.kt`, `ui/analytics/AnalyticsComponents.kt`
- Test: `ui/analytics/HomeChartLogicTest.kt` (new), existing `AnalyticsLogicTest` stays green

**Interfaces:**
- Consumes: `HomePeriod` (Task 4), `NetSeries` (Task 2), `WaveChart` (Task 3), `RangeWheelsSheet`, `SegmentedPill`, `AmountText`, `GroupedCard` (Task 6), `LocalOpenMore` (Task 7).
- Produces in VM: `homePeriod: StateFlow<HomePeriod>` (own state, **not** `PeriodSelection`; saved in `SavedStateHandle` as `mode|from|to`), `fun setHomeMode(HomeMode)`, `fun shiftHome(steps: Int)`, `fun setHomeRange(from: YearMonth, to: YearMonth)`; UI state gains `chart: HomeChart` with
  ```kotlin
  data class HomeChart(val unit: BucketUnit, val buckets: List<Bucket>, val values: List<Long?>, val net: Cell, val income: Cell, val expense: Cell, val currencyApprox: Boolean)
  fun homeChartValues(mode: HomeMode, income: List<Cell>, expense: List<Cell>, lastIndex: Int): List<Long?> // DAYS → NetSeries.running; MONTHS → NetSeries.perBucket; Cell→Long? per Task 2's rule
  ```
  Data: `analytics.observeByBucket(filter, Measure.INCOME/EXPENSE, buckets)` → `Reports.trend(…, converter)` with the existing scope converter.

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun daysUsesRunningNet() = assertEquals(listOf(-10L, 90L), homeChartValues(HomeMode.DAYS, cells(0, 100), cells(10, 0), lastIndex = 1))
  @Test fun monthsUsesPerBucket() = assertEquals(listOf(-10L, 100L), homeChartValues(HomeMode.MONTHS, cells(0, 100), cells(10, 0), lastIndex = 1))
  @Test fun emptyCellCountsAsZero() = assertEquals(listOf(0L), homeChartValues(HomeMode.DAYS, listOf(Cell.NONE), listOf(Cell.NONE), 0))
  @Test fun unconvertibleCellIsNull() = assertEquals(listOf(null), homeChartValues(HomeMode.MONTHS, listOf(unconvertible()), listOf(Cell.NONE), 0))
  ```
  (`cells(vararg minor)` builds `Cell`s with an `Estimate` of those minor units; `unconvertible()` = `Cell(null, 1, setOf(Currency("XYZ")), false, null)`.)
- [ ] **Step 2:** `--tests "*HomeChartLogicTest"` → FAIL.
- [ ] **Step 3:** Implement `homeChartValues` and the VM flow; remove `distribution` and the accounts carousel data from the dashboard state (keep `recent`, `upcoming`, `selectedAccountIds`, `hasAccounts`).
- [ ] **Step 4:** → PASS; all unit tests green.
- [ ] **Step 5:** Rebuild `DashboardScreen` (mockups `home-scroll.html`, `home-wave-zero.html`): top bar "FINORA" + ⋯ (`LocalOpenMore`), **no search**; `SegmentedPill` Days | Months + period chip (opens `RangeWheelsSheet`; multi‑month label "Nov 2025 – Feb 2026") + account selector chip (existing `AccountSelector`, restyled); hero label ("NET IN NOVEMBER" / "NET · NOV 2025 – FEB 2026" / "NET IN 2026"), `AmountText` colored `finance.income` if ≥ 0 else `finance.negative`, `≈` prefix when converted, small pill "In X · Out Y"; `HorizontalPager` (reverse, current period at page 0, `beyondViewportPageCount = 1`) of `WaveChart(zeroLine = true)` cards, page dots, settled page → `shiftHome`; today marker label on last elapsed point; round quick actions Expense (primary) / Income / Transfer / Recurring (`Routes.RECURRING`); Upcoming + Recent in `GroupedCard`s; empty state = existing first‑account card restyled. Strings: `home_net_in`, `home_net_range`, `home_in_out`, `home_days`, `home_months`, `home_today`, `quick_recurring`.
- [ ] **Step 6:** Previews light + dark (positive month, negative month, no data). Build → OK.
- [ ] **Step 7:** Commit `"Home: net wave with paging and range wheels"`.

---

### Task 10: Transactions

**Files:**
- Modify: `ui/operations/TransactionsScreen.kt`, `ui/common/TransactionRow.kt`, `ui/operations/TransactionFilterSheet.kt`, `CategoryFilterSheet.kt`, `TagFilterSheet.kt`, `FilterSheetComponents.kt`

**Interfaces:**
- Consumes: `FilterPill`, `GroupedCard`/`ListRow`, `RoundIcon`, `AmountText`, `LocalShellFab` (Tasks 6–7). `TransactionsViewModel` unchanged.

- [ ] **Step 1:** Rebuild per `transactions.html` + spec §5.3: title + sort (round) + ⋯ (CSV export); pill search field; horizontally scrolling `FilterPill`s (type, date, categories, tags, amount; active = primary tint + ✕ clears that filter; "Clear all" pill when ≥ 2 active); 3‑column In/Out/Net summary card (keep converted line + outdated‑rate note); sticky day headers "TODAY · NOV 10" with that day's net at right (compute from the day's items already in state); each day = one `GroupedCard`. `TransactionRow`: `RoundIcon` in category color (via `finance.accent`), title, "Category · Account" + tag chips, amount + time; transfers "A → B" with converted pair. FAB bound via `LocalShellFab` with speed‑dial Expense (primary) / Income / Transfer → existing `Routes.transactionNew(kind)`.
- [ ] **Step 2:** Restyle the filter sheets with `FinoraSheet`, `GroupedCard`, `SegmentedPill`, `PrimaryButton` — same options and behavior.
- [ ] **Step 3:** Swipe‑to‑delete: implement only if `SwipeToDismissBox` + the existing delete use‑case + snackbar Undo fit in this task; otherwise skip and note it in the commit message.
- [ ] **Step 4:** Previews light + dark (list, empty, filtered‑empty). Build + tests → green.
- [ ] **Step 5:** Commit `"Transactions: Obsidian list, filter pills, FAB"`.

---

### Task 11: Statistics — period block, in‑period trend with pan/pinch

**Files:**
- Modify: `ui/analytics/StatisticsViewModel.kt`, `ui/analytics/StatisticsScreen.kt`, `ui/analytics/ReportBuilder.kt` (if trend assembly lives there)
- Modify: `designsystem/chart/WaveChart.kt` (activate `gestures`)
- Test: `ui/analytics/StatisticsTrendTest.kt` (new)

**Interfaces:**
- Consumes: `ChartWindow` (Task 5), `WaveChart`/`WaveGestures` (Task 3), `RangeWheelsSheet`, `SegmentedPill` (Task 6).
- Produces: `StatsControls.trendYearly` and `setTrendYearly` **removed**; `fun trendBuckets(period: ResolvedPeriod, zone: ZoneId, previousUnit: BucketUnit?): Pair<BucketUnit, List<Bucket>>` (DAY when `ChartWindow.of(period).unit(previousUnit) == DAY` else MONTH buckets via `BucketPlan.auto`‑style clipping); `fun alignPrevious(current: List<Bucket>, previous: List<Bucket>): List<Int?>` (index i of current → index in previous by ordinal, null when previous is shorter); VM `fun previewWindow(window: ChartWindow)` (visual only, no recompute) and `fun commitWindow(window: ChartWindow)` → `environment` period set from `window.toPeriodState(firstDayOfWeek, today)` (Statistics keeps using the shared `PeriodSelection`).

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun monthUsesDayBuckets() = assertEquals(BucketUnit.DAY, trendBuckets(resolved(MONTH, nov10), UTC, null).first)
  @Test fun yearUsesMonthBuckets() = assertEquals(12, trendBuckets(resolved(YEAR, nov10), UTC, null).second.size)
  @Test fun alignShorterPrevious() = assertEquals(listOf(0, 1, null), alignPrevious(buckets(3), buckets(2)))
  @Test fun weekUsesSevenDays() = assertEquals(7, trendBuckets(resolved(WEEK, nov10), UTC, null).second.size)
  ```
- [ ] **Step 2:** `--tests "*StatisticsTrendTest"` → FAIL.
- [ ] **Step 3:** Implement; replace `BucketPlan.months/years` trend use in the VM with `trendBuckets` over the selected period; previous‑period series via the existing `previous` period + `alignPrevious`.
- [ ] **Step 4:** → PASS; all tests green.
- [ ] **Step 5:** UI (mockups `statistics.html`, `stats-tags.html`): text tabs with animated mint underline; period block = full‑width `SegmentedPill` Week | Month | Year (none selected for CUSTOM/TODAY) + stepper row `‹ name ▾ ›` (name opens `RangeWheelsSheet` with quick options Today / Custom days → existing `DateRangePickerDialog`; `›` disabled via `canShiftForward`); ☰ top‑right opens a sheet with the account filter + compare switch; hero label + `AmountText` + comparison line; trend card = `WaveChart(compare = …)` with avg per day/month.
- [ ] **Step 6:** Gestures in `WaveChart`: when `gestures != null`, `detectTransformGestures` → `onZoom(zoom, centroid.x / width)`; single‑finger horizontal drag → `onPan(-dx / width)`; long‑press‑then‑drag scrubs instead of panning; `onGestureEnd` on release. Screen keeps a local `ChartWindow` state updated by `panned/zoomed`, redraws via `previewWindow`, and calls `commitWindow` 300ms after the last gesture end (debounce with `LaunchedEffect` keyed on a gesture counter). Unit switch cross‑fades (`Crossfade` keyed on unit).
- [ ] **Step 7:** Previews light + dark. Build → OK.
- [ ] **Step 8:** Commit `"Statistics: in-period trend with pan and pinch"`.

---

### Task 12: Statistics — categories drill‑down, sections, Tags ranking

**Files:**
- Modify: `ui/analytics/StatisticsScreen.kt`, `ui/analytics/AnalyticsSections.kt`, `ui/analytics/StatisticsViewModel.kt`
- Create: `ui/analytics/TagRanking.kt`
- Test: `ui/analytics/TagRankingTest.kt`

**Interfaces:**
- Consumes: existing `CategoryDrill.rows` / `DrillRow(isDirect, hasChildren)`, `StatisticsViewModel` path API, `analytics.observeByTag`, `SplitBar` (Task 3), `GroupedCard` (Task 6).
- Produces:
  ```kotlin
  data class TagRankRow(val tag: Tag, val cell: Cell, val fraction: Float /*0..1 of the largest*/)
  fun rankTags(tags: List<Tag>, cells: Map<Long?, Cell>): List<TagRankRow> // drops tags with empty cells, sorts by amount desc then name, null-estimate rows last
  ```
  VM: Tags tab with no tag picked now emits `StatsContent.TagRanking(rows: List<TagRankRow>, measure: Measure)` (Spent/Earned switch = `setTagMeasure`); tapping a row = existing tag‑filter path with that tag; long‑press enters multi‑select (UI state only) and "Combine" applies the set with `TagMatchMode.ANY` default; the last tag view restores because `tagFilter` is already saved in `SavedStateHandle`.

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun sortsByAmountDesc() = assertEquals(listOf("trip","work"), rankTags(listOf(tag("work"), tag("trip")), mapOf(1L to cell(700), 2L to cell(1610))).map { it.tag.name })
  @Test fun largestIsFullBar() = assertEquals(1f, rankTags(listOf(tag("a")), mapOf(1L to cell(5))).single().fraction)
  @Test fun emptyTagsDropped() = assertTrue(rankTags(listOf(tag("a")), mapOf(1L to Cell.NONE)).isEmpty())
  @Test fun unconvertibleLast() = assertEquals("b", rankTags(listOf(tag("a"), tag("b")), mapOf(1L to unconvertible(), 2L to cell(1))).first().tag.name)
  @Test fun noTagsNoRows() = assertTrue(rankTags(emptyList(), emptyMap()).isEmpty())
  ```
- [ ] **Step 2:** `--tests "*TagRankingTest"` → FAIL.
- [ ] **Step 3:** Implement `rankTags` and the `TagRanking` content in the VM.
- [ ] **Step 4:** → PASS.
- [ ] **Step 5:** UI (mockups `stats-hierarchy.html`, `stats-tags.html`, `statistics.html`):
  - Categories: `SplitBar` + `GroupedCard` of `DrillRow`s — RoundIcon in category color, name, "N subcategories ›" (`hasChildren`) or "N transactions", amount, % badge in category color; `isDirect` row: dashed‑outline icon, muted, label `stats_directly_in` ("Directly in %1$s"). Breadcrumb chips (All › … › current) jump via existing path API; `BackHandler` pops a level when path non‑empty. List change animates with `AnimatedContent` (slide left on drill in, right on back) and the split bar weights animate.
  - Below categories on Expenses/Income: existing By account and By tag sections as `GroupedCard`s with split bars.
  - Tags tab: ranking list (# tile, name, amount, relative bar, count) with Spent/Earned `SegmentedPill`; tag detail: picked tags as primary chips with ✕ (removing the last returns to ranking), Any/All mini switch when ≥ 2, In/Out/Net figures + count + comparison, "Over time" wave with Spent/Earned/Moved, categories (same drill), By account, Recent + "All N ›" → existing Transactions preset; existing tag filter sheet behind ☰.
  - Accounts tab: grouped rows per account with period in/out/net (layout builder's call, same components).
- [ ] **Step 6:** Previews light + dark. Build + tests → green.
- [ ] **Step 7:** Commit `"Statistics: drill-down, sections and tag ranking"`.

---

### Task 13: Accounts list, archived section, detail

**Files:**
- Modify: `ui/accounts/AccountsScreen.kt`, `ui/accounts/AccountComponents.kt`, `ui/accounts/AccountDetailScreen.kt`, `ui/accounts/AccountViewModels.kt`
- Test: `ui/accounts/AccountsListLogicTest.kt` (new)

**Interfaces:**
- Consumes: `SplitBar`, `WaveChart(sparkline = true, color = accountColor)`, `GroupedCard`, `LocalShellFab` (Tasks 3, 6, 7); existing `analytics.observeBalanceSeries(accountId, buckets)` for sparklines (last 30 days, DAY buckets) and month change.
- Produces: `fun accountShares(items: List<Pair<Long, Long?>> /*accountId to converted balance minor*/): List<Pair<Long, Float>>` — negative and null balances count as 0; all‑zero → empty list.

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun sharesSumToOne() = assertEquals(1f, accountShares(listOf(1L to 300L, 2L to 100L)).sumOf { it.second.toDouble() }.toFloat(), 0.001f)
  @Test fun negativeIgnored() = assertEquals(listOf(1L to 1f), accountShares(listOf(1L to 50L, 2L to -20L)).filter { it.second > 0f })
  @Test fun allZeroEmpty() = assertTrue(accountShares(listOf(1L to 0L, 2L to null)).isEmpty())
  ```
- [ ] **Step 2:** `--tests "*AccountsListLogicTest"` → FAIL.
- [ ] **Step 3:** Implement `accountShares`; → PASS.
- [ ] **Step 4:** UI (mockups `accounts-list-v5.html`, `accounts-archived.html`, `accounts.html` right phone):
  - List: title + Transfer round button; header without card: "TOTAL · <CCY>" + `AmountText` total (existing valuation/≈ + outdated‑rate warning kept as a small line), "N accounts" at right, `SplitBar` of shares in account colors. **No currency chips, no month badge.**
  - Active accounts in one `GroupedCard`: 36dp rounded‑square icon tile (account color @14%), name, "Type · CCY", 52×22dp sparkline, balance + this‑month change (`finance.income` / `finance.negative`, "—" when 0).
  - Archived: bottom row (archive icon, "Archived" + "N accounts · hidden from totals", chevron rotating with `bouncy`); expands via `AnimatedVisibility(expandVertically)` into a greyed `GroupedCard` (alpha 0.6, no sparkline) with a primary "Restore" chip per row (existing unarchive action); row tap → detail. Remove the Active/Archived segmented control.
  - FAB via `LocalShellFab`: label "Account", no speed‑dial → `Routes.ACCOUNT_NEW`.
  - Detail: card with vertical gradient from account color @25% to `surfaceContainerLow`, type icon, "TYPE · CCY", balance, sparkline wave, opening balance line, "+X this month"; round actions Expense / Income / Transfer / Stats (existing routes with `sourceAccountId`); Recent `GroupedCard`; ⋯ → Edit / Archive|Unarchive / Delete with existing dialogs; archived banner kept.
  - Strings: `accounts_total`, `accounts_count` (plural), `accounts_archived_row`, `accounts_hidden_from_totals`, `accounts_restore`, `fab_account`.
- [ ] **Step 5:** Previews light + dark (several accounts, one account, archived expanded). Build + tests → green.
- [ ] **Step 6:** Commit `"Accounts: compact list, archived section, detail card"`.

---

### Task 14: Organize & App screens and forms

**Files:**
- Modify: `ui/categories/CategoriesScreens.kt`, `ui/categories/CategoryFormScreen.kt`, `ui/tags/TagsScreen.kt`, `ui/recurring/RecurringScreen.kt`, `RecurringDetailScreen.kt`, `RecurringEditScreen.kt`, `RecurringFormComponents.kt`, `RecurringUi.kt`, `ui/settings/SettingsScreen.kt`, `ui/data/DataScreen.kt`, `ui/data/CsvExportScreen.kt`, `ui/accounts/AccountEditScreen.kt`, `ui/common/Pickers.kt`, `EntityPickers.kt`, `DateTimePickerDialog.kt`, `DateRangePickerDialog.kt`, `TagComponents.kt`
- Test: `ui/recurring/RecurringListLogicTest.kt` (extend)

**Interfaces:**
- Consumes: Task 6 components. ViewModels unchanged.
- Produces: `fun nextThirtyDays(items: List<UpcomingItem>, today: LocalDate): Pair<Long, Long>` (out, in minor units in display scope) in `RecurringUi.kt`, if the existing recurring list state lacks it.

- [ ] **Step 1: Write the failing test** (only if `nextThirtyDays` is needed)
  ```kotlin
  @Test fun sumsOnlyNext30Days() = assertEquals(486L to 2600L, nextThirtyDays(listOf(out(486, inDays = 3), inc(2600, inDays = 18), out(100, inDays = 31)), today))
  ```
- [ ] **Step 2:** Run → FAIL; implement; run → PASS.
- [ ] **Step 3:** Restyle per spec §5.6–5.7 and `more-and-lists.html`: Categories (Expense|Income|Transfer `SegmentedPill`, one tree card with indented children + connector line, expand/collapse with chevron spring, tap edit, existing long‑press move); Recurring ("Next 30 days" card, UPCOMING `GroupedCard` with "Monthly · in N days" + date, PAUSED card faded with switch bound to existing pause/resume); Tags, Settings (Theme System/Light/Dark as `SegmentedPill`, Language, Display currency, About), Data, CSV export, all form screens and pickers → `GroupedCard` sections, `RoundIcon`s, `FilterPill`s, `FinoraSheet` for choices, full‑width `PrimaryButton` CTA. No behavior changes.
- [ ] **Step 4:** Light + dark previews per screen. Build + tests → green.
- [ ] **Step 5:** Commit `"Restyle organize, settings, data and forms"`.

---

### Task 15: Motion pass, accessibility check, design‑system doc

**Files:**
- Modify: screens touched above (only where motion is missing), `docs/ui-design-system.md`

- [ ] **Step 1:** Add the springy press (`Modifier.pressScale()` in `designsystem/motion/Motion.kt`: scale 0.96 with `FinoraMotion.press`, disabled when `rememberReduceMotion()`) to buttons, chips, rows, keypad keys; hero `AmountText` animates on value change; haptics on: wheel step, FAB open, save success (editor), Home page snap. Verify every animation in Tasks 3–14 falls back to instant/crossfade when reduce‑motion is on.
- [ ] **Step 2:** Accessibility sweep: icon‑only buttons have `contentDescription`; touch targets ≥ 48dp (`Modifier.minimumInteractiveComponentSize()`); check `negative`, `onSurfaceVariant`, `primary`‑on‑container ratios in both themes.
- [ ] **Step 3:** Rewrite `docs/ui-design-system.md` for the Obsidian system: package map of `designsystem/` (what goes where, the boundary rules the guard test enforces, every new component goes into the catalog), tokens (§3 of spec), components (§4) with their file paths, motion rules (§6), money rules kept (native currency, `≈`, tappable summaries). Remove the indigo palette and the "minimal motion" principle.
- [ ] **Step 4:** `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; install on a device/emulator and walk: new expense (amount → shelf → save + another), transfer between currencies, Home paging + wheels, Statistics pinch/pan + drill to level 3 + tag ranking, Accounts archive/restore, More sheet → each child, light/dark switch, reduce‑motion on.
- [ ] **Step 5:** Commit `"Motion pass, accessibility fixes, design-system doc"`.
