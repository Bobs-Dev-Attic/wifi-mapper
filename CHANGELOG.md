# Changelog

All notable changes to WiFi Mapper are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). The in-app version badge shows the
`versionName` defined in `app/build.gradle.kts`.

## [Unreleased]

## [0.15.1] - 2026-11-26
### Changed
- **Find my device: separate sort drop-downs.** "Your paired devices" and "Nearby
  now" each get their own Sort menu in their section header (previously one menu
  ordered both). Each is remembered separately between visits.

## [0.15.0] - 2026-11-26
### Added
- **Sort drop-down on the Wi-Fi and Bluetooth lists** (Nearby signals). Wi-Fi:
  signal strength (default, connected network first), name, distance, band &
  channel, security, recently seen. Bluetooth: signal strength (default), name,
  distance, maker, type (connectable first), recently seen. A reverse button flips
  the order. Hidden networks / unnamed devices / unknown makers stay at the bottom
  when sorting by that field, and ties go to the stronger signal. **Each list
  remembers its sort choice between visits** (and app restarts).
- The same **Sort** drop-down on **Find my device**, ordering both "Your paired
  devices" and "Nearby now". Paired devices sort by their live signal; ones not
  heard right now stay at the bottom (by name sort, all are ordered A→Z).
- `ListSort` helper with unit tests.

## [0.14.0] - 2026-11-25
### Added
- **Tracker blip vibration.** The phone buzzes the moment the ping wave reaches
  the blip, in step with the return bleep. The buzz gets longer and firmer as you
  get closer (20 → 60 ms; stronger on phones with amplitude control, like the
  Galaxy S25 Edge). Toggle it with the new **Vibrate** chip on the radar or in
  Settings → Radar → Tracker vibration; it works with Sound off, for quiet hunting.
- `VIBRATE` permission (a normal permission, granted automatically).

## [0.13.0] - 2026-11-25
### Added
- **Tracker radar: strongest direction highlighted.** The sweep returns now show
  the peak direction as a bright, outlined wedge (the sector holding the
  estimated bearing, or the strongest sector before a bearing is known). Every
  other direction is shaded as a gradient **relative to the next-strongest**
  direction, so one dominant peak no longer flattens the rest into darkness.
- **Radar icon on Bluetooth lists** (Nearby signals → Bluetooth, and both lists in
  Find my device). It opens Find my device for that device straight onto its
  radar sweep; Back returns to the hot/cold finder. Tapping the row still opens
  the finder.
- `SectorShading` helper with unit tests.

## [0.12.0] - 2026-11-25
### Added
- **Pinch to zoom the tracker radar.** Spread two fingers to zoom in to a 3 ft
  (0.9 m) view; pinch to zoom out. Up to 100 ft (30 m) the grid is fully drawn;
  beyond that you can keep zooming out to ~330 ft (100 m) while the range rings,
  spokes, ticks and ring labels **gradually fade** (signal-based distances are
  guesswork that far out). Compass letters, the blip and ping waves stay clear.
- Range rings adapt to the zoom with round numbers (1/3/10/30/100/300 ft or
  0.3/1/3/10/30/100 m); the default view is unchanged (3/10/30/100 ft).
- Pings and the return bleep follow the zoom. A target beyond the current view
  is pinned to the edge with a dashed outline. Double-tap resets to 100 ft; a
  new **RNG** readout shows the view range. One-finger drags still scroll.
- Unit tests for the zoom clamp, grid fade, zoomed range scale and rings.

## [0.11.0] - 2026-11-25
### Added
- **Distance units: metres or feet** (Settings → Units). Defaults to your phone's
  region (feet in the US) and applies everywhere a distance is shown: the radar
  range readout, tracker range rings (1/3/10/30 m, or round 3/10/30/100 ft rather
  than awkward conversions), the Wi-Fi and Bluetooth lists, Find my device, the
  finder, and floor spacing in Settings. Values are still stored in metres.
- `UnitFormat` helper with unit tests (formatting, ranges, lengths, tracker
  rings, regional default).

## [0.10.0] - 2026-11-25
### Added
- **Tracker return bleep.** Besides the outgoing ping, the tracker now bleeps the
  moment the expanding wave reaches the target blip, like the film tracker. The
  bleep's **pitch rises as you get closer** (~0.9 → 2 kHz), so you can home in by
  ear. Tones are synthesised (click-free envelope) and played on a background
  audio stream. The radar's "Sound" chip / Settings toggle controls both.
