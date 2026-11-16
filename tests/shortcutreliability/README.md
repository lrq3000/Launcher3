# Focused shortcut-reliability checks

Run `python tests/shortcutreliability/run_tests.py` from the Launcher3 worktree. This uses the existing JDK 21, Android Studio Kotlin compiler, and installed Android 35 SDK API jar, with `JAVA_HOME`, `KOTLIN_HOME`, and `ANDROID_SDK_ROOT` overrides available. Nothing is downloaded or installed. Generated classes, baseline source copies, and store fixtures remain under `%LOCALAPPDATA%/Temp/opencode/launcher3-shortcut-tests-*`; they are regenerable and intentionally not committed or removed by the runner.

Run `python tests/shortcutreliability/run_tests.py --baseline-receiver` to reproduce the original boot-complete bug. The runner extracts the real baseline receiver using `git show`, compiles it into its temporary directory, and is expected to exit 1 with `Boot complete is not uninstall evidence: removed=1`. It never resets or edits the worktree.

## What this exercises

- Actual production `SdCardAvailableReceiver`, `WorkspaceAvailabilityStore`, `WorkspaceAvailability`, `ModelLauncherCallbacks`, `PackageTaskFactory`, and `PackageUpdatedTask` sources are compiled and executed; none of their availability/removal decisions are mirrored in tests.
- Store tests use real filesystem writes, checksums, atomic replacement, interrupted sibling files, reloads, and corruption/failure fixtures.
- The default command passes 1 receiver regression, 25 store assertions, 20 model-boundary assertions, and 6 review regression cases. These are not platform/JUnit case counts. Run an individual review case with `python tests/shortcutreliability/run_tests.py --finding N` (N=1 through 6). The observed historical RED assertions and correction limits are recorded in `docs/shortcut-reliability-report.md`.
- Model boundary checks use in-memory favorites/model/service doubles, retaining exact rows, components, profiles, identity and appearance through inferred absence, exact recovery, discarded callback tasks, restart reconciliation, and uninstall/reinstall ordering. The favorites double rejects unexpected snapshot/deletion arguments.
- `AppInfo` is a boundary double reproducing the documented clearing of suspension in its runtime-flag refresh, so the real update task's independent-state preservation is exercised. The in-memory model applies predicates and retains objects, but does not implement production SQLite transactions, repository publication, loader scheduling, or UI binding.
- The installed Kotlin parser checks edited Kotlin syntax separately. This is not full AOSP type checking.
- The new SQLite birth helper is separately compiled against the actual Android 35 SDK jar and production journal, with only LauncherSettings constants supplied by a fixture. This catches API/type mistakes in that Java helper, not SQLite transaction/runtime behavior.

## Review-case boundaries

1. Reuse the same item ID/component with production durable insertion generation; reopen the journal and retain the old tombstone without applying it to the new incarnation. The real allocator/SQLite close/reopen case is a separate unrun canonical test.
2. Interleave two real adapter owners, a positive-observation batch, and a confirmed removal at one canonical journal path; inspect a fresh disk snapshot afterward. Constructor reflection exists only in test utilities; production owners cannot bypass the factory.
3. Hold the real operation gate over an empty migration-destination fixture; a concurrent real removal callback must wait for copy completion and then reconcile that old row. The helper-switch/SQLite implementation itself is exercised only by the authored unrun canonical latch test.
4. Fail favorites snapshots or database identity, discard callback tasks, remove/add rapidly, reserve a later insertion, forget process-local owners, and reload actual journal bytes. The favorites double calls production generation reservation but simulates SQLite birth storage; it is not evidence that native SQLite commit/copy is correct.
5. Capture the real removal predicate on the worker, then forbid service/database calls and change database identity before evaluating it. A new generation with the same ID/component must not match.
6. The cache double really evicts package entries and rebuilds registered activities. Evaluate the actual package-update task with a saved target absent from registration and only a cache appearance; a fresh cache wrapper must still see that entry. This is map-backed restart-boundary evidence, not native cache-database restart evidence. The separate canonical actual IconCache test uses sandbox in-memory SQLite.

## What this cannot establish

These are narrow executable policy/task-boundary tests, not platform, UI, device, emulator, Android SQLite integration, or end-to-end validation. Full loader, favorites appearance serialization, database identity/reset/insertion/copy atomicity, native concurrency, lifecycle/retry scheduling, actual callback/volume semantics, and both binding implementations require the canonical AOSP tests and a platform build. The 14 added `tests/multivalentTests` regressions are included by `launcher-tests-src` and cover real SQLite/loader/cache behavior, but have not been run in the standalone Windows checkout. No full AOSP application type-check/build claim follows from the limited SDK API compilation.

Fixture Java classes intentionally expose only the boundaries used by the selected production sources. Do not ship these fixtures in launcher source sets, or broaden their passing results into a full-launcher build claim.
