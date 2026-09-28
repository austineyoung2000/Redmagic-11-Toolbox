# REDMAGIC NX809J Game Space reverse engineering

## Purpose and maintenance policy

This is the living engineering record for REDMAGIC Game Space behavior investigated while building Redmagic 11 Toolbox. It covers the stock package split, in-game overlay invocation, privileged input monitoring, process policy, AutoLaunch recovery, and the design choices made in the Toolbox.

New Game Space findings must be added here when they are verified. Each addition should identify the firmware/build, evidence source, observed behavior, and whether the conclusion is confirmed or inferred. Raw captures may remain outside the repository when they are too large or contain device-specific noise, but the durable conclusion and the commands or method required to reproduce it belong in this report.

Native shoulder-trigger mapping is documented separately in [`NATIVE_TGK_REVERSE_ENGINEERING.md`](NATIVE_TGK_REVERSE_ENGINEERING.md). That report is authoritative for the TGK Binder ABI, input pipeline, mapping order, haptics, visual effects, and Game Space-independent proof.

## Tested environment

- Device: RedMagic 11 Pro / NX809J
- Firmware family: stock REDMAGIC Android 16
- Game launcher package observed: `cn.nubia.gamelauncher`
- In-game assistant package observed: `cn.nubia.gameassist`
- Performance companion package observed: `cn.nubia.gamepi`
- Toolbox package: `com.elitedarkkaiser.redmagic`

Firmware implementation details are proprietary and may change between builds. The Toolbox therefore probes vendor capabilities and keeps unrelated features independently gated.

## Stock package responsibilities

Captured package and service state shows that Game Space is not one monolithic application.

| Package | Confirmed or observed responsibility |
|---|---|
| `cn.nubia.gamelauncher` | Game library and settings UI, game lifecycle receivers, TGK configuration service, gamepad service, profile/configuration storage, and entry points for stock gaming settings. |
| `cn.nubia.gameassist` | In-game assistant surface, side indicators, gesture monitor, and privileged overlay/input behavior while a game is active. |
| `cn.nubia.gamepi` | Performance companion service and stock performance integration. |

Relevant exported or discoverable stock service actions include:

- `cn.nubia.tgk.TGKSERVICE`
- `cn.nubia.gamepad.startGamepadService`
- `cn.nubia.gamepanel.POWERPANELSERVICE`
- `cn.nubia.startGameEventService`
- `cn.nubia.game.GameBroadcastService`

These components configure and coordinate vendor framework facilities. They are not where native TGK contacts are synthesized; that occurs inside the modified Android input stack described by the TGK report.

## Stock side-handle architecture

### Window evidence

WindowManager and input dumps captured the stock `IndicateLeft` and corresponding right-side indicator as application-overlay windows owned by `cn.nubia.gameassist`. The left indicator had these important properties:

- Frame measured approximately `17 x 421` pixels in the captured landscape transform.
- `TYPE_APPLICATION_OVERLAY`.
- `NOT_FOCUSABLE` and `NOT_TOUCHABLE`.
- `LAYOUT_IN_SCREEN` and hardware acceleration.
- Private `TRUSTED_OVERLAY` behavior.
- A transparent format and edge placement.

The important result is that the visible indicator is not itself the swipe target. It is explicitly non-touchable.

### Gesture-monitor evidence

The input dispatcher simultaneously reported a separate connection named:

```text
[Gesture Monitor] cn.nubia.gameassist
```

The stock packages request and receive privileged permissions including:

