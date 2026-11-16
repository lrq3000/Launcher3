#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0
"""Execute the production full binder and exact ModelCallbacks async methods with explicit UI boundaries."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = Path(tempfile.mkdtemp(prefix="launcher3-binding-tests-", dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode"))
JAVA = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-21")) / "bin"
KOTLIN = Path(os.environ.get("KOTLIN_HOME", "C:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc"))

def source(relative):
    if "--baseline-binding" in sys.argv:
        return subprocess.check_output(["git", "show", f"07b2e9cc7287a7afe83bfccccc8872e7f16d084d:{relative}"], cwd=ROOT, text=True)
    return (ROOT / relative).read_text(encoding="utf-8")

def braced_method(text, declaration):
    start = text.index(declaration)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]

root_callbacks = source("src/com/android/launcher3/ModelCallbacks.kt")
shell = (HERE / "binding/CallbacksShell.kt.txt").read_text(encoding="utf-8")
field = next(line for line in root_callbacks.splitlines() if "@VisibleForTesting var activeBindTask" in line)
methods = braced_method(root_callbacks, "    override fun bindCompleteModelAsync(")
helper_start = root_callbacks.index("    private inline fun <T, R> CompletionStage<T>.thenOn(")
helper_end = root_callbacks.index("    /** Binds the model while inflating items asynchronously */", helper_start)
methods += "\n" + root_callbacks[helper_start:helper_end]
methods += "\n" + braced_method(root_callbacks, "    private fun bindModelWithAsyncInflation(")
generated = OUT / "ExtractedCallbacks.kt"
generated.write_text(shell.replace("// ACTIVE_FIELD", field).replace("// PRODUCTION_METHODS", methods), encoding="utf-8")
completion = OUT / "ExtractedLoaderCompletion.kt"
commit = braced_method(source("src/com/android/launcher3/LauncherModel.kt"), "        fun commit() {")
completion.write_text((HERE / "binding/LoaderCompletionShell.kt.txt").read_text(encoding="utf-8").replace("// PRODUCTION_COMMIT", commit), encoding="utf-8")
stubs = HERE / "binding/stubs"
sources = list(stubs.rglob("*.java"))
common_names = ["android/content/Context.java", "android/content/BroadcastReceiver.java", "android/content/Intent.java", "android/content/ComponentName.java", "android/content/pm/LauncherApps.java", "android/content/pm/LauncherActivityInfo.java", "android/content/pm/ApplicationInfo.java", "android/content/pm/PackageInstaller.java", "android/content/pm/ShortcutInfo.java", "android/os/UserHandle.java", "android/os/UserManager.java", "androidx/annotation/NonNull.java", "com/android/launcher3/model/data/ItemInfoWithIcon.java", "com/android/launcher3/model/data/WorkspaceItemInfo.java"]
sources += [HERE / "stubs" / name for name in common_names]
sources.append(HERE / "stubs/android/os/storage/StorageManager.java")
sources += [HERE / "writer/stubs" / name for name in ["androidx/annotation/AnyThread.java", "com/android/launcher3/dagger/ApplicationContext.java", "com/android/launcher3/model/data/ItemInfo.java", "com/android/launcher3/util/ContentWriter.java", "android/content/ContentValues.java"]]
for name in ["model/BaseLauncherBinder.java", "model/data/WorkspaceData.kt"]:
    saved = OUT / Path(name).name
    saved.write_text(source("src/com/android/launcher3/" + name), encoding="utf-8")
    sources.append(saved)
guard = ROOT / "src/com/android/launcher3/model/WorkspaceBindingGuard.java"
if guard.exists():
    sources.append(guard)
sources += list((HERE / "binding").glob("*.kt")) + [generated, completion]
cp = os.pathsep.join([str(OUT), str(KOTLIN / "lib/kotlin-stdlib.jar")])

def run(command):
    result = subprocess.run(command)
    if result.returncode:
        raise SystemExit(result.returncode)

run([str(JAVA / "java"), "-cp", str(KOTLIN / "lib/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-kotlin-home", str(KOTLIN), "-nowarn", "-d", str(OUT), *map(str, sources)])
run([str(JAVA / "javac"), "-proc:none", "-cp", cp, "-d", str(OUT), *[str(p) for p in sources if p.suffix == ".java"]])
test_args = [str(OUT)]
if "--mode" in sys.argv:
    test_args.append(sys.argv[sys.argv.index("--mode") + 1])
run([str(JAVA / "java"), "-ea", "-cp", cp, "BindingDeliveryTestKt", *test_args])
print(f"Binding boundary artifacts retained at {OUT}")
