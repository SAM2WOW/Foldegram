# Foldegram preview 5 — continuous home

## One mounted workspace

On foldables and tablet home layouts, the conversation list and existing chat stay in LaunchActivity. The workspace action expands that same list to choose a second chat. Selection adds a second independent navigation stack, slides it in from the right, moves the existing chat left and collapses the list into a persistent 56dp rail. The original list and first chat are never copied into another Activity by this path.

The rail contains the approved crane, a selector for each conversation and a close-two-chats action. The crane expands/collapses the original list. An inward swipe starting outside Android's Back gesture inset also expands it. List search/scroll and chat views stay attached. On smaller windows, expansion gives space to the list and active chat rather than squeezing three unreadable columns; the other pane stays mounted. Dragging a conversation temporarily collapses the list to expose the chat targets. Cancelling restores its expansion state, while an accepted drop changes only the destination stack. Forum topics still use tap selection.

Back closes viewers/popups through native handling, collapses an expanded list, or navigates the active pane. At a pane root it closes the additional pane and restores list + original chat. Explicitly closing the second pane ends its navigation stack; ordinary folding, resizing and list toggling do not. The active second chat and its saved self state can be restored after Activity recreation; its entire prior navigation history is not serialized. Account changes discard the extra stack. External media-picker results are routed to their initiating pane; losing that owner during Activity recreation cancels the result rather than sending it to a different pane, so the picker must be reopened.

Measured window width controls the layout, with a 600dp two-pane threshold and 8dp gutter. Fold/resize does not migrate or rebuild these stacks. Existing bubble/window-metric corrections remain in place. The additional pane uses native non-bubble insets: backgrounds extend behind the status bar while native chat controls retain their insets. Geometry changes animate for 220ms when app animations are enabled. No persistent blur layer is added.

## Header crane alignment

The collapsed-stories header had retained a 90 × 22dp wordmark box around a small crane. It now uses 26 × 26dp bounds aligned beside the story avatars and centered to the action-bar height. The ordinary title uses a centered image span instead of baseline alignment. Small translations compensate for the approved mark's visible polygon bounds; the SVG/vector artwork is unchanged and is not mirrored in RTL. The rail uses the same mark in a 48dp touch target.

The supplied header screenshot could not be materialized: the Library transfer helper returned `download failed` on two attempts. These are source-grounded corrections, not pixel verification against that screenshot. No new font or wordmark has been chosen or implemented.

## Validation and limits

Host checks exercise window geometry through repeated folds, narrow/wide sizes and expansion; repeated second-chat open/close and Back; original pane identity/draft preservation; drag ownership/cancellation boundaries; and existing bubble, inset, capture and drop-policy regressions. They use production Java geometry/methods with test doubles plus source assertions, not Android touch or rendering instrumentation.

No device is attached. The following still require phone testing:

- Open/close a second chat repeatedly while preserving first-chat scroll, drafts and list search position; verify the new chat comes from the right without changing Activity.
- Fold/unfold and resize with either pane active, list expanded/collapsed and keyboard open. Verify only visible focused content reads/responds and keyboards do not cover controls.
- Back through search, composer popup, native preview and each pane's history; close the extra pane and verify the original conversation survives.
- Drag a conversation onto each pane, cancel outside targets, lock/background during a drag, and change accounts. Verify the untouched pane does not change and no messages send.
- Repeat external Google Photos Photo/File previews and internal text/photo forwarding; preserve explicit Send and protected-content restrictions.
- Open a bubble conversation in the full app, then enter/exit two chats and fold. Check window bounds and navigation targets.
- Check status-bar wallpaper/background continuity, light/dark icon contrast, cutouts, landscape system bars, RTL, large text, and crane alignment with collapsed stories.

This remains a private development preview using the existing public dummy test certificate. It is not a production signing or public APK release. Master is not updated for this revision.
