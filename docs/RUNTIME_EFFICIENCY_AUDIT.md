# Runtime efficiency audit — step 4

Baseline: production b824998d9a542403762667104f74ff7477a9fce7.

## Failed phone test and rollback

The first test patch (187f2df) gated foreground fallback queries and removed
callback-driven performance-overlay redraws. CI tests and signed assembly
passed, but the owner reported disappearing overlays during phone testing.
The exact overlay, trigger state, process status and failure timing have not
yet been established. This is not a confirmed root-cause diagnosis.

Both optimizations are withdrawn. GameplayRuntimeService, RefreshRateOverlay,
PerformanceOverlayTelemetry and VendorFpsMonitor are restored byte-for-byte
to the production baseline. No runtime optimization remains in this PR.
Production was never changed; the PR remains draft and must not be merged as
a successful efficiency fix. Step 4 is paused for diagnostics; step 5 has not
started. CI cannot establish device overlay endurance.

## Retained source findings

- Runtime foreground fallback can query UsageEvents at the 750 ms controller
  interval when no root stream is present. Eligibility changes need to account
  for every foreground consumer and recovery path before testing again.
- Performance overlay has both a one-second ticker and callback-driven redraws.
  Any throttling requires device evidence and must preserve lifecycle recovery.
- Active root foreground cadence is one second; idle cadence is three seconds.
- Watchdog binder/configuration checks run every two seconds. The healthy root
  supervisor path uses pidof every two seconds and reserves Binder/settings/
  launch traffic for recovery. Preserve these reliability mechanisms.
- Temperature sampling is shared and non-root: 3 seconds foreground, 5 seconds
  hot background, 15 seconds cool interactive background, 30 seconds cool
  screen-off. Auto cooling subscribers and worker shutdown need continued review.
- Fan RPM overlay telemetry reads every 30 seconds while attached, using direct
  sysfs access before root fallback. Vendor FPS uses Binder callbacks.

## Required device evidence

Identify whether the gameplay launcher, trigger visuals, performance overlay
or all overlays disappeared; record time, foreground app and whether trigger
actions continued. Capture process/foreground-service state, exit history,
recent crash/runtime logs and supervisor status before restarting or rebooting.
Retest the restored baseline to distinguish the withdrawn patch from an APK
replacement/runtime-recovery problem. No CPU/battery/thermal benefit is claimed
measured, and the reported failure is not claimed fixed by the rollback.
