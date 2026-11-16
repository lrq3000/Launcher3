#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project
# SPDX-License-Identifier: Apache-2.0
"""Compile actual production Java with explicit JVM boundary doubles, not a mirrored policy."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
JAVA_HOME = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-21"))
TEMP = Path(os.environ.get("LOCALAPPDATA", tempfile.gettempdir())) / "Temp/opencode"
TEMP.mkdir(parents=True, exist_ok=True)
OUT = Path(tempfile.mkdtemp(prefix="launcher3-shortcut-tests-", dir=TEMP))
sources = list((HERE / "stubs").rglob("*.java"))
sources += list((HERE / "src").rglob("*.java"))
sources += [ROOT / "src/com/android/launcher3/model/SdCardAvailableReceiver.java"]
if "--baseline-receiver" in sys.argv:
    baseline = subprocess.check_output(["git", "show", "c612e6ece389f21c40f8cb9cd9a4b44239f00009:src/com/android/launcher3/model/SdCardAvailableReceiver.java"], cwd=ROOT, text=True)
    baseline_file = OUT / "baseline/SdCardAvailableReceiver.java"
    baseline_file.parent.mkdir()
    baseline_file.write_text(baseline, encoding="utf-8")
    sources[-1] = baseline_file
sources += [ROOT / "src/com/android/launcher3/model/tasks/PackageUpdatedTask.java"]
store = ROOT / "src/com/android/launcher3/model/availability/WorkspaceAvailabilityStore.java"
if store.exists():
    sources.append(store)
KOTLIN_HOME = Path(os.environ.get("KOTLIN_HOME", "C:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc"))
classpath = os.pathsep.join([str(OUT), str(KOTLIN_HOME / "lib/kotlin-stdlib.jar")])
kotlin = [ROOT / "src/com/android/launcher3/model/WorkspaceAvailability.kt",
          ROOT / "src/com/android/launcher3/model/ModelLauncherCallbacks.kt",
          ROOT / "src/com/android/launcher3/model/tasks/PackageTaskFactory.kt"]
subprocess.run([str(JAVA_HOME / "bin/java"), "-cp", str(KOTLIN_HOME / "lib/*"),
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-kotlin-home", str(KOTLIN_HOME),
                "-nowarn", "-d", str(OUT), *map(str, kotlin + sources)], check=True)
subprocess.run([str(JAVA_HOME / "bin/javac"), "-encoding", "UTF-8", "-cp", classpath,
                "-d", str(OUT), *map(str, sources)], check=True)
test_args = ["ShortcutReliabilityTest", str(OUT)]
if "--finding" in sys.argv:
    test_args = ["ReviewRegressionTest", sys.argv[sys.argv.index("--finding") + 1], str(OUT)]
subprocess.run([str(JAVA_HOME / "bin/java"), "-ea", "-cp", classpath, *test_args], check=True)
sdk = Path(os.environ.get("ANDROID_SDK_ROOT", "C:/Users/33632/AppData/Local/Android/Sdk"))
api_out = OUT / "android-api-check"
api_out.mkdir()
subprocess.run([str(JAVA_HOME / "bin/javac"), "-proc:none", "-encoding", "UTF-8",
                "-cp", str(sdk / "platforms/android-35/android.jar"), "-d", str(api_out),
                str(store), str(ROOT / "src/com/android/launcher3/model/availability/WorkspaceAvailabilityDb.java"),
                str(HERE / "stubs/com/android/launcher3/LauncherSettings.java")], check=True)
print("PASS SQLite birth helper: Android 35 API type check (not an AOSP build)")
compiler_cp = os.pathsep.join([str(OUT), str(KOTLIN_HOME / "lib/*")])
subprocess.run([str(JAVA_HOME / "bin/javac"), "-proc:none", "-cp", compiler_cp, "-d", str(OUT),
                str(HERE / "tools/KotlinSyntaxCheck.java")], check=True)
changed = subprocess.check_output(["git", "diff", "--name-only", "c612e6ece389f21c40f8cb9cd9a4b44239f00009"], cwd=ROOT, text=True).splitlines()
untracked = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=ROOT, text=True).splitlines()
syntax = sorted({str(ROOT / name) for name in changed + untracked if name.endswith(".kt")})
subprocess.run([str(JAVA_HOME / "bin/java"), "-cp", compiler_cp, "KotlinSyntaxCheck", *syntax], check=True)
print(f"Generated classes and test stores retained at {OUT}")
