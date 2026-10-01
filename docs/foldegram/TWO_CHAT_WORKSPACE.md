# Two-chat workspace

Open a normal conversation, then use **Open second chat** in its overflow menu and pick a different conversation. This opens two actual `ChatActivity` instances inside one Activity, with separate instance-owned navigation stacks. This is not the upstream tablet chat-list/detail layout and does not rely on Android split-screen launch behavior.

- At a measured content width of at least 600dp, both conversations are side by side
- Below 600dp, one conversation is shown; the two title buttons switch focus without destroying either stack
- Tap a visible pane to focus it. Back goes to the focused stack. Back at its root closes the workspace
- The replace icon, or a long press on a pane title, picks a different conversation for that pane
- Only the focused pane is resumed and owns input/read state. The other conversation remains visible and subscribed to updates
- Opening transfers ownership of the original chat into the workspace; closing returns to the underlying chat list. This prevents an old hidden composer from overwriting the newer draft
- Pending edit, recording, unsent audio/video, attachment sheet or forwarding state must be finished or canceled first. Text/reply drafts use Telegram's existing per-dialog/topic draft storage
- Both panes share the chosen account; account-slot reuse and logout close the workspace
- Passcode protection covers both panes. The window's screenshot protection composes with each conversation's security requirements

## Limits

This is an implementation requiring real Android/foldable validation. Ordinary chats and loaded forum topics are supported. Scheduled/saved-message subviews and comment threads are not offered as source views. A topic that is no longer available in the account's in-memory topic cache after process restoration is rejected instead of silently opening the wrong destination. Transient nested profile/picker screens are not restored after process death; the last restorable conversation is restored instead. Folding/resizing preserves the actual mounted fragments and their transient state.

## Verification

`python3 Tools/test_foldegram_workspace.py` checks source integration/security boundaries and executes the real pure-Java geometry policy when a JDK is on PATH. These are not device lifecycle tests.

Real-device checks still required:

1. Write distinct drafts in A and B. Repeatedly focus each pane and verify no text moves between them
2. Fold/unfold and rotate with text, emoji keyboard, a photo preview, and a nested profile visible. Check both drafts, scroll positions, and focused pane
3. Edit the source chat's draft inside the workspace, close it, and reopen that chat from the list. Verify the newer draft survives
4. Replace either pane several times; cancel a picker and press Back at different depths
5. Enable passcode, background the app until timeout, return and unlock. Repeat while changing theme and when another app window performs the unlock
6. Open a protected/secret chat alongside a normal chat. Change passcode/screenshot settings and confirm protected content remains secure
7. Start a media picker from A, return its result, and verify it routes to A. Repeat with Android process recreation and verify the saved pane destination
8. Drag a supported internal message to the other visible pane, cancel the preview, repeat and explicitly send. Lock/background/navigate during external-image import and verify stale callbacks are discarded
9. Log out and log in to a different account in the same slot; the old workspace must not reopen its conversations

No runtime or device QA is claimed by these source tests.
