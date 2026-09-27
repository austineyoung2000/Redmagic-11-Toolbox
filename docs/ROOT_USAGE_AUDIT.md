# Root usage and write-frequency audit

This audit describes where Redmagic 11 Toolbox currently uses root, which
operations write to the device, and the safeguards that prevent unnecessary
work. It is intended to be updated whenever a new privileged feature is added.

## Design rule

Use an Android or vendor API first. Use root only when firmware permissions
block the ordinary application process or when a kernel/sysfs interface is the
only available control surface. Read-only probes and state-changing writes are
kept separate so a capability check can never mutate hardware state.

## Read-only root activity

| Area | Frequency | Root work | Safeguard |
|---|---:|---|---|
| Main dashboard telemetry | At most once per 30 seconds while the activity is foreground | One batched command reads fan and pump nodes | A 30-second cache serves repeated callers; temperature uses the non-root monitor |
| Performance overlay fan RPM | At most once per 30 seconds while the overlay runtime is active | One `cat` fallback only if direct file access fails | Direct app read is attempted first |
| Device capability scan | Once when the main UI launches | One batched existence probe for all paths still hidden from the app UID | Direct `File.exists`, `Build`, package-manager, Binder, and settings probes run first |
| Device identity cache | First uncached dashboard load | ROM/CPU/RAM reads | The completed result is persisted and reused |
| Explicit “Check Root” action | Only on user tap | `id -u` | Never polled continuously |

The shared root command channel is serialized and persistent. Normal reads do
not create a new `su` process for every command; a one-shot shell is only a
compatibility fallback when an interactive root shell is rejected.

## Root-backed writes

| Area | When a write happens | Current reduction strategy |
|---|---|---|
| Fan, pump, and LED hardware | User changes a control or an enabled automation selects a new state | Hardware writes are grouped by resource and identical commands are suppressed within the duplicate-write window; telemetry cache is invalidated only after relevant successful writes |
| Performance and touch profiles | Selected app enters/exits or the user applies a profile | Desired settings are compared with current values; Android settings APIs are attempted first; root writes only changed protected values |
| Charge separation | User or profile changes the desired state | Vendor/global setting API is attempted first; no write occurs when the value already matches; root is fallback only |
| Magic Key modes | User changes slider behavior | Writes are user-driven and grouped into one settings command where multiple keys must change together |
| Legacy quick actions | A configured physical key action fires | One command is issued for the actual action; no polling writes occur |
| Haptic fallback | A requested hardware feedback event cannot use the normal vibrator path | Root sysfs write is fallback-only |
| RGB Studio animation | Only while RGB Studio owns the LEDs | One grouped LED command per due frame; saved speeds are clamped to 500–6000 ms, ownership is rechecked, and identical commands are suppressed |
| First-install permission bootstrap | Once after the user presses the explicit setup button | One grouped command grants app-ops/runtime permissions and merges this app into the existing accessibility-service list; every result is read back before setup is accepted |

Application preferences, profile JSON, editor coordinates, and backup data are
normal app-private or user-selected document writes. They do not use root and
do not write vendor partitions.

## Optimizations completed

- Consolidated normal root commands behind one serialized persistent session.
- Batched fan/pump telemetry into one command and cached hardware values for 30
  seconds.
- Coalesced overlapping dashboard refresh requests onto one activity runtime.
- Removed the Home tab's second independent dashboard scan on startup.
- Batched capability path checks into one read-only root fallback instead of
  issuing roughly one root command per vendor path.
- Replaced root `pm path` package checks with Android `PackageManager` queries.
- Added desired-state comparisons before protected settings writes.
- Added duplicate hardware-write suppression and profile-transition signature
  suppression.
- Serialized profile and automation stores whose read/modify/write sequences can
  be reached by UI, service, document-import, and boot callbacks concurrently.
- Coalesced simultaneous capability scans so all callers share one result instead
  of opening competing root probes.
- Routed document transfer, installed-app discovery, and master-profile disk work
  through the activity-owned background queue so it is cancelled with the host.
- Kept the RGB cycle's intentionally repeating writes bounded to one grouped
  command per due frame, with a 500 ms minimum interval and LED-owner checks.

## Review checklist for future features

1. Can the state be read or changed through a public/vendor Binder or settings
   API from the ordinary app UID?
2. Can multiple read-only probes be performed in one command?
3. Is the current value checked before writing?
4. Is the write triggered by a state transition rather than a polling tick?
5. Can repeated identical writes be safely deduplicated?
6. Is the work stopped when its activity, overlay, or service is no longer
   active?
7. Does failure leave the prior hardware state intact and report the backend
   used?
