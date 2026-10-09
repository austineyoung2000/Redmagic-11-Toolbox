# Runtime efficiency audit — step 4

Baseline: production `b824998d9a542403762667104f74ff7477a9fce7` after step 3.
This is a source audit and first test-branch patch. No device CPU, battery,
thermal or root-command measurements have been taken. Step 5 is not started.

## Changes in this patch

- GameplayRuntimeService previously queried recent UsageEvents every 750 ms
  when its root monitor was absent, including when no profile needed monitoring.
  It now skips that query if no native trigger/editor, refresh-rate or performance
  profile needs foreground monitoring. Accessibility configuration checks and
  recovery remain active. Root startup failure still permits the UsageEvents
  fallback for enabled profiles.
- The performance overlay previously rendered on the one-second ticker AND
  every accepted FPS callback, temperature sample, fan RPM sample and performance
  dispatch. Snapshot producers now update their caches; one visible-only ticker
  renders the latest values. Attachment still updates immediately. Subsequent
  readings/profile labels appear on the next tick, normally within one second
  plus main-thread scheduling delay. FPS callback collection remains live.
- Unchanged text and orientation no longer reassign TextView properties on every
  tick. Hide resets layout state, removes the ticker and stops telemetry/FPS.

## Source inventory and retained safeguards

| Area | Existing cadence/lifecycle | Audit decision |
| --- | --- | --- |
| Authoritative foreground root stream | 1-second active / 3-second idle; no stream without eligible profiles; stopped on screen-off | Retain detection cadence to protect trigger exit and profile restoration |
| Runtime controller | 750 ms, accessibility configuration checked every 5 seconds | Gate unused UsageEvents fallback; retain controller/recovery |
| Gameplay watchdog | 2-second binder/configuration checks | Retain cross-process recovery |
| Native TGK health | 10-second health-check limit | Retain verified trigger recovery |
| Game Mode | Foreground event polling while a selected game is active; paused on screen-off | No cadence change in this patch |
| Temperature | Shared non-root monitor: 3 seconds foreground, 5 seconds hot background, 15 seconds cool interactive background, 30 seconds cool screen-off | Retain thermal safety; worker stops after last subscriber closes |
| Auto fan / pump | Subscribe to shared temperature monitor; hardware writes follow changed level/profile | Retain hysteresis and cooling policy |
| Fan RPM overlay telemetry | 30 seconds, direct sysfs read then root fallback if necessary; only while overlay is attached | Retain sampling and lifecycle |
| Vendor FPS | Binder callbacks, service retry limited to 30 seconds during display reads | Retain collector; remove callback-driven rendering |
| Normal LEDs | Event-driven reapply, including delayed wake restoration | Retain ownership/transition caches and screen policy |
| RGB Studio | Frames require scheduled hardware writes while eligible; pauses for screen-off | Retain configured visual timing |
| Notification lighting | Bounded 3–30 second timer and partial wake lock; event-driven listener | Retain expiry, cooling and higher-priority owners |

HardwareController deduplicates recent writes and ModeTransitionCoordinator
skips unchanged complete profiles. Do not add permanent LED write caching:
external vendor changes, fan-power side effects and explicit restoration need
reapplication. Notification process-death cleanup still relies on listener
reconnection; the wake lock does not provide an independent hardware failsafe.

## Validation and remaining work

Run Android CI unit tests plus signed release assembly on the test branch.
Phone validation remains required:

1. With no eligible gameplay profiles, leave accessibility enabled: overlays
   stay absent and no foreground root stream should be active.
2. Enable a selected game's triggers, refresh-rate and performance profile;
   enter/exit repeatedly, confirming detection and restoration still work.
3. Verify FPS, temperature, display Hz, mode and fan RPM update on the visible
   overlay; rotate, hide/show, lock/unlock and reopen after runtime recovery.
4. Repeat the long COD session that previously lost overlays/triggers.
5. Test hot screen-off cooling, cool shutdown and step-3 notification/call/
   charging restoration. No change should weaken cooling or LED shutdown.

Further audit items: measure actual device cost of the dumpsys stream and
fallback queries; inspect supervisor and trigger-bridge loops before proposing
changes; review restart-generation races in telemetry/temperature workers and
hardware write invalidation. These are not claimed resolved by this patch.
