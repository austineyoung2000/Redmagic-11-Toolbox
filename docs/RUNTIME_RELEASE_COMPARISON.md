# Recovery comparison against official v2.6.0

Official baseline: be8c47310a789d3c8b29adf383246917913f75c5 (v2.6.0, published 2026-10-04). Production baseline: 300f0cde81b6aa2e81166ed842d9dd588b1b9c90. PR13 remains separate and unmerged; this diagnostic build does not include its performance/cooling changes.

Watchdog, TriggerAccessibilityService, TriggerRootService, RootShell and gameplay supervisor are identical to the official tag. GameplayRuntimeService gained optional TriggerBridgeFeedback show/hide calls; GameplaySpaceOverlay changed its backend label. MainActivity and boot paths additionally start/retain lighting services. No comparison finding proves which change, if any, causes the paired SIGKILL.

Device reports paired SIGKILL at 14:34:56 and 15:55:48 on 2026-10-12, followed by restarted processes and missing windows/listener. Module logs discard failed transaction83 output and abort before transaction81. Manual calls as UID10500 both return no-exception receipts matching the existing parser, so an automatic invocation/environment difference remains unresolved. Capture that difference rather than asserting a parser or killer cause.

The companion module repair attempts rebind even after an uncertain unbind receipt, retains bounded error/caller details and confirms success only through Android's actual connection callback. Existing firmware/device/access/enablement gates remain. It cannot prevent kills.

Settings diagnostics now expose runtime presence, accessibility configuration, selected native package/backend, overlay owner/orientation and actual GS/target attachment state, plus process creation events. Service presence is not window attachment and attachment is not an optical test. No new polling, root command, trigger mapping, overlay lifecycle or LED behavior is introduced. The next failure report can distinguish lost ownership from detached windows without speculative rollback.
