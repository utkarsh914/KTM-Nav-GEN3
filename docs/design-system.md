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
Small 48, Rest 56, Riding 64, Tile 72, TileCompact 80, TileLarge 84), `KtmIconButton`, `MapControlButton`,
`KtmFab`, `ListRow`, `KtmSwitchRow`, `KtmCheckboxRow`/`KtmRadioRow`, `KtmSelectionBar`, `KtmCard`, `KtmBadge`,
`KtmFilterChip`, `KtmSegmented`, `KtmSearchField`, `KtmTextField`, `KtmStepper`,
`KtmSwitch`, `KtmColorPicker`, `KtmTopBar`, `LargeTitle`, `SectionHeader`,
`EmptyState`, `InfoBanner`, `FixItBanner`, `Skeleton`/`SkeletonRow`, `StatTile`,
`ActivityStrip`, `BikeStatusPill`, `DashPreview`, `TurnIconRef`, `KtmDialog`,
`KtmModalSheet`/`KtmSheetContent`, `KtmSnackbar`, `LandscapePanel`, `KtmStep`/`StepState` (a checklist line: tick, number or spinner), `KtmStepProgress`/`KtmStepHeader` (the first-run bar with Back under it), `KtmMenuItem`, `TurnIconGlyph`, `RideDuration`, `Modifier.fullBleed()` (a row that runs to the edge of a padded sheet or page: rows pad themselves, so inside a padded container they would sit twice as far in).
Parameters worth knowing: `SectionHeader(trailing)` (the chosen value at the right) and `SectionHeader(action, onAction)` (an accent link at the right, a 48 dp target: Saved's See all), `KtmSheetContent`/`KtmModalSheet(onClose, closeSize)` (the sheet's own ✕, `IconButtonSize.Map` on riding sheets), `LandscapePanel(contentPadding)`, `KtmChoiceCard` (a two-way choice as cards), `KtmRadioRow(radioAtStart)`, `windowHeightDp(fallback)` next to `windowWidthDp` (layouts that give way on a short landscape screen), shell hooks `LocalOpenBikeSettings` and `LocalOpenPlaces` (null = the shell does not provide it, so the row or link is absent), `KtmStepper(trailing)` (a Play beside the +), `KtmCard(enabled)`, `KtmSwitchRow(badge)`, `ListRow(accentTitle)`, `KtmFilterChip(riding)` (56 dp for gloves). `EmptyState` has an 88 dp tile (56 dp below 480 dp screen height) on every empty page. `DashPalette` (internal) is the dash's own black and white, and the one place a raw palette is allowed besides the theme.
Screens are written as `*ViewModel` (state + actions), a stateless `*Content`
that previews and tests render, and a thin `*Screen` that wires them.

