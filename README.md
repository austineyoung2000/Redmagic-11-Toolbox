# Redmagic 11 Toolbox

[![Android CI](https://github.com/austineyoung2000/Redmagic-11-Toolbox/actions/workflows/android.yml/badge.svg?branch=sixteen)](https://github.com/austineyoung2000/Redmagic-11-Toolbox/actions/workflows/android.yml)
![Version](https://img.shields.io/badge/version-2.6.0-red)
![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Device](https://img.shields.io/badge/device-RedMagic%2011%20Pro-red)
![Root](https://img.shields.io/badge/root-required-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)

A hardware, gaming, cooling, lighting, and automation toolbox built specifically for the **RedMagic 11 Pro / NX809J**.

Redmagic 11 Toolbox combines cooling, liquid-pump, lighting, native shoulder-trigger touch mapping, per-app performance controls, Magic Key, charging, call-lighting, automation, diagnostics, and portable profiles in one Material-style Android application. The implementation uses interfaces validated on a physical NX809J and does not require the Game Space application to manage native TGK mappings.

> [!WARNING]
> Several hardware controls write directly to kernel and vendor interfaces through root. Stock vendor gaming features use compatibility-gated system APIs where available and fall back only where explicitly implemented. Do not expose NX809J-only hardware controls on another device without porting and validating every interface.

## Current platform

| Item | Configuration |
|---|---|
| Application ID | `com.elitedarkkaiser.redmagic` |
| Version | `2.6.0` (`versionCode 12`) |
| Development branch | `sixteen` |
| Minimum Android | Android 9 / API 28 |
| Target and compile SDK | API 35 |
| Language | Kotlin |
| Java compatibility | Java 17 |
| UI | Material Components |
| Root | Required for hardware controls; native TGK itself is non-root |
| Supported device | RedMagic 11 Pro / NX809J |

## What's new in 2.6.0 — independent GAME MODE bar

Version 2.6.0 separates the illuminated side **GAME MODE bar** from the rear logo. LED Zones and charging have dedicated bar entries; gaming and both call profiles have separate logo/bar sections. Each area saves its own enable state, preset color, and 32–255 brightness. Their shared Steady, Breathe, Flashing, or Rapid effect is labeled in the editor.

RGB Studio adds a bar color sequence, intensity, enable switch, and per-zone cycle speed. Master Profiles and JSON backups preserve both areas. Existing profiles initialize the bar from the logo to retain matching behavior. Editing one area composes a complete logo/bar program and preserves the other.

Owner root-shell tests confirmed independent colors and brightness on all four effects. The owner authorized merging and releasing **2.6.0** on October 4, 2026. Separate simultaneous effects are unverified and are not exposed. Reloading the shared program can restart both areas' effect phase.

See the [logo/bar reverse-engineering report](docs/LOGO_GAME_MODE_BAR_REVERSE_ENGINEERING.md) for the exact byte mappings, hashes, test observations, implementation, and APK checklist.

Selected colors and fan palettes use compact circular swatches with white selection rings and a thin dark contrast outline. Rings update immediately across LED Zones, charging, gaming, both call modes, and every RGB Studio sequence.

## What's new in 2.5.5

Version 2.5.5 adds adjustable **32–255 LED brightness** across LED Zones, gaming, charging, incoming/connected calls, RGB Studio, and Master Profiles. Changing a preset color or effect preserves the selected brightness. Colors remain preset-only; brightness has its own slider.

| Zone | Effects with brightness | Color support |
| --- | --- | --- |
| Fan | Steady, Breathe, Flashing, Blink, Rapid | Eight single colors and all eight multicolor palettes |
| Logo | Steady, Breathe, Flashing, Rapid | Eight preset colors |
| Triggers | Steady, Breathe, Flashing, Rapid | Matching or independent top/bottom preset colors; shared zone brightness |

- Fan **Blink** preserves the vendor circular chase, illuminating LEDs independently in order.
- RGB Studio saves independent fan, logo, and trigger brightness, even with synchronized cycle timing. Apply to All transfers each value to its corresponding normal zone.
- Master Profiles and JSON export/import retain normal, mode, and RGB Studio brightness. Older RGB Studio backups default to 255.
- Normal-zone Cancel restores the previous complete lighting selection. Mode editors keep staged changes unsaved until Save; charging fan previews no longer persist preferences early.
- Brightness scales validated vendor program RGB payloads. It does not use the driver brightness node as a dimmer, change current limits, or modify vendor firmware.

Owner device testing confirmed the expanded LED Zones build and reported the later profile/RGB Studio build working. See the [brightness reverse-engineering report](docs/LED_BRIGHTNESS_REVERSE_ENGINEERING.md), [test history](docs/LED_BRIGHTNESS_TEST.md), and [release notes](RELEASE_NOTES.md).

## What's new in 2.5.4

Version 2.5.4 adds independent top and bottom trigger LED preset colors with a shared lighting effect. Trigger LEDs remain inside **LED Zones**, alongside the fan and logo controls.

- Choose any combination of the eight preset colors for the two triggers.
- Save separate combinations in normal lighting, Game Mode, Charging Mode, and incoming/connected Call Lighting profiles.
- Use separate top/bottom color sequences in RGB Studio, including fixed combinations and sequences of different lengths.
- Retain split selections through Master Profiles, portable backups, and mode restoration.
- Use Steady, Breathe, Flashing, or Rapid with preset-only controls; no custom hex inputs or RGB sliders.

The feature uses the reverse-engineered AW22xxx vendor effect programs and validates their checksums and RGB byte locations before applying a split selection. It does not rewrite vendor firmware or increase LED current limits. Steady, Breathe, and Flashing split output was confirmed during device/community testing; later owner APK testing reported all four trigger effects working. See the [test and reverse-engineering report](docs/TRIGGER_LED_REVERSE_ENGINEERING.md).

## What's new in 2.5.3

Version 2.5.3 completes module-backed shoulder-trigger haptics. When Trigger
Bridge 0.3.1 or newer owns a configured game, Toolbox writes the profile's
haptics toggle plus the selected Hardware-tab Low, Medium, or High gain and
duration to the module. Native TGK remains unchanged and preferred on stock
firmware.

The companion module now detects landscape rotation through Android window and
display state when the vendor `SurfaceOrientation` field is absent. This keeps
saved L/R targets accurate without a manual property override.

## What's new in 2.5.2

Version 2.5.2 adds automatic dual-backend shoulder-trigger mapping and finishes
the post-update gameplay lifecycle work. Stock firmware continues to use Native
TGK. Compatible NX809J custom ROMs can use the optional Redmagic Trigger Bridge
v0.3.0 merged-touch module when the proprietary TGK implementation is absent or
rejects configuration.

### Native TGK and Trigger Bridge routing

- Native TGK remains the first-choice backend on compatible stock firmware.
- If the native probe or mapping apply fails, Toolbox releases partial native
  state and activates an installed Trigger Bridge v0.3.0 or newer.
- The module receives the active profile's normalized per-rotation L/R targets
  and is owned only while the configured application remains in the foreground.
- Both backends are disabled during every game-exit, screen-off, editor,
  configuration-change, and runtime-cleanup path.
- Older Trigger Bridge releases are rejected because their separate virtual
  touchscreen cannot safely coexist with physical gameplay contacts.
- A root-only exact validation marker remains available for maintainers to test
  the fallback on stock hardware without changing the normal native-first path.

### Verified merged-touch fallback

- Continuous thumbstick movement while tapping or holding either trigger
- Both shoulder triggers with one or two physical screen contacts
- Repeated finger removal and replacement during trigger activity
- Correct saved target coordinates in COD Mobile landscape mode
- Stable protocol-B slot ownership without dropped contacts or camera snapping
- Two-minute combined-input stress validation on NX809J Android 16

### Gameplay update recovery

- Foreground hints restart a missing gameplay runtime instead of being ignored.
- Stale foreground-monitor authority is retired so overlays do not remain
  outside the selected application.
- APK replacements that change gameplay services, overlays, or trigger routing
  produce a one-time reboot notice because Android may retain stale service and
  input state until the next boot.

Version 2.5.3 retains the complete 2.5.1 Game Space-style control surface and
runtime recovery stack described below.

### Game Space-style in-game controls

- A compact toolbox drawer for performance, trigger, cooling, lighting, and utility controls
- A movable floating `GS` button with a dedicated drag grip
- A separately movable drawer whose position is saved per application and orientation
- A compact movable shoulder-trigger editor with independent floating behavior menus
- Smaller L/R target markers that remain draggable during editing
- System-gesture inset handling that keeps the button and drawer away from Back gesture edges
- No edge-capture handles or full-height gesture interception

### Gameplay runtime recovery

- A dedicated gameplay foreground service owns overlays and native TGK state instead of the accessibility-service binding
- An independent watchdog process keeps a binding to the gameplay runtime and requests reconstruction after unexpected termination
- A minimal root-owned `service.d` supervisor survives firmware-wide application UID kills
- Recovery is routed through the Android shell identity and REDMAGIC's one-shot AutoLaunch whitelist before the watchdog foreground service is started
- Supervisor installation, replacement, stale-lock cleanup, bounded retry, and log rotation are handled without performing hardware writes
- Overlay and trigger state are reconstructed automatically when the configured game is still active

### Native shoulder-trigger touch mapping

- Independent per-app L and R touch targets using the firmware's native TGK engine
- Separate portrait and landscape mappings, named layouts, and in-game re-editing
- Persistent, nearly transparent saved targets that remain non-interactive during play
- Stock framework press feedback at the mapped targets plus the stock physical-trigger highlights
- Per-trigger single-touch, long-press, and rapid-fire behavior with supported counts of 2, 5, or 10
- Simultaneous L/R operation without cancelling held touchscreen contacts or thumbstick movement
- Foreground-only activation and immediate shutdown when the selected app loses focus
- Native TGK diagnostics, profile transfer, and Master Profile backup coverage
- No Game Space database dependency and no accessibility gesture, shell-tap, touchscreen `sendevent`, or virtual-gamepad injection

### Per-app stock gaming controls

- Compatibility-gated Eco, Balance, and Rise performance modes
- Per-app refresh-rate control through the stock REDMAGIC display service
- Per-app touch sampling, touch sensitivity, touch-follow, and micro-sensitivity profiles
- A foreground-only performance overlay showing refresh rate, touch sampling, active performance mode, FPS, temperature, and fan telemetry
- Stock charge-separation control with charger and minimum-battery safety checks
- Shared foreground lifecycle coordination so TGK, saved targets, refresh rate, touch tuning, performance mode, and telemetry follow the same selected application

### Existing toolbox features

- Fan and micropump control, temperature curves, telemetry, and screen-off cooling policy
- Fan, logo, and shoulder lighting plus RGB Studio and explicit LED ownership arbitration
- Magic Key stock actions, application launching, Android shortcuts, dual-app assignments, and daily schedules
- Game Mode, Charging Mode, Call Lighting, Quick Settings tiles, and the launcher cooling widget
- Portable, versioned Master Profiles and event-driven automation rules
- Shared persistent root execution, duplicate-write suppression, adaptive temperature sampling, and background worker cleanup

### Reverse-engineering documentation

The complete native TGK investigation—including firmware classes, Binder transactions, stock database schema, runtime traces, permission testing, activation ordering, visual effects, and the standalone Game Space-independent proof—is documented in [`docs/NATIVE_TGK_REVERSE_ENGINEERING.md`](docs/NATIVE_TGK_REVERSE_ENGINEERING.md).

Stock Game Space package roles, privileged gesture-monitor behavior, overlay-window evidence, AutoLaunch recovery policy, failed prototypes, and the Toolbox's compatible implementation are documented in the living [`docs/GAME_SPACE_REVERSE_ENGINEERING.md`](docs/GAME_SPACE_REVERSE_ENGINEERING.md) report. New verified Game Space findings should be added there as the investigation continues.

The sections below document the complete 2.6.0 behavior and current architecture.

## Compatibility

The official target is the NX809J running stock RedMagic Android 16 firmware.

LineageOS-based and other custom ROMs can work when they retain the stock RedMagic vendor and kernel interfaces. Compatibility requires equivalent implementations of:

- `/sys/kernel/fan/*`
- `/proc/driver/micropump/*`
- `/sys/class/leds/aw22xxx_led/*`
- `/sys/class/leds/sar0/*`
- `/sys/class/leds/sar1/*`
- RedMagic/Nubia Magic Key system settings
- Either the REDMAGIC Android 16 `IInputManager` TGK additions or the optional
  [Redmagic Trigger Bridge v0.3.1 or newer](https://github.com/austineyoung2000/Redmagic-Trigger-Bridge/releases/tag/v0.3.1)
  companion module for per-app touch mapping
- Stock display, touch-tuning, performance, and charge-separation services for their corresponding per-app features

The app verifies the NX809J identity before requesting root or exposing any controls. The launch gate accepts only exact NX809J model/product identities, including NX809J regional product suffixes, and does not rely on the marketing name alone.

The compatibility layer centralizes the confirmed fan, pump, LED, and trigger paths so capability diagnostics, telemetry, and hardware writes use the same interface definitions. Hardware writes are blocked again at the controller boundary if the device identity is unsupported, protecting against widget, Quick Settings, service, or boot entry points that bypass the activity.

For NX809J custom ROMs, CPU temperature detection resolves the confirmed `cpullc-0-0` sensor by thermal-zone type before falling back to known zone numbers. This tolerates framework-level thermal-zone reordering while remaining restricted to NX809J hardware.

Native TGK and the stock per-app gaming controls are exposed only after their specific compatibility probes pass. A custom ROM may retain the fan, pump, LED, trigger, and thermal interfaces while omitting the proprietary framework APIs. Trigger Bridge v0.3.1 or newer can provide merged physical-touch, L/R mapping, automatic rotation, and trigger haptics on an NX809J ROM that retains the confirmed SAR inputs, Synaptics touchscreen, arming nodes, vibrator nodes, and `/dev/uinput`; stock-only display, touch, performance, and charging controls remain independently gated. Restoring the actual native TGK implementation still requires porting the matching REDMAGIC framework, system-server, native input, vendor, and SELinux pieces rather than copying the application-side Binder calls alone.

## Application layout

The application contains five main tabs:

1. Home
2. Cooling
3. Controls
4. Hardware
5. Lighting

## Home

### Live Dashboard

The dashboard displays:

- Device model and root status
- Current CPU temperature
- Fan state, level, and RPM
- Pump state, frequency, and speed
- Current foreground application
- ROM/build fingerprint
- CPU and installed RAM

Fan and pump telemetry is collected through a batched root read and cached to reduce shell activity. Dashboard polling pauses when the activity is no longer visible, and a manual refresh remains available.

The Live Dashboard also includes a 30-minute thermal-history graph. It reuses samples already produced by the shared temperature monitor, keeps at most 600 points in memory, and performs no additional sensor reads or storage writes.

### Active Mode Inspector

The Home dashboard reports which feature currently owns the shared LED hardware:

- Charging Mode
- Call Lighting
- Game Mode
- RGB Studio
- Normal saved lighting

It also shows whether cooling is controlled by Game Mode, Auto Fan, Auto Pump, an active call fan pause, or manual saved controls. The last applied Master Profile is shown separately as the base configuration so a temporary higher-priority LED owner is not confused with the profile that supplied the underlying settings.

The inspector uses the app's existing ownership and preference state. It performs no additional root commands, sensor reads, polling loops, or hardware writes. Its priority display follows the real LED arbitration order: Charging, Call Lighting, Game Mode, RGB Studio, then Normal.

### Home-screen cooling widget

The optional **RedMagic Cooling** widget shows the current temperature, fan level, and pump profile. It provides direct Fan, Pump, and Refresh controls, while tapping the widget background opens the full application.

The widget has no scheduled update interval and performs no continuous polling. Hardware is read only when Android creates or updates the widget, when the user requests a refresh, or after a widget control is pressed. Fan and pump root work runs on one background executor rather than the launcher thread.

### Quick Settings tiles

Android Quick Settings can expose six optional RedMagic controls:

- Cooling Fan
- Cooling Pump
- Auto Cooling
- Shoulder Triggers
- RGB Studio
- Last applied Master Profile

Each tile reads its state when Android starts listening and refreshes after a press; the tiles do not run a continuous polling loop. Privileged hardware work is dispatched to a shared background executor. Unsupported devices show the tiles as unavailable, and the Shoulder Triggers tile reflects the parsed state of both NX809J trigger nodes.

### Diagnostics

The capability scanner reports whether the expected fan, pump, LED, trigger, and slider hardware interfaces are available. Missing interfaces are reported rather than silently treated as working.

## Cooling

### Fan control

- Fan power on/off
- Manual levels `0` through `5`
- Live RPM reading
- Fahrenheit or Celsius display
- Quiet, Balanced, and Turbo curve presets
- Automatic temperature-based fan control

Automatic fan levels are:

| Temperature | Level |
|---|---:|
| Below 95°F / 35°C | 0 |
| 95–103°F / 35–39°C | 1 |
| 104–112°F / 40–44°C | 2 |
| 113–121°F / 45–49°C | 3 |
| 122–130°F / 50–54°C | 4 |
| 131°F / 55°C and above | 5 |

A 5°F downward hysteresis prevents rapid changes near thresholds. The service does not rewrite the fan level when the desired state already matches the last applied state.

### Micropump control

| Profile | Frequency | Speed |
|---|---:|---:|
| Slow | 4 | 40 |
| Medium | 4 | 60 |
| Quick | 4 | 80 |
| OC / Experimental | 4 | 90 |

Automatic pump mode uses Slow below 95°F, Medium from 95°F through 104°F, and Quick at 105°F or higher. The OC profile is manual and intentionally marked experimental.

### Screen-off cooling policy

Normal fan and pump activity is blocked while the screen is off unless the device is hot. Cooling remains permitted around 100°F / 38°C or above. Shutdown commands and repeated state writes are deduplicated to reduce root work and battery use.

## Controls

The Controls tab provides root verification and RedMagic Magic Key configuration.

Stock Magic Key actions include:

- Camera
- Game Space
- Sound Mode
- Flashlight
- Voice Recorder
- Disabled

The Magic Key can alternatively launch a selected user or system application. It can also use confirmed ZTE mode `17` to launch an Android app shortcut, such as YouTube Search, a new message, or another shortcut published by an installed application.

Shortcut selection uses a two-stage picker: choose the application, then choose one of its manifest, dynamic, or cached Android shortcuts. Shortcut discovery runs through the system shortcut service on a background worker. Stock-action, app-launch, shortcut-launch, and dual-app modes are mutually exclusive.

### Dual-app slider

The optional dual-app mode assigns one launchable app to slider-up and another to slider-down. A scheduled pair can replace both default apps during a chosen daily time window, including schedules that cross midnight.

Slider changes are received through the Android setting observer rather than a polling loop. Enabling dual-app mode saves and temporarily replaces the existing Magic Key action; disabling it restores the action and selected application that were active beforehand.

## Hardware

### Shoulder triggers

The app can enable or disable the trigger hardware, configure automatic startup, and map the left and right triggers independently.

Available mappings include:

- None
- Volume Up
- Volume Down
- Play / Pause
- Next Track
- Previous Track

The Hardware tab presents Trigger Safety as its own card directly below the main Triggers card. Its dedicated configuration dialog reduces accidental input without continuously polling the raw SAR sensors. Four modes are available:

- **Off** — actions run immediately after a valid hardware press
- **Intent Unlock** — requires a configurable tap sequence before actions become active
- **Hold to Activate** — requires an 80, 120, 180, or 250 millisecond hold
- **Intent Unlock + Hold** — combines both protections

Intent Unlock supports separate left and right tap counts plus a 1.5, 2.5, 5, or 10 second unlock timeout. Optional controls can block actions while the keyguard is locked, allow actions only while a selected Game Mode app is active, and let a valid left-trigger press temporarily unlock the right trigger. Input debounce and action cooldown filtering reject duplicate hardware events while preserving held volume-repeat behavior.

Game Mode gating reuses the app's existing event-driven active-game state, so it does not add another foreground-app polling loop.

The Trigger Mapping and Trigger Safety dialogs use the same app-themed Material presentation as the rest of the control center: grouped surface cards, compact selection chips, visible selected states, outlined secondary actions, and accent-colored save actions. Both dialogs follow the selected system light or dark appearance.

Manual **Disable Triggers** stops the service and hardware without erasing the Auto-start preference. Automatic startup remains paused until the user presses **Enable Triggers** or restarts the phone.

### Game trigger mapping

The Hardware tab exposes a separate per-app trigger-mapping manager. This is not the media-action trigger service described above. On compatible stock Android 16 firmware it programs REDMAGIC's native TGK engine so physical L/R events become screen contacts at user-selected coordinates. On a compatible custom ROM, Trigger Bridge v0.3.1 or newer merges the retained physical touchscreen with the `KEY_F7`/`KEY_F8` trigger contacts through one virtual multitouch device and can provide hardware-vibrator feedback.

Each selected application can store named layouts with independent portrait and landscape targets. The editor launches the selected application, places draggable L/R controls over it, and saves the resulting rectangles. During normal play those targets are shown as nearly transparent, touch-through markers: they cannot steal gameplay input or be dragged until the user deliberately enters edit mode again.

The unified foreground runtime performs the following lifecycle:

1. Confirm that the selected package is the top-resumed application.
2. Load the matching orientation and active named layout.
3. Prefer Native TGK when its state can be read and its mapping can be applied.
4. If Native TGK is unavailable or rejects the mapping, release its state and activate an installed, compatible Trigger Bridge v0.3.0 or newer; v0.3.1 adds automatic rotation and module haptics.
5. Keep the unselected backend inactive so the two implementations never own the triggers simultaneously.
6. Keep the saved markers and optional performance telemetry attached to the same foreground owner.
7. Disable both backend paths and remove every related overlay immediately when the app loses the foreground, the screen turns off, or the runtime stops.

Single-touch, long-press, and rapid-fire behaviors can be chosen independently for L and R. Rapid-fire counts are restricted to the confirmed stock values. The framework's own top-edge and mapped-target visual effects provide down/up feedback without the app intercepting F7/F8 events.

Native TGK configuration is performed through ordinary app-accessible InputManager calls and does not require root on the tested stock firmware. It remains the preferred backend and retains the complete stock effects and behavior modes. Trigger Bridge v0.3.1 is a root module for NX809J ROMs that preserve the required vendor/kernel trigger, touchscreen, and vibrator interfaces; it is not a generic Android trigger solution. Its daemon boots inactive without a virtual touchscreen, creates the merged proxy only for a configured foreground game, and releases every virtual contact and physical device during cleanup. See the [native TGK reverse-engineering report](docs/NATIVE_TGK_REVERSE_ENGINEERING.md) and the companion module's [control contract](https://github.com/austineyoung2000/Redmagic-Trigger-Bridge/blob/main/docs/CONTROL.md).

### Haptic feedback

The Hardware tab contains optional hardware haptic feedback for shoulder-trigger actions, successful dual-app slider launches, and Master Profile application. It is disabled by default and offers Low, Medium, and High strengths with an immediate test pulse when a strength is selected. Trigger Bridge 0.3.1 and newer also use the selected strength for module-backed shoulder-trigger feedback when that game profile enables haptics.

Haptic pulses use the confirmed NX809J `zte_vibrator` duration, gain, and activate nodes through the shared root broker. Feedback is event-driven, rate-limited, and performs no continuous polling.

### Master profiles

Master profiles capture the wider application state, including:

- Fan, pump, lighting, and automatic-control state
- Game Mode profile and selected games
- Per-game profile assignments
- Charging Mode profiles
- Incoming and connected-call profiles
- Call fan-pause preference
- RGB Studio configuration
- Temperature-unit preference
- Magic Key mode and selected application
- Magic Key shortcut package, ID, and display label
- Dual-app slider mappings and schedule
- Hardware haptic enabled state and strength
- Real-time preview preference
- Trigger mappings, startup state, and complete Trigger Safety configuration
- Native TGK applications, named layouts, portrait/landscape targets, per-trigger behavior, rapid-fire counts, haptics, and saved-target visibility
- Charge-separation state
- Per-app refresh-rate, touch-tuning, and performance-mode profiles

Profiles can be named, applied, deleted, exported as a portable JSON backup, and imported on another installation.

The versioned profile format currently uses schema version 11. Versions 7 through 11 added native TGK profiles, charge separation, refresh-rate profiles, touch-tuning profiles, and performance-mode profiles respectively. Earlier schemas remain importable and receive safe defaults for fields they did not contain. The internal backup-format identifier remains backward compatible across the Redmagic 11 Toolbox rebrand.

### Automation rules

Saved Master Profiles can be assigned to power connected, power disconnected, battery low, battery recovered, and first-unlock-after-restart events. Android broadcasts trigger the rules only when those events occur; the automation engine performs no continuous polling. Rules are included in portable JSON backups and are cleared automatically if their assigned profile is deleted.

## Settings

The settings page opens from the gear beside the animated Redmagic 11 Toolbox header. It contains app-wide display preferences rather than physical hardware controls:

- Fahrenheit or Celsius temperature display
- Automatic light/dark appearance following the Android system theme

Physical haptic configuration remains in the Hardware tab.

## Lighting

The Lighting tab controls:

- Cooling fan LED
- Rear logo LED
- Illuminated side GAME MODE bar (2.6.0)
- Shoulder LED strips

Each zone can be enabled, disabled, and configured independently. Effects include Steady, Breathe, Flashing, and Rapid where supported. Colors include Red, Orange, Yellow, Green, Cyan, Blue, Purple, and Pink. Confirmed stock fan-light presets are also supported.

### Independent logo and GAME MODE bar (2.6.0)

Open the separate **LOGO LED** or **GAME MODE BAR** entry to edit that area while preserving its companion. Both retain separate preset colors, brightness, and enable switches. Their effect remains shared. Game Mode, charging, incoming/connected calls, and Master Profiles preserve the pair. RGB Studio also has an independent bar sequence and cycle speed. See the [mapping and test report](docs/LOGO_GAME_MODE_BAR_REVERSE_ENGINEERING.md).

### Split trigger colors

Open **Trigger LEDs** inside LED Zones and enable **Separate top and bottom colors** to choose a preset for each trigger. The same option is available in the Game Mode profile, Charging Trigger LEDs, and both incoming-call and connected-call profiles. Each mode saves its own combination. Both triggers share one effect: Steady, Breathe, Flashing, or Rapid. The controls use the existing eight-color palette; there are no custom hex inputs or RGB sliders.

Master Profiles and portable backups retain each mode's saved trigger combination. The existing lighting priority and restoration rules still decide which mode applies. Turning off the split option returns that profile to a matching-color selection.

RGB Studio also supports separate top/bottom preset sequences. Select one color for each trigger for a fixed combination, or multiple colors to cycle. Split colors use the validated vendor-program mapping described in the [reverse-engineering report](docs/TRIGGER_LED_REVERSE_ENGINEERING.md). Later owner testing confirmed all four trigger effects; see the updated test report.

The **Real-time preview** switch is located inside LED Zones and controls whether changes are written immediately while editing a profile.

## RGB Studio

RGB Studio provides a persistent multi-zone color cycle with:

- Synchronized or independent LED zones
- Selectable ordered color sequence
- Independent GAME MODE bar sequence, enable, brightness, and cycle speed in the 2.6.0 test build; shared logo/bar effect
- Optional separate top/bottom trigger preset sequences with a shared effect and cycle speed
- Steady, Breathe, Flash, and Rapid effects
- Per-zone speeds from 0.5 to 6 seconds
- Immediate, 1, 5, 15, or 30-minute screen-off timeouts
- Apply-to-all action
- Explicit service stop control

RGB Studio uses the shared persistent root broker instead of launching `su` for every animation frame.

## Game Mode

Game Mode applies a saved fan, pump, and LED profile when a selected application becomes active. Its trigger editor can save separate top/bottom preset colors with a shared effect. It uses Usage Access and accessibility foreground-app events instead of continuous idle polling.

While a selected game is active, a slow two-minute verification poll checks state. Polling stops when the game closes or the screen turns off, and the normal hardware profile is restored.

## Per-app gaming controls

The stock-only per-app controls share the same top-resumed-activity detection used by native TGK. Each controller probes its vendor dependency before exposing or applying a profile:

- **Refresh rate** selects the requested stock display mode for the chosen package.
- **Touch tuning** stores the confirmed REDMAGIC sampling-rate, sensitivity, follow, and micro-sensitivity settings per package.
- **Performance mode** selects Eco, Balance, or Rise and synchronizes the stock audio/performance state when that vendor API is available.
- **Performance overlay** is visible only for opted-in foreground packages and can show display refresh rate, configured touch sampling, performance mode, live FPS when the vendor monitor supplies it, CPU temperature, and cached fan telemetry.

These features are deliberately gated separately. Failure or absence of one vendor API does not disable unrelated NX809J hardware controls or native TGK.

## Charge separation

Charge separation uses the stock ZTE provider and `charge_separation_switch` system state when the compatibility probe succeeds. Enabling is blocked unless a charger is connected and the battery meets the controller's minimum threshold. The saved state is covered by Master Profiles.

## Charging Mode

Charging Mode applies dedicated fan, logo, and shoulder LED profiles while power is connected. Open **Charging Trigger LEDs** to select matching or split top/bottom colors and their shared effect. Android power and battery broadcasts drive the service, so charging state is not continuously polled. The previous valid lighting owner is restored after charging ends.

## Call Lighting

Call Lighting supports separate profiles for ringing and connected calls. Each profile has its own top/bottom trigger color combination and shared effect. An optional fan-pause feature saves the previous fan state, stops automatic fan control, turns the fan off during the call, then restores the previous state when the call ends.

## LED priority

The ownership system prevents lower-priority modes from overwriting higher-priority lighting:

1. Charging Mode
2. Call Lighting
3. Game Mode
4. RGB Studio
5. Normal saved LEDs

When a mode ends, the next valid owner is restored. Normal and Game Mode LED writes are blocked while the screen is off.

## Temperature monitoring

Temperature is read from readable Linux thermal zones without opening a root shell. The monitor shares one cached value between the dashboard, Auto Fan, and Auto Pump and reacts immediately to Android thermal-status events.

| State | Sampling interval |
|---|---:|
| App visible and interactive | 3 seconds |
| Background and hot | 5 seconds |
| Background, cool, and interactive | 15 seconds |
| Screen off and cool | 30 seconds |

The worker thread stops when no subscribers remain. The Fahrenheit/Celsius preference is also respected by the shared foreground notification.

## Root execution architecture

Normal privileged commands use one serialized persistent root shell. This lowers process churn, preserves command ordering, and shares one broker between cooling, lighting, RGB Studio, and trigger actions.

If the persistent shell fails, it is recreated automatically. A one-shot `su -c` path remains as a compatibility fallback for root providers that reject interactive shells. Command exit codes are checked.

The two trigger input readers remain dedicated blocking processes because each must continuously consume its kernel input stream.

## Hardware write safeguards

- Fan levels are clamped to `0–5`.
- Fan PWM values are clamped to `0–255`.
- Pump profiles generate known fixed commands.
- Stock fan-light presets use an allowlist.
- Duplicate writes to the same hardware resource are skipped for two seconds.
- Fan and pump telemetry caches are invalidated after successful writes.
- Automatic services skip unchanged states.
- Root hardware writes are serialized.
- Screen-off cooling and LED policies prevent unnecessary hardware activity.

These controls reduce risk and redundant work but cannot eliminate every risk of root-level hardware access.

## Foreground services

Auto Fan, Auto Pump, fan-light persistence, and RGB Studio use Android's `specialUse` foreground-service type. This describes continuous user-enabled internal hardware control and avoids incorrectly consuming Android 15's six-hour `dataSync` allowance.

Android displays an ongoing notification while continuous hardware services are active.

## Boot behavior

After boot or user unlock, the app can restore enabled behavior for:

- Trigger auto-start
- Charging Mode
- Call Lighting
- RGB Studio
- Dual-app slider handling
- First-unlock Master Profile automation

A temporary manual trigger disable is cleared by a full restart.

## Permissions

| Permission or access | Purpose |
|---|---|
| Root / superuser | Write fan, pump, LED, trigger, and Magic Key state |
| Foreground Service | Keep explicitly enabled hardware controls active |
| Foreground Service Special Use | Correctly classify continuous hardware-control services |
| Notifications | Display required foreground-service state |
| Boot Completed | Restore user-enabled services after restart |
| Usage Access | Detect selected foreground games |
| Accessibility Service | Receive foreground-app events and support triggers |
| Phone State | Apply ringing and connected-call lighting |
| Display-over-other-apps app-op | Support trigger setup where required |

## Privacy and integrity

The app is designed to operate locally.

- The manifest does **not** request Android's `INTERNET` permission.
- No advertising framework is included.
- No analytics SDK is included.
- No cloud account is required.
- No remote-control service is included.
- No device telemetry is uploaded.
- Profiles and settings remain in local Android application storage.

GitHub and reference links open in the user's chosen browser. The app itself does not download web content.

The public repository contains the application source, Gradle configuration, and GitHub Actions workflow. Because the app receives root access, users should install trusted builds and review changes before granting permanent superuser permission.

## Battery and performance design

- Shared persistent root process
- Batched root telemetry reads
- Thirty-second hardware telemetry cache
- Dashboard polling paused outside the foreground
- Adaptive non-root temperature monitoring
- In-memory thermal history using existing samples
- Event-driven charging and phone-state handling
- Event-driven Master Profile automation rules
- Event-driven dual-app slider launches and scheduled mapping selection
- Event-driven, rate-limited hardware haptic feedback
- Event-assisted Game Mode activation
- Two-minute Game Mode checks only while a selected game is active
- Fan, pump, and general write deduplication
- Background-priority worker threads
- Screen-off cooling and lighting policies
- Configurable RGB screen-off timeout
- Worker cleanup when services stop

## First launch

On first launch, the app validates the device and presents a complete setup checklist before requesting root. It applies the approved feature permissions, verifies every item, scans the hardware interfaces, and then opens the main interface.

The root-assisted setup can grant Usage Access, notification permission, phone-state permission, display-over-other-apps access, and accessibility-service activation. If Android or the root manager rejects an item, the app displays the full five-item status instead of a shortened toast and links directly to the first relevant Android settings page. Review the root request before approving it.

## Installation

1. Open the repository's [Releases](https://github.com/austineyoung2000/Redmagic-Control-Center/releases) page.
2. Download the signed release APK.
3. Allow installation from the browser or file manager if Android requests it.
4. Install the APK.
5. When updating an existing installation, reboot the phone once so Android and REDMAGIC discard the replaced accessibility, overlay, watchdog, and trigger runtime.
6. Open the application, grant root if requested, and confirm the Toolbox accessibility service remains enabled.

Development artifacts are available from successful [Android CI](https://github.com/austineyoung2000/Redmagic-Control-Center/actions/workflows/android.yml) runs.

## Building locally

Requirements:

- Git
- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0

```bash
git clone https://github.com/austineyoung2000/Redmagic-Control-Center.git
cd Redmagic-Control-Center
git checkout sixteen
./gradlew assembleDebug
```

The debug APK is generated in `app/build/outputs/apk/debug/`.

Signed release builds use:

- `SIGNING_STORE_FILE`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Never commit signing credentials or keystores.

## GitHub Actions and releases

Android CI runs for pushes and pull requests involving `main` or `sixteen`, manual workflow dispatches, and version tags beginning with `v`.

The workflow builds and uploads the signed release APK and creates a GitHub Release when a version tag is pushed. Release signing material is supplied through encrypted repository secrets.

## Troubleshooting

### Root access is missing

- Confirm Magisk, KernelSU, APatch, or another compatible `su` provider is installed.
- Verify Redmagic 11 Toolbox is allowed in the root manager.
- Remove an existing denied entry and reopen the app if necessary.
- Use **Controls → Check Root**.

### Hardware is reported as missing

Confirm the ROM retains the stock RedMagic vendor and kernel interfaces. Use Home diagnostics to identify the unavailable subsystem.

### Game Mode does not activate

- Grant Usage Access.
- Enable the accessibility service.
- Select at least one game.
- Save a Game Mode profile.
- Confirm the screen is on and unlocked.

### Triggers do not auto-start

- Enable Auto-start triggers.
- Confirm the trigger nodes are detected.
- Confirm the accessibility service is enabled.
- If the APK was just updated, reboot once before testing either native TGK or the companion module.
- If manually disabled, press Enable Triggers or restart.

### Lighting is replaced unexpectedly

Check whether Charging Mode, Call Lighting, Game Mode, or RGB Studio currently owns the LEDs.

### Fan or pump values show `?`

Verify root access and confirm that the expected vendor nodes exist on the current ROM, then refresh the dashboard.

## Known limitations

- The app is intentionally restricted to NX809J.
- Other RedMagic generations require validation and porting.
- Vendor paths can change between firmware releases.
- Custom ROM support depends on retained stock vendor and kernel interfaces.
- Root denial prevents hardware control.
- Game Mode requires Usage Access.
- Call Lighting requires phone-state access.
- Continuous services display an Android notification.
- The OC pump profile is experimental.
- Profiles are local and are not cloud-synchronized.
- Clearing application data removes saved profiles.
- Magic Key system settings can remain active until changed again or reset by the ROM.

## Reporting issues

Use the repository [issue tracker](https://github.com/austineyoung2000/Redmagic-Control-Center/issues) and include:

- Device model
- ROM and build fingerprint
- Android and app versions
- Root solution
- Steps to reproduce
- Relevant Logcat output
- Home diagnostics results
- Whether the problem occurs on stock firmware

Do not publish phone numbers, account credentials, signing material, or unrelated personal logs.

## Contributing

Changes should preserve NX809J device gating, background-thread execution, LED ownership, screen-off safety, write deduplication, local privacy, profile compatibility, and CI build compatibility.

Changes to sysfs, procfs, vendor settings, foreground services, or root execution should be tested on supported physical hardware.

## Project links

- [Repository](https://github.com/austineyoung2000/Redmagic-Control-Center)
- [Releases](https://github.com/austineyoung2000/Redmagic-Control-Center/releases)
- [Android CI](https://github.com/austineyoung2000/Redmagic-Control-Center/actions/workflows/android.yml)
- [Issue tracker](https://github.com/austineyoung2000/Redmagic-Control-Center/issues)

## Disclaimer

This project is independent and is not affiliated with, endorsed by, or maintained by RedMagic, Nubia, or ZTE.

Root access and direct hardware control can cause unexpected behavior, instability, increased heat, battery drain, or hardware stress when used incorrectly. You are responsible for reviewing and testing the software on your device.

Use the application at your own risk.

## Trigger LED investigation

See [Trigger LED reverse engineering and split-color tests](docs/TRIGGER_LED_REVERSE_ENGINEERING.md) for the observed driver interface, vendor program mappings, physical test results, implementation checks, and remaining validation.


### Lighting brightness implementation

The brightness feature was developed and owner-tested on `test/led-brightness`, then merged into `sixteen` for 2.5.5. It covers LED Zones, gaming, charging, incoming/connected calls, RGB Studio, and Master Profiles. See the [reverse-engineering report](docs/LED_BRIGHTNESS_REVERSE_ENGINEERING.md) for the discovery, driver findings, payload mappings, safeguards, and physical evidence.

### Logo and GAME MODE bar implementation

Version 2.6.0 uses separate physical RGB groups in the existing logo vendor program. [The reverse-engineering report](docs/LOGO_GAME_MODE_BAR_REVERSE_ENGINEERING.md) records the discovery and validation. The owner authorized the merge and release on October 4, 2026.

### Trigger Bridge edge highlights

Toolbox adds an optional per-game red/blue edge glow for module-backed gameplay. It observes the bridge's reserved left/right touch IDs without grabbing input or modifying the module. Native TGK retains its stock visuals. See [implementation and device checklist](docs/TRIGGER_BRIDGE_VISUAL_FEEDBACK_TEST.md); The owner confirmed working mapping and highlights together after reboot on October 4, 2026, and authorized merging into `sixteen`.

### Root module boot startup

With compatible Toolbox and Trigger Bridge builds installed, the module initializes Toolbox background services after Android finishes booting and user 0 first unlocks. No Toolbox screen opens. Root/system/shell-only receiver access, NX809J validation, and the existing per-boot startup claim protect this fallback. It respects saved lighting/trigger settings and keeps game mappings under normal foreground ownership. Grant required permissions and configure Toolbox once before using automatic startup. This also works while Native TGK owns the triggers.

The module makes up to five broadcast attempts and records the latest result in `/data/adb/redmagic_trigger_bridge/toolbox-boot.log`. Broadcast delivery does not guarantee every configured service started; confirm device behavior after reboot. Older APKs lack this receiver. Both updated APK and module are required.

### Screen-off lighting behavior

Normal, game, and call lighting shut down when the screen turns off. Their queued profile writes are rejected while the screen remains off, and shutdown does not depend on clearing stale ownership flags first. Charging lighting remains available while charging. RGB Studio pauses immediately on screen-off, regardless of an older saved timeout. AOD/doze does not count as an awake screen; both interactive power state and the default display being ON are required for ordinary lighting. Saved colors, effects, and brightness are preserved for restoration on wake.

### Step 1 validation: screen-off and AOD
Test normal lighting, a selected game, and RGB Studio separately while unplugged. Lock with AOD enabled and disabled; LEDs must turn off immediately and stay off during AOD clock updates and notification pulses. Unlock and verify saved colors, effects, split zones, and brightness resume. Repeat several lock/wake cycles. Plugged-in charging lighting remains the explicit exception. Notification RGB is not implemented in this step.

Device AOD validation is pending for this change.

### Boot diagnostics
Settings → Boot diagnostics opens a scrollable report with Refresh, Copy report, and Close. It records the boot event and startup decision, per-service startup requests or exceptions, configured automatic features, current visible app services, screen/charging state, and the root helper's latest log. Startup requests are explicitly distinguished from proof of running; service presence is not proof that cooling hardware is enabled. Older boot records and helper logs are labeled. Diagnostics are local and read on demand. Install this build and reboot to populate startup events.

### Lighting priority and screen-off notifications (step 3)
Incoming ringing calls → plugged-in charging → timed screen-off notifications → selected foreground game → RGB Studio/normal LEDs. Answering or dismissing a call releases lighting ownership; connected-call profiles no longer apply. Charging ownership uses the plugged-in battery state, including charge separation/full battery. Ordinary lighting remains off during lock/AOD.

Lighting → Notification lighting allows opt-in notification access and a saved profile per launchable app: fixed palette color, effect, brightness, selected zones, and a 3–30 second duration. Logo also includes the GAME MODE bar. Fan/trigger lighting may enable fan power; the existing cooling screen policy is applied afterward. Notification contents are not read, stored, or uploaded. App/package and notification keys are used for routing/deduplication. Updates to a notification do not extend its window; bursts share the current deadline. Charging and calls preempt notifications. Removal, wake, disabling, disconnect, and expiry restore the next eligible owner. A bounded partial wake lock keeps the timer runnable without lighting the display.

Device validation: test Facebook blue/YouTube red or other installed apps, expiry while locked/AOD, repeated updates, notification removal, enable/disable, calls while plugged in, answer/dismiss, unplug, and return from a selected game. Confirm saved split zones/effects/brightness restore. These changes require device validation before merge.
