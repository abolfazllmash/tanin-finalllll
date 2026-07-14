# Resonance — native Android (Kotlin + Gradle)

A one-tap chain-reaction game. Tap once, a pulse blooms outward, every orb it
touches blooms and continues the chain. Clear each level's goal to advance.
This is the native Android port of the web prototype, with the same engine,
level curve, responsive scaling, immersive fullscreen, and hardware back support.

## Open & run
1. **Android Studio** (Koala/Ladybug or newer, JDK 17) → *Open* this folder.
2. Let it sync Gradle (it will download the wrapper distribution declared in
   `gradle/wrapper/gradle-wrapper.properties`). If it asks to generate the
   Gradle wrapper, accept — or from a terminal with Gradle installed run
   `gradle wrapper` once.
3. Run the `app` configuration on a device/emulator (minSdk 26).

Toolchain it targets (Android Studio may offer to adjust these):
AGP 8.6.0 · Gradle 8.9 · Kotlin 1.9.24 · JDK 17 · compileSdk/targetSdk 34 · minSdk 26.

> Note: this project was written and structured carefully but could not be
> compiled in the authoring environment (no Android SDK there), so you may need
> a tiny tweak or two on first sync — versions especially.

## How it maps to the web build
| Web (HTML/Canvas)            | Android (this project)                              |
|------------------------------|-----------------------------------------------------|
| `<canvas>` + `requestAnimationFrame` | `GameView` (SurfaceView) + `Choreographer` loop |
| draw calls                   | `Canvas` + `Paint` (additive glow via `PorterDuff.ADD`) |
| DOM overlays (menu/pause/result) | Android Views in `activity_main.xml`            |
| `history.pushState` back trick | `OnBackPressedCallback` (play→pause→menu→exit)    |
| Fullscreen API               | `WindowInsetsControllerCompat` immersive            |
| scale factor `S` from CSS px | engine works in **dp**; canvas scaled by `density`  |
| in-memory best level         | `SharedPreferences` (`Prefs`)                       |
| Web Audio pluck              | `AudioTrack` synth (`SoundManager`)                 |
| `navigator.vibrate`          | `VibrationEffect` (`Haptics`)                       |

## Where to tune
- **Difficulty / sizes / counts:** `GameConfig.kt` and the `levelXxx()` curves at
  the top of `GameEngine.kt` (`levelTotal`, `levelTarget`/`targetPct`, `levelSpeed`,
  `levelGrowMul`, `levelClusters`). These are the same numbers as the web build.
- **"So close!" threshold:** `GameConfig.CLOSE_FRACTION`.
- **Look:** colors in `res/values/colors.xml`; glow/bloom drawing in `GameView`.

## Optional: the Space Grotesk font
The UI uses the system `sans-serif-medium`. To match the web build's font:
1. Put `SpaceGrotesk-Medium.ttf` in `app/src/main/res/font/space_grotesk.ttf`.
2. In the XML, replace `android:fontFamily="sans-serif-medium"` with
   `android:fontFamily="@font/space_grotesk"`.
3. In `GameView.textPaint(...)`, load it via
   `ResourcesCompat.getFont(context, R.font.space_grotesk)` and assign to `typeface`.

## Project layout
```
app/src/main/
  AndroidManifest.xml
  java/com/example/resonance/
    MainActivity.kt     scenes, immersive fullscreen, back handling, wiring
    GameView.kt         SurfaceView render loop + drawing + touch
    GameEngine.kt       simulation, field generation, scaling, level/round state
    Entities.kt         Orb / Pulse / Particle / Dust + enums
    GameConfig.kt       tunable constants
    SoundManager.kt     AudioTrack tone synth
    Haptics.kt          VibrationEffect ticks
    Prefs.kt            best-level persistence
  res/                  layout, strings, colors, theme, drawables, launcher icon
```
