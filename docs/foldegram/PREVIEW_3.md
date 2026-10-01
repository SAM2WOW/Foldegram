# Foldegram preview 3

Fixes the earlier multicolored Foldegram crane briefly shown on startup. The inherited light Android 12+ and dark startup themes explicitly reference `tg_splash_320`, independently of the launcher icon. The app-level override still contained the earlier nine-path multicolored crane. It now uses a static navy disc and the exact approved six crane paths from preview 2. The disc fits the platform's two-thirds safe circle. Older startup themes use plain window backgrounds; launcher aliases and the platform fallback already resolve to the Foldegram launcher. No startup theme behavior or crane geometry is redesigned.

Resource inspection can verify the packaged theme references and vector paths. It cannot prove cold/warm-launch timing or launcher caching on a phone. No device was attached for this revision.

## Bubbles

There is no additional in-app bubbles toggle. `SharedConfig.chatBubbles` defaults to true on Android 11+ and has no preference loader or UI setter in this source. The notification builder adds bubble metadata to ordinary conversation notifications; broadcast channels and story notifications are excluded.

On the Pixel, allow system bubbles and Foldegram notification/bubble permissions. Ensure the desired chat is unmuted (open the conversation menu and select **Unmute** if it is muted), then receive a new message while outside that chat. Expand its notification and use Android's bubble control if offered. If Android is set to selected conversations, allow that conversation to bubble. The client does not automatically expand bubbles (`setAutoExpandBubble(false)`). No separate Foldegram "open as bubble" command is required.

Preview 2's drag, branding and bubble lifecycle changes remain. Bubble freeze resolution and physical-device cold/warm launch behavior remain unverified.
