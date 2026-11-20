# Design system & UX standards

The tokens and components every screen uses, so the app stays consistent and new
screens inherit the same feel. The reference mock-ups are in
`overhaul/design/redesign.html` (local, not committed); the code follows them.
When adding UI, reach for these before hand-rolling values, and run
`overhaul/scripts/check-ui-rules.sh` (a local-only script; the rules are listed below).

## Theme (`ui/theme/`)
- `KtmTheme(darkTheme, accent) { … }` wraps the app (`KtmApp`). Read tokens with
  `KtmTheme.colors`, `KtmTheme.accent`, `KtmTheme.type`, `KtmTheme.motion`,
  `KtmTheme.riding`; Material 3 components see the same palette through
  `ktmColorScheme`.
- **Colours** (`KtmColors.kt`): neutrals `bg`, `surf`, `surf2`, `surf3`, lines
  `line`/`line2`, text `fg`/`fg2`/`fg3`, status `ok`/`warn`/`bad`/`info` with
  containers, inverse `inv`/`onInv` (snackbars), route colours. Dark and light
  sets: `KtmDarkColors`, `KtmLightColors`.
- **Accent** (`AccentTones.kt`): presets in `AccentPalettes`, or any `#RRGGBB`
  (`customAccent`, with the contrast derived and checked). `KtmTheme.accent` has
  `fill`/`onFill`, `container`/`onContainer`, `text`. Stored as
  `accentColorId`; `null` is KTM orange.
- **Riding scope** (`RidingTheme`, `RidingColors.kt`): riding surfaces follow
  Day/Night independently of the app theme (`NavThemeMode`, sunrise/sunset from
  `SunTimes`). Inside it `KtmTheme.riding` has `guidance`, `surface`,
  `overspeed`… and `KtmTheme.colors` resolve to the riding palette.
- **Type** (`KtmTypography.kt`, `Type.kt`): M3 roles plus `KtmTheme.type` extras
  (`buttonCompact`, riding roles, `mono` for logs and codes). Families follow
  `AppFonts.mode` (brand fonts or the phone's). No `.sp` in screens.
- **Layout** (`KtmLayout.kt`): `KtmSpacing` (4/8/12/16/24/32/48), `KtmShapes`
  (Badge 8, Control 12, Card 16, Sheet 28, Pill), `KtmElevation` (0/2/6/12),
  touch targets `targetRest` 48, `targetPrimary` 56, `targetRiding` 64, panel
  widths (352, 320 below 800 dp), top bar 64.
- **Icons**: `KtmIcons` only (own vectors, one set). No Material icons dependency.

## Components (`ui/components/`)
`KtmButton` (Primary, Tonal, Neutral, Outline, Text, Danger, DangerTonal; sizes
Small 48, Rest 56, Riding 64, Tile 72), `KtmIconButton`, `MapControlButton`,
`KtmFab`, `ListRow`, `KtmSwitchRow`, `KtmCheckboxRow`/`KtmRadioRow`, `KtmSelectionBar`, `KtmCard`, `KtmBadge`,
`KtmFilterChip`, `KtmSegmented`, `KtmSearchField`, `KtmTextField`, `KtmStepper`,
`KtmSwitch`, `KtmColorPicker`, `KtmTopBar`, `LargeTitle`, `SectionHeader`,
`EmptyState`, `InfoBanner`, `FixItBanner`, `Skeleton`/`SkeletonRow`, `StatTile`,
`ActivityStrip`, `BikeStatusPill`, `DashPreview`, `TurnIconRef`, `KtmDialog`,
`KtmModalSheet`/`KtmSheetContent`, `KtmSnackbar`, `LandscapePanel`.
Screens are written as `*ViewModel` (state + actions), a stateless `*Content`
that previews and tests render, and a thin `*Screen` that wires them.

## Motion (`ui/theme/KtmMotion.kt`)
- Every animation takes its spec from `KtmTheme.motion` (`KtmMotionSpecs`):
  `spatialFast/Default/Slow` (springs), `effectsFast/Default` (fades),
  `ridingSpatial(delayMs)`, `ridingQuick()` (160 ms), `ridingFade(delayMs)`,
  `ridingColor()` (100 ms). Riding surfaces: at most 250 ms, no overshoot,
  numbers never animate.
- **Remove animations** (`rememberReducedMotion`, read live): spatial moves become
  short crossfades with no offset, camera moves jump. Code that slides must zero
  its offset when `motion.reduced`.
- Shared elements: `SharedElements`/`rideContainer` (ride card to replay page),
  `TripBarSheet` (trip bar to trip sheet), `tripStripBounds` (trip bar to the
  activity strip). The sheets are drawn in the tree (not in a window) so they
  can share bounds.
- Tabs fade through with a 0.92 scale; pushed pages share the X axis (30 dp);
  predictive back previews the page shrinking to 90 %. Pages without a map (`listPage`: settings pages, pickers,
  logs, edit ride) take 28 dp corners while they leave; map pages stay square (a clip over the Nav SDK's map view
  isn't worth the risk).
- Landscape: one 352 dp (320 below 800 dp) floating panel on the left, also for the browse sheet; the rail shows the
  bike's state as a dot on Navigate. The trim bar and its time labels stay left to right in every language, like the
  chart above them.

## Haptics (`ui/theme/Haptics.kt`)
`val haptics = rememberHaptics()`: `tap()`, `toggle(on)`, `confirm()`, `reject()`
(destructive or blocked), `segmentTick()`, `clockTick()` (steppers and detents),
`longPress()`. Touches only; never for riding events (gloves and a vibrating
mount hide them: sound and the dash carry those). Shared components already fire
the right one.

## Feedback
Snackbars through `LocalFeedback` (`Feedback.snackbar(message, actionLabel, …)`),
never a Toast; Undo lasts 8 s (`Feedback.ACTION_MS`). While riding the same events
are chips (`RidingChips`). Confirms use `KtmDialog` (riding variant stacks 64 dp
buttons with the safe choice on top).

## Accessibility
Targets 48 dp at rest, 56 for primary actions, 64 riding. Every interactive
element has a role or label; groups of choices use `selectableGroup`; custom
controls expose range info and actions (`KtmColorPicker`); sheets are titled
panes with a Close action; glyphs are decorative unless given a description
(`TurnIconRef(contentDescription = …)`). Font scale 1.3 and TalkBack are part of
the QA list.

## Rules (grep-checked by the local-only `overhaul/scripts/check-ui-rules.sh`)
No `Toast`, no raw `AlertDialog` (use `KtmDialog`), no Material icons, no
`Color(0x…)` or `.sp`/`.em` in screens, no `AppSettings(…)` in `ui/` (use the
container's `SettingsRepository`), no legacy `Ktm.*` accessor. Strings live in
`strings.xml` (`<feature>_<element>_<meaning>`), distances are metric.
