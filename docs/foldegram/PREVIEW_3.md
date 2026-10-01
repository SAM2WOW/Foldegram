# Foldegram preview 3

Fixes the earlier multicolored Foldegram crane briefly shown on startup. The inherited light Android 12+ and dark startup themes explicitly reference `tg_splash_320`, independently of the launcher icon. The app-level override still contained the earlier nine-path multicolored crane. It now uses a static navy disc and the exact approved six crane paths from preview 2. The disc fits the platform's two-thirds safe circle. Older startup themes use plain window backgrounds; launcher aliases and the platform fallback already resolve to the Foldegram launcher. No startup theme behavior or crane geometry is redesigned.

Resource inspection can verify the packaged theme references and vector paths. It cannot prove cold/warm-launch timing or launcher caching on a phone. No device was attached for this revision.

## Bubbles

There is no additional in-app bubbles toggle. `SharedConfig.chatBubbles` defaults to true on Android 11+ and has no preference loader or UI setter in this source. The notification builder adds bubble metadata to ordinary conversation notifications; broadcast channels and story notifications are excluded.

On the Pixel, allow system bubbles and Foldegram notification/bubble permissions. Ensure the desired chat is unmuted (open the conversation menu and select **Unmute** if it is muted), then receive a new message while outside that chat. Expand its notification and use Android's bubble control if offered. If Android is set to selected conversations, allow that conversation to bubble. The client does not automatically expand bubbles (`setAutoExpandBubble(false)`). No separate Foldegram "open as bubble" command is required.

Preview 2's drag, branding and bubble lifecycle changes remain. Bubble freeze resolution and physical-device cold/warm launch behavior remain unverified.

## Bubble to unfolded main-window status bar

The drawer root previously published every window's top/bottom insets to process-wide status/navigation bar metrics, but only when that root's local cached insets changed. A bubble could therefore publish zero, and a main window returning with the same local insets would not restore its metrics. Bubble roots now retain their local insets without publishing them globally. Full roots reconcile shared metrics on every inset dispatch, including unchanged local insets, and request fresh insets on window-focus return. No fixed padding or blanket fullscreen flag change is used. Main activity resume/configuration already refreshes display metrics from its own window.

Regression checks execute the production metric synchronization method with Java doubles over repeated main/bubble transitions, unchanged main insets, changed unfolded insets and zero-inset full windows. These do not emulate Android WindowManager.

Device check: open a bubble while folded, unfold, return to the full Foldegram main window, then repeat with the bubble expanded/collapsed and keyboard visible/hidden. Check the status icons and chat toolbar do not overlap; fold/refold and repeat several times. Also check fullscreen media retains its intended behavior. This specific device report remains unconfirmed in the executor because no phone is attached.

## Compact workspace and conversation sidebar

A visible split-view icon in a normal chat's native glass header opens the second-chat picker; the overflow entry remains. Inside the workspace, each chat keeps its actual Telegram capsule/glass header, including recipient/profile and menu controls. The extra 48dp workspace toolbar is removed, so unfolded chat headers occupy one row. The renderer is the existing `ActionBar.setupGlass` path with Telegram's own blur/theme/performance settings; no separate glass imitation is introduced.

The sidebar icon in either chat header focuses that pane and opens a collapsible native searchable conversation picker. Its two rounded pane selectors choose the destination; tap a conversation to open it there. It overlays at most 360dp and leaves an outside-dismiss target, including on narrow folded screens, rather than taking a permanent third column. Close it with its close control, outside tap or Back. The bottom control returns to the underlying chat list by closing the workspace. A thin themed outline identifies the focused pane in wide mode; selectors expose selected state and recipient descriptions to accessibility.

Opening/closing the sidebar never rebuilds either chat. Pane switching preserves both stacks. Choosing a different chat uses normal navigation in the selected pane, preserving the previous chat for Back and leaving the other pane untouched. Telegram's normal pause path saves drafts and scroll anchors. Editing/recording/unsent attachments and forwarding previews must be completed or cancelled before changing conversations. The sidebar closes on background/lock/destroy, and only the picker or active chat owns resumed state.

Device QA: confirm the split icon is discoverable and header titles still fit at large font size; compare native glass in light/dark and reduced-effects modes. Open the sidebar, select each destination, search/select chats and forum topics, close/cancel repeatedly, then Back to the earlier chat and verify draft/scroll. Fold/unfold with the sidebar open; check no extra permanent column or duplicate top bar, correct insets, TalkBack selection and 48dp controls. Verify message drag still works with the sidebar closed. No Android UI screenshot or physical-device validation was available here.
