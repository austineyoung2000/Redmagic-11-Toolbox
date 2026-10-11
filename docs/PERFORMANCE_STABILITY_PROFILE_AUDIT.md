# NX809J production performance, stability and profile audit

Baseline: Toolbox sixteen 300f0cde81b6aa2e81166ed842d9dd588b1b9c90; Trigger Bridge main da48d43dd8f23bd873d9c75406864c2a1555f306. Work is isolated from the dirty previous worktree. This is a source audit and regression build, not a measured battery-drain result.

## Confirmed fixes in this test branch

- LED ownership queried RgbStudioStorage.isEnabled, which read, split, filtered and sanitized the full Studio profile on every check. Read only the enable preference. Full editor/runtime reads retain their existing sanitization; live screen/call/power priority checks remain live.
- Every notification callback enumerated all preference keys to find a configured app. Use a direct contains lookup for that app key. Package-list rendering, saved profiles, deduplication, notification commands and timers are unchanged.
- Nested LED write receipts replaced the outer eligibility predicate. Inherit outer eligibility and prior failure so nested helpers cannot escape a revoked owner or hide an earlier failed write.
- Unknown effect/color values could reach the stock fallback and silently become steady/red output. Validate zone, finite accepted preset colors, known effects, brightness wrappers, split routing, logo/bar schema and legacy fan palettes before command construction. Reject malformed or oversized selections and mark the profile receipt failed. Preserve valid existing encoded selections and firmware values. RGB Studio already sanitizes its own inputs; notification batch already uses a strict whitelist.
- Auto fan/pump state caches advanced even after a failed hardware command; cold screen-off shutdown also cached success unconditionally. Commit only acknowledged states and require both shutdown commands to succeed. Fence shutdown completion against an intervening LED power-change generation so an old acknowledgement cannot resurrect a revoked cache. Existing temperature/hysteresis thresholds and hardware values stay intact. Failed writes retry on existing temperature events, with no new polling or wake locks.
- Multi-write fan level/PWM and pump profile commands used semicolons, allowing a later write to hide an earlier failure. Use && to stop on the first unsuccessful write and retain the failure receipt. LED scripts and programming order are unchanged.

## Reviewed runtime paths

| Area | Source finding / treatment |
| --- | --- |
| Temperature monitor | Shared non-root sampling; 3s visible, 5s hot background, 15s interactive background, 30s cool screen-off. Last subscription closes worker/listener. Retained for cooling safety. |
| Auto cooling | Uses shared temperature events rather than independent fast timers. Commands are state-dependent. Acknowledgement caching corrected; no threshold changes. |
| Normal LED guard / charging | Screen/power or battery events; lifecycle receivers and worker teardown present. Keep the proven screen-off guard and charging priority. |
| Notification LEDs | Exact durable deadline plus bounded partial wake locks; no display wake. Event metadata dedup/cache remains bounded. Listener/module recovery remains as merged. |
| RGB Studio | Requested color animation can generate repeated hardware programming at a minimum 500ms frame interval. Screen-off stops ordinary output. Full-rate animation consumes work by design; no promise of zero battery cost. Enable-only ownership lookups optimized. |
| Game LEDs | Event-driven startup; no idle game polling; 120s active recheck. Preserve foreground ownership and cooling generation fences. |
| Gameplay / overlays / triggers | Watchdog 2s liveness checks and visible overlay refresh remain. Native input, overlay lifetime, accessibility binding and gameplay RootShell are untouched after the earlier regression. |
| Telemetry | Temperature cache 750ms; hardware read cache 30s; overlay RPM read at most 30s. Global root read can block; see remaining risks. |
| Profile / backup | Master schema and supplemental import limits exist; Studio sanitizes lists, timing and brightness; split firmware writes check template hashes. New write-boundary validation adds defense across normal/game/call/charging profile paths. Invalid data is not silently rewritten. |
| Module | Existing supervisor checks event-written identity every 20s while enabled, no persistent wake lock or notification dumps, bounded recovery/backoff. No module change in this branch. |

## Remaining risks and scope limits

The SIGKILL source remains unidentified. No source-only change proves reduced measured drain or prevents vendor/system termination. Global RootShell still performs blocking reads without the dedicated lighting channel's five-second deadline; changing it would affect proven trigger/gameplay paths and is not included in this first patch. Hardware telemetry may retry failed uncached reads more frequently than its successful cache interval; future work should isolate read-only timeout/backoff without changing gameplay semantics. Root unavailability or driver/kernel hangs cannot be guaranteed recoverable.

A stale service-local cooling state after another owner changes hardware remains a separate concern beyond command-success caching. Existing HardwareController power-cache invalidation and the notification cooling policy remain; this patch does not claim to solve every external hardware change. The source audit therefore does not establish zero stale states or zero battery drain.

## Validation and phone acceptance

Regression coverage adds partial cooling shutdown and stale-generation fencing, revoked outer-owner nesting, prior failed outer writes, stock/legacy fan palettes, split/brightness compatibility, zone routing and malformed/command-like selection rejection. Run the complete Android unit suite and signed client build in GitHub Actions. CI validates source and host behavior; physical cooling and LEDs require NX809J confirmation.

On the phone: repeat the working screen-off notifications and duration; test charging/call takeover and normal/game/Studio restoration; check split logo/bar/triggers and brightness; verify fan/pump still cool a hot locked phone and stop when cool; verify the overlay survives an actual game session. Compare idle use over a consistent unplugged interval if evaluating battery improvement. Do not merge until the user approves the tested build.
