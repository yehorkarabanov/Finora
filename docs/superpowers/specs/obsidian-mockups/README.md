# Obsidian redesign — final mockups

Visual reference for `../2026-11-11-obsidian-redesign-design.md`. Throwaway HTML from brainstorming: match look, spacing, motion; don't port code. **Where a mockup and the spec disagree, the spec wins.**

These were served inside a brainstorm frame, so opened directly some headings/option wrappers render with browser defaults. The phone frames themselves are self-contained.

| File | Screen | Out of date in the mockup (follow the spec) |
|---|---|---|
| `home-scroll.html` | Home: swipeable net wave, Days/Months, period chip | Header shows a search button — removed; only ⋯ stays. |
| `home-wave-zero.html` | Home: zero line, mint above / red below, Days vs Months | Same search button — removed. |
| `range-wheels-v2.html` | Range wheels sheet | Spec adds "Today" / "Custom days" quick options on Statistics. |
| `add-tx-sheet-v2.html` | Add/Edit: keypad + details shelf | Transfer shelf (From / To / They receive) not drawn. |
| `fab-above.html` | Floating nav bar + Add FAB + speed‑dial | — |
| `transactions.html` | Transactions | FAB position: follow `fab-above.html` (above the bar, not beside it). |
| `accounts-list-v5.html` | Accounts list | Archived row: follow `accounts-archived.html`. |
| `accounts-archived.html` | Accounts list with improved archived section | — |
| `accounts.html` | **Account detail only** (right phone) | Left phone (stacked cards list) was rejected. FAB position per `fab-above.html`. |
| `statistics.html` | Statistics: tabs, hero, trend, categories | Period block: follow `stats-tags.html`. Trend gestures (pan/pinch) not drawn. Search icon n/a. |
| `stats-tags.html` | Tags tab detail + final period block | Empty "pick a tag" state replaced by the tag ranking in `stats-hierarchy.html`. |
| `stats-hierarchy.html` | Category drill‑down (interactive) + Tags ranking | — |
| `more-and-lists.html` | More sheet, Categories tree, Recurring | — |
