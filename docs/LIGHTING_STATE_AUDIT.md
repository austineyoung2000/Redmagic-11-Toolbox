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
| Selected game restoration | Reentering the existing game service could poll the same game without replaying its LEDs after charging/call restoration. Own app foreground was ignored. | Mark current game for reapplication on restoration; count Toolbox foreground as a game exit; retain the most recently observed foreground package between UsageEvents windows. Require live selected-foreground evidence as well as the saved override, and clear an orphan override when Usage Access is unavailable. Failed game LED writes remain pending for the existing active-game poll. |
| Shutdown | Semicolon commands could report only the last write's exit status; cached shutdown could suppress a new clear. | Always execute a physical clear, attempt every region, aggregate failures and retry once. Existing values/order preserved. |
| Legacy LED profile commands | A successful last cfg could mask an earlier shell failure. | Execute legacy commands in a fail-fast subshell; leave existing self-managed batch scripts unchanged. Root-write receipts retain failures even if a later rollback/write succeeds. Cooling write caches are invalidated even when a LED script fails after partially changing fan/pump power. |
| Notification listener | No screen-off reconciliation when no window was owned; timer runnable ended without checking current elapsed deadline. | Reconcile on SCREEN_OFF; invalidate eligibility immediately on wake/plug/settings; deadline-check delayed expiry. Distinct-alert restart and dedup behavior preserved. |
| Call/charging/Studio lifecycle | Graceful stop could leave obsolete state/output behind. | Worker-ordered cleanup/reconciliation on graceful service stop, without restarting Studio from its own enabled stop path. Charging write failures invalidate evaluator caches so a later battery evaluation retries. |
| Studio off acknowledgement | Off flag was set even when application failed. | Commit the off flag only after successful coordinator acknowledgement; run screen-off clearing before the ordinary owner gate. |
| Master profiles / boot | LED application routes through service/coordinator restoration rather than direct LED node writes. | Covered by central eligibility and post-write guards. |
| Hardware facade / storage | Facade has no callers outside its definition; storage only persists settings. | No new hardware writer or device protocol introduced. |

## Verification and limits

Automated checks cover all priority combinations with screen-on/off, stale deadlines, wake/plug/ringing exclusion, changed-owner rejection, root result aggregation, nested receipts and a real shell simulation that attempts all three shutdown regions despite cfg failures. GitHub Actions performs the complete Android unit suite and signed release build; final result belongs in PR #12.

The follow-up replaces the app-local listener alarm with a persisted, identity-fenced `PendingIntent` exact-idle alarm. Its receiver can restart the app after an ordinary process kill. Root grants and verifies exact-alarm access; an alert does not claim ownership or program LEDs unless both alarm scheduling and the window record succeed. Setup and visible deadlines have separate identities even within the same window, so a late setup callback cannot truncate the visible duration.

Logical release does not discard recovery early. The window/alarm remains until the replacement profile or all-off writes are acknowledged; otherwise up to three bounded recovery alarms reconcile the *current* owner. Old window/deadline/boot callbacks do nothing. This record is recovery metadata, not authority to relight an expired notification. Listener expiry retains a bounded partial wake lock through the physical restoration attempt instead of releasing it before root cleanup; the display remains asleep.

LED commands use a dedicated persistent root channel with a five-second command limit and bounded PID-verified cancellation. Each generated subshell registers its actual PID, kernel start time and boot identity before touching nodes. Timed-out and former-process writers are revoked and canceled; reused PIDs and former-boot records cannot be targeted. A late authorization sees revocation and exits before hardware writes. Cancellation verifies writer exit, zombie state or replacement identity rather than treating accepted signal delivery as completed termination. An unacknowledged cancellation quarantines the LED channel rather than allowing competing writers. Noninteractive root implementations can use bounded `su -c` on the next operation; partial programs are never automatically replayed. RootShell, gameplay overlays/watchdog and Trigger Bridge remain unchanged.

Per-resource controller locks and generation-based telemetry invalidation prevent lighting from waiting on a hung cooling/dashboard shared-root read. Profile receipts reject subsequent zone writes after an earlier failure or ownership change. LED power side effects invalidate the screen-off cooling shutdown receipt as well as hardware write caches, so a previous cold shutdown cannot suppress enforcement after LED commands enable fan power again. The dedicated channel verifies UID 0 before admitting hardware commands. Call fan-pause/restoration runs separately from LED handoffs, with a bounded read of the same NX809J fan nodes preserving the pre-call cooling snapshot. Game Mode also runs cooling application/restoration on a separate worker, rejecting superseded queued cooling requests by generation. It releases its LED ownership and reconciles the next profile before normal cooling restoration. Existing fan/pump values and temperature policy are unchanged.

Settings boot diagnostics now include exact-alarm access, recovery-record/logical-active state, and LED root quarantine status. Its root boot-log read uses the bounded channel too. Runtime deadline/PID records are not included in profile backups.

Root acknowledgement is not optical verification. Android force-stop/disable/uninstall can cancel alarms or block receiver delivery; a reboot clears system alarms and boot fencing rejects older callbacks. An unresponsive kernel/driver or unavailable root cancellation cannot be repaired reliably by app code and is reported as a failure, not a completed handoff. These conditions remain explicit limits. Ordinary process kill and stalled *shell* recovery are implemented but still need NX809J phone verification. Sustained distinct alerts intentionally restart the selected timer. Source/host-shell checks are not claimed as device tests.

## Phone checks

1. Normal and Studio LEDs: lock/AOD with no alert; all LED zones go off. Hot fan/pump cooling can continue.
2. Enable all notification zones, choose 10 seconds: send single alerts and overlapping alerts from two senders; all chosen zones start, then clear 10 seconds after the final accepted distinct alert. Repeat lock/unlock cycles without reboot.
3. While a notification is lit: unlock or plug in. Normal/game/Studio or charging takes over; old notification timeout must not later extinguish the new owner.
4. Charging at full/separation: notifications and editor previews cannot steal LEDs. Ringing interrupts; answer/dismiss restores charging.
5. Unplug while screen-off with no ringing/notification: all LEDs clear; hot cooling remains permitted.
6. Selected game: call/charging exit restores its saved LED profile; leaving for Toolbox clears game ownership. Verify saved effects, brightness and split zones on restored ordinary profiles.
7. Settings boot diagnostics: exactAccess=true and no root quarantine. Test ordinary main-process kill during a locked notification separately from Android force-stop; expiry/reconnection must restore the eligible owner. Use a prepared delayed Termux `su -c` command so observing the test does not require waking the phone.

No merge until phone results and explicit authorization.
