# LED Zones brightness test branch

Branch: `test/led-brightness`. Based on `sixteen` at `f1e08dd`. Build label: `2.5.4-brightness-test`, same application ID and signing key. This branch must remain separate until the owner tests the signed APK and approves merging.

## Scope and controls

Only normal Lighting → LED Zones gets brightness controls in this first experiment. Fan, Logo, and Trigger LEDs each have a 32–255 integer slider; 255 is full output and old preferences default to 255. The trigger control scales both selected trigger colors together. Preset-only colors and split top/bottom selection remain available. The enable switch controls Off. 32 is the lowest level physically tested, not a hardware minimum.

Slider movement stages the value. Hardware previews happen when dragging ends, respecting the existing real-time preview setting. Save persists the complete selection; Cancel, Back, or outside dismissal restores the original enabled state, effect, color/palette, split colors, and brightness. Changing to an unsupported dimming effect resets brightness to 255 and disables the slider with an explanation.

Other profile editors and RGB Studio do not gain sliders in this branch. Existing normal-state storage, Master Profile snapshots/backups, and mode-exit restoration preserve the encoded normal effect. No separate daemon is introduced.

## Mechanism and compatibility

The driver brightness callback was inspected in the supplied `zte_led.ko`: it queues a task-control routine and branches on zero versus nonzero rather than using the supplied magnitude as a dimming factor. It is therefore not used as a slider. `imax` and hardware reset controls remain untouched.

Brightness scales only mapped RGB payload bytes using `(component * level + 127) / 255`. All timing, routing, and task commands remain unchanged. A temporary copy is checked against an exact SHA-256 allowlist before LED controller writes; vendor files are never modified. Unsupported programs reject reduced brightness. Supported programs are replayed even at 255 so full-brightness restoration does not rely on reselecting an already-active numeric effect.

The normal effect field carries `dim:<32..255>:<existing effect>`, including nested split selections such as `dim:128:split:breathe:FF0000:0000FF`. Original colors are retained, so repeated brightness changes never compound scaling. Steady fan palettes scale every inspected color block.

Supported mappings in this test build: trigger Steady/Breathe/Flashing/Rapid (existing validated split templates), logo Steady/Breathe, fan single-color Steady, and all eight steady fan palettes. Trigger Flashing/Rapid dimming and steady palettes other than 102 are structurally validated but still require physical dimming confirmation. Fan breathing/flashing/rapid and logo flashing/rapid dimming remain disabled pending inspection. At 255 their existing stock effects remain usable.

Hardware writes are serialized. On an application failure, the controller attempts to replay that zone’s last successful complete command in the current app process, bypassing duplicate suppression. Recovery is best effort and cannot guarantee restoration after an I2C failure or process termination. Updates across zones remain sequential; no exact simultaneous-start guarantee is made.

## Physical evidence supplied by the owner

- Steady red at 255, 128, 32: visible dimming on triggers, logo, and fan.
- Steady yellow at 255, 128, 32: visible dimming on fan, and logo/triggers together.
- Dim yellow Breathe on logo/triggers: both worked and appeared synchronized; exact phase timing was not measured.
- Fan palette 102 (green/cyan/blue/purple), Steady full → low → full: owner confirmed working.
- Palette 101 Breathe: owner could not judge dimming reliably; inconclusive.
- Early standalone scripts restored the test palette or only its selector, not the original complete profile. Reapplying the original app selection restored normal output. This is why app Cancel/restore replays the full desired program.

The hex fixtures reproduce the supplied dumps and match their recorded hashes. Unit tests execute generated scripts against simulated sysfs files, assert only RGB bytes change, exercise every inspected steady palette, and restore a different original palette at its saved brightness. These do not emulate physical hardware.

## Owner APK acceptance test

1. Check each zone at 255, 128, 32, and an intermediate value such as 96. Preset hues should remain stable.
2. Check separate trigger colors; brightness must preserve both selected presets.
3. Preview a different color, palette, effect, and brightness, then Cancel/Back/outside-dismiss. The original full profile must return.
4. Save, close/reopen the app, disable/re-enable, and return from a lighting mode. The saved normal brightness must return.
5. Check 255 restores full output, especially fan palette 102 after dimming another palette.
6. Unsupported dimming effects should show full brightness and an unavailable slider; existing effects should still work.
7. Check that the slider is present only in LED Zones and no other profile editor.

Do not merge based solely on CI success. Merge only after the owner confirms the APK behavior.
