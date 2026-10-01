# Maintaining Foldegram against Telegram

## Baseline and ownership

Upstream: https://github.com/DrKLO/Telegram

Baseline commit: `f2908b14133bbffbf7ab04f641ecb5bfaf533242`

Baseline app version: 12.10.6 (7112)

The `upstream-12.10.6` branch preserves the unmodified source baseline. Work lives
on `foldegram/pixel-fold-preview`. No public repository has been created and no
changes have been pushed as part of this local work.

Keep logical changes separate:

1. Window/lifecycle state refresh
2. Foldegram identity, isolated services/configuration and private build inputs
3. Independent workspace and staged drag/drop integration, including live-fragment
   migration and foreground routing corrections
4. Safe geometry diagnostics, validation tooling and build/test documentation

Apply the complete ordered series. The feature integration patch also hardens
pane migration; cherry-picking the first cache refresh patch is not the full fix.

## Reproducible patch export

After committing reviewed changes locally:

    git format-patch --output-directory ../foldegram-patches upstream-12.10.6..HEAD
    git bundle create ../foldegram-source.bundle upstream-12.10.6 foldegram/pixel-fold-preview

A bundle of this shallow checkout may require a full baseline fetch before it
can be distributed as a standalone clone. The source archive plus ordered
patches is an alternative; include submodule commit IDs and their upstream URLs.
Never include local API configuration, generated BuildConfig with private API
values, signing credentials, account databases, or Gradle logs containing secrets.

## Taking a later upstream release

Work on a new integration branch; preserve a known working branch and APK.

    git fetch origin --tags
    git switch -c foldegram/integrate-<version> <verified-upstream-commit>
    git submodule update --init --recursive
    git am ../foldegram-patches/*.patch

Resolve conflicts against the new upstream behavior, not by blindly preferring
old Foldegram code. Review upstream fixes for lifecycle, multiwindow, forwarding,
protected-content policy, media permissions and account handling before retaining
our patches. Update the recorded baseline only after validation.

## Required acceptance checks

- Build the arm64 development APK with privately supplied app credentials
- Check manifest/package/account authorities remain isolated from Telegram
- Review permission and network/service configuration changes
- Run all available source/unit/build checks, then the device test matrix
- Re-test cancellation, process death, passcode, fold/unfold, both drafts and send
  destination after every update affecting navigation or ChatActivity
- Review patch order and confirm no private build input appears in history

## Distribution

Telegram's source is GPL-licensed; preserve license and copyright notices and
supply corresponding source for distributed binaries. Foldegram is unofficial,
uses its own icon and application identity, and must use its own Telegram API
application registration. Development signing is for personal testing, not a
production release/update security policy. Remote publication and production
signing need a separate explicit decision.
