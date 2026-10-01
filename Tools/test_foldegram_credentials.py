#!/usr/bin/env python3
"""Exercise the actual Gradle credential guard offline with non-secret test fixtures.

Pass a Gradle 8.13 executable path. No Android SDK or network access is required.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
GRADLE = sys.argv[1] if len(sys.argv) > 1 else "gradle"
FIXTURE_HASH = "0123456789abcdef0123456789abcdef"

with tempfile.TemporaryDirectory(prefix="foldegram-config-test-") as directory:
    work = Path(directory)
    (work / "settings.gradle").write_text("rootProject.name = 'foldegram-config-test'\ninclude ':TMessagesProj_AppFoldegram', ':TMessagesProj'\n")
    app_fixture = work / 'TMessagesProj_AppFoldegram'
    app_fixture.mkdir()
    (app_fixture / 'build.gradle').write_text("""
        tasks.register('compileDebugJavaWithJavac') { dependsOn ':TMessagesProj:packageDebugResources' }
        tasks.register('processDebugMainManifest') {}
        tasks.register('packageDebugResources') {}
        ['packageDebug', 'packageDebugAndroidTest', 'validateSigningDebug', 'bundleDebug', 'signDebugBundle'].each { name ->
            tasks.register(name) { doLast { throw new GradleException('Forbidden task actually executed') } }
        }
    """)
    library_fixture = work / 'TMessagesProj'
    library_fixture.mkdir()
    (library_fixture / 'build.gradle').write_text("""
        tasks.register('packageDebugResources') {}
        tasks.register('bundleDebugLocalLintAar') {}
        tasks.register('bundleDebugAar') { doLast { throw new GradleException('Forbidden distributable AAR task actually executed') } }
        tasks.register('packageDebugAndroidTest') { doLast { throw new GradleException('Forbidden library test APK task actually executed') } }
    """)
    (work / "build.gradle").write_text(
        "apply from: " + repr(str(ROOT / "gradle/foldegram-credentials.gradle")) + "\n"
        + "tasks.register('assembleGuardProbe') {}\n"
        + "tasks.register('assertValidationPlaceholders') { doLast { assert rootProject.ext.foldegramApiId == 0; assert rootProject.ext.foldegramApiHash == '' } }\n"
    )
    cases = [
        ("missing credentials", {}, [], False),
        ("official example ID", {"FOLDEGRAM_API_ID": "4", "FOLDEGRAM_API_HASH": FIXTURE_HASH}, [], False),
        ("invalid ID", {"FOLDEGRAM_API_ID": "2147483648", "FOLDEGRAM_API_HASH": FIXTURE_HASH}, [], False),
        ("invalid hash", {"FOLDEGRAM_API_ID": "123456", "FOLDEGRAM_API_HASH": 'bad"\\n'}, [], False),
        ("valid syntax", {"FOLDEGRAM_API_ID": "123456", "FOLDEGRAM_API_HASH": FIXTURE_HASH}, [], True),
        ("explicit setup mode", {}, ["-PfoldegramStubCredentials=true", "assertValidationPlaceholders"], True),
        ("compile-only guard", {}, ["-PfoldegramCompileOnly=true", "assertValidationPlaceholders"], True),
        ("app Java/manifest, library resources and internal lint AAR allowed", {}, ["-PfoldegramCompileOnly=true", ":TMessagesProj_AppFoldegram:compileDebugJavaWithJavac", ":TMessagesProj_AppFoldegram:processDebugMainManifest", ":TMessagesProj_AppFoldegram:packageDebugResources", ":TMessagesProj:bundleDebugLocalLintAar"], True),
    ]
    for name, overrides, arguments, expected in cases:
        env = {k: v for k, v in os.environ.items() if not k.startswith("FOLDEGRAM_")}
        env.update(overrides)
        env["GRADLE_USER_HOME"] = str(work / "gradle-home")
        result = subprocess.run(
            [GRADLE, "--offline", "--no-daemon", "--max-workers=2", "--console=plain", "verifyFoldegramCredentials", *arguments],
            cwd=work, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        assert (result.returncode == 0) == expected, f"{name}: unexpected result\n{result.stdout}"
        assert FIXTURE_HASH not in result.stdout, f"{name}: credential appeared in output"
        if not expected:
            assert "Foldegram requires your own" in result.stdout, f"{name}: missing useful error\n{result.stdout}"
        print(f"PASS: {name}", flush=True)

    for name, arguments, expected_message in [
        ("compile-only packaging rejected", ["-PfoldegramCompileOnly=true", "assembleGuardProbe"], "for compiler/lint validation only"),
        ("mutually exclusive modes", ["-PfoldegramCompileOnly=true", "-PfoldegramStubCredentials=true"], "not both"),
        *[(f"direct {task} rejected", ["-PfoldegramCompileOnly=true", f":TMessagesProj_AppFoldegram:{task}"], "Blocked task:")
          for task in ("packageDebug", "packageDebugAndroidTest", "validateSigningDebug", "bundleDebug", "signDebugBundle")],
        ("library test APK rejected", ["-PfoldegramCompileOnly=true", ":TMessagesProj:packageDebugAndroidTest"], "Blocked task:"),
        ("distributable library AAR rejected", ["-PfoldegramCompileOnly=true", ":TMessagesProj:bundleDebugAar"], "Blocked task:"),
    ]:
        result = subprocess.run(
            [GRADLE, "--offline", "--no-daemon", "--max-workers=2", "--console=plain", "verifyFoldegramCredentials", *arguments],
            cwd=work, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        assert result.returncode != 0 and expected_message in result.stdout, f"{name}: unexpected result\n{result.stdout}"
        print(f"PASS: {name}", flush=True)

    # These are inert text files for path validation, not generated signing keys.
    owned_path = work / "existing-fixture.jks"
    owned_path.write_text("NOT A KEYSTORE: path-validation fixture only")
    dummy_path = work / "TMessagesProj/config/release.keystore"
    dummy_path.parent.mkdir(parents=True)
    dummy_path.write_text("NOT A KEYSTORE: public-path rejection fixture only")
    complete = {
        "FOLDEGRAM_KEYSTORE_PATH": str(owned_path),
        "FOLDEGRAM_KEYSTORE_PASSWORD": "synthetic-store-password",
        "FOLDEGRAM_KEY_ALIAS": "synthetic-alias",
        "FOLDEGRAM_KEY_PASSWORD": "synthetic-key-password",
    }
    for name, config, expected_message in [
        ("release signing missing", {}, "Release signing requires"),
        ("release keystore missing", {**complete, "FOLDEGRAM_KEYSTORE_PATH": str(work / "absent.jks")}, "does not exist"),
        ("public signing path rejected", {**complete, "FOLDEGRAM_KEYSTORE_PATH": str(dummy_path)}, "publicly known upstream"),
        ("release configuration path validated", complete, None),
    ]:
        clean_env = {k: v for k, v in env.items() if not k.startswith("FOLDEGRAM_")}
        clean_env.update(config)
        result = subprocess.run(
            [GRADLE, "--offline", "--no-daemon", "--max-workers=2", "--console=plain", "verifyFoldegramReleaseSigning"],
            cwd=work, env=clean_env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        assert (result.returncode == 0) == (expected_message is None), f"{name}: unexpected result\n{result.stdout}"
        if expected_message is not None:
            assert expected_message in result.stdout, f"{name}: missing error\n{result.stdout}"
        assert "synthetic-store-password" not in result.stdout and "synthetic-key-password" not in result.stdout
        print(f"PASS: {name}", flush=True)
