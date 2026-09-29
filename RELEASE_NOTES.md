# Redmagic 11 Toolbox 2.5.2

Version 2.5.2 adds the production Trigger Bridge fallback and completes the
gameplay-runtime lifecycle fixes introduced after 2.5.1. It preserves the
existing application ID and signing identity, so it installs over earlier
versions without clearing saved profiles.

## Automatic trigger backend selection

- Continues to prefer REDMAGIC Native TGK on compatible stock firmware.
- Falls back to the optional root companion module when Native TGK is missing
  or rejects the active mapping.
- Requires Redmagic Trigger Bridge v0.3.0 (`versionCode 10`) or newer; older
  separate-touchscreen builds are rejected.
- Writes normalized portrait and landscape targets to the module and activates
  it only while a configured game owns the foreground.
- Disables partial native state before module activation and disables both
  paths during game exit, screen-off, editing, and runtime teardown.
- Keeps a root-only exact validation marker for maintainers without changing
  normal native-first production selection.

## Verified merged-touch gameplay

Trigger Bridge v0.3.0 combines the physical Synaptics touchscreen and two
reserved shoulder-trigger slots into one protocol-B input device. On-device COD
Mobile validation covered:

- Continuous thumbstick movement while tapping and holding L/R
- Both shoulder triggers with one and two physical screen contacts
- Repeated lifting and replacement of physical fingers during trigger input
- Accurate saved targets in the measured landscape orientation
- Stable slot ownership without stuck contacts, dropped touch, or aim snapping
- A final two-minute combined-input stress test

Native TGK remains the preferred full-fidelity backend for stock haptics,
rapid-fire modes, and vendor visual effects. The module backend is intended for
compatible NX809J ROMs retaining the required SAR, touchscreen, arming-node,
uinput, root, and SELinux interfaces.

## Gameplay runtime and overlay recovery

- Restart a missing gameplay runtime when a valid foreground hint arrives.
- Retire stale foreground-monitor authority so overlays do not remain visible
  outside the configured application.
- Preserve the movable GS button, movable drawer, saved per-app positions, and
  Android Back-gesture access from 2.5.1.
- Show a one-time reboot notice after an APK replacement changes the gameplay,
  overlay, accessibility, watchdog, or trigger stack. Android may otherwise
  retain stale service or input state until the next boot.

## Diagnostics and documentation

- Device scanning reports Native TGK and compatible Trigger Bridge availability
  independently.
- Trigger diagnostics identify the selected backend and its live state.
- README and native TGK reverse-engineering documentation now cover the merged
  input architecture, minimum module version, validation evidence, and custom
  ROM boundary.

## Compatibility boundary

- Primary target: REDMAGIC 11 Pro / NX809J.
- The module fallback was forced and verified end to end on stock Android 16.
- Automatic selection on an actual custom ROM without Native TGK remains
  unverified because the test device remains on stock firmware.
- Stock-only performance, display, touch-tuning, charge-separation, haptic,
  rapid-fire, and visual-effect features still require their corresponding
  REDMAGIC framework implementations.

## Installation

Install the signed APK over the existing Toolbox installation, then reboot the
phone once before testing gameplay overlays or trigger mapping. Keep Trigger
Bridge v0.3.0 enabled in KernelSU, Magisk, or APatch; Toolbox leaves it inactive
unless the module backend is actually selected.