- **Check permissions** page (Settings → Check permissions): status and purpose of
  each runtime permission for this Android version (location, nearby Wi-Fi,
  camera, Bluetooth scan/connect), system switches that silently break features
  (Location services, Wi-Fi, Bluetooth, install-updates), and hardware (ARCore,
  compass, barometer), with a one-tap fix for each, "Grant missing", "Open app
  settings", and an "All set" / "N need attention" summary. Refreshes on return.
- Settings → About shows the version and build, with a **View changelog** link.

### Changed
- The version badge moved from the home screen (and the capture / 3D view bars)
  to Settings → About.
- Unit tests: tone synthesis (length, silent edges, pitch), wave-crossing
  detection, bleep pitch, permission catalogue per Android version.

## [0.9.1] - 2026-11-25
### Changed
- **Tracker radar grid now rotates with your compass heading.** Spokes (every 30°),
  ring ticks (every 15°) and the dashed ring segments are locked to real bearings
  and sweep across the fan as you turn.
- **Compass labels:** **N** in red with a red spoke, E/S/W in cyan, and NE/SE/SW/NW as
  smaller labels. When north is behind you, a red **◀ N** / **N ▶** at the edge shows
  the shorter way to turn. A fixed notch at the top marks the way you're facing.
- Compass heading is smoothed (wrap-safe across north, unit-tested) so the rotating
  grid doesn't shimmer.

