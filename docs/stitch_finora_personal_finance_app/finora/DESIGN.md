---
name: Finora
colors:
  surface: '#0f1412'
  surface-dim: '#0f1412'
  surface-bright: '#353a37'
  surface-container-lowest: '#0a0f0c'
  surface-container-low: '#181d1a'
  surface-container: '#1c211e'
  surface-container-high: '#262b28'
  surface-container-highest: '#313632'
  on-surface: '#dfe4de'
  on-surface-variant: '#c9c4d5'
  inverse-surface: '#dfe4de'
  inverse-on-surface: '#2c322e'
  outline: '#928f9e'
  outline-variant: '#484553'
  surface-tint: '#c8bfff'
  primary: '#c8bfff'
  on-primary: '#2e138e'
  primary-container: '#7565d7'
  on-primary-container: '#fffeff'
  inverse-primary: '#5d4cbd'
  secondary: '#c8bfff'
  on-secondary: '#2f1d7d'
  secondary-container: '#463795'
  on-secondary-container: '#b7abff'
  tertiary: '#5cddab'
  on-tertiary: '#003826'
  tertiary-container: '#008661'
  on-tertiary-container: '#fcfffa'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e5deff'
  primary-fixed-dim: '#c8bfff'
  on-primary-fixed: '#190064'
  on-primary-fixed-variant: '#4532a4'
  secondary-fixed: '#e5deff'
  secondary-fixed-dim: '#c8bfff'
  on-secondary-fixed: '#1a0063'
  on-secondary-fixed-variant: '#463795'
  tertiary-fixed: '#7bf9c6'
  tertiary-fixed-dim: '#5cddab'
  on-tertiary-fixed: '#002115'
  on-tertiary-fixed-variant: '#005139'
  background: '#0f1412'
  on-background: '#dfe4de'
  surface-variant: '#313632'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '600'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-sm:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 38px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Inter
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '500'
    lineHeight: 26px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '500'
    lineHeight: 22px
    letterSpacing: -0.01em
  balance-hero:
    fontFamily: Inter
    fontSize: 34px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.03em
  balance-card:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.02em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  mono-metric:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  margin: 1.25rem
  margin-sm: 1rem
  margin-lg: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style
The design system establishes a focused, premium, and tranquil financial management environment. Built for discerning users navigating personal wealth, investments, and budgeting, the interface replaces traditional fintech friction and panic with composure, precision, and restrained luxury.

The aesthetic merges **Dark Modernism** with **Atmospheric Layering**:
- **Palette Temperament:** A twilight baseline of deep botanical graphite grounded by muted sage undertones, rejecting harsh pitch-black in favor of organic depth.
- **Accent Strategy:** Soft lavender and controlled purple luminescence replace ubiquitous electric blue, imbuing value transfer and data manipulation with quiet refinement.
- **Form & Flow:** Large corner radii, hyper-tactile pill interactions, and soft low-opacity perimeter strokes evoke bespoke physical hardware cards.
- **Ergonomics:** Designed strictly for fluid, one-handed mobile interactions, prioritizing lower-canvas reach zones and thumb-driven navigation hubs.

## Colors
The color architecture relies on a specialized dark graphite spectrum infused with faint chloric/sage undertones, preserving low eye fatigue and high dynamic range.

