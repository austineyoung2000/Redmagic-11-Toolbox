# Redmagic 11 Toolbox 2.5.1

Version 2.5.1 is a stability and in-game usability release for the standalone gaming stack introduced in 2.5.0. It preserves the existing application ID and signing identity, so it installs over earlier versions without clearing saved profiles.

## Reliable gameplay runtime

- Moved gameplay overlay and native TGK ownership out of the accessibility-service binding and into a dedicated foreground runtime.
- Added an independent watchdog process that binds the runtime and requests state reconstruction after unexpected process termination.
- Added a minimal root-owned `service.d` supervisor because REDMAGIC firmware can kill every process belonging to the application UID simultaneously.
- Routed external recovery through the Android shell identity required by framework Binder services.
- Integrated REDMAGIC AutoLaunch transaction 6 as a one-shot launch whitelist before restarting the watchdog foreground service.
- Added safe supervisor replacement, stale-lock recovery, bounded retry backoff, process checks, and rotating diagnostics.
- Restored overlays, saved trigger targets, and native TGK state after firmware process kills without requiring an accessibility toggle or reboot during normal gameplay recovery.
- APK updates that replace the accessibility, watchdog, overlay, or trigger runtime require one device reboot before validation; the app now detects an update installed during the current boot and explains this requirement.

## In-game Game Space controls

- Added a compact Game Space-style drawer for performance, triggers, cooling, lighting, and tools.
- Restored a single movable `GS` button with a dedicated drag grip.
- Made the compact drawer independently movable from its header.
- Persisted button and drawer positions separately for every selected application and orientation.
- Kept the button and drawer inside Android system-gesture insets and excluded the physical edge strips from the drawer backdrop.
- Removed experimental edge handles and their touch-capture windows so the Android Back gesture remains unobstructed.

## Trigger editor polish

- Reduced the editor footprint and L/R target size.
- Made the complete shoulder-trigger editor movable.
- Replaced expanding behavior lists with independent floating dropdown menus.
- Kept trigger behavior, rapid-fire counts, native mapping, and runtime lifecycle semantics unchanged.

## Lighting and efficiency

- Combined fan multicolor palettes with supported lighting effects.
- Reduced persistent monitoring and hardware-control overhead.
- Prevented target-editor theme failures from taking down the accessibility binding.
- Tightened foreground lifecycle recovery around application transitions and runtime races.

## Reverse-engineering reports

- Retained `docs/NATIVE_TGK_REVERSE_ENGINEERING.md` as the authoritative native Touch Game Key report.
- Added `docs/GAME_SPACE_REVERSE_ENGINEERING.md` for stock package roles, window and input evidence, privileged handle invocation, AutoLaunch policy, process-kill recovery, rejected prototypes, and the Toolbox implementation.
- Future verified Game Space findings should be recorded in the Game Space report so the repository remains the project record rather than relying on chat history.

## Compatibility

- Primary target: RedMagic 11 Pro / NX809J.
- Native TGK and the new vendor gaming controls require compatible REDMAGIC Android 16 framework services.
- General hardware features can work on NX809J custom ROMs that preserve the required vendor and kernel interfaces.
- Stock-only controls are hidden or rejected when their compatibility checks fail.
- Root remains required for direct fan, pump, LED, trigger-node, and other privileged hardware controls.

## Installation

Download and install the signed release APK attached to this release. Existing users can install it directly over the previous signed version. Review the README before granting permanent superuser, accessibility, usage-access, phone-state, notification, or overlay permissions.