## [0.9.0] - 2026-11-25
### Added
- **AR signal view** (AR icon on the home screen, and on a map's 3D view):
  - **Live paint:** walk with the camera up and a trail of glowing orbs is left in
    mid-air every ~40 cm, coloured by the connected Wi-Fi's signal at that spot.
    A HUD shows the live signal, quality, bars and the colour legend; Clear resets.
  - **Saved map overlay:** pick a floor, stand at its start point facing the same
    way, and its saved measurements appear as orbs where they were taken, along
    with detected walls and their dB-loss labels.
  - Optional dBm labels on nearby orbs; orbs scale with distance.
- `ArProjector`: projects world points to the screen with ARCore's view/projection
  matrices (drawn as a 2D overlay on the camera feed). Unit-tested.

### Notes
- No occlusion: orbs show through walls. Saved-map alignment depends on matching
  the floor's start point and facing direction (expect ~0.5–1 m).

## [0.8.0] - 2026-11-25
### Added
- **Bluetooth devices on the 3D map.** Mapping can also record nearby Bluetooth LE
  devices at each point (optional switch in set-up; needs the Bluetooth permission).
- **Color by** selector in the 3D view: strongest Wi-Fi (as before), a specific
  **Wi-Fi network**, or a specific **Bluetooth device**. Points where it wasn't
  heard show grey and are left out of the stats.
- **Estimated transmitter locations** (◇ markers) for the chosen network or device,
  from a power-weighted centroid of its strongest readings. Multi-AP / mesh
  networks get one marker per access point. Unit-tested on simulated surveys
  (within 1.5 m when the source is inside the walked area).
- **Motion-tracker radar style** (inspired by the *Aliens* tracker): forward 180°
  fan with glowing segmented range arcs on a log scale (1/3/10/30 m), spokes,
  emitter dome, expanding **ping waves** whose rate rises with signal strength
  (optional ping sound), a target blip at the sweep bearing and estimated range
  ("BEHIND" when outside the fan), faint sweep returns, and a red digital range
  readout with signal / heading / bearing / sweep readouts. Tracker and Classic
  are switchable on the radar screen and in Settings.

### Changed
- **More realistic distance estimates.** Wi-Fi distance now uses an indoor
  log-distance model (≈ −33 dBm at 1 m on 5 GHz, exponent 2.7) instead of
  free-space loss, which overestimated indoors by roughly 3–5× (e.g. −60 dBm
  showed ~48 m; it's now 5.9–22 m). Ranges are shown instead of single numbers
  on the radar and Wi-Fi list.
- Database schema v4 (`ble_readings` table); existing maps are kept.

## [0.7.0] - 2026-11-25
### Added
- **Find my device** (Bluetooth icon on the home screen):
  - **Your paired devices**, each marked "heard now" with its signal and rough
    distance when it's advertising, or why it can't be heard (off / in its case /
    classic-Bluetooth-only).
  - **Nearby now**: every Bluetooth LE device currently advertising.
- **Hot/cold finder** for a chosen device: a proximity ring (Far → Getting closer →
  Near → Very close → Right here), Geiger-counter **beeps and vibration that speed
  up as you get closer**, strongest-so-far marker with a "you may have walked
  past it" warning, warmer/colder trend, last-heard timer, and a **Find direction**
  button that opens the radar sweep for the device.
- Matching by address with a fallback to the advertised name, for devices that
  rotate their Bluetooth address.
- `ProximityEstimator` (median + moving-average smoothing that ignores single
  RSSI spikes) with unit tests.

### Changed
- Locating a Bluetooth device from Nearby signals now opens the finder (radar is
  one tap away from there).
- Adds the `BLUETOOTH_CONNECT` permission (Android 12+) to read paired device names.

## [0.6.0] - 2026-11-25
### Added
- **Multi-floor maps.** A map now holds floors, each captured in its own pass that
  starts at a marked reference point (the same spot, stacked vertically, facing
  the same direction), which lines the floors up. "Add floor" places it above or
  below the existing ones.
- **Floors page:** rename floors, reorder them (▲/▼), see point counts, delete a
  floor (with its points and walls). The 3D view stacks floors in that order, and
  the floor filter chips use your labels.
- **Settings page:** app theme (system/light/dark), Material You colours, signal
  palette with previews, floor spacing for the 3D view, and version info.
- **Nearby signals page** with Wi-Fi and Bluetooth tabs:
  - Wi-Fi: name, BSSID, RSSI + bars + quality, band, channel, channel width,
    Wi-Fi standard (4/5/6/6E/7), security (WPA2/WPA3/Open…), rough distance,
    connected marker, band filter, and a note on Android's scan throttling.
  - Bluetooth LE: live list with name, manufacturer, RSSI + bars, advertised TX
    power, rough distance, connectable vs beacon.
- **Signal radar** to find a router, hotspot or Bluetooth device: a compass-
  oriented polar plot built from a body-shadow sweep (turn slowly on the spot),
  an arrow toward the strongest direction with "turn N° left/right" guidance,
  a contrast check that says when there's no clear direction, a warmer/colder
  trend for walking toward the source, and a rough distance. Live for the
  connected Wi-Fi and for Bluetooth devices; other Wi-Fi networks are limited by
  Android's scan throttling (the screen explains how to lift it).
- Unit tests: radar direction finding (50 randomised noisy sweeps), warmer/colder
  trend, Wi-Fi channel/security/standard parsing, floor ordering, and the v3
  migration schema.

### Changed
- System Back now returns to the previous screen instead of closing the app.
- Database schema v3: `floors` table and `floorId` on points and walls. Existing
  maps are migrated to a single "Floor 1".
- Wall loss is analysed per floor (points on different floors are never paired).
- CI prints Room's exported schema so it can be committed for future migrations.

## [0.5.0] - 2026-11-25
### Added
- **Wall detection.** ARCore vertical-plane finding is enabled during capture (with
  the Depth API where supported, e.g. Galaxy S25 Edge). Wall-like vertical
  surfaces (≥ 0.6 m wide) are recorded per session; the capture screen shows a
  live "Walls seen" count. Walls autosave every 10 s and again on stop.
- **Signal loss per wall.** For pairs of nearby points (≤ 3 m) on opposite sides of
  a wall, the strongest shared access point's RSSI drop is measured, the normal
  distance fall-off (from pairs crossing no wall) is subtracted, and the median is
  reported with a confidence level (low / medium / high by number of pairs).
  Walls only seen from one side show "no data".
- 3D viewer draws walls as translucent grey panels labelled with their loss
  (dashed outline = not measured across), a **Walls** toggle chip, floor
  filtering for walls, and a walls summary line in the stats panel.
- Unit tests (run in CI): wall-loss analyzer against synthetic surveys with known
  losses, segment/wall intersection, and a check that the database migration SQL
  matches Room's exported schema.

### Changed
- Database schema v2 (new `walls` table) with a migration that keeps existing maps.
- CI now runs `testDebugUnitTest` in addition to building the APK.

## [0.4.0] - 2026-11-25
### Added
- **Colour-blind-friendly palettes** with an in-viewer picker (palette icon in the
  top bar). Default is now **Blue → Yellow (Cividis)**, designed for colour-vision
  deficiency. Also available: Blue → Yellow (vivid), Purple → Yellow (Viridis),
  Black → Cream (Magma), Blue → Orange, and the classic Red → Green (flagged as
  not colour-blind safe). The choice is remembered between sessions.
- Legend, point colours and the uniformity-score swatch all follow the chosen palette.

### Changed
- Strongest/weakest markers are distinguished by **shape**, not red/green: ▲ MAX is
  a solid double ring, ▼ MIN is a dashed ring.
- Capture screen tracking indicator uses blue/orange instead of green/red.

## [0.3.0] - 2026-11-25
### Added
- **Strongest / weakest signal markers** in the 3D viewer: the best and worst
  measured points are ringed (green MAX / red MIN) and labelled with their RSSI.
- **Coverage uniformity score** (0–100%) with a qualitative label, shown in a new
  stats panel along with strongest, weakest, mean RSSI, spread, and a count of
  weak-signal zones (≤ −75 dBm). Score = `100 × (1 − min(stdDev_dB / 15, 1))`.
- Stats and markers respect the active floor filter (recomputed per floor).

## [0.2.0] - 2026-11-25
### Added
- **In-app auto-update.** On launch the app checks GitHub Releases; when a newer
  version is available it shows a banner to download and install the APK
  (`update/` package: `UpdateChecker`, `ApkInstaller`, `UpdateViewModel`,
  `UpdateBanner`). Uses a `FileProvider` + the system package installer.
- **CI workflow** (`.github/workflows/ci.yml`) building a debug APK on every push
  and pull request — this is also the project's first real build verification.
- **Release workflow** (`.github/workflows/release.yml`): pushing a `vX.Y.Z` tag
  builds a (signed, if secrets are configured) release APK and publishes it to
  GitHub Releases, which makes the app downloadable and drives the auto-updater.
- Version is now overridable from CI via `-PappVersionName` / `-PappVersionCode`,
  derived from the release tag.
- `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions for the updater.

### Changed
- Release builds use a keystore from CI secrets when present, otherwise fall back
  to debug signing so a build always produces an installable APK.

## [0.1.0] - 2026-11-25
### Added
- Initial project scaffold: Kotlin + Jetpack Compose (Material 3), min SDK 26,
  target SDK 35.
- **Fused 3D positioning** combining three sources:
  - ARCore (SceneView) for fine-grained local `(x, y, z)` pose via visual-inertial
    tracking — the backbone for genuine 3D, multi-level mapping.
  - GPS / fused location to set a per-session geo-anchor (georeferences the cloud;
    not used for indoor x/y).
  - Barometer for an independent vertical signal and floor-index inference.
- WiFi scanning via `WifiManager` with Android scan-throttling handled (broadcast
  listener + connected-AP RSSI fallback). 2.4 / 5 / **6 GHz (Wi-Fi 6E/7)** band
  detection.
- Room persistence: sessions, sample points, and per-AP readings; schema export
  enabled for DB version tracking.
- Capture screen: live tracking/floor/AP/RSSI status, auto-capture on movement,
  manual "capture point", and runtime permission handling.
- 3D viewer: self-contained rotatable/zoomable Compose-canvas point cloud coloured
  by signal strength (red→green), per-floor filtering, and a signal legend.
- In-app version badge (from `BuildConfig`).
- Device tuning notes for the Samsung Galaxy S25 Edge (ARCore + Depth, barometer,
  Wi-Fi 7 / 6 GHz, One UI 7 dynamic colour).

[Unreleased]: https://github.com/bobs-dev-attic/wifi-mapper/compare/v0.11.0...HEAD
[0.11.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.11.0
[0.10.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.10.0
[0.9.1]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.9.1
[0.9.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.9.0
[0.8.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.8.0
[0.7.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.7.0
[0.6.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.6.0
[0.5.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.5.0
[0.4.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.4.0
[0.3.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.3.0
[0.2.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.2.0
[0.1.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.1.0
