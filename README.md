# WiFi Mapper

Android app that builds a **3D map of WiFi signal strength** as you walk through a
space — including multi-level buildings like a house. Each measurement is placed
in 3D by **fusing three positioning sources**, then visualised as a rotatable,
colour-coded point cloud.

> Current version: **0.1.0** (shown in-app; see [CHANGELOG.md](CHANGELOG.md)).

## Why not just GPS?

GPS is the obvious first idea but it's the wrong primary tool indoors:

- Horizontal accuracy indoors degrades to tens of metres (walls/roof attenuate the
  signal; multipath causes drift).
- **Vertical accuracy is worse than a storey height (±10–20 m)** — GPS cannot tell
  which floor you're on, which is exactly what a multi-level map needs.

So GPS is used only to **geo-anchor** a session (place the whole cloud on the
globe), not for indoor position.

## How positioning works (the fusion)

| Source | Role |
|---|---|
| **ARCore** (visual-inertial odometry via SceneView) | Fine-grained local `(x, y, z)` as you move — the backbone of true 3D / multi-level. |
| **Barometer** | Independent vertical signal → floor index (ARCore's Y drifts over long walks; pressure doesn't accumulate the same drift). |
| **GPS / fused location** | One good fix per session → geo-anchor (lat/lon/alt + heading) so clouds align to the real world and across sessions. |

Each WiFi snapshot becomes a `FusedSample` = AR pose + barometric floor + per-AP
RSSI, persisted in Room.

## Architecture

```
model/      Vector3, GeoAnchor, FusedSample  (framework-free, unit-testable)
data/       Room entities, DAO, database, repository
capture/    WifiScanner, LocationProvider, BarometerProvider, PoseSource,
            FusionEngine, ArCaptureSurface (SceneView), CaptureViewModel
viz/        SignalColor/WifiBand, PointCloud3DView (Compose canvas), ViewerViewModel
ui/         Sessions / Capture / Viewer screens, theme
```

The **fusion and capture logic has no compile-time dependency on SceneView** —
`ArCaptureSurface.kt` is the only AR-rendering file, so the pipeline is testable
and resilient to SceneView API changes.

## WiFi scanning note

Since Android 9, a foreground app may call `startScan()` only ~4×/2 min; beyond
that the OS returns cached results. The app listens for scan broadcasts and also
reads the **connected AP's RSSI** (not throttled) for denser sampling.

## Build & run

Requires **Android Studio** (Koala or newer) with the Android SDK and an
**ARCore-supported device** (AR needs real hardware; it won't run on the emulator).

```bash
./gradlew assembleDebug      # build the APK
./gradlew installDebug       # install on a connected device
```

Permissions requested at runtime: camera (AR), fine location + nearby-WiFi
(required to read scan results).

## Target device tuning — Samsung Galaxy S25 Edge

- Snapdragon 8 Elite, One UI 7 / Android 15 → `targetSdk 35`, Material You dynamic
  colour enabled.
- ARCore + **Depth API** supported; barometer present → full fusion path available.
- **Wi-Fi 7 with 6 GHz** → band detection covers 2.4 / 5 / **6 GHz**.
- ~6.7″ 1.5K display → Compose edge-to-edge layout.

See [CHANGELOG.md](CHANGELOG.md) for version history.
