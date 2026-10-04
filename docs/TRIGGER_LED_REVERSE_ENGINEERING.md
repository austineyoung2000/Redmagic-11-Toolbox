# Trigger LED reverse engineering and split-color tests

This records the investigation behind the independent top/bottom trigger colors on the `sixteen` branch. Evidence comes from owner-supplied root-shell output, stock effect-file dumps, physical observations, and a community test reported by the owner. The commands were run on phones by participants; the implementation's automated tests use simulated sysfs files.

## Result and scope

The shoulder triggers can use independent RGB colors while sharing a lighting effect. The investigation confirmed split colors with Steady, Breathe, and Flashing effects. A community participant confirmed blue on the top trigger and purple on the bottom trigger with breathing. Rapid is implemented from the corresponding vendor program layout and tested for byte preservation, but its physical behavior still needs confirmation.

## Observed hardware and software

| Item | Observed value |
| --- | --- |
| Investigated phone | Redmagic 11 / NX809J |
| Android branch | Android 16 / `sixteen` |
| LED sysfs directory | `/sys/class/leds/aw22xxx_led` |
| I2C device | `5-006a` |
| Driver | `aw22xxx_led` |
| Loaded module | `zte_led` |
| Device-tree compatible | `awinic,aw22xxx_led` |
| Controller identification in firmware | `aw22127` |
| Reported kernel during one capture | `6.12.23-android16-OP-WILD` |
| Main firmware | `/vendor/firmware/aw22xxx_fw.bin`, 17,477 bytes |
| Main firmware SHA-256 | `9ef3b05115f42a9364c4f446e2bd81c739fe6309a42cfeb94f7687d9887fb31b` |

Reference files compared in the investigation had identical LED driver and effect-program bytes. A community participant was reported to use the same OS version.

The initial `rgb` read showed `rgb[0] = 0x000002` with the remaining entries zero and `effect = 0x2000000`. Later logs show `rgb[0]`, `[1]`, and `[2]` being set to the lamp, effect type, and color selector. These values therefore cannot simply be interpreted as measured red/green/blue intensities.

## Driver interface and stock loading path

Available sysfs nodes included `brightness`, `cfg`, `effect`, `fw`, `hwen`, `imax`, `para`, `pattern`, `reg`, `rgb`, `task0`, `task1`, and `task_irq`.

Driver inspection established hexadecimal parsing for `effect` and hexadecimal register/value pairs for `reg`. The stock log decoded:

| Effect value | Lamp | Type | Color | Requested file |
| --- | --- | --- | --- | --- |
| `0x1004007` | 1 | 4 | 7 | `aw_cfg4_7.bin` |
| `0x2004007` | 2 | 4 | 7 | `aw_touch4_7.bin` |
| `0x2000000` | 2 | 0 | 0 | `aw_touch0_0.bin` |

The effect selector uses a lamp field shifted by 24 bits, a type field shifted by 12 bits, and a color field. Lamp 2 selects the trigger programs in the observed driver. Types 2, 3, 4, and hexadecimal `a` correspond to Steady, Breathe, Flashing, and Rapid in the app mapping.

The kernel repeatedly logged direct firmware-load error `-2`, followed by sysfs fallback, successful `cfg_loaded`, and `cfg update complete`. This sequence is evidence of a successful fallback load; the first error alone is not evidence that the effect file was missing permanently.

`/system/etc/ueventd.rc` contained:

```text
firmware_directories /etc/firmware/ /odm/firmware/ /vendor/firmware/ /firmware/image/
```

## Vendor program format and color locations

The investigated `.bin` effect files replay as ordered register/value byte pairs. Repeated `06 <value>` writes carry program data. The files begin with pairs including `ff 00` and end with `05 80`. The implementation preserves the entire program except identified RGB value bytes.

Comparing color variants exposed recurring color payloads. For the blue template, each identified RGB triplet is `00 54 ff`. Custom colors replace that triplet with arbitrary `R G B` values; pure blue is `00 00 ff`, rather than the stock blue template's green component.

Offsets below are **zero-based file-byte offsets of the red value byte**, not register addresses. Green is at red offset +4 and blue at red offset +8. Each value must have a preceding register byte `06`.

| Effect | Template | Bytes | RGB triplet starts | Top/bottom grouping |
| --- | --- | --- | --- | --- |
| Steady | `aw_touch2_7.bin` | 228 | 183, 215 | First 1 top; last 1 bottom |
| Breathe | `aw_touch3_7.bin` | 396 | 183, 211, 239, 267, 299, 327, 355, 383 | First 4 top; last 4 bottom |
| Flashing | `aw_touch4_7.bin` | 284 | 183, 211, 243, 271 | First 2 top; last 2 bottom |
| Rapid | `aw_toucha_7.bin` | 332 | 151, 179, 207, 235, 263, 291 | First 3 top; last 3 bottom; physical mapping pending |

