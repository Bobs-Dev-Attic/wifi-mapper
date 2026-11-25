# Changelog

All notable changes to WiFi Mapper are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). The in-app version badge shows the
`versionName` defined in `app/build.gradle.kts`.

## [Unreleased]

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

[Unreleased]: https://github.com/bobs-dev-attic/wifi-mapper/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.6.0
[0.5.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.5.0
[0.4.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.4.0
[0.3.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.3.0
[0.2.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.2.0
[0.1.0]: https://github.com/bobs-dev-attic/wifi-mapper/releases/tag/v0.1.0