- `android.permission.MONITOR_INPUT`
- `android.permission.INTERNAL_SYSTEM_WINDOW`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.INJECT_EVENTS`

This explains how stock Game Space can display thin non-touchable indicators while observing an edge swipe independently of ordinary application windows. The visual layer and the input-monitor layer are separate.

### Why a normal APK cannot copy it exactly

`MONITOR_INPUT`, trusted overlays, and internal system windows are privileged facilities. A normal user-installed application cannot acquire the same global gesture monitor merely by declaring the permissions. An application-overlay window can receive touches only inside its touchable region; placing that region on the physical edge competes with Android's Back gesture.

Therefore an exact stock clone requires one of the following:

- A platform-signed or privileged system application with the matching permissions.
- A framework or system-server integration.
- A root/module implementation that adds an equivalent privileged input component.

The regular Toolbox APK intentionally does not impersonate this privileged path.

## Toolbox invocation design history

### Floating button

The first Toolbox implementation used a small floating `GS` button to open the in-game drawer. This was technically reliable and did not require edge interception.

### Dual edge-handle prototype

The next prototype replaced the button with left and right application-overlay handles. The handles were moved upward and their touch windows were narrowed and offset using system gesture insets. This improved coexistence but could not reproduce stock behavior: unlike Game Space's non-touchable indicator plus privileged gesture monitor, the Toolbox handle had to remain touchable to detect a swipe.

Even a narrow touch target at the physical edge can interfere with predictive or classic Back gestures. The prototype was removed rather than disguising that architectural limitation.

### Final 2.5.1 design

Version 2.5.1 restores a single two-part floating control:

- A dedicated grip moves the control without accidentally opening the drawer.
- The `GS` action opens or closes the drawer.
- The button remains inside detected left and right system-gesture insets.
- Its normalized position is saved per application and orientation.
- The compact drawer moves independently from its header.
- Drawer position is also saved per application and orientation.
- The drawer's backdrop excludes the physical left and right gesture strips, leaving Android Back available.
- No edge indicator or edge touch-capture window remains.

This is not visually identical to stock Game Space, but it is honest about normal-APK constraints and predictable during gameplay.

## Drawer and editor ownership

The `GameplaySpaceOverlay` is owned by the same selected-application lifecycle as native TGK and saved target markers. It is attached only for a configured foreground application and is removed on game exit, screen-off, editor transitions, or runtime teardown.

The drawer provides compact entry points for:

- Performance state and profiles
- Native L/R trigger editing and layouts
- Cooling state and controls
- Lighting state and controls
- Supporting tools

The shoulder-trigger editor is a separate movable overlay. Its behavior selectors open independent floating menus rather than expanding the editor panel. L/R targets remain draggable only while editing; saved gameplay markers remain touch-through.

Accessibility is used as one foreground-state signal and lifecycle binding. It is not used to synthesize TGK touches. Native shoulder contacts continue to come from the REDMAGIC input framework.

## Firmware process-kill investigation

### Observed failure

During gameplay the firmware repeatedly terminated both Toolbox processes at nearly the same timestamp:

- `com.elitedarkkaiser.redmagic`
- `com.elitedarkkaiser.redmagic:gameplay_watchdog`

`ApplicationExitInfo` reported reason `SIGNALED`, status `9` (`SIGKILL`), with both processes at foreground-service importance. There was no Java exception or application crash trace. After the UID-wide kill, Android retained dead accessibility connection records but did not reconstruct the gameplay runtime. Opening the Toolbox UI temporarily restored overlays because it made the application eligible to start again.

This established that an in-APK service, a second process, `START_STICKY`, and mutual Binder bindings cannot be the final recovery boundary when the firmware kills the entire application UID.

### External supervisor boundary

The recovery boundary was moved to a minimal root-owned script installed at:

```text
/data/adb/service.d/redmagic_gameplay_supervisor.sh
```

Its state and rotating log are stored under:

```text
/data/adb/redmagic_toolbox/
```

The supervisor performs no fan, pump, LED, TGK, or other hardware writes. In steady state it performs a cheap `pidof` check for the watchdog process. If the process is missing, the package is installed, and the Toolbox accessibility service remains configured, it requests a watchdog foreground-service start with bounded exponential retry.

The script also:

- Uses a lock directory and validated PID file to prevent duplicate supervisors.
- Replaces older script versions safely.
- Cleans stale locks.
- Rotates its log at a bounded size.
- Removes an orphaned installed script after repeated confirmation that the package no longer exists.

## Android caller identity findings

Framework commands did not behave correctly when issued directly from the root/KSU SELinux identity. Examples included `settings` and `am` calls failing with a Binder transaction error. Running the same framework-facing commands through UID 2000 supplied the Android shell calling identity expected by those services:

```sh
su 2000 -c 'settings --user 0 get secure enabled_accessibility_services'
```

The watchdog service is exported but protected by `android.permission.DUMP`, which the shell identity holds. This permits the external supervisor to target the service without exposing it to arbitrary applications.

Root remains necessary to install and retain the `service.d` supervisor, while shell identity is used only for the Android framework calls it performs.

## REDMAGIC AutoLaunch policy

### Service discovery

The firmware registers:

```text
AutoLaunch: [android.app.IAutoLaunchManager]
```

The cached policy entry for the Toolbox contained self-start and related-start policy fields, but changing the persistent-looking policy through the initially tested transaction did not make an external foreground-service start eligible. The framework continued to return:

```text
Error: Blocked by AutoLaunch
```

No corresponding Auto Launch, App Launch, or Startup Manager page was exposed in the tested Settings UI.

### Working recovery sequence

Transaction 6 behaved as a one-shot pending-launch whitelist. The confirmed sequence is:

```sh
su 2000 -c \
    'service call AutoLaunch 6 s16 com.elitedarkkaiser.redmagic'

