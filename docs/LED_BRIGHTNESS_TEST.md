# Lighting brightness test history

Branch: `test/led-brightness`. Based on `sixteen` at `f1e08dd`. Build label: `2.5.4-brightness-test`, same application ID and signing key. The owner confirmed both test APK stages and authorized merging on 2026-10-04. The tested feature is included in `sixteen` for version 2.5.5 (versionCode 11).

## Scope and controls

The initial LED Zones experiment passed owner APK testing. Brightness controls now also extend to gaming, charging, incoming/connected calls, and RGB Studio. Fan, Logo, and Trigger LEDs each have a 32–255 integer slider; 255 is full output and old preferences default to 255. The trigger control scales both selected trigger colors together. Preset-only colors and split top/bottom selection remain available. The enable switch controls Off. 32 is the lowest level physically tested, not a hardware minimum.

Slider movement stages the value. Hardware previews happen when dragging ends, respecting the existing real-time preview setting. Save persists the complete selection; Cancel, Back, or outside dismissal restores the original enabled state, effect, color/palette, split colors, and brightness. Changing to an unsupported dimming effect resets brightness to 255 and disables the slider with an explanation.

Gaming, charging, and call profiles stage brightness with their existing effect fields; changing effects preserves intensity and Cancel leaves staged preferences unsaved. Charging fan previews no longer persist preferences before Save and restore only while charging owns the LEDs. Master Profiles retain these encoded selections in existing snapshots/backups.

RGB Studio stores independent `logoBrightness`, `shoulderBrightness`, and `fanBrightness` values. Preferences and Master Profile JSON preserve them; missing fields in old backups default to 255 and imported values are clamped to 32–255. Timing synchronization does not overwrite per-zone intensities. Both split trigger colors use the trigger brightness. Save & Apply starts the cycle with these values; Apply to All transfers each intensity to its corresponding normal LED Zone. Cycle frames use validated full-program replay even at 255, and include brightness in their ownership signature. Only due zones are updated, using the existing cycle interval and serialized root write path. No extra polling or daemon is introduced.

## Mechanism and compatibility

The driver brightness callback was inspected in the supplied `zte_led.ko`: it queues a task-control routine and branches on zero versus nonzero rather than using the supplied magnitude as a dimming factor. It is therefore not used as a slider. `imax` and hardware reset controls remain untouched.

Brightness scales only mapped RGB payload bytes using `(component * level + 127) / 255`. All timing, routing, and task commands remain unchanged. A temporary copy is checked against an exact SHA-256 allowlist before LED controller writes; vendor files are never modified. Unsupported programs reject reduced brightness. Supported programs are replayed even at 255 so full-brightness restoration does not rely on reselecting an already-active numeric effect.

The normal effect field carries `dim:<32..255>:<existing effect>`, including nested split selections such as `dim:128:split:breathe:FF0000:0000FF`. Original colors are retained, so repeated brightness changes never compound scaling. Steady fan palettes scale every inspected color block.

Supported mappings: triggers Steady/Breathe/Flashing/Rapid; logo Steady/Breathe/Flashing/Rapid; fan Steady/Breathe/Flashing/Blink/Rapid, for single colors and all eight palettes. Logo Blink has no supplied vendor template and is not exposed. Fan Blink uses vendor type 6: the owner observed a circular chase, lighting LEDs independently in order. RGB scaling preserves this routing and timing.

All 32 animated fan palette templates were inspected and hash-validated. Each effect has its own mapping; offsets must not be inferred by adding a uniform stride across palette boundaries. Physical tests cover palette 102 across all effects; other animated palettes have structural verification and need APK testing.

Hardware writes are serialized. On an application failure, the controller attempts to replay that zone’s last successful complete command in the current app process, bypassing duplicate suppression. Recovery is best effort and cannot guarantee restoration after an I2C failure or process termination. Updates across zones remain sequential; no exact simultaneous-start guarantee is made.

## Physical evidence supplied by the owner

- Steady red at 255, 128, 32: visible dimming on triggers, logo, and fan.
- Steady yellow at 255, 128, 32: visible dimming on fan, and logo/triggers together.
- Dim yellow Breathe on logo/triggers: both worked and appeared synchronized; exact phase timing was not measured.
- Fan palette 102 (green/cyan/blue/purple), Steady full → low → full: owner confirmed working.
- Follow-up standalone test: fan solid blue Breathe, then palette 102 Breathe and solid blue/palette 102 Flashing/Blink/Rapid, plus logo blue Flashing/Rapid, at 255 → 128 → 32 → 255: owner confirmed all worked on 2026-10-04.
- First signed APK: owner confirmed trigger brightness on all effects/colors, logo Steady/Breathe on all colors, fan steady solid and one palette; saved brightness survived color changes and app reopening; Cancel restored the original selection after adjustments.
- Earlier palette 101 Breathe observation was inconclusive; the follow-up uses palette 102.
- Early standalone scripts restored the test palette or only its selector, not the original complete profile. Reapplying the original app selection restored normal output. This is why app Cancel/restore replays the full desired program.

The hex fixtures reproduce the supplied dumps and match their recorded hashes. Unit tests execute generated scripts against simulated sysfs files, assert only RGB bytes change, exercise every inspected steady and animated palette, and restore a different original palette at its saved brightness. These do not emulate physical hardware.

## Owner APK acceptance test

1. Check each zone at 255, 128, 32, and an intermediate value such as 96. Preset hues should remain stable.
2. Check separate trigger colors; brightness must preserve both selected presets.
3. Preview a different color, palette, effect, and brightness, then Cancel/Back/outside-dismiss. The original full profile must return.
4. Save, close/reopen the app, disable/re-enable, and return from a lighting mode. The saved normal brightness must return.
5. Check 255 restores full output, especially fan palette 102 after dimming another palette.
6. Check every fan effect with single colors and all eight palettes, including the Blink circular chase. Check logo Flashing/Rapid as well as Steady/Breathe.
7. Check gaming, charging, incoming calls, and connected calls: save different brightness values per zone, reopen the editors, activate the mode, then exit it. Normal saved brightness must return.
8. Check RGB Studio with different brightness per zone, both synchronized and per-zone speeds, and split triggers. Changing effect/colors must preserve each intensity. Check Apply to All transfers the correct zone values and stopping the cycle restores normal selections.
9. Capture a Master Profile, alter brightness in normal and mode profiles, then apply the snapshot. Export/import it and verify the same values return. Import an older backup and verify RGB Studio defaults to 255.
10. Confirm RGB cycling stays responsive with the validated replay path, especially at the fastest cycle speed.

The merge was authorized after owner device reports, not solely CI success. The checklist remains useful for regression testing.

On 2026-10-04 the owner confirmed the expanded LED Zones APK worked across its newly enabled effects. The owner subsequently reported the profile/RGB Studio expansion working and authorized the 2.5.5 merge.

The discovery and mechanism are documented in [LED brightness reverse engineering](LED_BRIGHTNESS_REVERSE_ENGINEERING.md).
