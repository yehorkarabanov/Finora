---
name: Obsidian Wealth
colors:
  surface: '#121314'
  surface-dim: '#121314'
  surface-bright: '#39393a'
  surface-container-lowest: '#0d0e0f'
  surface-container-low: '#1b1c1d'
  surface-container: '#1f2021'
  surface-container-high: '#292a2b'
  surface-container-highest: '#343536'
  on-surface: '#e3e2e3'
  on-surface-variant: '#bacbbe'
  inverse-surface: '#e3e2e3'
  inverse-on-surface: '#303031'
  outline: '#849589'
  outline-variant: '#3b4a41'
  surface-tint: '#00e297'
  primary: '#6dffba'
  on-primary: '#003822'
  primary-container: '#00e599'
  on-primary-container: '#00613e'
  inverse-primary: '#006c46'
  secondary: '#c6c6cb'
  on-secondary: '#2e3035'
  secondary-container: '#47494d'
  on-secondary-container: '#b7b8bd'
  tertiary: '#dfe5f4'
  on-tertiary: '#2b313c'
  tertiary-container: '#c3c9d8'
  on-tertiary-container: '#4e5461'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#4dffb2'
  primary-fixed-dim: '#00e297'
  on-primary-fixed: '#002112'
  on-primary-fixed-variant: '#005234'
  secondary-fixed: '#e2e2e7'
  secondary-fixed-dim: '#c6c6cb'
  on-secondary-fixed: '#1a1c20'
  on-secondary-fixed-variant: '#45474b'
  tertiary-fixed: '#dde2f1'
  tertiary-fixed-dim: '#c1c6d5'
  on-tertiary-fixed: '#161c26'
  on-tertiary-fixed-variant: '#414753'
  background: '#121314'
  on-background: '#e3e2e3'
  surface-variant: '#343536'
typography:
  display-hero:
    fontFamily: Space Grotesk
    fontSize: 56px
    fontWeight: '700'
    lineHeight: 64px
  display-hero-mobile:
    fontFamily: Space Grotesk
    fontSize: 44px
    fontWeight: '700'
    lineHeight: 52px
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  label-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
  label-sm:
    fontFamily: Manrope
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
  keypad-num:
    fontFamily: Space Grotesk
    fontSize: 26px
    fontWeight: '500'
    lineHeight: 32px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.25rem
---

## Brand & Style

This design system targets modern, financially conscious individuals seeking clarity, high efficiency, and absolute control over their capital and portfolio. Drawing inspiration from top-tier fintech experiences like Revolut, the aesthetic fuses minimalism with a sleek dark-mode posture: deep obsidian tones, high-legibility typographic hierarchies, and luminous emerald accents.

The interface prioritizes immediacy, security, and tactile responsiveness. Visual weight is concentrated on numerical data, financial movement, and key conversion paths, while structural overhead is suppressed to near-invisibility.

## Colors

The palette revolves around deep pitch obsidian (`#0C0D0E`) and rich charcoal containers (`#16181B`, `#1E2024`), creating an immersive backdrop that eliminates eye strain while framing financial metrics.

- **Primary (`#00E599`)**: Electric mint/emerald green used strategically for positive performance metrics, primary call-to-action touchpoints, active sliders, and completion states.
- **Secondary (`#1E2024`)**: Elevated surface fill for interactive pills, bottom action containers, and numerical keypads.
- **Tertiary (`#6C727F`)**: Subdued secondary text, conversion indicators, and inactive control paths.
- **Neutral (`#0C0D0E`)**: Deep root canvas background, anchoring the display. Pure white (`#FFFFFF`) serves as the primary typographic foreground for unmatched contrast and legibility.

## Typography

The type hierarchy combines **Space Grotesk** for numerical power and impact with **Manrope** for clarity and geometric stability in data presentation. 

- Use **Space Grotesk** for balance indicators, keypad numbers, transaction amounts, and high-level headings. Its geometric stance delivers technical authority.
- Use **Manrope** across all supporting micro-copy, account descriptions, metadata, button text, and legal disclosures.
- Numerical displays should always use tabular numerals where possible to avoid horizontal jitter during balance updates and animations.

## Layout & Spacing

The layout model is mobile-first, utilizing a vertical stack layout bounded by strict safe areas and dynamic padding. 

- **Outer Margins:** Fixed 20px (`1.25rem`) padding on mobile viewports ensures touch-friendly edge margins while maximizing usable real estate.
- **Rhythm & Stacking:** Financial figures occupy generous vertical breathing room (`2.25rem`), separating primary transaction totals from sub-metrics and selector pills.
- **Interaction Clustered Bottom Bar:** Action elements—such as interactive slider pills and primary swipe-to-confirm surfaces—pin strictly to the base of the viewport with safe-area spacing to support natural thumb reach.

## Elevation & Depth

Visual hierarchy is maintained via tonal layering and surface luminance rather than dramatic drop shadows.

- **Base Canvas:** The deepest tone (`#0C0D0E`) absorbs background light.
- **Layer 1 (Containers & Modules):** Subtle lift using `#16181B` or `#1E2024`. Outlines are kept soft and minimal—utilize a 1px border with 6% to 10% white opacity (`rgba(255, 255, 255, 0.08)`).
- **Layer 2 (Interactive Floating Controls):** Sliders, bottom action buttons, and modal trays use darker translucent fills (`rgba(30, 32, 36, 0.95)`) backed by a 20px background blur (`backdrop-filter: blur(20px)`).
- **Light accents & feedback:** Subtle radial halos behind active emerald indicators provide an ambient glow without cluttering structural edges.

## Shapes

The design system adopts a **Pill-shaped (level 3)** geometry for all primary interactive controls, creating approachable, smooth targets that contrast sharply against the dark architectural framework.

- Buttons, chips, source-account selectors, and swipe bars are fully rounded capsules (`rounded-full` or `9999px`).
- Input modules, large card sheets, and modal bottoms use large 24px (`1.5rem`) corner radii for a soft, tailored finish.

## Components

### Buttons & Interactive Controls
- **Swipe-to-Confirm / Action Pills:** Full-width pill container with dark charcoal background (`#1E2024`) containing a circular thumb button (`#FFFFFF` or `#00E599`) that slides along an arrow track. Text is centered in medium-weight Manrope.
- **Account & Source Selectors:** Compact horizontal capsules with subtle border outlines (`rgba(255,255,255,0.08)`) containing an account indicator icon, masked account identifier (`Onyx •• 4821`), balance total, and twin selection arrows.

### Number Pad / Keypad
- Fixed 3x4 layout integrated seamlessly without borders.
- Number keys rendered in `Space Grotesk` (26px), responding on press with a subtle circular dark gray wash (`#25282E`) to give immediate haptic confirmation.
- Lower row houses the decimal point on the left and a backspace glyph on the right.

### Input Fields & Amount Displays
- Unboxed, borderless hero amount displays centered vertically. 
- Currency glyph and value are paired in prominent display typography (`44px` to `56px`), with converted secondary values (e.g., `≈ 1.00 USDC`) stacked underneath in subdued tertiary gray (`#6C727F`).

### Cards & Sheets
- Elevated contextual sheets leverage dark graphite surfaces (`#16181B`) with 24px top radius corners, standard 16px internal padding, and minimal border dividers.