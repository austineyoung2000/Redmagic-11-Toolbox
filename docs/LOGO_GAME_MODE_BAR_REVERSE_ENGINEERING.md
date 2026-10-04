# Independent logo and GAME MODE bar lighting on NX809J

## Status and scope

Included in Redmagic 11 Toolbox 2.6.0, developed on `test/logo-game-mode-bar` and merged into `sixteen`. The root-shell tests below passed on the owner's phone. On October 4, 2026, after client APK testing and palette UI corrections, the owner authorized merge and release. This authorization is not a claim that every checklist item below was separately reported. Austin supplied the physical observations and ran the experiments; Codex prepared the scripts, interpreted the vendor data, and implemented the feature.

The rear logo and illuminated side GAME MODE bar follow one stock logo setting but contain separately addressable RGB payload groups. Independent colors and brightness were physically confirmed with Steady, Breathe, Flashing, and Rapid. This does not establish that they support different effects simultaneously. The app therefore presents a shared effect and independent physical-area controls.

## Discovery and reported tests — October 4, 2026

1. Selecting logo red also made the side GAME MODE bar red. This established that both followed the stock logo profile, not that their physical channels were inseparable.
2. The existing steady logo template contained two mapped RGB groups. A checksum-gated temporary replay assigned red to the first and green to the second, then reversed them. The owner reported split output.
3. The next test assigned RGB intensity 32 to red in the first group and intensity 255 to green in the second, then reversed the intensities. The owner specifically reported **dim red logo / bright green bar**, followed by **bright red logo / dim green bar**. This identifies the group-to-area mapping and confirms independent brightness.
4. Breathing, Flashing, and Rapid templates were tested with dim red logo / bright green bar, then bright red logo / dim green bar. The owner reported that all effects, brightness levels, and colors worked.
5. Each script ended by replaying both steady groups as full red. This was a known test baseline, not automatic restoration of an arbitrary previously selected app profile.

These are owner reports from a physical NX809J, not a claim of community validation, oscilloscope verification, exact phase synchronization, or exhaustive ROM/color coverage. The earlier community blue/purple test concerned shoulder triggers, not this logo/bar discovery.

## Program format and physical mapping

Vendor `.bin` files are alternating register-address/value bytes. The repeated `04 07 06 value` sequence selects the data path and writes its next payload byte. Existing brightness work identified the RGB payload offsets; this experiment establishes which half belongs to each physical area. Offsets below are **zero-based byte offsets in the original file**, not hardware register addresses.

For each RGB start `o`, red is at `o`, green at `o + 4`, and blue at `o + 8`. The first half of each list belongs to the logo, the second half to the bar.

| Shared effect | Vendor template | Bytes | Logo RGB starts | GAME MODE bar RGB starts |
|---|---|---:|---|---|
| Steady | `aw_cfg2_7.bin` | 228 | 183 | 215 |
| Breathe | `aw_cfg3_7.bin` | 396 | 183, 211, 239, 267 | 299, 327, 355, 383 |
| Flashing | `aw_cfg4_7.bin` | 284 | 183, 211 | 243, 271 |
| Rapid | `aw_cfga_7.bin` | 564 | 183, 211, 239, 267, 295, 323, 351 | 383, 411, 439, 467, 495, 523, 551 |

| Template | SHA-256 |
|---|---|
| Steady | `989f6b94603c742c0868d90135e3bbd486ee4ecaa85bcb77b7aae31575665090` |
| Breathe | `f5efcf845344d8ff624e6b4977bd3581b15d822bc744754bb4449f295af91303` |
| Flashing | `9f5864d0c6cba08da6f2eb08ad44590049149fab78b0ee44d625b08eb46e680c` |
| Rapid | `6202f4725e681f68da451a4cba4de7d6ba4d7f06f3925ef3b09aa8db91ce3890` |

The tested shell colors were pure red/green. The app retains the existing eight preset colors and their observed vendor payloads. Brightness scales each area's preset channels independently:

`scaled = (component * brightness + 127) / 255`

The user-facing brightness range remains 32–255. An independently disabled area gets zero RGB in its groups while preserving the companion area. If both are disabled, existing logo-program disable handling applies. Independent off behavior is an APK test item; the physical discovery tests established colors and brightness, not separate disable switches.

## Implementation and persistence

