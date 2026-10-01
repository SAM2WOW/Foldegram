# Foldegram preview verification

This is an experimental, unofficial Telegram fork. It has not been tested on the
reported Pixel 11 Fold. A successful build is not evidence that the reported
freeze is fixed.

## Reported baseline

- Official Google Play Telegram 12.10.6
- User-reported Pixel 11 Fold
- Android 17, build CD1A.260905.001.B1
- Android security and Google Play system updates: September 1, 2026
- Symptoms: frozen last frame or blank/unresponsive app around fold/unfold,
  split-screen or resume; half-screen sometimes helps; often app reopen required
- Exact reproduction: not yet established

Source baseline: DrKLO/Telegram commit
`f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6, version code 7112).

## Safety before testing

Install alongside the official app. Do not uninstall the official app or clear
its storage. Start with Saved Messages and a test conversation. Never share chat
content, phone numbers, login codes, API hashes, or unredacted logs in a report.
Do not treat a setup-only APK as a working chat client.

## Device test matrix (not yet run)

Record initial screen/window mode, exact gestures, resulting screen, whether
Android Home/Recents remains responsive, and whether returning restores the app.

1. Open Saved Messages folded, unfold, fold, unfold; repeat with keyboard open
2. Start unfolded, enter Android split-screen, resize repeatedly, exit split
3. Open chat, background app, fold/unfold, return from Recents
4. Repeat with light/dark mode change and display/font size changes
5. Rotate if supported; verify visible chat and scroll position are preserved
6. Lock and unlock the device with app visible and in the background
7. Exercise app passcode/autolock and blocked screenshots in every workspace
8. Kill the app process from Android developer tools, restore from Recents;
   verify restored destinations and drafts, without depending on live fragments

## Two-conversation workspace

- Open workspace from a chat, choose a different second conversation
- Confirm messages, typing, scroll, selection and drafts remain independent
- Send a harmless test message explicitly in each pane; verify actual recipient
- Change active pane, open keyboard, switch panes and dismiss keyboard
- Fold to narrow window and switch active pane, then unfold again
- Back navigates only the active pane before exiting the workspace
- Opening transfers ownership of the original conversation into the workspace;
  closing returns to the underlying launcher stack, without reviving a stale composer
- Open picker then Cancel/Back; repeat picker and select another chat
- Try same conversation and different account; verify guarded behavior
- Delete/leave a visible conversation elsewhere; ensure targeted closure works
- Open photo viewer in one pane and dismiss; both panes remain usable

## Drag and drop

- Drag unprotected, already sent text and photo to the other pane
- Verify forwarding/composition preview appears and nothing is sent on drop
- Cancel preview, drag again, explicitly Send, verify recipient and content
- Drop ordinary text from another app into an empty and nonempty draft
- Drop a single content-URI image; inspect preview then Cancel and retry
- Try inaccessible URI, remote URL, file URI, unsupported MIME, multiple items,
  cancelled drag, dropped outside pane and drop while activity is pausing
- Test protected-source messages, secret chats, unsent/failed messages,
  media-restricted targets, cross-account data and active edit/reply/forward state
- Verify no silent draft loss and no content exposure to external apps
- Verify temporary copied image cleanup after cancel and lifecycle teardown

## Verification status

Update the delivery report with exact commands and pass/fail/blocked outcomes.
Never relabel source inspection, syntax parsing or a setup-only build as device
behavior verification. Record APK SHA-256 and signing-certificate SHA-256 for any
APK provided; identify development signing explicitly.

## Preview feature boundaries

- The two-chat workspace uses real independent conversation instances in one app
  window. Widths of at least 600dp show both panes; narrower windows use focus
  buttons. Each pane has its own navigation and draft state
- The initial drag implementation accepts external plain text and one JPEG, PNG
  or WebP image (20 MB and 32 million pixels maximum). It does not export content
  to other apps
- Internal dragging supports one already-sent ordinary text/photo message at a
  time, within the same account and into a different conversation. Albums,
  secret/protected/sensitive/ephemeral and unsent messages are excluded
- Choose Drag in the message menu, then hold that message again to start dragging
- New controls are experimental English UI; translation coverage is incomplete
- Drops never send immediately. The user must explicitly send the resulting
  draft, forwarding preview or photo preview


## Focused debug diagnostics

The debuggable client emits geometry/state-only Android logcat records under
`FoldegramWindow`. It does not enable Telegram's verbose network/authentication
logging or log message content, dialog IDs, phone numbers, or API configuration.
Release builds omit this channel. With an already-authorized developer device:

    adb logcat -s FoldegramWindow:D '*:S'

Reproduce only with a test conversation and review the captured output before
sharing. Host state-transition tests do not replace these actual device traces.