Top/bottom grouping for Steady, Breathe, and Flashing is supported by the physical split-color tests. Rapid's grouping is inferred from its layout and remains a device-test requirement.

Exact supported template SHA-256 values:

```text
aw_touch2_7.bin  01e64e48ee9b7012025868c885e0c12dbfa49d16877a668a258fd343d395cc93
aw_touch3_7.bin  3827fd2731060da9c801866676ad56d083641da40097c2e6942df2ea6a7223a3
aw_touch4_7.bin  d989f3d2443999e2b12a650309ca5483e308f793ee587004c2c9ba40149d8107
aw_toucha_7.bin  0052635ab29486f4f3ec5c698dc1e767c245d892433e21d548c6f28aac5cb711
```

These offsets and hashes apply to the captured programs. A different ROM's programs need inspection and validation before support can be added.

## Physical test record

| Test | Reported outcome | What it establishes |
| --- | --- | --- |
| Red top / green bottom, solid | Owner confirmed requested colors | Independent color selection works for these channels |
| Yellow top / blue bottom, solid | Owner confirmed requested colors | A second split combination works |
| Split colors with breathing | Owner confirmed it worked | Breathing can retain distinct trigger colors |
| Split colors with flashing | Owner confirmed it worked | Flashing can retain distinct trigger colors |
| Blue top / purple bottom, breathing | Owner reported successful community test | Both requested colors work together with breathing |
| Rapid | No explicit physical confirmation captured | Requires device confirmation |

The original intermediate shell commands were not all retained in this repository, so this document does not invent a verbatim transcript. The retained implementation and four binary fixtures provide a reproducible program transformation. The standalone community test did not require the app to be installed; it used root access and the phone's vendor files/interfaces.

Successful requested colors do not establish unrestricted hardware safety across all phones, ROMs, brightness levels, or durations. They establish the observed operation on the tested devices. The software avoids increasing current limits or changing stock timing instructions.

## App implementation and guardrails

`ShoulderLedSplit.kt` encodes a selection as:

```text
split:<steady|breathe|flashing|rapid>:<top RRGGBB>:<bottom RRGGBB>
```

For example, blue top / purple bottom breathing is `split:breathe:0000FF:FF00FF`. The existing effect preference carries this string through saved state and restoration. The hardware adapter forwards it without normalizing it to a stock effect.

For each application, the generated root-shell script:

1. Checks writable `reg` and `effect` interfaces and creates a temporary directory.
2. Copies the stock template into the temporary directory and checks its exact SHA-256 before any controller write.
3. Captures the current numeric effect selector.
4. Validates file size, RGB locations, preceding register bytes, original RGB values, and replacement count.
5. Initializes the matching trigger effect, then replays the register/value pairs with only RGB value bytes replaced.
6. Removes temporary files. After a failed write, it attempts to return to page 0 and reapply the prior numeric effect.

This rollback is an attempt, not a guarantee: communication failures can also prevent recovery. A prior custom split program is not reconstructed from the numeric sysfs selector alone.

The code does not modify vendor firmware files, flash partitions, change `imax`, toggle `hwen`, or reset the controller. Root access and supported vendor interfaces/programs are required. Unknown checksums fail before controller writes.

The Lighting tab gives Trigger LEDs a separate section from fan/logo LED Zones. Users select matching stock colors or separate top/bottom colors from the preset palette, with one shared effect. The UI has no hex input or RGB sliders. Cancel restores the original app selection. This is independent color control; it is not independent effect timing per trigger.

## Automated validation and remaining checks

`ShoulderLedSplitTest.kt` uses the four captured templates in `app/src/test/resources/trigger-led/` and simulated sysfs files. Four JVM tests passed:

- Custom RGB selections round-trip and malformed selections are rejected.
- All four generated programs preserve every byte outside the selected RGB locations.
- A modified template is rejected before controller writes.
- An injected register-write failure exercises numeric-effect rollback and temporary-file cleanup.

The new color dialog also passed a Kotlin 2.0.21 compile check against Android 35 and Material libraries. Local Gradle dependency downloads were blocked, but GitHub Actions subsequently passed the complete unit-test and signed release APK build after the test harness was adjusted to use Android-compatible file APIs. The successful run is [Android CI 1085](https://github.com/austineyoung2000/Redmagic-11-Toolbox/actions/runs/37169567638). These JVM checks do not emulate the LED controller or prove physical output.

Before release, verify the built app on hardware: both trigger orientations, preset color combinations, each effect, Save/Cancel, disable/re-enable, restart/restoration, and transitions to other lighting profiles. Rapid requires an explicit community/device result.
