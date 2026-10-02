<h1 align="center">
  <img src="docs/foldegram/wordmark.svg" width="480" alt="Foldegram — origami crane and Bricolage Grotesque wordmark">
</h1>
<p align="center"><strong>More room for conversation.</strong><br>A foldable-focused Telegram client for Android.</p>
<p align="center">
  <a href="docs/BUILD_FOLDEGRAM.md">Build guide</a> ·
  <a href="docs/foldegram/PREVIEW_6.md">Preview notes</a> ·
  <a href="LICENSE">License</a>
</p>

Foldegram brings two conversations into one workspace, with quick chat switching and drag-and-drop previews built around Android's native Telegram interface. It uses the Telegram service and builds on Telegram for Android **12.10.6**.

**Unofficial and experimental.** Foldegram is an independent fork, not affiliated with or endorsed by Telegram. Its crane identity and separate Android package (`dev.foldegram.messenger`) let it coexist with the official app.

## What makes it Foldegram?

| Area | Official Telegram foundation¹ | Foldegram's additions |
| --- | --- | --- |
| Workspace | Native phone and tablet navigation | Two live chat panes on wide screens; an active pane on narrow screens, with fold/resize lifecycle work |
| Switching chats | Chat list and search | Persistent avatar rail and the same searchable home list; Split opens a right-side chat picker; drag a conversation onto either pane |
| Sharing into a chat | Native attachment, media-preview and sending flows | External text/image drops into an ordinary chat or workspace pane; a single image offers **Photo · compressed** or **File · original quality**, followed by preview and explicit Send |
| Moving between chats | Native forwarding | Internal text/photo forwarding previews between panes, retaining content restrictions |
| Look and feel | Native chat views, headers and transitions | Original origami-crane branding, rounded panes, a small gutter, soft hover outlines and subtle acceptance feedback |

¹ Comparison is with the [pinned Telegram 12.10.6 source](https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242), not a claim about every current official client or platform.

Drops do not send automatically. Account, lock, destination permissions and protected-content checks remain in place. Secret/protected-chat and passcode screenshot restrictions are preserved. Added sidebar and acceptance animations honor the app's animation setting.

## Preview status

**Dev6 is a development preview, not a production release.** See the [preview notes](docs/foldegram/PREVIEW_6.md) for validation and device-test limits.

These checks are **not device verification**. Screenshot behavior, Google Photos delivery, folding, edge gestures and animation feel still need physical-device testing. See the [latest findings and phone checklist](docs/foldegram/PREVIEW_6.md).

Service integration is incomplete: push notifications may be delayed, embedded Google Maps is unconfigured, and several official-app sign-in and billing integrations are disabled. Development APKs use a publicly known test signing key and are unsuitable as maintained production releases. Details are in the [build guide](docs/BUILD_FOLDEGRAM.md).

## Build and contribute

Use the [Foldegram build guide](docs/BUILD_FOLDEGRAM.md) for the toolchain, your own Telegram API configuration and signing requirements. Keep credentials out of source control. Development lives on [`foldegram/continuous-home-dev5`](https://github.com/SAM2WOW/Foldegram/tree/foldegram/continuous-home-dev5).

Bug reports are most useful with the device, Android version, folded/unfolded state and steps to reproduce. Please omit private messages and credentials.

## Credits and license

Built on [Telegram for Android](https://github.com/DrKLO/Telegram), by Nikolai Kudashov and its contributors. The core source is licensed under **GNU GPL v2 or later**; see [LICENSE](LICENSE) and the notices in individual files. Third-party components retain their respective licenses. The original Foldegram crane is included under the same GPL-2.0-or-later terms.

Preserve upstream notices and provide corresponding source when distributing builds, as required by the applicable licenses. Upstream references: [original README](https://github.com/DrKLO/Telegram/blob/f2908b14133bbffbf7ab04f641ecb5bfaf533242/README.md) · [Telegram API](https://core.telegram.org/api) · [developer security guidelines](https://core.telegram.org/mtproto/security_guidelines).

The wordmark uses [Bricolage Grotesque](https://github.com/google/fonts/tree/main/ofl/bricolagegrotesque) by its project authors, under [SIL OFL 1.1](TMessagesProj/src/main/assets/fonts/BricolageGrotesque-OFL.txt). [Typography source and generation notes](docs/foldegram/WORDMARK.md).
