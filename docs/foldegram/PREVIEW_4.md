# Foldegram preview 4

## Screenshot security

Chat view recreation previously replaced `flagSecure` with a new reason object without releasing an attached old object. The original reason could stay counted after the protected fragment was gone. Binding now reuses the fragment's reason in the same window, and releases the old reason when moving to another window. Host regressions exercise 100 rebuilds, two-pane composition, secret/protected content, independent passcode protection and window changes with the actual `FlagSecureReason` reference-count implementation.

This is a concrete lifecycle defect, not a reproduced diagnosis of Sam's blank screenshot. Screen capture remains blocked when the app's passcode setting disallows it or a visible secret/protected conversation requires it. No global `FLAG_SECURE` removal, settings override or software-rendering workaround is introduced. Ordinary-chat screenshots and protected-chat rejection still require phone testing.

## Header placement

The visible two-chat/sidebar action moves after the overflow action, with a 12dp gap. Call and the workspace control are separated by the overflow control rather than adjacent. The native single-row glass header and overflow entry remain.

## Google Photos to one chat, and workspace drops

Provider validation now occurs after taking the drag URI grant. On Android 11+, that grant also makes its content provider visible to package queries; resolving the provider before taking the grant could return null and silently reject the drop. See [Android package visibility and URI access](https://developer.android.com/training/package-visibility/use-cases#grant-uri-access). Self-provider rejection still occurs before opening any data. Failed target/provider checks now show a notice. No broad package visibility or storage permission is added.

The attached drop receiver now uses a native FrameLayout/child dispatch path so Android owns child drag bookkeeping, while EditText is excluded from automatic insertion. A visible paused Android split-screen chat can receive a drop without manually resuming its Activity. Top-fragment, visibility, lock, secret-chat, write-permission and composition guards remain. Workspace drops still explicitly activate their destination pane.

Provider reads use typed AssetFileDescriptor streams with a read-only asset fallback, preserving virtual-provider compatibility and descriptor offset/length. URI access remains enforced by Android. A missing transient token does not reject an already-readable exact dropped URI; no persistent or broader access is requested. Up to 10 items, 100 MiB combined copying, a 30-second timeout and bounded image dimensions remain. Image decoding recognizes JPEG/PNG/WebP and supported HEIF/HEIC/AVIF data; decoding support is checked before preview. Other files use the document confirmation flow.

One dropped image offers **Photo · compressed** or **File · original quality**, then Telegram's visual PhotoViewer preview. File mode uses the document-picker preview and sends the exact copied provider bytes as a document, without recompression. Photo mode uses Telegram's photo preparation. Both require explicit Send, slow-mode checking, paid-message confirmation where applicable, and a fresh permission/ownership check after confirmation. Existing composer text is preserved. Cancel, lock, navigation or lifecycle teardown deletes unsubmitted copies; a generation guard cancels delayed preview opening. No automatic sending, cross-account forwarding, secret-content export or internal-message drag-out is introduced.

## Phone QA still required

1. With two ordinary chats and capture allowed, take a screenshot. Change theme, switch chats, fold/unfold and repeat. Then test one protected/secret pane: capture must remain blocked. Return to only ordinary content and verify capture is allowed when no independent protection remains.
2. Verify Call → overflow → gap → workspace ordering, tap targets and title fit at larger font sizes. Confirm split/sidebar controls remain discoverable and no extra header row appears.
3. Android split-screen: Google Photos beside Foldegram with one ordinary writable chat. Drag a local photo; choose Photo, review/cancel, repeat and explicitly Send. Repeat with File and verify original file bytes/size, then a cloud-backed photo. Test denied access and unsupported/oversized data.
4. Repeat image/text drops into each Foldegram workspace pane, including the initially inactive pane. Check lock/hidden/secret/read-only targets, draft preservation, preview cancel and backgrounding during import. Confirm no send happens before Send and no send happens after cancelling a pending paid confirmation by leaving/locking the chat.

Host checks, assembly, lint and APK/resource/signature/alignment inspection are not physical screenshot, Google Photos, touch, WindowManager or render validation. No device is attached in this executor.

## Sidebar drag-to-pane and lighter pane treatment

The existing searchable chats/contacts overlay now also opens through a 48dp tap handle on the left. An inward horizontal swipe can start in the narrow strip just beyond Android's reported system-gesture inset; the app does not exclude or claim the OS back-edge area. A child may still own a conflicting gesture, so the tap handle remains the reliable alternative. No extra toolbar is added.

Long-press a conversation row or a person in search, then drag it onto a pane. The picker becomes temporarily invisible while staying attached, exposing both targets. The hovered pane gets a soft rounded outline; releasing opens the conversation only in that pane through normal navigation. Cancelling restores the same picker/search position without changing chats. On a folded screen, the visible active pane is the target; choose the destination in the sidebar first. Forum rows retain tap-to-select-topic behavior rather than guessing a topic on drag.

The drag carries an expiring random token and process-local state, never serialized chat contents or a global drag grant. Account/user identity, lock, lifetime, target availability and pending composition are checked. Duplicate opposite-pane conversations are rejected. Opening a secret/protected chat retains its normal capture policy.

Pane views use native rounded clipping (12dp corners), an 8dp wide-layout gutter and lightweight layered target strokes instead of a persistent blur pass. Accepted navigation uses a small scale/overshoot pulse plus the existing native chat transition. The pulse and sidebar slide respect SharedConfig.animationsEnabled(); reduced-motion settings therefore suppress these added animations.

Additional phone QA: tap the handle, swipe inward outside the OS edge, verify Android Back still works, long-press a search result/contact, hover both panes, cancel and retry/drop on each. Repeat with drafts, a protected chat, account/logout/lock, fold/unfold, font scaling and animations disabled. Check rounded clipping, gutter and message-space use on the actual Fold. These gestures and rendering effects were not tested on a physical device here.