## Insets (`ui/theme/KtmTheme.kt`)
Content is kept clear of the bars, the display cutout and the keyboard by one of two modifiers, never by a hand-written `windowInsetsPadding`: `edgePadding()` (all system bars and the cutout, for overlays on a map) and `pageInsets(sides)` (a page's own sides; the default is the cutout sides and the bottom, for a pushed page below a `KtmTopBar`). A tab page passes only the sides its tab bar does not take (a bar under the content already takes the bottom). `KtmSheetContent` handles the keyboard itself (`imePadding = false` for a sheet that floats on the map), and `EmptyState` scrolls when the page is shorter than it (`scrollable = false` inside a list or another scroll).
Riding text: the maneuver card, the trip bar and the speed are clamped to 1.15x the rider's font scale (`RidingTheme`); a riding sheet with body text that scrolls (Add a stop, the Bike sheet) is wrapped in `RidingTheme(clampFont = false)` so it follows the rider's font setting in the riding palette. Stacked tile buttons and the Where-to tiles grow with their label (rows of tiles take the tallest one's height); the Where-to peek height grows with the font scale. The stepper's unit is deliberately smaller than the number and shares its baseline (the mock sets both in one size).
Lists and sheets: `ListDivider(withIcon)` starts at the text (past the leading icon) or, for rows without one, at the row's own padding; inside a padded sheet a row runs out with `fullBleed`. A sheet is `KtmModalSheet(title, onClose, actions)`: the ✕ is for a task with no Cancel button, actions are pinned at the foot, and nothing adds its own side or bottom padding. Menu-style sheets (the place's actions, the Bike sheet) have a header of their own instead of a title row. Where the mock's later `.r2` layer and its earlier CSS differ, `.r2` wins (chip padding 16 dp, gap 8 dp, the sheet handle's 12 dp zone; the handle is 36 dp wide). Copy: "Try again" is the action after a failure (the Bike sheet's "Retry now" is a different, immediate button), "Remove" for places and "Delete" for rides (data is destroyed), British spelling throughout ("Recentre", "Minimise", "colour").
A control in a corner of the map (Back, the compass, the pick hint) uses `mapCornerPadding()`, so they share one margin. A screen that draws a sheet, a side panel or a button over the foot of the content says so with `SnackbarClearance(bottom, start)` (the full size of what it draws; the shell takes off the inset it already applies, and the largest of several wins), so a snackbar never covers a primary action. `PickOnMapScreen` is the same in portrait (sheet) and landscape (the shared panel, `onFootprint` tells the host to pad the map so the pin stays in the open part), and takes `guidance` to keep the next turn in view while a trip runs.

## Motion (`ui/theme/KtmMotion.kt`)
- One source of physics. Every animation takes its spec from `KtmTheme.motion` (`KtmMotionSpecs`); feature code never
  writes a numeric `tween(`/`spring(` (`check-ui-rules.sh`). Material components get the same numbers through
  `KtmMotionScheme` (`MaterialExpressiveTheme(motionScheme = …)`), so a sheet, a switch or the nav-bar indicator moves
  like the rest of the app. Off the bike everything is a spring, so an interrupted move (a second tap, a back swipe)
  carries on from where it is instead of restarting.
- Tokens (fluid tier): `page()` (critically damped, stiffness 400, visually settled in about 250 ms: pages, the plan sheet's entrance);
  `spatialFast/Default/Slow()` (damping 0.85/0.85/0.9, stiffness 800/380/200: sheets, dialogs, chips, shared bounds,
  list placement; a hair of overshoot at most); `stage(delayMs)` (a named 200 ms tween for staged choreography: fade-through); `playful()` (damping 0.7, stiffness 800: a press released, a pin dropped; never a page, never riding; the switch is Material's); `effectsFast/Default/Slow()` (damping 1, stiffness 3800/1600/800: alpha, colour, scrim; never bounce).
  The Navigate stages: the chrome fades, the plan sheets (`PlanPanel`, `stageSheet()`) travel in from their own edge on `spatialDefault` and leave on `spatialFast`. Pages never visibly overshoot. Rules for new code: exits use the fast tokens; behind a pushed page or a dialog we dim,
  never blur; a page that hosts the Nav SDK map is never scaled, clipped or blurred.
- Riding tier (`RidingTheme`): tweens only, never a spring, at most 250 ms, nothing overshoots, numbers never animate.
  Arriving and resizing use One UI's basic curve (0.22, 0.25, 0, 1): `ridingSpatial(delayMs)`, `ridingQuick()` (160 ms,
  the next-maneuver swap); leaving is quicker and eases in: `ridingExit()` (200 ms); fades are `ridingFade(delayMs)` and
  `ridingFadeOut()` (100 ms) are linear (fades only), as are `ridingSwapOut()` (60 ms) and `ridingColor()` (100 ms). The entrance staggers are
  `RIDING_CARD_DELAY_MS` and `RIDING_BAR_DELAY_MS`. Material components on riding surfaces use
  `KtmMotionScheme(calm = true)`: critically damped springs whose stiffness is chosen so each settles within 250 ms (a test checks it); sheets, the scrim and the snackbar are Material's own motion fed by that scheme. `check-ui-rules.sh` fails if the
  riding screens or the landscape panel ask for a fluid token. `staggeredFade(index)` is the search results' 20 ms stagger.
- Overlays and swaps: a dialog (`KtmDialog`) fades and settles in from 92 %; a back swipe previews the card shrinking to
  90 % (riding: 95 %) and fading with its dim, a committed swipe finishes the fade before the window goes (a dialog window
  can't play an exit), a cancelled one eases back; no preview under Remove animations. `contentSwap()` is the one
  transition for a loading skeleton, empty state or row replaced by its content: a fade-through (the old fades out fast, the
  new fades in 90 ms later, text never overlaps) with the size snapping. Buttons press down to 0.97 at once and release on
  `playful()`, except the riding and stacked sizes, which release on a plain fade. The snackbar and the sheet scrim are
  Material's own motion (fed by `KtmMotionScheme`); the sheet scrim is a static dim.
- **Remove animations** (`rememberReducedMotion`, read live): every fluid token becomes a
  short fade with no offset, camera moves jump. Code that slides must zero
  its offset when `motion.reduced`. The system setting itself is the animator scale
  being 0, and Compose multiplies every duration by it, so in practice those
  crossfades are cuts (decision D22: accepted, nothing is animated while it is off).
- Shared elements: `SharedElements`/`rideContainer` (ride card to replay page), `searchContainer` (the Where to? field grows
  into the search panel and back: `sharedBounds`, the panel scaled to the bounds and the one-row field remeasured, a no-op
  under Remove animations; `KtmMotionSpecs.SEARCH_CONTAINER` off restores the old slide-up; with no field on screen the
  panel fades; the keyboard waits until the container has settled),
  `TripBarSheet` (trip bar to trip sheet), `tripStripBounds` (trip bar to the
  activity strip). The sheets are drawn in the tree (not in a window) so they
  can share bounds.
- The map is one layer (`ui/map/MapLayer.kt`) behind every page, in the shell; it is never re-parented, faded, scaled,
  clipped or blurred. A page takes it with `MapClaim`. Map pages (`isMapPage()`: Navigate, Replay, Trim, Pick on map) are
  transparent over it and only their controls move; every other page paints `coverGround()` (opaque, and it takes the
  touches nobody inside wanted, so nothing falls through to the map). The map is hidden from TalkBack while no page claims it.
- Pages (`ui/shell/PageTransitions.kt`, every page registered with `page<T>`; D28): a pushed page is opaque
  and slides in from the right edge, full width, on `page()`, with no alpha at all (`check-ui-rules.sh` fails on slide plus
  fade); the page underneath moves back a quarter of the width and dims (`KtmLayout.pageDim`, 25 % black, a plain overlay
  that never blurs). A map page underneath does not move, it only dims (the map is not part of the page); two map pages cross-fade their controls. Back is the exact
  reverse (except where the map is between the pages); the system Back (button or swipe) is the arrow: every page handles it by closing itself, so nothing follows the finger and the release plays the arrow's own transition, tab bar included (D41; dialogs and the trip sheet do follow the finger). Tabs: the new tab fades in over the old one at once (no gap, no
  scale, the old one stays put). Where the map is between the pages (a map page over, or back to, an opaque page) both
  dissolve; the ride card's shared-bounds move keeps Rides still while the replay fades and its container carries its own
  ground. Tabs and the card move are not dimmed. Onboarding steps still use the page and fade-through tokens. With Remove
  animations: plain fades, no offset, no dim.
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
never a Toast (the one exception is the launcher's Record-a-ride shortcut, which has no UI of its own and says why it could not start); Undo lasts 8 s (`Feedback.ACTION_MS`). While riding the same events
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
No `Toast` (except the Record shortcut activity), no raw `AlertDialog` (use `KtmDialog`), no Material icons, no
`Color(0x…)` or `.sp`/`.em` in screens, no `AppSettings(…)` in `ui/` (use the
container's `SettingsRepository`), no legacy `Ktm.*` accessor. Strings live in
`strings.xml` (`<feature>_<element>_<meaning>`), distances are metric. No raw dp in screens: use `KtmLayout`/`KtmSpacing` tokens. Rider-facing times go through `ClockFormat` (the phone's 12/24 h setting and locale); only the dash uses `EtaFormatter` (always 24 h). Status and navigation bar icons have one owner, `SystemBarIcons`.