su 2000 -c \
    'am start-foreground-service --user 0 \
        -a com.elitedarkkaiser.redmagic.KEEP_GAMEPLAY_WATCHDOG \
        -n com.elitedarkkaiser.redmagic/.GameplayRuntimeWatchdogService'
```

After transaction 6, the watchdog process and main gameplay runtime started successfully. Supervisor version 3 arms this whitelist immediately before each recovery attempt. The call is best-effort so retained `service.d` support still works on ROMs that do not expose the REDMAGIC service.

## Runtime reconstruction chain

The final recovery topology is:

1. The root-owned supervisor detects that the watchdog process is absent.
2. It verifies that the package exists and accessibility is configured.
3. It uses the shell identity to arm AutoLaunch transaction 6.
4. It starts `GameplayRuntimeWatchdogService` as a foreground service.
5. The watchdog process binds `GameplayRuntimeService` in the main application process.
6. The runtime reconstructs foreground ownership, TGK programming, saved markers, and opted-in overlays.
7. Normal application lifecycle logic removes state when the configured game is no longer foreground.

On-device validation included a full Call of Duty match, loadout editing, supply-crate interaction, and more than fifteen minutes of continuous play without losing the overlay. Closing and reopening the game also restored the intended controls without toggling accessibility or rebooting.

## Stock Game Space versus Toolbox

| Area | Stock Game Space | Redmagic 11 Toolbox |
|---|---|---|
| Invocation | Non-touchable trusted edge indicators plus privileged global gesture monitor | Movable floating button kept outside Back gesture regions |
| Privilege | Platform/vendor privileged permissions | Normal overlay/accessibility permissions plus root only for hardware and external recovery |
| Trigger contacts | REDMAGIC native TGK input pipeline | Same firmware TGK pipeline, configured independently |
| Configuration storage | Stock Game Space providers and internal data | Toolbox per-application profiles and versioned Master Profiles |
| Runtime recovery | Vendor/system integration | Watchdog binding plus root-owned `service.d` supervisor and AutoLaunch one-shot whitelist |
| UI | Vendor full-screen assistant | Compact movable drawer and independent movable trigger editor |

## Security and compatibility boundaries

- The supervisor is installed only on a validated NX809J and only while accessibility is configured.
- The supervisor starts the existing protected watchdog service; it does not accept arbitrary commands.
- Vendor AutoLaunch transaction 6 is narrowly scoped to the Toolbox package.
- Native TGK remains non-root on the tested firmware, but direct hardware controls and the external recovery boundary require root.
- The Toolbox does not request or emulate `MONITOR_INPUT` and does not create trusted system overlays.
- Custom ROMs require compatible vendor framework services; copied application code alone cannot recreate missing TGK, AutoLaunch, performance, display, or touch-tuning implementations.

## Reproduction checklist for future investigations

When adding new findings, capture and compare as applicable:

1. `service list` and the interface descriptor of the target vendor service.
2. Package manifests, requested/granted privileged permissions, services, receivers, and providers.
3. `dumpsys window windows` for names, types, flags, ownership, frames, and trusted-overlay state.
4. `dumpsys input` for gesture monitors and input-channel ownership.
5. `dumpsys activity services` for process, caller UID, foreground-service eligibility, and bindings.
6. `dumpsys activity exit-info` for firmware kills versus Java crashes.
7. Binder calls under both root and shell identity when caller identity may affect framework policy.
8. A stock baseline, an isolated feature test, and a Game Space-stopped test before declaring a dependency removed.
9. The exact firmware build and date of the capture.

## Confirmed conclusions

- Stock Game Space's visible side indicators are non-touchable; a separate privileged gesture monitor performs invocation.
- A normal overlay APK cannot duplicate that architecture exactly without competing with Android Back.
- Native TGK does not require the Game Space application process once the REDMAGIC input framework is configured directly.
- REDMAGIC firmware may kill all processes in the Toolbox UID despite foreground-service importance.
- An in-app watchdog cannot recover from a UID-wide kill by itself.
- Framework calls must use the appropriate Android caller identity; Linux UID 0 is not automatically accepted by every Binder service.
- AutoLaunch transaction 6 provides the one-shot eligibility needed for shell-originated watchdog recovery on the tested firmware.
- The 2.5.1 movable button and drawer design preserves Back gesture access without pretending to have stock privileged input-monitor capabilities.