- LED Zones has separate **LOGO LED** and **GAME MODE BAR** entries. Each opens that area's enable, preset color, and brightness controls; the shared effect is explicitly labeled.
- Game Mode and incoming/connected-call profile editors contain separate logo and bar sections with one shared effect row.
- Charging has separate logo and GAME MODE bar entries, with staged edits saved only on Save.
- `LogoBarSelection` stores both enables, both intensities, the bar preset, and the shared effect in the existing logo effect field. The existing logo color field continues to carry the logo preset. The outer LED enabled flag represents whether either area is enabled.
- The encoded format is `areas:<effect>:<logoEnabled>:<logoBrightness>:<barEnabled>:<barColor>:<barBrightness>`. Flags are exactly 0/1; effects and presets are allowlisted; brightness is bounded. Malformed requests fail before controller writes.
- Existing normal, gaming, charging, incoming/connected-call, and Master Profile capture/apply/JSON paths already preserve effect strings and thus preserve the composite selection. Normal edits and Cancel restore the complete pair. Editing only the bar never replaces the logo selection, and vice versa.
- Older profiles without the composite selection initialize the bar to the logo's saved color, intensity, enabled state, and effect. They retain their existing appearance until edited.
- RGB Studio saves bar enable, colors, intensity, and cycle speed separately, alongside a logo enable switch. Older RGB backups mirror the logo defaults. Master Profile JSON includes these RGB fields; Apply to All carries the independent bar selection into normal lighting.

## Controller writes and animation timing

Both areas share the logo vendor program and sysfs controller. A change builds one program containing the current settings for both areas, rather than sending two competing stock logo selections. The controller copies an allowlisted template into temporary storage, verifies its exact SHA-256 and size, checks the expected data-write markers and source blue RGB bytes, replaces only mapped RGB values, then replays register pairs through the existing serialized root broker.

Firmware files, routing, timing, current limits, reset controls, and driver brightness nodes are unchanged. Programs always regenerate from an original template, so repeated brightness changes do not compound rounding. Unknown templates are rejected before effect/register writes. Existing owner arbitration and best-effort previous-command recovery remain in place.

RGB Studio retains its 500 ms minimum frame interval and existing owner/screen policies. In per-zone mode, a logo or bar deadline replays their combined program with the companion's cached color, without advancing the companion sequence. Synchronized mode advances both sequences on the shared deadline, each wrapping its own list. Changing either area reloads the shared program; breathing/flash phase can restart on both. Independent sequence cadence does not imply independent animation engines or phase continuity.

No new daemon, continuous register polling, Trigger Bridge dependency, or extra root broker is introduced.

## Automated checks and APK acceptance

Tests replay all four templates with unequal intensities and all combinations of area enables; they assert every byte outside mapped RGB positions remains identical. They also cover malformed selections, tampered-template rejection before writes, legacy matching migration, pair persistence, RGB default migration, and combined RGB frame generation. These tests simulate sysfs and do not substitute for device verification.

Regression checklist for the released APK:

- Separate LED Zones entries edit only their selected area; effect changes affect both as labeled.
- Each area keeps its saved color/brightness after reopening, changing the companion, and changing effects.
- Each enable switch turns off only its area; both off and subsequent re-enable behave correctly.
- Normal preview Cancel/back/outside dismissal restores both original areas, including brightness and enables; preview-off staging does not write hardware.
- Game Mode, charging, incoming calls, and connected calls apply their own pair and restore the previous lighting owner on exit.
- RGB Studio preserves different logo/bar sequences, brightness, enables, and per-zone speeds, including one-element sequences and unequal list lengths. Synchronized mode shares timing without collapsing colors.
- RGB Apply to All, Master Profile capture/apply, and JSON export/import preserve both areas. Legacy profiles retain matching output.
- Unsupported-template/root failures are visible and do not silently substitute a matching stock program.

Related reports: [brightness discovery](LED_BRIGHTNESS_REVERSE_ENGINEERING.md), [brightness test history](LED_BRIGHTNESS_TEST.md), and [split-trigger reverse engineering](TRIGGER_LED_REVERSE_ENGINEERING.md).

## Palette UI follow-up

Owner screenshots revealed stretched logo/bar color buttons and missing-looking selection highlights. The shared editor now uses the existing fixed 42dp circular swatches. Charging/calling fan preset selections were captured at dialog creation; live callbacks and redraws now follow changes. Gaming redraws its palettes when switching back to solid colors. White rings blended into white dialogs, so a thin dark contrast outline was added to shared solid and multicolor swatches. RGB Studio uses the same rings for multiple selections while preserving sequence order and its one-color minimum. These changes affect UI selection indicators, not controller payloads.
