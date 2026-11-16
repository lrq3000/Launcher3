# Focused shortcut-reliability checks

Run `python tests/shortcutreliability/run_tests.py` from the Launcher3 worktree. This uses the existing JDK 21 and Android Studio Kotlin compiler, with `JAVA_HOME` and `KOTLIN_HOME` overrides available. Nothing is downloaded or installed. Generated classes, baseline source copies, and store fixtures remain under `%LOCALAPPDATA%/Temp/opencode/launcher3-shortcut-tests-*`; they are regenerable and intentionally not committed or removed by the runner.

Run `python tests/shortcutreliability/run_tests.py --baseline-receiver` to reproduce the original boot-complete bug. The runner extracts the real baseline receiver using `git show`, compiles it into its temporary directory, and is expected to exit 1 with `Boot complete is not uninstall evidence: removed=1`. It never resets or edits the worktree.

## What this exercises

- Actual production `SdCardAvailableReceiver`, `WorkspaceAvailabilityStore`, `WorkspaceAvailability`, `ModelLauncherCallbacks`, `PackageTaskFactory`, and `PackageUpdatedTask` sources are compiled and executed; none of their availability/removal decisions are mirrored in tests.
- Store tests use real filesystem writes, checksums, atomic replacement, interrupted sibling files, reloads, and corruption/failure fixtures.
- Model boundary checks use in-memory favorites/model/service doubles, retaining exact rows, components, profiles, identity and appearance through inferred absence, exact recovery, discarded callback tasks, restart reconciliation, and uninstall/reinstall ordering. The favorites double rejects unexpected snapshot/deletion arguments.
- `AppInfo` is a boundary double reproducing the documented clearing of suspension in its runtime-flag refresh, so the real update task's independent-state preservation is exercised. The in-memory model applies predicates and retains objects, but does not implement production SQLite transactions, repository publication, loader scheduling, or UI binding.
- The installed Kotlin parser checks edited Kotlin syntax separately. This is not full AOSP type checking.

## What this cannot establish

These are narrow executable policy/task-boundary tests, not platform, UI, device, emulator, SQLite integration, or end-to-end validation. Full loader, favorites appearance serialization, database identity/reset, lifecycle/retry scheduling, actual callback/volume semantics, and both binding implementations require the canonical AOSP tests and a platform build. The added `tests/multivalentTests` regressions are included by `launcher-tests-src` and cover real SQLite/loader behavior, but have not been run in the standalone Windows checkout.

Fixture Java classes intentionally expose only the boundaries used by the selected production sources. Do not ship these fixtures in launcher source sets, or broaden their passing results into a full-launcher build claim.
