# Foldegram preview 6

## Clear split selection, in the same home

Pressing Split immediately mounts a native searchable **Choose a chat** picker in the right pane. The current chat moves left and the original home list collapses into its existing rail. Selecting a conversation replaces the picker in that pane; it does not launch another Activity or recreate the original chat. Repeated Split taps focus the existing picker rather than creating more pickers. Back uses native search/topic navigation first, then cancels the root picker and restores the original layout. Existing edit/recording/forward-preview conflicts show a notice.

Dev5 expanded the already-visible home list on wide screens, which could make Split appear inert. The explicit right-pane picker removes that ambiguity. Duplicate-chat validation now reads constructor arguments before a new ChatActivity has resolved its dialog ID, preventing an uninitialized ID from bypassing the opposite-pane check.

## Restrained transitions and spacing

Dev5 interpolated view widths and requested a full layout every animation frame. Dev6 measures the destination layout once, then animates view translation/opacity for 200ms. Text is not scaled, no chat screenshots are captured, and no persistent blur or spring effect is added. Interrupted transitions capture current visual positions before cancellation, so rapid reversals continue from their current position. Window-width or height changes—including fold, split-screen resize and keyboard geometry—cancel stale motion and settle to the new bounds. Pausing also settles motion. Focus synchronization is coalesced.

The visible inter-pane gap is reduced from 8dp to 2dp. A 24dp invisible band around its midpoint still accepts conversation drags and assigns them to the corresponding pane. Rounded native panes remain.

Rail buttons now use Telegram's avatar image loader. Saved Messages uses its bookmark avatar; absent user/group/channel/bot photos use the matching native symbolic avatar instead of an initial. The active pane is distinguished subtly, and buttons retain accessible conversation names and 48dp touch targets.

## Approved typography

Option B is applied: **Bricolage Grotesque 650 / width 100 / optical size 24**. The app name uses this face in the home header, welcome page and Settings/About version label. The collapsed-stories header uses the matching outlined lockup when it fits, with the original crane fallback when space is constrained. The rail remains icon-only. Ordinary chat text, story counts, controls and status text retain native Telegram typography.

The README uses an outlined SVG so the approved face renders without remote font loading. The approved crane paths are unchanged. The original SIL OFL 1.1 copyright/license and the static font instance are packaged in the APK. See [source and generation notes](WORDMARK.md).

## Validation and device limits

49 host tests plus the identity check pass on this source. Java compilation passes. Final APK assembly, lint, signature, asset and alignment verification are recorded with the delivered build.

Host regressions cover repeated opening/closing, immediate picker mounting and repeated Split taps, Back, preserved original stack/drafts, interrupted translation continuity, narrow/wide geometry, resizing cancellation, and drag ownership/cancellation. Existing capture-policy, media-drop, bubble/inset, identity, crane and branding checks remain. Font checks verify weight 650, static compatibility, glyph coverage, original license bytes, lockup crane paths and font scope.

Host doubles/source checks do not measure Android frame timing, RenderThread behavior or actual touch delivery. No device is attached. Sam's dev5 testing informed this revision but does not validate dev6. Phone QA:

- Press Split; verify the right picker appears immediately, search/select/cancel and repeat rapidly. Check nested forum topics, same-chat rejection, drafts and original scroll position.
- Reverse expanded/collapsed layouts during motion; fold/unfold, resize and show/hide the keyboard mid-transition. Verify stable view state, no stranded translations and acceptable frame timing.
- Check real avatars, Saved Messages, user/group/channel/bot fallbacks, active-pane indication and accessibility labels.
- Drag conversations into both panes, across the thin gutter and outside all targets. Test lock/background/account changes and cancellation.
- Repeat bubble-to-full navigation, ordinary/protected screenshot policies, external Photo/File image previews and internal message forwarding. Explicit Send remains required; protected-content restrictions remain.
- Inspect wordmark placement and menu fit in light/dark themes, RTL, large text and collapsed/expanded stories; check welcome and About. Profile actual frame performance before claiming native-quality smoothness.

This is an install-over-dev5 private development build using the existing public dummy test certificate, not a production signing or public APK release. Master remains unchanged.
