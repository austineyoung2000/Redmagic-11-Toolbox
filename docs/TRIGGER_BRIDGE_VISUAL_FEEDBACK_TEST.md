# Trigger Bridge edge feedback — implementation and validation

The app observes the bridge's `redmagic_trigger_bridge` virtual input device through a blocking root `getevent` reader. It never grabs the device or injects events. The inspected module reserves tracking IDs 65535 and 65534 for left and right; physical contacts are ignored. Updates are committed at SYN_REPORT, with UI work only on changed trigger states. No polling loop or module modification is added.

A per-game **Trigger Bridge edge highlights** switch defaults on and is saved with the trigger profile. Feedback starts only after successful module-backend activation. Native TGK continues using stock visuals. Runtime exit, editor entry, mapping failure, and backend transition remove the reader and window. Reader failure removes feedback without changing input mapping.

The non-focusable, non-touchable overlay uses window alpha 0.5, red/blue gradient glows, and a 120 ms release fade. Landscape uses top/bottom according to rotation; portrait uses the corresponding side edge. Placement is approximate and needs device confirmation against the physical trigger positions. No claim of exact Game Space parity is made.

## Device regression checklist

- Force/select module backend using the existing test mechanism; confirm diagnostics.
- Left press red, right press blue, held glow, release fade, both glows.
- Keep moving the game thumbstick and use two screen fingers while pressing both triggers: no blocked or dropped touches.
- Test both landscape rotations and portrait; verify edge/left-right placement.
- Disable the per-game switch, re-enter game, and confirm no highlights.
- Exit game, switch apps, screen off, open mapping editor: no lingering highlights.
- Return to Native TGK: only stock feedback.
- Repeat game entry and app/runtime restart; check no duplicate reader/window.

Requires root getevent access and the inspected module tracking-ID contract. If SELinux prevents observation, mapping continues without glows. The owner confirmed that mapped touches and edge highlights work together after reboot on October 4, 2026. The initial failure cleared after reboot; the exact stale state was not identified. A separately reported connection latency issue was confirmed by the owner to be Wi-Fi-related, unrelated to this project. Reboot after updating the APK/module before evaluating input behavior. The owner authorized merging into `sixteen`; the checklist above remains useful for broader regression testing.
