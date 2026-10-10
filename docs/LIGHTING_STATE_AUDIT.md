# NX809J lighting ownership audit

Scope: Toolbox LED writers, profile restoration, notification timers, charging/call lifecycle, normal/Studio previews, selected-game lighting, boot/profile restoration entry points. Source baseline: `272278591b7b3446c5df5199720e9b25a35f6372`. Trigger Bridge and gameplay overlays/runtime are outside the changes.

## Required ownership

Ringing call > enabled charging lighting while physically plugged in (including full/separation) > eligible timed notification > selected foreground game > RGB Studio/normal. Answered calls do not own LEDs. Screen-off/AOD excludes game, Studio and normal lighting. Notification requires enabled settings, a live elapsed deadline, screen-off, unplugged and no ringing owner.

Fan/pump cooling is separate. LED shutdown writes only the three LED regions and cfg, using the existing NX809J shutdown values/order. It does not stop hot-phone cooling. Notification programming still uses the unchanged reverse-engineered NX809J batch and existing temperature-dependent cooling cleanup.

## Confirmed paths and changes

| Path | Finding | Safeguard |
| --- | --- | --- |
| Ownership resolver/restoration | Persisted call-active or notification deadline alone could retain ownership outside eligible conditions. | Call ownership reads ringing state; notification checks enable, elapsed deadline, display, plug and ringing. Screen-off resolves ordinary owners to NONE. |
| Coordinator | Only ordinary screen-off was checked after a profile; ignored zone failures could be recorded as an applied profile. | Capture every root LED result; recheck ownership after the writes; clear an invalid/failed profile; reconcile the new owner with bounded recursion. Never cache an unsuccessful handoff. |
| Normal and charging editor previews | Direct queued hardware writes bypassed profile serialization; charging fan previews could interrupt ringing calls. | Route all these previews through the same coordinator. Saved settings still persist even when a higher owner blocks preview. |
| Saved game profile / fan restore worker | Direct hardware writes bypassed ownership. | Guard saved-game LEDs with selected foreground membership and GAME ownership; guard worker with NORMAL ownership. Cooling application remains separate. |
| Selected game restoration | Reentering the existing game service could poll the same game without replaying its LEDs after charging/call restoration. Own app foreground was ignored. | Mark current game for reapplication on restoration; count Toolbox foreground as a game exit; retain the most recently observed foreground package between UsageEvents windows. |
| Shutdown | Semicolon commands could report only the last write's exit status; cached shutdown could suppress a new clear. | Always execute a physical clear, attempt every region, aggregate failures and retry once. Existing values/order preserved. |
| Legacy LED profile commands | A successful last cfg could mask an earlier shell failure. | Execute legacy commands in a fail-fast subshell; leave existing self-managed batch scripts unchanged. Root-write receipts retain failures even if a later rollback/write succeeds. |
| Notification listener | No screen-off reconciliation when no window was owned; timer runnable ended without checking current elapsed deadline. | Reconcile on SCREEN_OFF; invalidate eligibility immediately on wake/plug/settings; deadline-check delayed expiry. Distinct-alert restart and dedup behavior preserved. |
| Call/charging/Studio lifecycle | Graceful stop could leave obsolete state/output behind. | Worker-ordered cleanup/reconciliation on graceful service stop, without restarting Studio from its own enabled stop path. Charging write failures invalidate evaluator caches so a later battery evaluation retries. |
| Studio off acknowledgement | Off flag was set even when application failed. | Commit the off flag only after successful coordinator acknowledgement; run screen-off clearing before the ordinary owner gate. |
| Master profiles / boot | LED application routes through service/coordinator restoration rather than direct LED node writes. | Covered by central eligibility and post-write guards. |
| Hardware facade / storage | Facade has no callers outside its definition; storage only persists settings. | No new hardware writer or device protocol introduced. |

## Verification and limits

Automated checks cover all priority combinations with screen-on/off, stale deadlines, wake/plug/ringing exclusion, changed-owner rejection, root result aggregation, nested receipts and a real shell simulation that attempts all three shutdown regions despite cfg failures. GitHub Actions performs the complete Android unit suite and signed release build; final result belongs in PR #12.

Root command acknowledgement is not optical verification. A driver/root command that never returns still blocks the shared root queue and cleanup. Abrupt process death does not run onDestroy; notification listener reconnection clears orphaned lighting, but there is no independent process-death hardware deadline. Sustained distinct notifications intentionally restart the user-selected timer; expiry is measured after the last accepted distinct alert. These limits are not claimed fixed or device-tested by this audit.

## Phone checks

1. Normal and Studio LEDs: lock/AOD with no alert; all LED zones go off. Hot fan/pump cooling can continue.
2. Enable all notification zones, choose 10 seconds: send single alerts and overlapping alerts from two senders; all chosen zones start, then clear 10 seconds after the final accepted distinct alert. Repeat lock/unlock cycles without reboot.
3. While a notification is lit: unlock or plug in. Normal/game/Studio or charging takes over; old notification timeout must not later extinguish the new owner.
4. Charging at full/separation: notifications and editor previews cannot steal LEDs. Ringing interrupts; answer/dismiss restores charging.
5. Unplug while screen-off with no ringing/notification: all LEDs clear; hot cooling remains permitted.
6. Selected game: call/charging exit restores its saved LED profile; leaving for Toolbox clears game ownership. Verify saved effects, brightness and split zones on restored ordinary profiles.

No merge until phone results and explicit authorization.
