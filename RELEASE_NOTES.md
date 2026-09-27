# Redmagic 11 Toolbox 2.5.0

Version 2.5.0 rebrands RedMagic Control Center as **Redmagic 11 Toolbox** and introduces a standalone native gaming stack for the RedMagic 11 Pro / NX809J.

The application ID and signing identity are unchanged, so this release installs as an update over existing versions and keeps saved app data.

## Native TGK shoulder-trigger mapping

- Added independent per-app L and R screen targets through REDMAGIC's native TGK engine.
- Added separate portrait and landscape mappings, named layouts, and in-game editing.
- Added persistent touch-through saved-target markers with deliberate re-edit mode.
- Added independent single-touch, hold, and rapid-fire behavior for each trigger.
- Added supported rapid-fire counts of 2, 5, and 10.
- Added stock top-edge trigger highlights and native pressed-target visual feedback.
- Added stock TGK haptics, driver activation, version negotiation, state verification, and runtime diagnostics.
- Added native TGK profile import/export and complete Master Profile backup coverage.
- Unified TGK and overlay ownership with the selected application's foreground lifecycle.

Native TGK does not require the Game Space application or root on the tested stock firmware. Touch contacts are generated inside the firmware input pipeline, preserving simultaneous touchscreen movement and multitouch.

## Per-app gaming controls

- Added compatibility-gated per-app refresh-rate profiles through the stock REDMAGIC display service.
- Added per-app touch-sampling, sensitivity, touch-follow, and micro-sensitivity profiles.
- Added per-app Eco, Balance, and Rise performance-mode management.
- Added a foreground-only performance overlay showing refresh rate, touch sampling, performance mode, FPS, CPU temperature, and fan telemetry.
- Added stock charge-separation control with charger and battery safety checks.
- Added separate capability probes so missing vendor APIs remain unavailable without breaking general toolbox features.

## Existing toolbox

Version 2.5.0 retains fan and micropump control, automatic cooling curves, lighting and RGB Studio, Magic Key actions and shortcuts, dual-app slider schedules, Game Mode, Charging Mode, Call Lighting, Quick Settings tiles, the cooling widget, thermal history, automation rules, diagnostics, and portable Master Profiles.

## Reverse-engineering report

The repository now includes `docs/NATIVE_TGK_REVERSE_ENGINEERING.md`, documenting the firmware classes, Binder transactions, stock database observations, permission testing, kernel trace, runtime sequencing, visual effects, and Game Space-independent validation behind native TGK support.

## Compatibility

- Primary target: RedMagic 11 Pro / NX809J.
- Native TGK and the new vendor gaming controls require compatible REDMAGIC Android 16 framework services.
- General hardware features can work on NX809J custom ROMs that preserve the required vendor and kernel interfaces.
- Stock-only controls are hidden or rejected when their compatibility checks fail.
- Root remains required for direct fan, pump, LED, trigger-node, and other privileged hardware controls.

## Installation

Download and install the signed release APK attached to this release. Existing users can install it directly over the previous signed version. Review the README before granting permanent superuser, accessibility, usage-access, phone-state, notification, or overlay permissions.
