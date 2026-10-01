# Foldegram verification checkpoint — October 1, 2026

This is a source checkpoint, not a completed or device-tested APK release.

## Passed

- Official upstream 12.10.6 baseline commit verified in the checkout
- Arm64 native build: `:TMessagesProj:externalNativeBuildDebug` with explicit
  credentials-free compile-only mode (Gradle 8.13, JDK 21, SDK 36, NDK 27.2)
- Native ELF LOAD segments inspected: alignment 0x4000 (16KB)
- Real modified library Java compilation
- Full app Java compilation task completed within the ongoing aggregate check
- Real debug merged manifest: package `dev.foldegram.messenger`, separate content
  provider authorities, Foldegram icon/label, backup disabled, no FCM initializer
  or billing permission
- 7 host lifecycle/routing tests; 2 drop-policy test suites; 11 workspace checks
- 21 build/API/signing guard cases, including no placeholder APK packaging
- Source identity/XML checks and `git diff --check`

Host tests execute extracted methods or pure geometry with doubles; they do not
exercise Android rendering, OS drag delivery, runtime permission prompts or a
real Telegram account. The candidate lifecycle change is not a confirmed fix of
the reported Pixel freeze.

## Still running or pending

- Aggregate Android lint was running at this checkpoint; no lint-pass claim
- A small final diagnostic logging change requires another compilation check
- Final functional APK packaging/signature/install verification awaits privately
  supplied app API configuration; no user API value has been collected
- Production release signing requires an existing private key; release R8 and
  resource shrinking have not been exercised
- All actual device/fold/unfold, login, dual-chat, drag, cancel, passcode, media,
  notifications and send-destination tests remain unrun

## Important scope limits

- Cross-app drops are inbound only; exporting content to another app is absent
- One text/photo message or one external supported image per drag, subject to
  the documented content and size guards
- New controls are experimental English UI
- No Firebase push or configured Google Maps integration
- No GitHub repository or public release has been published

See BUILD_FOLDEGRAM.md, FOLDEGRAM_TESTING.md and foldegram/TWO_CHAT_WORKSPACE.md.
