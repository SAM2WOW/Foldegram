# Foldegram preview 2

This revision responds to first device feedback: the two-chat workspace opens, but its custom controls felt inconsistent and dragging was not usable. This is not confirmation that the fold/resume freeze is resolved.

## Interaction changes

- The workspace uses Telegram's native text tab strip, theme colors, indicator and selectors. Tap a tab or pane to focus it; long-press a tab or use the replace control to choose another conversation. Chat navigation and the existing 600dp narrow/wide policy remain intact.
- In a visible two-chat workspace, touch and hold a forwardable sent message and drag directly to the other pane. There is no menu-then-hold prerequisite. The existing message-menu Drag option remains available outside that workspace. In the two-pane workspace, releasing back in the source pane opens the normal message menu; existing multi-select mode keeps its usual long-press behavior.
- Text, photos, videos, files, voice/music, stickers/GIFs, contacts, locations and forwardable polls use Telegram's native forwarding preview. Loaded albums move as a group; any protected member rejects the group. Destination-specific send permissions are rechecked. Telegram restrictions, secret chats, ephemeral/protected/sensitive content and unsent/service messages remain excluded.
- The root drop path delegates through an attached native View so Android performs drag-focus and handler bookkeeping. A themed highlight identifies the destination. Message data stays in process; internal drags do not export content to other apps.
- External drops accept plain text/HTTP(S) links or up to 10 content-URI files, with a 100 MiB combined copy cap and 30-second import timeout. A source app must support Android dragging and grant read access. JPEG/PNG/WebP use the photo preview; other files (including video/audio and multi-file selections) show names, sizes and destination in a confirmation dialog and are sent as documents. No implicit URL fetch, file-URI access or Intent execution occurs.
- Text is appended to the existing draft. Forward/photo previews and file confirmation require explicit Send. Existing replies/edits/attachments are not silently overwritten. Paid-message confirmation, destination rechecks, cancellation cleanup, source-provider checks and lifecycle cancellation remain enforced.

## Client branding

Client labels use an explicit allowlist for Foldegram branding, including packed/cloud localization paths. Telegram account/network, Premium, support, URLs, protocol identifiers and legal references retain their meaning. About identifies the client as unofficial and based on Telegram. Main navigation, onboarding and notifications use the approved crane. The notification resource is `drawable-anydpi`, overriding the upstream density-specific marks. The launcher polygons and seams are unchanged apart from a uniform optical translation (+1.8, +1.3 in the 108-unit viewport); in-app and notification marks use the same placement with tighter framing.

## Bubble lifecycle corrections

The source audit found duplicate stack resumes, missing explicit geometry invalidation, stale previous-conversation bubble bookkeeping, and absent fragment-stack destruction. The revision balances resume/pause behind Activity/passcode state, refreshes geometry on configuration changes, clears the old conversation marker before switching, saves replacement intents, and destroys fragments on teardown. Opening a bubble no longer broadcasts a request to finish all other app Activities. Notification bubble intents are stable per account/conversation; shortcut intents include the account, and desired height is supplied in dp.

These are source-level defects and candidate fixes, not a reproduced diagnosis of Sam's official Telegram freeze.

## Verification boundary

- Host Java tests execute extracted production drag policy and bubble resume/pause gates with doubles. They are not Android touch, window manager, URI-provider, notification, or device lifecycle tests.
- Branding tests execute the client-label allowlist and check service boundaries. Icon geometry/identity and existing fold/workspace regression checks remain applicable.
- APK assembly, lint, manifest/icon inspection, signing and ZIP/ELF alignment are reported separately for the delivered build.
- No connected phone is available in the executor. Use test conversations first.

## Device regression plan

1. Update the existing Foldegram installation without clearing data; check app name, onboarding where applicable, About, notification icon, and themed launcher icon.
2. Open two chats; focus/replace panes, use narrow tabs, open nested screens and Back, and switch light/dark theme. Check keyboard, drafts and scroll positions while folding/rotating/resizing.
3. Directly hold and drag text, photo, video, voice note, document and an album between panes. Cancel and retry; verify preview destination and explicit Send. Check a restricted destination and protected/secret source.
4. In Android split-screen, drag text, a link, photo, video, PDF and multiple files from an app that supports dragging. Check granted and denied URI access, preview cancel, background/lock during copying, and preserved drafts.
5. For bubbles, repeatedly open/collapse/reopen, navigate to profile/media and Back, switch conversations/accounts, transition between the main app and bubble, and fold/resize with the bubble expanded. Repeat with keyboard and passcode. Check that previous conversations do not remain marked open after switching and that the main app stays available.
6. Record the exact failing gesture and whether Home/Recents remain responsive if any freeze persists. No test here substitutes for that device evidence.
