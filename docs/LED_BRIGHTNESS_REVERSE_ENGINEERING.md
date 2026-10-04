# LED brightness reverse engineering on NX809J

This report explains the investigation and implementation behind Redmagic 11 Toolbox 2.5.5. Austin supplied driver/firmware captures, ran root-shell experiments on his NX809J, and tested the resulting APKs; Codex assisted with driver analysis, byte comparisons, test scripts, implementation, and automated verification. Physical observations below are owner reports. Automated tests replay commands against simulated sysfs files, not real hardware.

## Discovery: why the readout of 128 was interesting

The LED sysfs directory `/sys/class/leds/aw22xxx_led` exposed `brightness`, whose captured value was 128. That suggested a possible dimming control, but a readable value alone did not establish its hardware meaning.

Inspection of the supplied `zte_led.ko` showed `aw22xxx_set_brightness` saving the requested value and queuing `aw22xxx_brightness_work`. In the inspected worker, zero versus nonzero controls task startup/shutdown; the numeric magnitude is not used to scale RGB intensity. The app therefore does not use that node as its brightness slider. This finding applies to the inspected driver, not every AW22xxx driver revision.

The investigation moved to vendor effect programs already studied for split-trigger colors. Their repeated RGB values offered a different, testable dimming path.

## Observed interface and program structure

| Item | Captured value or role |
| --- | --- |
| Phone | REDMAGIC 11 Pro / NX809J |
| Driver / module | `aw22xxx_led` / `zte_led` |
| I2C device | `5-006a` |
| Main firmware | `/vendor/firmware/aw22xxx_fw.bin` |
| Effect programs | `aw_touch*` triggers, `aw_cfg*` logo, `aw_fan*` fan |
| Effect selector | Lamp field at bit 24, effect type at bit 12, low color/palette field |
| Program replay | Register/value pairs written through the driver's `reg` sysfs control |
| Brightness mechanism | Scale mapped RGB value bytes in a temporary program copy |

Logs initially included direct firmware lookup error `-2`, followed by sysfs fallback, a loaded-program size, and successful configuration completion. Those captures show successful fallback loading, not a missing program overall.

The effect files are ordered register/value streams. Many payload entries have the pattern `04 07 06 VV`; RGB components occupy three value positions separated by four file bytes. These are **file byte offsets**, not direct RGB output register addresses. Task/routing headers differ between zones, so copying a trigger program indiscriminately to the fan or logo would be incorrect.

## Method: scale RGB, preserve the program

Each component is scaled from its original value:

```text
scaled = (original_component * brightness + 127) / 255
```

Integer division rounds to the nearest supported byte value. At 255 the payload is unchanged; at 128 it is approximately half; at 32 it is approximately one eighth. These ratios describe encoded RGB values, not a calibrated measurement of optical brightness or perceived brightness. The original palette/preset remains stored, so repeated adjustments do not compound scaling.

Only identified RGB value bytes change. Timing, channel selection, task commands, and other payload fields remain intact. Each animation frame/color block must be mapped separately; offsets are not safely derived from one fixed stride across palette boundaries.

## Mappings established from supplied dumps

Effect types are hexadecimal: 2 Steady, 3 Breathe, 4 Flashing, 6 fan Blink, and `a` Rapid.

| Program family | Bytes | RGB-start offsets in the file |
| --- | ---: | --- |
| Fan single Steady | 172 | 159 |
| Fan single Breathe | 256 | 159, 187, 215, 243 |
| Fan single Flashing | 200 | 159, 187 |
| Fan single Blink | 812 | 255, 283, 311, 339, 367, 399, 427, 455, 483, 511, 543, 571, 599, 627, 655, 687, 715, 743, 771, 799 |
| Fan single Rapid | 280 | 195, 223; the zero-color pause block stays unchanged |
| Fan palettes Steady | 364 | 255, 287, 319, 351 |
| Fan palettes Breathe | 700 | 255, 283, 311, 339, 371, 399, 427, 455, 487, 515, 543, 571, 603, 631, 659, 687 |
| Fan palettes Flashing | 476 | 255, 283, 315, 343, 375, 403, 435, 463 |
| Fan palettes Blink | 812 | Same RGB-start offsets as single Blink; original palette routing remains intact |
| Fan palettes Rapid | 556 | 291, 319, 351, 379, 411, 439, 471, 499; zero-color pause stays unchanged |
| Logo Steady | 228 | 183, 215 |
| Logo Breathe | 396 | 183, 211, 239, 267, 299, 327, 355, 383 |
| Logo Flashing | 284 | 183, 211, 243, 271 |
| Logo Rapid | 564 | 183, 211, 239, 267, 295, 323, 351, 383, 411, 439, 467, 495, 523, 551 |

