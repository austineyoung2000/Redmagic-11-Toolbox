# Redmagic 11 Toolbox 2.5.5

Version 2.5.5 brings adjustable LED brightness to the lighting controls and profiles throughout the app, building on the independent split-trigger colors introduced in 2.5.4.

## Adjustable brightness throughout lighting

- Set brightness anywhere from **32 to 255**. 255 is full intensity; 32 is the lowest tested slider level. Off remains controlled by the enable switch.
- LED Zones, Game Mode, Charging Mode, incoming calls, and connected calls retain brightness when changing preset colors or effects.
- Fan supports Steady, Breathe, Flashing, Blink, and Rapid for single colors and all eight multicolor palettes. Blink retains the circular chase observed in device testing.
- Logo and triggers support Steady, Breathe, Flashing, and Rapid. Independent trigger colors share one zone brightness.
- RGB Studio stores separate fan, logo, and trigger intensity even when cycle timing is synchronized. Apply to All transfers those intensities to normal lighting.
- Master Profile capture/apply and JSON export/import preserve brightness. Older RGB Studio backups default to 255; imported RGB Studio intensity is bounded to the supported slider range.

## Save, Cancel, and restoration

Normal-zone Cancel restores the original color/palette, effect, enabled state, and brightness. Mode editors stage edits until Save. Charging fan previews no longer persist preferences before Save, and preview restoration respects charging ownership. Full-brightness restoration replays complete validated programs rather than relying on a duplicate stock selector reload.

## How brightness was discovered

The sysfs brightness readout of 128 prompted inspection of the supplied `zte_led` driver. Its brightness worker treats zero/nonzero as task control rather than scaling by the value. Comparing vendor effect programs instead exposed repeated RGB payloads. Scaling only those bytes made 32 visibly dimmer, first on triggers and then logo/fan. Follow-up tests established animated fan palettes, remaining logo effects, and the circular fan Blink animation. The implementation validates exact template hashes and keeps routing/timing/current-limit controls unchanged.

See [LED brightness reverse engineering](docs/LED_BRIGHTNESS_REVERSE_ENGINEERING.md) and [owner test history](docs/LED_BRIGHTNESS_TEST.md). Automated tests exercise RGB-only changes across inspected programs, independent RGB cycle intensity, full-output replay, malformed/tampered inputs, and profile selection persistence. Owner reports confirmed the LED Zones and expanded profile APKs before merge; they are not an exhaustive certification of every color combination or ROM.

## Installation

- Version **2.5.5**, Android `versionCode` **11**.
- Supported device: rooted REDMAGIC 11 Pro / NX809J with matching vendor LED programs and driver interfaces.
- Application ID and signing configuration remain unchanged; install the signed APK over the current Toolbox installation to retain settings.
- LED brightness and split-trigger lighting use the LED controller directly and do not require Trigger Bridge.

---

# Redmagic 11 Toolbox 2.5.4

Version 2.5.4 adds independent top and bottom shoulder-trigger LED colors across the app's lighting modes. Choose any combination from Red, Orange, Yellow, Green, Cyan, Blue, Purple, and Pink, with one shared lighting effect.

## Split trigger colors in every lighting profile

- Open **Trigger LEDs** inside **LED Zones** and enable **Separate top and bottom colors**.
- Save a different combination for normal lighting, Game Mode, Charging Mode, and each incoming-call/connected-call profile.
- Select Steady, Breathe, Flashing, or Rapid. Changing the effect preserves both selected colors.
- Keep the existing matching-color option. Controls use preset colors only, without custom hex values or RGB color sliders.
- Profile editors stage changes until Save; Cancel leaves saved mode settings unchanged.

## RGB Studio split sequences

- Select separate preset sequences for the top and bottom triggers.
- Select one color per trigger for a fixed combination, or multiple colors to cycle.
- Each sequence wraps independently, even when the two lists have different lengths.
- Both triggers share the selected effect and trigger cycle speed. Synchronized mode shares timing with fan/logo cycles while retaining the chosen trigger pair.
- Apply the split pair after other zone writes so those updates do not replace it. The matching-color Apply to All action is hidden while split triggers are selected.

## Saved profiles and lighting ownership

- Master Profiles and portable JSON backups retain each mode's split selection and RGB Studio sequences.
- Older settings retain matching-trigger behavior.
- Charging, calls, games, RGB Studio, and normal lighting keep their existing priority and restoration rules.

## How the feature was achieved

Comparing stock AW22xxx effect programs identified the RGB value-byte locations for each trigger. The split path copies an exact supported vendor template to temporary storage, validates its checksum and byte layout, replaces only the mapped RGB values, and replays the program through the serialized root broker. Vendor firmware files, stock timing/routing bytes, current limits, and hardware reset controls are unchanged. Unrecognized program versions are rejected before split-program writes.

Device testing confirmed split Steady, Breathe, and Flashing output. A community test confirmed blue top/purple bottom breathing without requiring the app. Rapid is implemented and covered by byte-preservation tests, but its physical split output still needs confirmation.

The README and [reverse-engineering/test report](docs/TRIGGER_LED_REVERSE_ENGINEERING.md) document the mappings, firmware checksums, reported physical tests, implementation, and remaining validation. Regression tests cover program byte preservation, rejected firmware, failure cleanup, sequence wrapping, and saved-profile editing behavior.

## Installation

- Target device: REDMAGIC 11 Pro / NX809J, with root and the supported vendor LED interfaces/programs.
- App version: **2.5.4**, Android `versionCode` **10**.
- Install the signed APK over your existing Toolbox installation. The application ID and signing configuration are unchanged, preserving saved profiles.
- This lighting feature uses the phone's LED controller and does not require Trigger Bridge. Existing gameplay and Trigger Bridge support from 2.5.3 remains available.
