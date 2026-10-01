# Foldegram verified source checkpoint — October 1, 2026

This is a source checkpoint, not a completed or device-tested APK release.

## Passed

- Official upstream 12.10.6 baseline commit verified in the checkout
- Arm64 native build: `:TMessagesProj:externalNativeBuildDebug` with explicit
  credentials-free compile-only mode (Gradle 8.13, JDK 21, SDK 36, NDK 27.2)
- Native ELF LOAD segments inspected: alignment 0x4000 (16KB)
- Real modified library Java compilation
- Full app Java compilation and aggregate Android lint completed successfully
  together: 0 lint errors, 32 warnings remain
- Real debug merged manifest: package `dev.foldegram.messenger`, separate content
  provider authorities, Foldegram icon/label, backup disabled, no FCM initializer
  or billing permission
- 7 host lifecycle/routing tests; 2 drop-policy test suites; 11 workspace checks
- 21 build/API/signing guard cases, including no placeholder APK packaging
- Complete ordered patch series replayed against a clean upstream worktree;
  resulting Git tree matched the development source exactly
- Geometry-only logcat call verified in compiled LaunchActivity bytecode
- Source identity/XML checks and `git diff --check`

Host tests execute extracted methods or pure geometry with doubles; they do not
exercise Android rendering, OS drag delivery, runtime permission prompts or a
real Telegram account. The candidate lifecycle change is not a confirmed fix of
the reported Pixel freeze.

## Pending / not verified

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

## Lint warnings retained

The final lint run has no errors and 32 warnings: 2 version-dependent attributes,
2 inherited selected-photo-access warnings, 18 branding translation declarations,
1 data-extraction-rules warning, 8 unused resources, and 1 monochrome-icon warning
(the Android 13-specific adaptive icon already includes a monochrome layer).
These are not a claim of production readiness. Four intentionally removed
Firebase component declarations use narrowly scoped MissingClass annotations;
the merged manifest was separately checked to verify those components are absent.

## Repeating the verified checks

With JDK 21 and the documented SDK/NDK versions installed:

    ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g' :TMessagesProj_AppFoldegram:compileDebugJavaWithJavac :TMessagesProj_AppFoldegram:lintDebug -PfoldegramCompileOnly=true
    ./gradlew --no-daemon --max-workers=2 :TMessagesProj:externalNativeBuildDebug -PfoldegramCompileOnly=true
    python3 Tools/tests/test_foldable_layout.py
    python3 Tools/tests/test_chat_drop_policy.py
    python3 Tools/test_foldegram_workspace.py
    python3 Tools/test_foldegram_identity.py
    python3 Tools/test_foldegram_credentials.py /path/to/gradle-8.13/bin/gradle

Use the bounded Gradle heap/workers options when memory is limited. Allow at
least 20–25 GB of free workspace for sources, tools, caches and native outputs.
The tests are build/source checks, not a substitute for the device matrix.
