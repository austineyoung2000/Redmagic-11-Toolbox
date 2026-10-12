# Per-app Game Mode profiles

Lighting → Game Mode → Select apps chooses which foreground apps can use Game Mode.
The searchable Material picker shows the app icon, name, package and selection state.
Search does not clear selections; Cancel leaves the saved selection unchanged.

App profiles lists configured apps with an Edit action. Each editor is titled with
the app name and package. Save stores fan, pump and all lighting settings for that
package only, including encoded brightness and split-zone settings. Add/remove
apps changes eligibility without deleting saved profiles.

Shared defaults remain accessible from the profile list. Apps with no saved
profile inherit those defaults. Older partial per-app records inherit missing
fields; corrupt records fall back to defaults. Existing per-app JSON keys remain
compatible with master profiles and backup. Saving preserves unrelated metadata.

Saving a profile from Toolbox does not apply its hardware settings outside a
selected foreground app. Game-to-game transitions resolve the destination app's
full profile through the existing ownership and screen policies. Incoming calls,
charging, timed notifications, screen-off shutdown and cooling safety retain
their existing priority rules and hardware commands.

The same searchable app-list presentation is used for notification profiles,
native trigger mappings, refresh-rate, performance, touch tuning, Magic Key and
slider app assignments. Single-selection and confirmed-selection behavior remain
feature-specific; Game Mode retains a staged multi-selection Save action.
