# Build Foldegram

Foldegram is an unofficial, experimental fork of Telegram for Android, based on
upstream commit `f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6).
It is not affiliated with Telegram. This guide describes configuration and build
commands, not a claim that a device or end-to-end messaging test has passed.

## Supported target

- Module: `TMessagesProj_AppFoldegram`
- Android application ID: `dev.foldegram.messenger`, independent of official Telegram
- Android account type and contact MIME types are isolated to the same ID
- ARM64 (`arm64-v8a`) client, Android 6.0+; target/compile SDK 36
- Development signing uses the already checked-in, publicly known upstream dummy key
- Launcher: original folded-paper crane; vector master in `foldegram/origami-crane.svg`
- Adaptive and Android 13 monochrome launcher assets are included

Only the Foldegram application module is included in Gradle settings. The upstream
application modules and their example service configurations remain as source
references, but are not built or packaged into Foldegram.

## Prerequisites

Use JDK 21, the checked-in Gradle 8.13 wrapper, Android SDK platform 36,
build-tools 36.0.0, CMake 3.22.1, and NDK 27.2.12479018. Initialize the pinned
submodules with `git submodule update --init --recursive`. Accept Android SDK
licenses yourself, then install the required SDK packages through the official
Android tools. Configure `ANDROID_HOME` or the ignored `local.properties` file.
Do not replace pinned submodule revisions with their latest branches.

The main client requires the full native Telegram library toolchain. The explicit
setup-only build below does not compile or link that library.

## Private Telegram API configuration

The real client requires a separately obtained API ID and API hash from
[Telegram's application page](https://my.telegram.org/apps). See the
[official API-ID instructions](https://core.telegram.org/api/obtaining_api_id).
Credential/account creation is a user-controlled step; this project does not
create or retrieve credentials for you.

Choose one of these routes:

1. Set `FOLDEGRAM_API_ID` and `FOLDEGRAM_API_HASH` as environment variables in a
   private local shell or build secret store, without putting values in shell
   command arguments, chat, issue trackers, or checked-in configuration
2. Copy `foldegram.properties.example` to `foldegram.properties`, restrict file
   access (for example `chmod 600 foldegram.properties`), and edit the values locally

Environment variables take precedence. The private file is git-ignored. Never add
it to a source archive. Do not put secrets in `gradle.properties`, `-P` command
arguments, or `BuildVars.java`. Missing/malformed configuration and upstream's
example API ID are rejected with an actionable message. Values are not printed.

An Android APK necessarily contains the application's API ID/hash. This setup
keeps credentials out of source control and routine build logs; it does not make
an APK an appropriate place for a server-side secret. Do not publish build caches,
generated `BuildConfig.java`, or local build output directories as source.

## Full debug client

```sh
./gradlew verifyFoldegramCredentials
./gradlew :TMessagesProj_AppFoldegram:assembleDebug
```

Expected output:
`TMessagesProj_AppFoldegram/build/outputs/apk/debug/foldegram-arm64-debug.apk`

After verifying the APK's signature and package name, install it on an authorized
connected device with `adb install -r <apk-path>`. It coexists with official
Telegram. Neither commands nor source changes prove fold/unfold correctness;
follow the separate device-validation checklist before relying on the fork.

Debug Java remains debuggable; native debugging is disabled in the APK to allow
native symbol stripping. The dummy signing key is public and is suitable only for local development. No
new signing key or credential has been provisioned. Distribution requires the
user-owned release-signing configuration below and an update plan. Changing the signing
key later generally requires uninstalling this debug package, losing its local data.

## Distribution build with an existing private signing key

Never distribute the public-key debug APK as your maintained release. Anyone can
use that public key. Keep a stable, privately controlled signing identity for all
updates to the same application ID.

The optional `release` variant requires all four settings below, through private
environment variables or the ignored `foldegram.properties` file:

- `FOLDEGRAM_KEYSTORE_PATH`: existing user-owned keystore, preferably outside this checkout
- `FOLDEGRAM_KEYSTORE_PASSWORD`: its private store password
- `FOLDEGRAM_KEY_ALIAS`: the existing signing alias
- `FOLDEGRAM_KEY_PASSWORD`: that alias's private key password

Do not paste any password into command arguments, chat, logs, or source. Do not
upload the keystore or commit it. No tool here generates a key or falls back to
the public debug key for release. Missing settings, a missing file, and selecting
the checked-in dummy keystore are rejected. This checks configuration only; it
cannot establish key ownership or validate a signing certificate without the
actual user's local keystore. Do not copy the dummy key to a different path.

Once you have privately configured your API values and existing signing key:

```sh
./gradlew verifyFoldegramCredentials verifyFoldegramReleaseSigning
./gradlew :TMessagesProj_AppFoldegram:assembleRelease
```

Expected output:
`TMessagesProj_AppFoldegram/build/outputs/apk/release/foldegram-arm64-release.apk`

This route has not been signed or certificate-verified here. Release enables R8
minification and resource shrinking with the upstream ProGuard rules; those
optimizations require a successful local build plus real-device regression tests.
Verify the final certificate fingerprint, package ID, permissions, native library
alignment, and behavior before sharing. Preserve your private key and backup plan:
a different signing key usually cannot update an already installed build.
If you do not have a private signing identity, create/manage it yourself using
the official Android tools; do not substitute this repository's public key.

## Explicit offline setup APK

```sh
./gradlew :TMessagesProj_AppFoldegram:assembleDebug -PfoldegramStubCredentials=true
```

Expected output:
`TMessagesProj_AppFoldegram/build/outputs/apk/debug/foldegram-setup-debug.apk`

This is only an installation/branding check. It shows an honest setup screen and
has no Telegram library dependency, no Telegram initialization, no login, and no
network permission. It does not exercise chats, foldable layouts, notifications,
or two-chat behavior. The API ID/hash are not included. It uses the same application
ID and development key as the real debug client, so the two replace each other.
Do not describe or distribute it as a working messaging client.

## Compile checks without credentials

```sh
./gradlew :TMessagesProj_AppFoldegram:compileDebugJavaWithJavac -PfoldegramCompileOnly=true
./gradlew :TMessagesProj_AppFoldegram:processDebugMainManifest -PfoldegramCompileOnly=true
./gradlew :TMessagesProj_AppFoldegram:lintDebug -PfoldegramCompileOnly=true
```

This explicit validation mode forces API ID 0/hash empty and exposes the real debug
app variant for Java/resource compilation, manifest merging and lint. A task-graph
guard rejects APK/AAB packaging, signing, assemble, AAR bundle, install and publish
tasks, including direct `packageDebug` invocations and Android-test APK packaging.
Library resource packaging and lint's internal `LocalLintAar` snapshots remain
allowed because compiler/lint analysis needs them; normal distributable AAR bundles
are blocked. Release
variants remain disabled in this mode. Do not run or redistribute generated class
files. Compiler/lint success is not runtime/device validation.
`Tools/test_foldegram_credentials.py <gradle-path>` separately tests the actual
credential guard offline with synthetic fixtures and no Android SDK.
`python3 Tools/test_foldegram_identity.py` checks source manifest/account/provider
isolation; it does not replace inspection of the final merged APK manifest.

## Deliberately unconfigured integrations

- No upstream API ID/hash, Google Services JSON, Google/Huawei service key,
  Google sign-in client ID, or official SMS signing hash is used by this target
- Firebase automatic initialization/collection and FCM token registration are
  disabled. Background notification delivery may be delayed without push,
  particularly under Android battery restrictions
- Google Maps has no configured API key; embedded Google maps are not operational
- Official-app passkeys, Google sign-in shortcuts, SafetyNet/Firebase SMS flows,
  Google Play billing, and upstream automatic app replacement are disabled
- Upstream merchant/bot invoice flows remain upstream functionality; they have
  not been validated or advertised as supported payment integrations
- Verbose upstream debug and private-debug logging are disabled even in the
  debuggable build, to avoid default authentication-token logging
- Existing account backups are disabled for this experimental app

Any future vendor integration needs the fork's own configuration, a review of
package/signing restrictions, and real-device testing. Do not fix an integration
by copying official Telegram credentials into it.

## Identity and license notes

The core `org.telegram` Java namespace is deliberately retained to minimize the
upstream diff; this is distinct from Android's installed application ID.
Launcher aliases retain upstream class names needed by the icon-selector code,
but every alias resolves to Foldegram's crane. Some protocol content and upstream
screens still refer to the Telegram service. The fork is not a full textual rebrand.

The crane vector is original work included under this repository's GPL-2.0-or-later
terms. Preserve Telegram's copyright and license notices, and provide corresponding
source and changes alongside any distributed build, as required by the GPL.
