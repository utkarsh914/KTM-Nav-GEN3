# Design system & UX standards

One place describing the shared tokens every screen should use, so the app stays
consistent and future additions inherit the same feel. When adding UI, reach for
these before hand-rolling values.

## Colour & accent
- All colours come from the reactive `Ktm` accessor (`ui/theme/DesignTokens.kt`).
  Never hard-code hex in screens (map/track colours are the only exception and
  live in `RideTrackMap.kt` / route-preview constants).
- Brand supplies the **neutrals + wordmark**; the **accent** is chosen
  independently by the rider (Settings → Appearance → Accent colour) and stored
  in `AppSettings.accentColorId`. `null` = follow the brand's own accent.
- Accent presets live in `AccentPalettes` (`DesignTokens.kt`); each carries a
  contrast-correct `onAccent`. Read the active accent with `Ktm.Orange` /
  `Ktm.OnAccent` (names are historical; they resolve to the current accent).
- A **custom** accent is any `#RRGGBB` stored as the `accentColorId`; `accentFor`
  parses it via `parseHexColor`/`customAccent` (auto-deriving deep + onAccent).
  The Settings picker offers presets **and** a full HSV colour picker.
- To show a brand's *own* accent regardless of the override use
  `identityFor(brand).accent`.

## Typography (`ui/theme/Type.kt`)
- Families: `BarlowCondensed` (headings/labels), `Barlow` (body),
  `JetBrainsMono` (numeric/technical).
- Prefer the named roles in `AppText` (`CardTitle`, `SectionLabel`, `Body`,
  `BodySmall`, `StatLabel`, `StatValue`, `ButtonLabel`) over ad-hoc
  fontFamily/size/weight.
- **Font mode (Brand vs System).** The public `Barlow`/`BarlowCondensed`/
  `JetBrainsMono` families are reactive getters that read `AppFonts.mode`
  (mirroring the `Ktm` colour object): in `FontMode.BRAND` (default) they return
  the bundled families; in `FontMode.SYSTEM` they collapse to
  `FontFamily.Default` (the phone font). Because every `fontFamily = Barlow`
  call site reads that Compose state, flipping the mode re-fonts the whole app
  live with no per-screen edits. `AppText` roles and `appTypography()` (used by
  `OpenDashTheme`) resolve the same way.
- Persisted as `AppSettings.fontMode`, applied at startup in
  `OpenDashApplication` (no first-frame flash) and switchable in
  **Settings → Appearance → App font**.

## Motion (`ui/theme/Motion.kt`)
- Durations: `Motion.Fast` (160) for exits, `Motion.Medium` (240) for
  enter/move, `Motion.Slow` (320) for hero transitions.
- Easing: `Motion.StandardEasing`; springs: `Motion.pressSpring()` (press),
  `Motion.popSpring()` (select/save "pop").
- Reuse the transition builders: the shell's NavHost transitions (screen changes),
  `bottomSheetTransition(rising)` (full-screen panels), `driftTransition()`
  (card/overlay stage changes). Don't inline new `tween(...)` specs in screens.

## Haptics (`ui/theme/Haptics.kt`)
- `val haptics = rememberHaptics()` then `haptics.tap()` / `toggle(on)` /
  `confirm()` / `warn()` / `longPress()`.
- Shared components (`KtmComponents.kt`) already fire the right haptic, so most
  screens get it for free. Bespoke clickables should call the matching one:
  taps → `tap()`, switches → `toggle()`, primary/positive → `confirm()`,
  destructive/close → `warn()`, long-press → `longPress()`.

## Controls & chrome
- Full-width actions: `KtmPrimaryButton` / `KtmOutlineButton`.
- Icon chrome: `IconPill` (square 52dp), `CircleBackButton`, `SquareMapControl`
  (compass/recenter — used on every map screen), `ConnectPill` /
  `ConnectIconPill`.
- **Map controls standard location:** recenter/locate + compass live in a
  bottom-right stack (compass on top, shown only when the map is rotated) on the
  browse map, active navigation and the ride replay — keep new map screens the
  same.
- Radii: `Ktm.RadiusCard` (16), `Ktm.RadiusButton` (12), `Ktm.RadiusRow` (13);
  control height/size: `Ktm.ControlHeight` (52).
- Press feedback: use `rememberPressScale(interaction)` + `graphicsLayer` (see
  existing components) so held controls scale to `Motion.PressedScale`.
