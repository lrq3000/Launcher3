#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0
"""Run the real ModelWriter schedulers and birth helper against explicit platform/SQL boundaries."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
JAVA = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-21")) / "bin"
KOTLIN = Path(os.environ.get("KOTLIN_HOME", "C:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc"))
OUT = Path(tempfile.mkdtemp(prefix="launcher3-writer-tests-", dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode"))
overrides = HERE / "writer/stubs"
sources = [p for p in (HERE / "stubs").rglob("*.java") if not (overrides / p.relative_to(HERE / "stubs")).exists() and p.name != "ItemInstallQueue.java"]
sources += list(overrides.rglob("*.java"))
sources += [ROOT / f"src/com/android/launcher3/model/availability/{name}.java" for name in ("WorkspaceAvailabilityStore", "WorkspaceAvailabilityDb")]
sources += [ROOT / "src/com/android/launcher3/model/tasks/AddWorkspaceItemsTask.java", ROOT / "src/com/android/launcher3/model/ItemInstallQueue.java"]
kotlin = [ROOT / f"src/com/android/launcher3/{name}.kt" for name in ("model/ModelWriter", "model/IModelWriter", "ui/LauncherUiStateNotifier")]
kotlin += list((HERE / "writer").glob("*Test.kt"))
if "--baseline-consumer" in sys.argv or "--baseline-queue" in sys.argv:
    # Replay the exact coordinator-reviewed integration failure without changing the worktree.
    for paths in (sources, kotlin):
        for index, path in enumerate(paths):
            baseline_names = {"AddWorkspaceItemsTask.java", "ItemInstallQueue.java"}
            if "--baseline-consumer" in sys.argv:
                baseline_names |= {"ModelWriter.kt", "WorkspaceAvailabilityDb.java"}
            if path.name in baseline_names:
                relative = path.relative_to(ROOT).as_posix()
                saved = OUT / "baseline" / relative
                saved.parent.mkdir(parents=True, exist_ok=True)
                saved.write_bytes(subprocess.check_output(["git", "show", f"0552f21428eb781b7280af3bb4c2b59678ccd73a:{relative}"], cwd=ROOT))
                paths[index] = saved
cp = os.pathsep.join([str(OUT), str(KOTLIN / "lib/kotlin-stdlib.jar")])
def run(command):
    result = subprocess.run(command)
    if result.returncode:
        raise SystemExit(result.returncode)

run([str(JAVA / "java"), "-cp", str(KOTLIN / "lib/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-kotlin-home", str(KOTLIN), "-nowarn", "-d", str(OUT), *map(str, kotlin + sources)])
run([str(JAVA / "javac"), "-proc:none", "-encoding", "UTF-8", "-cp", cp, "-d", str(OUT), *map(str, sources)])
cases = ["WriterFailureTestKt", "QueueFailureTestKt"]
if "--case" in sys.argv:
    cases = [{"writer": "WriterFailureTestKt", "queue": "QueueFailureTestKt"}[sys.argv[sys.argv.index("--case") + 1]]]
for case in cases:
    run([str(JAVA / "java"), "-ea", "-cp", cp, case, str(OUT)])
print(f"Writer boundary artifacts retained at {OUT}")