### System Palette Roles
- **Canvas Base (`#101512`):** Deep graphite floor; non-interactive backdrop anchoring all viewport views.
- **Surface Layer 1 (`#1A241E`):** Dark green-gray base for primary cards, balance sheets, and segmented containers.
- **Surface Layer 2 (`#202D25`):** Elevated tonal surface for active toggles, nested cells, modals, and sheets.
- **Interactive Accent (`#7565D7`):** Primary action violet utilized for solid interactive targets, key action buttons, and active tabs.
- **Luminescent Accent (`#A99BFF`):** Soft ethereal lilac used for micro-highlights, metric focus states, and directional badges.
- **Affirmative / Inflow (`#55D6A5`):** Mint green indicative of portfolio gains, positive cash flows, and validated entries.
- **Critical / Outflow (`#FF858B`):** Desaturated coral red indicating debt, expenses, and system warnings without inducing distress.
- **Text Primary (`#E8EEE9`):** High-legibility off-white with cool jade bias for titles, balance displays, and hero figures.
- **Text Secondary (`#9AA99F`):** Muted gray-green for sub-labels, currency symbols, timestamps, and chart indices.
- **Boundary & Stroke (`#2B3930`):** Precise sub-surface divider defining edges against ambient dark backgrounds.

## Typography
Typographic implementation centers on structured legibility, high scannability, and numeric stability across fluctuating financial ledgers.

- **Tabular Numerals Mandate:** All numerical data—including currency symbols, transaction timestamps, portfolio percentages, and account matrices—must strictly employ OpenType tabular figures (`font-variant-numeric: tabular-nums; font-feature-settings: "tnum"`). This prevents jitter and visual misalignment across vertically scrolling lists and live charts.
- **Optical Kerning:** Display metrics above 24px utilize tightly compressed letter-spacing (`-0.02em` to `-0.03em`) to project modern executive presence. Small labels (`11px`) leverage expanded spacing (`+0.04em`) to safeguard legibility under OLED contrast states.
- **Hierarchy Distinction:** Bold weights are explicitly avoided in favor of crisp Medium (`500`) and Semi-Bold (`600`) steps, preserving visual lightness across dense screens.

## Layout & Spacing
The layout model operates as a mobile-optimized fluid single-column spine, engineered for modern edge-to-edge mobile screens and vertical thumb gestures.

### Layout Mechanics
- **Grid Architecture:** 4-column dynamic fluid layout on mobile viewports with an interior `gutter` of `1rem` (16px) and an outer safe canvas `margin` of `1.25rem` (20px).
- **Horizontal Boundaries:** Critical actionable items must rest within the comfortable primary sweep arc of the lower thumb zone (the bottom 45% of the mobile viewport).
- **Spatial Rhythm:** Built strictly on an 8pt base grid with a 4pt micro-step. Use `space-xs` (4px) and `space-sm` (8px) exclusively for internal atom gaps (such as icon-to-label offsets and badge padding). Use `space-lg` (16px) and `space-xl` (24px) for distinct module separations.
- **Safe Area Anchors:** Permanent bottom actions, nav bars, and drawers anchor cleanly to hardware gesture indicators with standard environmental padding (`env(safe-area-inset-bottom) + 0.75rem`).

## Elevation & Depth
Depth in the system is forged through tonal chromatic progression, subtle rim lighting, and restrained focal luminescence, rather than traditional drop shadows.

### Elevation Architecture
1. **Level 0 (Canvas Base):** Solid `#101512`. Infinite depth; zero reflection.
2. **Level 1 (Card Resting):** Surface `#1A241E` backed by an ultra-fine structural stroke (`1px solid #2B3930`). Visual lift is reinforced by a faint inner zenith glow: `inset 0px 1px 0px 0px rgba(232, 238, 233, 0.05)`.
3. **Level 2 (Active Sheets / Elevated Modals):** Surface `#202D25` elevated via dark-field occlusion: `box-shadow: 0px 12px 32px -4px rgba(0, 0, 0, 0.45)`.
4. **Level 3 (Action Luminescence):** Primary interactive elements (such as transfer buttons and floating action pills) utilize a focused accent aura: `box-shadow: 0px 8px 24px -2px rgba(117, 101, 215, 0.28)`.

### Optical Highlights & Glass
Glass surfaces (such as top navigation bars and floating transaction summaries) utilize heavy backdrop saturation filters (`backdrop-filter: blur(20px) saturate(160%)`) over `#1A241E` at 80% opacity, bordered by `#2B3930`.