For each start offset, green is at start + 4 and blue at start + 8. All eight fan palettes (hex IDs 101–108) have their own exact checksum. The application allowlist is in [LedBrightness.kt](../app/src/main/java/com/elitedarkkaiser/redmagic/LedBrightness.kt); hash-matching captured bytes are stored in [test fixtures](../app/src/test/resources/led-brightness). Trigger mappings/checksums are documented separately in [split-trigger reverse engineering](TRIGGER_LED_REVERSE_ENGINEERING.md).

The supplied logo type-6 file was absent. The app exposes only the four verified logo effects. Fan Blink showed a circular chase during owner testing: individual LEDs light in order around the fan. Dimming preserves that animation rather than replacing it with a software blink loop.

## Device experiments and findings

1. Trigger RGB scaling at 255, 128, and 32 visibly dimmed the lights. The owner specifically observed 32 being much dimmer.
2. Logo and fan tests established the same mechanism for their own program headers and color blocks. A second fan color also worked.
3. Logo and triggers could be updated together. Breathing appeared synchronized, but writes were sequential and exact simultaneous start or phase alignment was not measured.
4. An early restoration attempt reselected a stock effect but left reduced output. Reapplying the app selection restored full output. This established that restoration must replay a complete desired program, not depend on the driver reloading an identical selector.
5. An early palette-101 breathing observation was inconclusive. Subsequent palette-102 tests (green/cyan/blue/purple) confirmed dimming for animated effects.
6. A combined script exercised fan palette Breathe; solid blue and palette Flashing/Blink/Rapid; and logo blue Flashing/Rapid at 255 → 128 → 32 → 255. The owner reported all working. Incorrect palette offsets in an earlier script were rejected during preparation before LED writes; corrected mappings were checked against the captures.
7. The LED Zones APK was reported working across its effects. Brightness survived color changes and app reopening; Cancel restored the previous complete selection after edits.
8. The subsequent gaming/call/charging/RGB Studio/Master Profile expansion APK was reported working. The owner authorized merging and version 2.5.5 on 2026-10-04.

These reports establish working examples and owner acceptance. They do not measure output current, certify every palette/color combination, or guarantee compatibility with every custom ROM or future firmware revision. The full checklist and chronology remain in [test history](LED_BRIGHTNESS_TEST.md).

## Implementation and restoration

Normal and mode profiles carry brightness in their existing effect field as `dim:<32..255>:<effect>`, including split-trigger selections. Colors remain preset-only. RGB Studio stores independent fan/logo/trigger values in preferences and Master Profile JSON, with absent legacy fields defaulting to 255. Timing synchronization does not erase per-zone brightness.

The rendering path copies the selected vendor template to temporary storage, verifies its exact SHA-256 and mapped byte layout, prepares the scaled register/value stream, then initializes/replays through the serialized root broker. Unknown or malformed programs reject brightness replay. Vendor files, current limits (`imax`), hardware reset controls, and the sysfs brightness node are not modified by the slider.

Full intensity also replays the validated program. Save and mode restoration keep the complete desired selection; normal-zone Cancel restores the original color/palette, effect, enabled state, and intensity. Mode editors stage changes until Save. Charging fan previews do not persist preferences early and restore only when charging owns the LEDs.

RGB cycle updates replay only due zones using the existing scheduling. There is no new daemon or polling loop. Program replay adds work per cycle frame; the implementation serializes it and retains the existing scheduling limits. Exact cross-zone start synchronization is not claimed.

On a zone write failure, the controller attempts the last successful complete zone command held by the current app process. This recovery is best effort, not guaranteed after an I2C error or process termination.

## Automated verification

The test suite checks RGB-only scaling at 32, 128, and 255 across 49 inspected non-trigger programs, plus the existing split-trigger template tests. It checks that routing/timing bytes remain unchanged, rejects tampered templates before effect/register writes, and verifies full-program restoration of a different original palette. Added integration tests replay a multi-zone RGB frame with different intensities, restore both to 255, and ensure only due zones are included. Profile tests retain brightness through split/matching toggles, color/effect edits, and reopening the staged selection.

Both signed test builds passed GitHub Actions and were owner-tested before merge. The version-only/documentation merge receives its own production-branch build. Automated replay is simulated; physical behavior and restoration remain device-test responsibilities when porting or changing templates.
