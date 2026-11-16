#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project
# SPDX-License-Identifier: Apache-2.0
"""Compile actual production Java with explicit JVM boundary doubles, not a mirrored policy."""
import os
from pathlib import Path
import subprocess
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
store = ROOT / "src/com/android/launcher3/model/availability/WorkspaceAvailabilityStore.java"
if store.exists():
    sources.append(store)
subprocess.run([str(JAVA_HOME / "bin/javac"), "-encoding", "UTF-8", "-d", str(OUT), *map(str, sources)], check=True)
subprocess.run([str(JAVA_HOME / "bin/java"), "-ea", "-cp", str(OUT), "ShortcutReliabilityTest", str(OUT)], check=True)
print(f"Generated classes and test stores retained at {OUT}")