## Shapes
The shape philosophy favors smooth, generous radii that echo luxury hardware aesthetics, creating an approachable and ergonomic interface.

- **Primary Cards & Containers:** Standardized with expansive curvetures (`1.5rem` to `1.75rem` / `rounded-2xl` through `rounded-3xl`) to present account blocks, chart frames, and budget widgets as tangible, pebble-like surfaces.
- **Interactive Targets:** Buttons, active chips, segment controls, and input search boxes embrace complete pill shaping (`9999px` / full pill).
- **Nested Insets:** Internal elements within large cards scale down to an inner radius of `0.75rem` to `1rem` to maintain strict concentric corner alignment.

## Components

### Buttons
- **Primary Action Button:** Full pill height (`52px` on mobile), filled with `#7565D7`, text in `#E8EEE9` (`label-md`), framed by an ambient glow (`rgba(117, 101, 215, 0.3)`). Under press/touch, the surface shifts to scale `0.98` with color `#6554C8`.
- **Secondary Button:** Surface `#1A241E`, border `1px solid #2B3930`, text in `#E8EEE9`. Active state shifts the surface to `#202D25`.
- **Tertiary / Ghost Button:** Transparent background, text in `#A99BFF`, zero border. Reserved strictly for non-destructive, tertiary flows.

### Cards & Asset Tiles
- **Balance / Hero Account Card:** Built on `#1A241E` with a subtle top edge light. Displays balance using `balance-hero` in `#E8EEE9`. Secondary asset distributions feature subtle `#55D6A5` or `#FF858B` trending pills.
- **Nested Detail Cell:** Set on elevated surface `#202D25` with `1rem` radius. Acts as line-item separators within parent cards.

### Transaction Lists
- **List Item Architecture:** Zero-divider configuration. Visual separation is accomplished using `space-md` gaps.
- **Leading Indicator:** `44px` circular or soft-curved avatar badge tinted with `#202D25` housing an icon colored with `#A99BFF` or merchant branding.
- **Center Body:** Merchant/payee in `#E8EEE9` (`headline-sm`) positioned directly above the transaction category in `#9AA99F` (`body-sm`).
- **Trailing Metas:** Absolute tabular figures in `#E8EEE9` (`mono-metric`), with positive receipts explicitly tinted in `#55D6A5` prefixed with `+`.

### Chips & Filter Pills
- **State Neutral:** Pill shape, background `#1A241E`, border `1px solid #2B3930`, label in `#9AA99F` (`label-sm`).
- **State Selected:** Background `#202D25`, border `1px solid #A99BFF`, text in `#A99BFF` accompanied by a micro inner purple indicator.

### Input Fields & Keypads
- **Form Text Field:** Height `54px`, fully rounded pill or `1rem` rounded rectangle. Surface `#1A241E`, border `1px solid #2B3930`. Focus transition invokes a `1px solid #A99BFF` perimeter stroke and a subtle lavender glow ring.
- **Numerical Financial Keypad:** Minimalist grid layout with `display-sm` glyphs, haptic click-back feedback, zero button borders, and subtle active background rings (`#202D25`).

### Checkboxes & Toggle Switches
- **Toggle Switches:** `48px × 28px` track. Inactive: track `#1A241E` with border `#2B3930` and knob `#9AA99F`. Active: track `#7565D7` with white-tinted knob `#E8EEE9` translating smoothly along the horizontal axis.
- **Selection Checks:** `20px` circle with a `1.5px` border in `#2B3930`. On selection, fills with `#7565D7` containing a centered off-white checkmark icon.

### Quick Actions Floating Bar
- Detached, elevated bottom navigation dock floating `1rem` above the bottom screen safe area. Uses glassmorphic `#1A241E` at 85% opacity, `rounded-full`, bordered by `#2B3930`, containing thumb-optimized icon actions spaced evenly with haptic-ready states.