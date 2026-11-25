# WiFi Mapper

Android app that builds a **3D map of WiFi signal strength** as you walk through a
space — including multi-level buildings like a house. Each measurement is placed
in 3D by **fusing three positioning sources**, then visualised as a rotatable,
colour-coded point cloud.

> Current version: **0.8.0** (shown in-app; see [CHANGELOG.md](CHANGELOG.md)).

## Download & install

Built APKs are published on the repo's
[**Releases**](https://github.com/Bobs-Dev-Attic/wifi-mapper/releases) page once a
`vX.Y.Z` tag is pushed (see *Releasing* below). On your phone, download the APK
and open it to install (you'll be asked to allow installing from this source).

Once a version is installed, the app **auto-updates itself**: on launch it checks
GitHub Releases and, if a newer version exists, offers a one-tap *Download &
install* banner. (There's no Play Store listing, so this replaces Play
auto-update for sideloaded builds.)

> Note: reliable in-place auto-update requires every release to be signed with the
> **same** keystore — otherwise Android rejects the update on a signature
> mismatch. Configure signing once (below) so all CI releases share a key.

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

## Multiple floors

Map one floor per pass. Before each pass, stand at a **reference spot you can find on
every floor, stacked vertically** (e.g. the corner by the stairs) and **face the same
direction** each time. Each pass's AR tracking starts there, so the floors line up.
Use the **Floors** page (layers icon in the 3D view) to rename and reorder floors;
floor spacing is in Settings.

## Finding a router or device (radar)

**Nearby signals** (Wi-Fi icon on the home screen) lists Wi-Fi networks and Bluetooth
LE devices; tap the locate icon to open the radar. Phone antennas can't sense
direction, so the radar uses a **body-shadow sweep**: hold the phone upright and turn
slowly on the spot. Your body blocks 5–10 dB from behind, so the signal peaks when
you face the source. The radar fits that peak from the whole circle and points to
it; the warmer/colder trend helps as you walk closer.

Live updates work for your **connected Wi-Fi** and **Bluetooth** devices. Other Wi-Fi
networks only refresh when Android allows a scan (about 4 per 2 minutes) unless you
turn off *Developer options → Wi-Fi scan throttling*.

## Colour by network or device, and find the router on the map

In the 3D view, **Color by** switches between the strongest Wi-Fi, one specific Wi-Fi
network, or one Bluetooth device (if you mapped with "Also record Bluetooth devices"
on). For a specific network or device, a ◇ marks its **estimated location**: one per
access point for mesh networks. It's a power-weighted average of where the signal
was strongest, so it can only place a source inside or near the area you walked.

## Radar styles

The radar has a **motion-tracker** style (forward 180° fan, ping waves that speed
up as the signal strengthens, a blip at the target's bearing and range, red range
readout) and a **classic** compass style. Switch on the radar screen or in Settings.

## Find my Bluetooth device

**Find my device** (Bluetooth icon on the home screen) lists your paired devices and
everything advertising nearby. Pick one for a **hot/cold finder**: beeps and
vibration speed up as you get closer, and a "strongest so far" marker warns if you
walk past it. **Find direction** opens the radar sweep.

It can only find a device that's **advertising**: not one that's off, out of
battery, or (for most earbuds) closed in its case. It can't make the device ring;
use the maker's app for that (e.g. SmartThings Find for Galaxy Buds).

## Walls and signal loss

While mapping, ARCore's vertical-plane detection records wall-like surfaces. The
viewer then estimates **how many dB each wall costs**:

1. Find pairs of nearby points (≤ 3 m) whose straight line passes through the wall.
2. Compare the strongest access point seen at both points.
3. Subtract the normal distance fall-off, estimated from nearby pairs that cross no wall.
4. Report the median, with confidence from the number of pairs.

**Limits:** ARCore can't tell a wall from any other flat vertical surface (a
fridge side, a wardrobe), and blank or glass walls may be missed. A wall only shows
a loss if you measured on **both sides** of it, so walk through doorways and around
rooms. Expect real-home estimates to be noisier than lab numbers (multipath, people,
furniture), and treat them as relative ("this wall costs much more than that one").

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

## Releasing (makes the app downloadable + drives auto-update)

CI (`.github/workflows/ci.yml`) builds a debug APK on every push/PR. To cut a
downloadable release, bump the version and push a tag:

```bash
# bump versionName/versionCode default in app/build.gradle.kts first
git tag v0.2.0
git push origin v0.2.0
```

`release.yml` then builds the APK (version derived from the tag) and publishes it
to GitHub Releases. The in-app updater checks `releases/latest`, so the next
launch of an older install offers the update.

### One-time signing setup (recommended)

So auto-update works across versions, sign every release with one keystore stored
as GitHub secrets. Run locally (the private key never leaves your machine or the
secrets store):

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias wifimapper
base64 -w0 release.jks > release.jks.b64   # macOS: base64 -i release.jks -o release.jks.b64
```

Then add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `release.jks.b64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `wifimapper` |
| `KEY_PASSWORD` | the key password |

Without these, releases still build but are **debug-signed** (fine for a first
install; not guaranteed to update in place across machines). Do **not** commit the
keystore — `.gitignore` already excludes `*.jks`.

See [CHANGELOG.md](CHANGELOG.md) for version history.
